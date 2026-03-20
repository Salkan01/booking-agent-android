package com.example.bookingagent.sms.booking

import com.example.bookingagent.sms.calendar.CalendarWriteResult
import com.example.bookingagent.sms.calendar.CalendarWriter
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.model.BookingStatus
import com.example.bookingagent.sms.data.model.ReviewState
import com.example.bookingagent.sms.data.repo.BookingRepository
import com.example.bookingagent.sms.settings.AppSettings
import com.example.bookingagent.sms.sms.DryRunSmsGateway
import com.example.bookingagent.sms.sms.ParsedSms
import com.example.bookingagent.sms.sms.RealSmsGateway
import com.example.bookingagent.sms.sms.ReplyDecision
import com.example.bookingagent.sms.sms.ShiftInfo
import com.example.bookingagent.sms.sms.ShiftRuleEvaluator
import com.example.bookingagent.sms.sms.SmsGateway
import com.example.bookingagent.sms.sms.SmsGatewayResult
import com.example.bookingagent.sms.sms.SmsParser
import java.time.LocalDate
import java.time.LocalTime

class BookingOrchestrator(
    private val bookingRepository: BookingRepository,
    private val smsParser: SmsParser = SmsParser(),
    private val shiftRuleEvaluator: ShiftRuleEvaluator = ShiftRuleEvaluator(),
    private val realSmsGateway: SmsGateway = RealSmsGateway(),
    private val dryRunSmsGateway: SmsGateway = DryRunSmsGateway(),
    private val calendarWriter: CalendarWriter,
    private val appSettingsProvider: () -> AppSettings,
) {
    suspend fun handleIncomingSms(sender: String, messageBody: String) {
        val appSettings = appSettingsProvider()

        when (val parsedSms = smsParser.parse(messageBody)) {
            is ParsedSms.Offer -> handleOffer(
                sender = sender,
                messageBody = messageBody,
                shiftInfo = parsedSms.shiftInfo,
                appSettings = appSettings,
            )

            is ParsedSms.Confirmation -> handleConfirmation(
                sender = sender,
                messageBody = messageBody,
                shiftInfo = parsedSms.shiftInfo,
                appSettings = appSettings,
            )

            null -> {
                bookingRepository.insert(
                    BookingEntity(
                        sender = sender,
                        rawSms = messageBody,
                        messageType = MESSAGE_TYPE_UNKNOWN,
                        shiftDate = null,
                        startTime = null,
                        endTime = null,
                        details = null,
                        replySent = null,
                        recommendedReply = null,
                        recommendationFromRules = false,
                        intendedReply = null,
                        reviewState = ReviewState.AUTO_PROCESSED,
                        status = BookingStatus.UNKNOWN_SMS,
                        matchedBookingId = null,
                        eventId = null,
                        errorMessage = null,
                        dryRun = appSettings.dryRunMode,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    suspend fun approveOffer(bookingId: Long) {
        processOfferDecision(
            bookingId = bookingId,
            decision = ReplyDecision.J,
            reviewState = ReviewState.APPROVED,
        )
    }

    suspend fun rejectOffer(bookingId: Long) {
        processOfferDecision(
            bookingId = bookingId,
            decision = ReplyDecision.N,
            reviewState = ReviewState.REJECTED,
        )
    }

    suspend fun rerunDecision(bookingId: Long) {
        val booking = bookingRepository.getById(bookingId) ?: return
        if (booking.messageType != MESSAGE_TYPE_OFFER || booking.status == BookingStatus.DUPLICATE_IGNORED) {
            return
        }

        val shiftInfo = booking.toShiftInfoOrNull() ?: return
        val recommendedReply = shiftRuleEvaluator.evaluate(shiftInfo).name
        bookingRepository.update(
            booking.copy(
                recommendedReply = recommendedReply,
                recommendationFromRules = true,
                errorMessage = booking.errorMessage.takeUnless { it == NO_MATCHING_OFFER_MESSAGE },
            ),
        )
    }

    suspend fun retryConfirmationMatch(bookingId: Long) {
        val booking = bookingRepository.getById(bookingId) ?: return
        if (booking.messageType != MESSAGE_TYPE_CONFIRMATION || booking.status == BookingStatus.DUPLICATE_IGNORED) {
            return
        }

        val matchedOffer = findMatchingAcceptedOffer(booking)
        bookingRepository.update(
            booking.copy(
                matchedBookingId = matchedOffer?.id,
                status = when {
                    hasEventBeenProcessed(booking) -> booking.status
                    matchedOffer != null -> BookingStatus.CONFIRMED
                    else -> BookingStatus.FAILED
                },
                errorMessage = if (matchedOffer != null) {
                    null
                } else {
                    NO_MATCHING_OFFER_MESSAGE
                },
            ),
        )
    }

    suspend fun createEventManually(bookingId: Long) {
        val appSettings = appSettingsProvider()
        val booking = bookingRepository.getById(bookingId) ?: return
        if (booking.messageType != MESSAGE_TYPE_CONFIRMATION ||
            booking.status == BookingStatus.DUPLICATE_IGNORED ||
            hasEventBeenProcessed(booking)
        ) {
            return
        }

        val refreshedBooking = refreshConfirmationMatch(booking)
        processConfirmationEvent(
            booking = refreshedBooking,
            appSettings = appSettings,
            reviewState = ReviewState.APPROVED,
        )
    }

    private suspend fun handleOffer(
        sender: String,
        messageBody: String,
        shiftInfo: ShiftInfo,
        appSettings: AppSettings,
    ) {
        val recommendedDecision = shiftRuleEvaluator.evaluate(shiftInfo)
        val savedBooking = saveBooking(
            BookingEntity(
                sender = sender,
                rawSms = messageBody,
                messageType = MESSAGE_TYPE_OFFER,
                shiftDate = shiftInfo.date.toString(),
                startTime = shiftInfo.startTime.toString(),
                endTime = shiftInfo.endTime.toString(),
                details = shiftInfo.details,
                replySent = null,
                recommendedReply = recommendedDecision.name,
                recommendationFromRules = true,
                intendedReply = null,
                reviewState = reviewStateForIncoming(appSettings),
                status = BookingStatus.RECEIVED_OFFER,
                matchedBookingId = null,
                eventId = null,
                errorMessage = null,
                dryRun = appSettings.dryRunMode,
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (isDuplicate(sender = sender, rawSms = messageBody, savedBooking = savedBooking)) {
            bookingRepository.update(
                savedBooking.copy(
                    reviewState = ReviewState.AUTO_PROCESSED,
                    status = BookingStatus.DUPLICATE_IGNORED,
                    errorMessage = DUPLICATE_MESSAGE,
                ),
            )
            return
        }

        if (shouldQueueForReview(appSettings)) {
            return
        }

        processOfferReply(
            booking = savedBooking,
            decision = recommendedDecision,
            appSettings = appSettings,
            reviewState = ReviewState.AUTO_PROCESSED,
        )
    }

    private suspend fun handleConfirmation(
        sender: String,
        messageBody: String,
        shiftInfo: ShiftInfo,
        appSettings: AppSettings,
    ) {
        val savedBooking = saveBooking(
            BookingEntity(
                sender = sender,
                rawSms = messageBody,
                messageType = MESSAGE_TYPE_CONFIRMATION,
                shiftDate = shiftInfo.date.toString(),
                startTime = shiftInfo.startTime.toString(),
                endTime = shiftInfo.endTime.toString(),
                details = shiftInfo.details,
                replySent = null,
                recommendedReply = null,
                recommendationFromRules = false,
                intendedReply = null,
                reviewState = reviewStateForIncoming(appSettings),
                status = BookingStatus.CONFIRMED,
                matchedBookingId = null,
                eventId = null,
                errorMessage = null,
                dryRun = appSettings.dryRunMode,
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (isDuplicate(sender = sender, rawSms = messageBody, savedBooking = savedBooking)) {
            bookingRepository.update(
                savedBooking.copy(
                    reviewState = ReviewState.AUTO_PROCESSED,
                    status = BookingStatus.DUPLICATE_IGNORED,
                    errorMessage = DUPLICATE_MESSAGE,
                ),
            )
            return
        }

        val refreshedBooking = refreshConfirmationMatch(savedBooking)
        if (shouldQueueForReview(appSettings)) {
            return
        }

        processConfirmationEvent(
            booking = refreshedBooking,
            appSettings = appSettings,
            reviewState = ReviewState.AUTO_PROCESSED,
        )
    }

    private suspend fun processOfferDecision(
        bookingId: Long,
        decision: ReplyDecision,
        reviewState: ReviewState,
    ) {
        val appSettings = appSettingsProvider()
        val booking = bookingRepository.getById(bookingId) ?: return
        if (booking.messageType != MESSAGE_TYPE_OFFER ||
            booking.status == BookingStatus.DUPLICATE_IGNORED ||
            hasReplyBeenProcessed(booking)
        ) {
            return
        }

        processOfferReply(
            booking = booking,
            decision = decision,
            appSettings = appSettings,
            reviewState = reviewState,
        )
    }

    private suspend fun processOfferReply(
        booking: BookingEntity,
        decision: ReplyDecision,
        appSettings: AppSettings,
        reviewState: ReviewState,
    ) {
        if (hasReplyBeenProcessed(booking) || booking.status == BookingStatus.DUPLICATE_IGNORED) {
            return
        }

        val replyText = decision.name
        val smsGateway = selectSmsGateway(appSettings)
        when (val sendResult = smsGateway.send(booking.sender, replyText)) {
            SmsGatewayResult.Success -> {
                bookingRepository.update(
                    booking.copy(
                        replySent = if (appSettings.dryRunMode) null else replyText,
                        intendedReply = if (appSettings.dryRunMode) replyText else null,
                        reviewState = reviewState,
                        status = decision.toBookingStatus(),
                        errorMessage = if (appSettings.dryRunMode) {
                            DRY_RUN_REPLY_MESSAGE
                        } else {
                            null
                        },
                        dryRun = appSettings.dryRunMode,
                    ),
                )
            }

            is SmsGatewayResult.Failure -> {
                bookingRepository.update(
                    booking.copy(
                        replySent = null,
                        intendedReply = replyText,
                        reviewState = reviewState,
                        status = BookingStatus.FAILED,
                        errorMessage = "Failed to send reply \"$replyText\": ${sendResult.errorMessage}",
                        dryRun = appSettings.dryRunMode,
                    ),
                )
            }
        }
    }

    private suspend fun processConfirmationEvent(
        booking: BookingEntity,
        appSettings: AppSettings,
        reviewState: ReviewState,
    ) {
        if (hasEventBeenProcessed(booking) || booking.status == BookingStatus.DUPLICATE_IGNORED) {
            return
        }

        val matchedOffer = findMatchingAcceptedOffer(booking)
        if (matchedOffer == null) {
            bookingRepository.update(
                booking.copy(
                    reviewState = reviewState,
                    status = BookingStatus.FAILED,
                    matchedBookingId = null,
                    errorMessage = NO_MATCHING_OFFER_MESSAGE,
                    dryRun = appSettings.dryRunMode,
                ),
            )
            return
        }

        if (appSettings.dryRunMode) {
            bookingRepository.update(
                booking.copy(
                    reviewState = reviewState,
                    status = BookingStatus.EVENT_CREATED,
                    matchedBookingId = matchedOffer.id,
                    eventId = null,
                    errorMessage = dryRunEventMessage(appSettings),
                    dryRun = true,
                ),
            )
            return
        }

        val shiftInfo = booking.toShiftInfoOrNull()
        if (shiftInfo == null) {
            bookingRepository.update(
                booking.copy(
                    reviewState = reviewState,
                    status = BookingStatus.FAILED,
                    matchedBookingId = matchedOffer.id,
                    errorMessage = INVALID_SHIFT_MESSAGE,
                    dryRun = false,
                ),
            )
            return
        }

        when (
            val calendarResult = calendarWriter.createEvent(
                shiftInfo = shiftInfo,
                rawSms = booking.rawSms,
                calendarName = appSettings.targetCalendarName,
                eventTitle = appSettings.eventTitle,
            )
        ) {
            is CalendarWriteResult.Success -> {
                bookingRepository.update(
                    booking.copy(
                        reviewState = reviewState,
                        status = BookingStatus.EVENT_CREATED,
                        matchedBookingId = matchedOffer.id,
                        eventId = calendarResult.eventId,
                        errorMessage = null,
                        dryRun = false,
                    ),
                )
            }

            is CalendarWriteResult.Failure -> {
                bookingRepository.update(
                    booking.copy(
                        reviewState = reviewState,
                        status = BookingStatus.FAILED,
                        matchedBookingId = matchedOffer.id,
                        errorMessage = calendarResult.errorMessage,
                        dryRun = false,
                    ),
                )
            }
        }
    }

    private suspend fun refreshConfirmationMatch(booking: BookingEntity): BookingEntity {
        val matchedOffer = findMatchingAcceptedOffer(booking)
        val updatedBooking = booking.copy(
            matchedBookingId = matchedOffer?.id,
            errorMessage = if (matchedOffer != null) {
                booking.errorMessage.takeUnless { it == NO_MATCHING_OFFER_MESSAGE }
            } else {
                NO_MATCHING_OFFER_MESSAGE
            },
        )
        bookingRepository.update(updatedBooking)
        return updatedBooking
    }

    private suspend fun findMatchingAcceptedOffer(booking: BookingEntity): BookingEntity? {
        val shiftDate = booking.shiftDate ?: return null
        val startTime = booking.startTime ?: return null
        val endTime = booking.endTime ?: return null
        val details = booking.details ?: return null

        return bookingRepository.findLatestAcceptedOffer(
            sender = booking.sender,
            shiftDate = shiftDate,
            startTime = startTime,
            endTime = endTime,
            details = details,
        )
    }

    private suspend fun saveBooking(booking: BookingEntity): BookingEntity {
        val id = bookingRepository.insert(booking)
        return booking.copy(id = id)
    }

    private suspend fun isDuplicate(
        sender: String,
        rawSms: String,
        savedBooking: BookingEntity,
    ): Boolean =
        bookingRepository.findRecentDuplicate(
            sender = sender,
            rawSms = rawSms,
            createdAfter = savedBooking.createdAt - DUPLICATE_WINDOW_MS,
            createdBefore = savedBooking.createdAt,
            excludeId = savedBooking.id,
        ) != null

    private fun shouldQueueForReview(appSettings: AppSettings): Boolean =
        !appSettings.automationEnabled || appSettings.manualReviewMode

    private fun reviewStateForIncoming(appSettings: AppSettings): ReviewState =
        if (shouldQueueForReview(appSettings)) {
            ReviewState.PENDING_REVIEW
        } else {
            ReviewState.AUTO_PROCESSED
        }

    private fun selectSmsGateway(appSettings: AppSettings): SmsGateway =
        if (appSettings.dryRunMode) {
            dryRunSmsGateway
        } else {
            realSmsGateway
        }

    private fun hasReplyBeenProcessed(booking: BookingEntity): Boolean =
        booking.replySent != null ||
            booking.intendedReply != null ||
            booking.status == BookingStatus.REPLIED_J ||
            booking.status == BookingStatus.REPLIED_N

    private fun hasEventBeenProcessed(booking: BookingEntity): Boolean =
        booking.eventId != null || booking.status == BookingStatus.EVENT_CREATED

    private fun BookingEntity.toShiftInfoOrNull(): ShiftInfo? {
        val date = shiftDate ?: return null
        val start = startTime ?: return null
        val end = endTime ?: return null
        val shiftDetails = details ?: return null

        return runCatching {
            ShiftInfo(
                date = LocalDate.parse(date),
                startTime = LocalTime.parse(start),
                endTime = LocalTime.parse(end),
                details = shiftDetails,
            )
        }.getOrNull()
    }

    private fun dryRunEventMessage(appSettings: AppSettings): String =
        "Dry run mode: would create event \"${appSettings.eventTitle}\" in calendar \"${appSettings.targetCalendarName}\""

    private companion object {
        const val MESSAGE_TYPE_OFFER = "offer"
        const val MESSAGE_TYPE_CONFIRMATION = "confirmation"
        const val MESSAGE_TYPE_UNKNOWN = "unknown"
        const val DUPLICATE_WINDOW_MS = 5 * 60 * 1000L
        const val DUPLICATE_MESSAGE = "Duplicate SMS detected; side effects skipped"
        const val NO_MATCHING_OFFER_MESSAGE = "No matching accepted offer found"
        const val DRY_RUN_REPLY_MESSAGE = "Dry run mode: no real SMS was sent"
        const val INVALID_SHIFT_MESSAGE = "Stored shift data is invalid"
    }
}

private fun ReplyDecision.toBookingStatus(): BookingStatus =
    when (this) {
        ReplyDecision.J -> BookingStatus.REPLIED_J
        ReplyDecision.N -> BookingStatus.REPLIED_N
    }
