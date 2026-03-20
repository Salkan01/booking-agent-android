package com.example.bookingagent.sms.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bookingagent.sms.data.db.AppDatabase
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.repo.BookingRepository
import com.example.bookingagent.sms.booking.BookingOrchestrator
import com.example.bookingagent.sms.calendar.CalendarWriter
import com.example.bookingagent.sms.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LogUiState(
    val isLoading: Boolean = true,
    val bookings: List<BookingEntity> = emptyList(),
)

class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val bookingRepository = BookingRepository(
        AppDatabase.getInstance(application).bookingDao(),
    )
    private val settingsRepository = SettingsRepository.getInstance(application)
    private val bookingOrchestrator = BookingOrchestrator(
        bookingRepository = bookingRepository,
        calendarWriter = CalendarWriter(application),
        appSettingsProvider = settingsRepository::getCurrentSettings,
    )

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookingRepository.getAll().collect { bookings ->
                _uiState.value = LogUiState(
                    isLoading = false,
                    bookings = bookings,
                )
            }
        }
    }

    fun approveOffer(bookingId: Long) {
        viewModelScope.launch {
            bookingOrchestrator.approveOffer(bookingId)
        }
    }

    fun rejectOffer(bookingId: Long) {
        viewModelScope.launch {
            bookingOrchestrator.rejectOffer(bookingId)
        }
    }

    fun rerunDecision(bookingId: Long) {
        viewModelScope.launch {
            bookingOrchestrator.rerunDecision(bookingId)
        }
    }

    fun retryConfirmationMatch(bookingId: Long) {
        viewModelScope.launch {
            bookingOrchestrator.retryConfirmationMatch(bookingId)
        }
    }

    fun createEventManually(bookingId: Long) {
        viewModelScope.launch {
            bookingOrchestrator.createEventManually(bookingId)
        }
    }
}
