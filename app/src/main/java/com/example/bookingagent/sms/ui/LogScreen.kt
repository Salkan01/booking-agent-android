package com.example.bookingagent.sms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookingagent.sms.data.model.BookingEntity

@Composable
fun LogScreen(
    uiState: LogUiState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Text(
                    text = "Loading...",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                )
            }

            uiState.bookings.isEmpty() -> {
                Text(
                    text = "No bookings yet",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = uiState.bookings,
                        key = { booking -> booking.id },
                    ) { booking ->
                        BookingLogItem(booking = booking)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingLogItem(
    booking: BookingEntity,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Sender: ${booking.sender}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LogField(label = "Type", value = booking.messageType)
            LogField(label = "Date", value = booking.shiftDate)
            LogField(label = "Start", value = booking.startTime)
            LogField(label = "End", value = booking.endTime)
            LogField(label = "Details", value = booking.details)
            LogField(label = "Reply", value = booking.replySent)
            LogField(label = "Status", value = booking.status.name)
            LogField(label = "Event ID", value = booking.eventId?.toString())
        }
    }
}

@Composable
private fun LogField(
    label: String,
    value: String?,
) {
    Text(
        text = "$label: ${value ?: "-"}",
        style = MaterialTheme.typography.bodyMedium,
    )
}
