package com.example.bookingagent.sms.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.example.bookingagent.sms.booking.BookingOrchestrator
import com.example.bookingagent.sms.data.db.AppDatabase
import com.example.bookingagent.sms.data.repo.BookingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) {
            return
        }

        val sender = extractSender(messages)
        val messageBody = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        val pendingResult = goAsync()
        val orchestrator = BookingOrchestrator(
            bookingRepository = BookingRepository(
                AppDatabase.getInstance(context).bookingDao(),
            ),
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                orchestrator.handleIncomingSms(
                    sender = sender,
                    messageBody = messageBody,
                )
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to save incoming SMS", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun extractSender(messages: Array<SmsMessage>): String =
        messages.firstNotNullOfOrNull { smsMessage ->
            smsMessage.originatingAddress?.takeIf { it.isNotBlank() }
        }.orEmpty()

    private companion object {
        const val TAG = "IncomingSmsReceiver"
    }
}
