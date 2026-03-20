package com.example.bookingagent.sms.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.example.bookingagent.sms.sms.ShiftInfo
import java.time.ZoneId

class CalendarWriter(
    context: Context,
) {
    private val contentResolver = context.applicationContext.contentResolver

    fun createEvent(shiftInfo: ShiftInfo, rawSms: String): Long? {
        val calendarId = findWritableCalendarId() ?: return null
        val zoneId = ZoneId.systemDefault()
        val startMillis = shiftInfo.date.atTime(shiftInfo.startTime).atZone(zoneId).toInstant().toEpochMilli()
        val endMillis = shiftInfo.date.atTime(shiftInfo.endTime).atZone(zoneId).toInstant().toEpochMilli()
        val description = buildDescription(shiftInfo.details, rawSms)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, EVENT_TITLE)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, zoneId.id)
        }

        return runCatching {
            contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        }.getOrNull()?.let { eventUri ->
            runCatching { eventUri.lastPathSegment?.toLong() }.getOrNull()
        }
    }

    private fun findWritableCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection =
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} = ? AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CALENDAR_NAME, CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        return runCatching {
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )
        }.getOrNull()?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
            } else {
                null
            }
        }
    }

    private fun buildDescription(details: String, rawSms: String): String {
        val trimmedDetails = details.trim()
        return if (trimmedDetails.isNotEmpty()) {
            "$trimmedDetails\n\n$rawSms"
        } else {
            rawSms
        }
    }

    private companion object {
        const val CALENDAR_NAME = "عمل"
        const val EVENT_TITLE = "عمل"
    }
}
