package com.example.bookingagent.sms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.settings.AppSettings

data class PermissionUiState(
    val permission: String,
    val label: String,
    val isGranted: Boolean,
)

@Composable
fun LogScreen(
    uiState: LogUiState,
    settings: AppSettings,
    permissions: List<PermissionUiState>,
    onRequestPermission: (String) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsSummaryCard(settings = settings)
            }

            item {
                PermissionSummaryCard(
                    permissions = permissions,
                    onRequestPermission = onRequestPermission,
                )
            }

            if (uiState.isLoading) {
                item {
                    InfoCard(text = "Loading bookings...")
                }
            } else if (uiState.bookings.isEmpty()) {
                item {
                    InfoCard(text = "No bookings yet")
                }
            } else {
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

@Composable
private fun SettingsSummaryCard(
    settings: AppSettings,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Current Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LogField(label = "Automation enabled", value = settings.automationEnabled.toString())
            LogField(label = "Calendar name", value = settings.targetCalendarName)
            LogField(label = "Event title", value = settings.eventTitle)
        }
    }
}

@Composable
private fun PermissionSummaryCard(
    permissions: List<PermissionUiState>,
    onRequestPermission: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            permissions.forEach { permission ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = permission.label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (permission.isGranted) "Granted" else "Missing",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (!permission.isGranted) {
                        Button(
                            onClick = { onRequestPermission(permission.permission) },
                            modifier = Modifier.width(92.dp),
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    text: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
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
            LogField(label = "Error", value = booking.errorMessage)
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
