package com.example.bookingagent.sms.sms

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftRuleEvaluatorTest {
    private val evaluator = ShiftRuleEvaluator()

    @Test
    fun weekday0700To1900ReturnsN() {
        val decision = evaluator.evaluate(shiftInfo("2026-03-05", "07:00", "19:00"))

        assertEquals(ReplyDecision.N, decision)
    }

    @Test
    fun saturday0700To1900ReturnsJ() {
        val decision = evaluator.evaluate(shiftInfo("2026-03-07", "07:00", "19:00"))

        assertEquals(ReplyDecision.J, decision)
    }

    @Test
    fun sunday0800To1800ReturnsJ() {
        val decision = evaluator.evaluate(shiftInfo("2026-03-08", "08:00", "18:00"))

        assertEquals(ReplyDecision.J, decision)
    }

    @Test
    fun saturday0659To1900ReturnsN() {
        val decision = evaluator.evaluate(shiftInfo("2026-03-07", "06:59", "19:00"))

        assertEquals(ReplyDecision.N, decision)
    }

    @Test
    fun saturday0700To1901ReturnsN() {
        val decision = evaluator.evaluate(shiftInfo("2026-03-07", "07:00", "19:01"))

        assertEquals(ReplyDecision.N, decision)
    }

    private fun shiftInfo(date: String, startTime: String, endTime: String): ShiftInfo =
        ShiftInfo(
            date = LocalDate.parse(date),
            startTime = LocalTime.parse(startTime),
            endTime = LocalTime.parse(endTime),
            details = "A Skänninge Alla",
        )
}
