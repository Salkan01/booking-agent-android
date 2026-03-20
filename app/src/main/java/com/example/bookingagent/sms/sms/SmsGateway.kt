package com.example.bookingagent.sms.sms

sealed interface SmsGatewayResult {
    data object Success : SmsGatewayResult

    data class Failure(
        val errorMessage: String,
    ) : SmsGatewayResult
}

interface SmsGateway {
    fun send(destination: String, messageBody: String): SmsGatewayResult
}
