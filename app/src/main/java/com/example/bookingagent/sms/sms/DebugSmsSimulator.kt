package com.example.bookingagent.sms.sms

import com.example.bookingagent.sms.booking.BookingOrchestrator

class DebugSmsSimulator(
    private val bookingOrchestrator: BookingOrchestrator,
) {
    suspend fun simulateIncomingSms(sender: String, messageBody: String) {
        bookingOrchestrator.handleIncomingSms(
            sender = sender,
            messageBody = messageBody,
        )
    }
}
