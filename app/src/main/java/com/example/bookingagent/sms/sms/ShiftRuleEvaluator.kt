package com.example.bookingagent.sms.sms

import java.time.DayOfWeek
import java.time.LocalTime

enum class ReplyDecision {
    J,
    N,
}

class ShiftRuleEvaluator {
    fun evaluate(shiftInfo: ShiftInfo): ReplyDecision {
        val isWeekend =
            shiftInfo.date.dayOfWeek == DayOfWeek.SATURDAY ||
                shiftInfo.date.dayOfWeek == DayOfWeek.SUNDAY
        val startsInsideWindow = !shiftInfo.startTime.isBefore(MIN_START_TIME)
        val endsInsideWindow = !shiftInfo.endTime.isAfter(MAX_END_TIME)

        return if (isWeekend && startsInsideWindow && endsInsideWindow) {
            ReplyDecision.J
        } else {
            ReplyDecision.N
        }
    }

    private companion object {
        val MIN_START_TIME: LocalTime = LocalTime.of(7, 0)
        val MAX_END_TIME: LocalTime = LocalTime.of(19, 0)
    }
}
