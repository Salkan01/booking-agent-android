package com.example.bookingagent.sms.sms

class RealSmsGateway(
    private val smsSender: SmsSender = SmsSender(),
) : SmsGateway {
    override fun send(destination: String, messageBody: String): SmsGatewayResult =
        when (val sendResult = smsSender.send(destination, messageBody)) {
            SmsSendResult.Success -> SmsGatewayResult.Success
            is SmsSendResult.Failure -> SmsGatewayResult.Failure(sendResult.errorMessage)
        }
}
