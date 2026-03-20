package com.example.bookingagent.sms.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.bookingagent.sms.settings.AppSettings
import com.example.bookingagent.sms.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository.getInstance(application)

    val settings: StateFlow<AppSettings> = settingsRepository.settings

    fun updateTargetCalendarName(value: String) {
        settingsRepository.updateTargetCalendarName(value)
    }

    fun updateEventTitle(value: String) {
        settingsRepository.updateEventTitle(value)
    }

    fun updateAutomationEnabled(value: Boolean) {
        settingsRepository.updateAutomationEnabled(value)
    }

    fun updateDryRunMode(value: Boolean) {
        settingsRepository.updateDryRunMode(value)
    }
}
