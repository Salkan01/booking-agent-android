package com.example.bookingagent.sms.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository private constructor(
    context: Context,
) {
    private val sharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun getCurrentSettings(): AppSettings = _settings.value

    fun updateTargetCalendarName(value: String) {
        sharedPreferences.edit()
            .putString(KEY_TARGET_CALENDAR_NAME, value)
            .apply()
        _settings.value = readSettings()
    }

    fun updateEventTitle(value: String) {
        sharedPreferences.edit()
            .putString(KEY_EVENT_TITLE, value)
            .apply()
        _settings.value = readSettings()
    }

    fun updateAutomationEnabled(value: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_AUTOMATION_ENABLED, value)
            .apply()
        _settings.value = readSettings()
    }

    private fun readSettings(): AppSettings =
        AppSettings(
            targetCalendarName = sharedPreferences.getString(
                KEY_TARGET_CALENDAR_NAME,
                AppSettings.DEFAULT_TARGET_CALENDAR_NAME,
            ) ?: AppSettings.DEFAULT_TARGET_CALENDAR_NAME,
            eventTitle = sharedPreferences.getString(
                KEY_EVENT_TITLE,
                AppSettings.DEFAULT_EVENT_TITLE,
            ) ?: AppSettings.DEFAULT_EVENT_TITLE,
            automationEnabled = sharedPreferences.getBoolean(
                KEY_AUTOMATION_ENABLED,
                AppSettings.DEFAULT_AUTOMATION_ENABLED,
            ),
        )

    companion object {
        private const val PREFERENCES_NAME = "app_settings"
        private const val KEY_TARGET_CALENDAR_NAME = "target_calendar_name"
        private const val KEY_EVENT_TITLE = "event_title"
        private const val KEY_AUTOMATION_ENABLED = "automation_enabled"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
    }
}
