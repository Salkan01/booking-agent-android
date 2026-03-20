package com.example.bookingagent.sms.sms

import android.telephony.SmsManager

sealed interface SmsSendResult {
    data object Success : SmsSendResult

    data class Failure(
        val errorMessage: String,
    ) : SmsSendResult
}

open class SmsSender {
    open fun send(destination: String, messageBody: String): SmsSendResult =
        runCatching {
            SmsManager.getDefault().sendTextMessage(destination, null, messageBody, null, null)
        }.fold(
            onSuccess = { SmsSendResult.Success },
            onFailure = { throwable ->
                SmsSendResult.Failure(
                    errorMessage = throwable.message ?: "Failed to send SMS reply",
                )
            },
        )
}
