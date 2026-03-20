package com.example.bookingagent.sms.settings

data class AppSettings(
    val targetCalendarName: String = DEFAULT_TARGET_CALENDAR_NAME,
    val eventTitle: String = DEFAULT_EVENT_TITLE,
    val automationEnabled: Boolean = DEFAULT_AUTOMATION_ENABLED,
    val dryRunMode: Boolean = DEFAULT_DRY_RUN_MODE,
) {
    companion object {
        const val DEFAULT_TARGET_CALENDAR_NAME = "عمل"
        const val DEFAULT_EVENT_TITLE = "عمل"
        const val DEFAULT_AUTOMATION_ENABLED = true
        const val DEFAULT_DRY_RUN_MODE = true
    }
}
