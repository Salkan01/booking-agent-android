package com.example.bookingagent.sms.sms

open class DryRunSmsGateway : SmsGateway {
    override fun send(destination: String, messageBody: String): SmsGatewayResult = SmsGatewayResult.Success
}
