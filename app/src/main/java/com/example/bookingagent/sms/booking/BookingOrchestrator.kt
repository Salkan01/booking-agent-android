package com.example.bookingagent.sms.booking

import com.example.bookingagent.sms.calendar.CalendarWriter
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.model.BookingStatus
import com.example.bookingagent.sms.data.repo.BookingRepository
import com.example.bookingagent.sms.settings.SettingsRepository
import com.example.bookingagent.sms.sms.ParsedSms
import com.example.bookingagent.sms.sms.ReplyDecision
import com.example.bookingagent.sms.sms.ShiftRuleEvaluator
import com.example.bookingagent.sms.sms.SmsParser
import com.example.bookingagent.sms.sms.SmsSender

class BookingOrchestrator(
    private val bookingRepository: BookingRepository,
    private val smsParser: SmsParser = SmsParser(),
    private val shiftRuleEvaluator: ShiftRuleEvaluator = ShiftRuleEvaluator(),
    private val smsSender: SmsSender = SmsSender(),
    private val calendarWriter: CalendarWriter,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun handleIncomingSms(sender: String, messageBody: String) {
        val appSettings = settingsRepository.getCurrentSettings()

        when (val parsedSms = smsParser.parse(messageBody)) {
            is ParsedSms.Offer -> {
                val shiftInfo = parsedSms.shiftInfo
                val booking = BookingEntity(
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
                    createdAt = System.currentTimeMillis(),
                )
                val bookingId = bookingRepository.insert(booking)
                if (!appSettings.automationEnabled) {
                    return
                }

                val decision = shiftRuleEvaluator.evaluate(shiftInfo)
                val replyText = decision.name

                smsSender.send(sender, replyText)
                bookingRepository.update(
                    booking.copy(
                        id = bookingId,
                        replySent = replyText,
                        status = decision.toBookingStatus(),
                    ),
                )
            }

            is ParsedSms.Confirmation -> {
                val shiftInfo = parsedSms.shiftInfo
                bookingRepository.insert(
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
                        createdAt = System.currentTimeMillis(),
                    ),
                )

                if (!appSettings.automationEnabled) {
                    return
                }

                val acceptedOffer = bookingRepository.findLatestAcceptedOffer(
                    sender = sender,
                    shiftDate = shiftInfo.date.toString(),
                    startTime = shiftInfo.startTime.toString(),
                    endTime = shiftInfo.endTime.toString(),
                    details = shiftInfo.details,
                ) ?: return

                val eventId = calendarWriter.createEvent(
                    shiftInfo = shiftInfo,
                    rawSms = messageBody,
                    calendarName = appSettings.targetCalendarName,
                    eventTitle = appSettings.eventTitle,
                )

                bookingRepository.update(
                    acceptedOffer.copy(
                        status = if (eventId != null) {
                            BookingStatus.EVENT_CREATED
                        } else {
                            BookingStatus.FAILED
                        },
                        eventId = eventId,
                    ),
                )
            }

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
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private companion object {
        const val MESSAGE_TYPE_OFFER = "offer"
        const val MESSAGE_TYPE_CONFIRMATION = "confirmation"
        const val MESSAGE_TYPE_UNKNOWN = "unknown"
    }
}

private fun ReplyDecision.toBookingStatus(): BookingStatus =
    when (this) {
        ReplyDecision.J -> BookingStatus.REPLIED_J
        ReplyDecision.N -> BookingStatus.REPLIED_N
    }
