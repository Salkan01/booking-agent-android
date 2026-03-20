package com.example.bookingagent.sms.sms

import android.telephony.SmsManager

class SmsSender {
    fun send(destination: String, messageBody: String) {
        SmsManager.getDefault().sendTextMessage(destination, null, messageBody, null, null)
    }
}
