package com.example.bookingagent.sms.booking

import com.example.bookingagent.sms.calendar.CalendarWriteResult
import com.example.bookingagent.sms.calendar.CalendarWriter
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.model.BookingStatus
import com.example.bookingagent.sms.data.repo.BookingRepository
import com.example.bookingagent.sms.settings.AppSettings
import com.example.bookingagent.sms.sms.ParsedSms
import com.example.bookingagent.sms.sms.ReplyDecision
import com.example.bookingagent.sms.sms.ShiftInfo
import com.example.bookingagent.sms.sms.ShiftRuleEvaluator
import com.example.bookingagent.sms.sms.SmsParser
import com.example.bookingagent.sms.sms.SmsSendResult
import com.example.bookingagent.sms.sms.SmsSender

class BookingOrchestrator(
    private val bookingRepository: BookingRepository,
    private val smsParser: SmsParser = SmsParser(),
    private val shiftRuleEvaluator: ShiftRuleEvaluator = ShiftRuleEvaluator(),
    private val smsSender: SmsSender = SmsSender(),
    private val calendarWriter: CalendarWriter,
    private val appSettingsProvider: () -> AppSettings,
) {
    suspend fun handleIncomingSms(sender: String, messageBody: String) {
        when (val parsedSms = smsParser.parse(messageBody)) {
            is ParsedSms.Offer -> handleOffer(
                sender = sender,
                messageBody = messageBody,
                shiftInfo = parsedSms.shiftInfo,
            )

            is ParsedSms.Confirmation -> handleConfirmation(
                sender = sender,
                messageBody = messageBody,
                shiftInfo = parsedSms.shiftInfo,
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
                        status = BookingStatus.UNKNOWN_SMS,
                        eventId = null,
                        errorMessage = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private suspend fun handleOffer(
        sender: String,
        messageBody: String,
        shiftInfo: ShiftInfo,
    ) {
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
                status = BookingStatus.RECEIVED_OFFER,
                eventId = null,
                errorMessage = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (isDuplicate(sender = sender, rawSms = messageBody, savedBooking = savedBooking)) {
            bookingRepository.update(
                savedBooking.copy(
                    status = BookingStatus.DUPLICATE_IGNORED,
                    errorMessage = DUPLICATE_MESSAGE,
                ),
            )
            return
        }

        val appSettings = appSettingsProvider()
        if (!appSettings.automationEnabled) {
            bookingRepository.update(
                savedBooking.copy(
                    errorMessage = AUTOMATION_DISABLED_REPLY_MESSAGE,
                ),
            )
            return
        }

        val decision = shiftRuleEvaluator.evaluate(shiftInfo)
        val replyText = decision.name
        when (val sendResult = smsSender.send(sender, replyText)) {
            SmsSendResult.Success -> {
                bookingRepository.update(
                    savedBooking.copy(
                        replySent = replyText,
                        status = decision.toBookingStatus(),
                        errorMessage = null,
                    ),
                )
            }

            is SmsSendResult.Failure -> {
                bookingRepository.update(
                    savedBooking.copy(
                        status = BookingStatus.FAILED,
                        errorMessage = "Failed to send reply \"$replyText\": ${sendResult.errorMessage}",
                    ),
                )
            }
        }
    }

    private suspend fun handleConfirmation(
        sender: String,
        messageBody: String,
        shiftInfo: ShiftInfo,
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
                status = BookingStatus.CONFIRMED,
                eventId = null,
                errorMessage = null,
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (isDuplicate(sender = sender, rawSms = messageBody, savedBooking = savedBooking)) {
            bookingRepository.update(
                savedBooking.copy(
                    status = BookingStatus.DUPLICATE_IGNORED,
                    errorMessage = DUPLICATE_MESSAGE,
                ),
            )
            return
        }

        val appSettings = appSettingsProvider()
        if (!appSettings.automationEnabled) {
            bookingRepository.update(
                savedBooking.copy(
                    errorMessage = AUTOMATION_DISABLED_EVENT_MESSAGE,
                ),
            )
            return
        }

        val acceptedOffer = bookingRepository.findLatestAcceptedOffer(
            sender = sender,
            shiftDate = shiftInfo.date.toString(),
            startTime = shiftInfo.startTime.toString(),
            endTime = shiftInfo.endTime.toString(),
            details = shiftInfo.details,
        )

        if (acceptedOffer == null) {
            bookingRepository.update(
                savedBooking.copy(
                    status = BookingStatus.FAILED,
                    errorMessage = NO_MATCHING_OFFER_MESSAGE,
                ),
            )
            return
        }

        when (
            val calendarResult = calendarWriter.createEvent(
                shiftInfo = shiftInfo,
                rawSms = messageBody,
                calendarName = appSettings.targetCalendarName,
                eventTitle = appSettings.eventTitle,
            )
        ) {
            is CalendarWriteResult.Success -> {
                bookingRepository.update(
                    savedBooking.copy(
                        status = BookingStatus.EVENT_CREATED,
                        eventId = calendarResult.eventId,
                        errorMessage = null,
                    ),
                )
            }

            is CalendarWriteResult.Failure -> {
                bookingRepository.update(
                    savedBooking.copy(
                        status = BookingStatus.FAILED,
                        errorMessage = calendarResult.errorMessage,
                    ),
                )
            }
        }
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

    private companion object {
        const val MESSAGE_TYPE_OFFER = "offer"
        const val MESSAGE_TYPE_CONFIRMATION = "confirmation"
        const val MESSAGE_TYPE_UNKNOWN = "unknown"
        const val DUPLICATE_WINDOW_MS = 5 * 60 * 1000L
        const val DUPLICATE_MESSAGE = "Duplicate SMS detected; side effects skipped"
        const val AUTOMATION_DISABLED_REPLY_MESSAGE = "Automation disabled; reply skipped"
        const val AUTOMATION_DISABLED_EVENT_MESSAGE = "Automation disabled; event creation skipped"
        const val NO_MATCHING_OFFER_MESSAGE = "No matching accepted offer found"
    }
}

private fun ReplyDecision.toBookingStatus(): BookingStatus =
    when (this) {
        ReplyDecision.J -> BookingStatus.REPLIED_J
        ReplyDecision.N -> BookingStatus.REPLIED_N
    }
