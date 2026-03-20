package com.example.bookingagent.sms.sms

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {
    private val parser = SmsParser()

    @Test
    fun offerSmsParsingWorks() {
        val message =
            "Hej! Ledigt pass 2026-03-05 07:00 - 19:00 A Skänninge Alla. " +
                "Vill du anmäla intresse för passet svara: J annars N"

        val parsed = parser.parse(message)

        assertTrue(parsed is ParsedSms.Offer)
        val offer = parsed as ParsedSms.Offer
        assertEquals(LocalDate.parse("2026-03-05"), offer.shiftInfo.date)
        assertEquals(LocalTime.parse("07:00"), offer.shiftInfo.startTime)
        assertEquals(LocalTime.parse("19:00"), offer.shiftInfo.endTime)
        assertEquals("A Skänninge Alla", offer.shiftInfo.details)
    }

    @Test
    fun confirmationSmsParsingWorks() {
        val message = "Du är nu bokad på pass 2026-04-12 07:00 - 19:00 A Skänninge Alla"

        val parsed = parser.parse(message)

        assertTrue(parsed is ParsedSms.Confirmation)
        val confirmation = parsed as ParsedSms.Confirmation
        assertEquals(LocalDate.parse("2026-04-12"), confirmation.shiftInfo.date)
        assertEquals(LocalTime.parse("07:00"), confirmation.shiftInfo.startTime)
        assertEquals(LocalTime.parse("19:00"), confirmation.shiftInfo.endTime)
        assertEquals("A Skänninge Alla", confirmation.shiftInfo.details)
    }

    @Test
    fun invalidSmsReturnsNull() {
        val parsed = parser.parse("Det här är inte ett giltigt sms-format")

        assertNull(parsed)
    }
}
