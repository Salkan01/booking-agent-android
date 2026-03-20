package com.example.bookingagent.sms.sms

import java.time.LocalDate
import java.time.LocalTime

data class ShiftInfo(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val details: String,
)

sealed class ParsedSms {
    abstract val shiftInfo: ShiftInfo

    data class Offer(override val shiftInfo: ShiftInfo) : ParsedSms()

    data class Confirmation(override val shiftInfo: ShiftInfo) : ParsedSms()
}

class SmsParser {
    fun parse(message: String): ParsedSms? {
        val normalizedMessage = message.trim()
        return parseOffer(normalizedMessage) ?: parseConfirmation(normalizedMessage)
    }

    private fun parseOffer(message: String): ParsedSms.Offer? {
        val matchResult = OFFER_REGEX.matchEntire(message) ?: return null
        val shiftInfo = matchToShiftInfo(matchResult) ?: return null
        return ParsedSms.Offer(shiftInfo)
    }

    private fun parseConfirmation(message: String): ParsedSms.Confirmation? {
        val matchResult = CONFIRMATION_REGEX.matchEntire(message) ?: return null
        val shiftInfo = matchToShiftInfo(matchResult) ?: return null
        return ParsedSms.Confirmation(shiftInfo)
    }

    private fun matchToShiftInfo(matchResult: MatchResult): ShiftInfo? {
        val (date, startTime, endTime, details) = matchResult.destructured
        return runCatching {
            ShiftInfo(
                date = LocalDate.parse(date),
                startTime = LocalTime.parse(startTime),
                endTime = LocalTime.parse(endTime),
                details = details.trim(),
            )
        }.getOrNull()
    }

    private companion object {
        val OFFER_REGEX = Regex(
            """^Hej! Ledigt pass (\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}) - (\d{2}:\d{2}) (.+)\. Vill du anmäla intresse för passet svara: J annars N$""",
        )

        val CONFIRMATION_REGEX = Regex(
            """^Du är nu bokad på pass (\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}) - (\d{2}:\d{2}) (.+)$""",
        )
    }
}
