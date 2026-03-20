package com.example.bookingagent.sms.calendar

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.example.bookingagent.sms.sms.ShiftInfo
import java.time.ZoneId

sealed interface CalendarWriteResult {
    data class Success(
        val eventId: Long,
    ) : CalendarWriteResult

    data class Failure(
        val errorMessage: String,
    ) : CalendarWriteResult
}

open class CalendarWriter() {
    private var contentResolver: ContentResolver? = null

    constructor(context: Context) : this() {
        contentResolver = context.applicationContext.contentResolver
    }

    open fun createEvent(
        shiftInfo: ShiftInfo,
        rawSms: String,
        calendarName: String,
        eventTitle: String,
    ): CalendarWriteResult {
        val resolver = contentResolver ?: return CalendarWriteResult.Failure(
            errorMessage = "Calendar writer is not initialized",
        )
        val calendarId = findWritableCalendarId(
            contentResolver = resolver,
            calendarName = calendarName,
        ) ?: return CalendarWriteResult.Failure(
            errorMessage = "Calendar \"$calendarName\" not found or not writable",
        )
        val zoneId = ZoneId.systemDefault()
        val startMillis = shiftInfo.date.atTime(shiftInfo.startTime).atZone(zoneId).toInstant().toEpochMilli()
        val endMillis = shiftInfo.date.atTime(shiftInfo.endTime).atZone(zoneId).toInstant().toEpochMilli()
        val description = buildDescription(shiftInfo.details, rawSms)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, eventTitle)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, zoneId.id)
        }

        return runCatching {
            resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        }.fold(
            onSuccess = { eventUri ->
                val eventId = eventUri?.lastPathSegment?.toLongOrNull()
                if (eventId != null) {
                    CalendarWriteResult.Success(eventId = eventId)
                } else {
                    CalendarWriteResult.Failure(
                        errorMessage = "Failed to read the created calendar event id",
                    )
                }
            },
            onFailure = { throwable ->
                CalendarWriteResult.Failure(
                    errorMessage = throwable.message ?: "Failed to insert calendar event",
                )
            },
        )
    }

    private fun findWritableCalendarId(
        contentResolver: ContentResolver,
        calendarName: String,
    ): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection =
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} = ? AND " +
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(calendarName, CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

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
}
