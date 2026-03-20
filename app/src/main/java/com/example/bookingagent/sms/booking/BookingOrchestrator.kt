package com.example.bookingagent.sms.booking

import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.model.BookingStatus
import com.example.bookingagent.sms.data.repo.BookingRepository
import com.example.bookingagent.sms.sms.ParsedSms
import com.example.bookingagent.sms.sms.SmsParser

class BookingOrchestrator(
    private val bookingRepository: BookingRepository,
    private val smsParser: SmsParser = SmsParser(),
) {
    suspend fun handleIncomingSms(sender: String, messageBody: String) {
        val booking = when (val parsedSms = smsParser.parse(messageBody)) {
            is ParsedSms.Offer -> {
                val shiftInfo = parsedSms.shiftInfo
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
                    createdAt = System.currentTimeMillis(),
                )
            }

            is ParsedSms.Confirmation -> {
                val shiftInfo = parsedSms.shiftInfo
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
                )
            }

            null -> BookingEntity(
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
            )
        }

        bookingRepository.insert(booking)
    }

    private companion object {
        const val MESSAGE_TYPE_OFFER = "offer"
        const val MESSAGE_TYPE_CONFIRMATION = "confirmation"
        const val MESSAGE_TYPE_UNKNOWN = "unknown"
    }
}
