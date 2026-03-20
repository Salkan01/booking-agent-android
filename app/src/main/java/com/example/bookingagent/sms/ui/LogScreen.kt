package com.example.bookingagent.sms.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bookingagent.sms.data.model.BookingEntity
import com.example.bookingagent.sms.data.model.BookingStatus
import com.example.bookingagent.sms.data.model.ReviewState
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
    onApproveOffer: (Long) -> Unit,
    onRejectOffer: (Long) -> Unit,
    onRerunDecision: (Long) -> Unit,
    onRetryConfirmationMatch: (Long) -> Unit,
    onCreateEventManually: (Long) -> Unit,
) {
    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }
    val filteredBookings = uiState.bookings.filter { booking -> selectedFilter.matches(booking) }

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

            item {
                FilterCard(
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                )
            }

            if (uiState.isLoading) {
                item {
                    InfoCard(text = "Loading bookings...")
                }
            } else if (filteredBookings.isEmpty()) {
                item {
                    InfoCard(text = "No bookings for this filter")
                }
            } else {
                items(
                    items = filteredBookings,
                    key = { booking -> booking.id },
                ) { booking ->
                    BookingLogItem(
                        booking = booking,
                        settings = settings,
                        onApproveOffer = onApproveOffer,
                        onRejectOffer = onRejectOffer,
                        onRerunDecision = onRerunDecision,
                        onRetryConfirmationMatch = onRetryConfirmationMatch,
                        onCreateEventManually = onCreateEventManually,
                    )
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
            LogField(label = "Dry run mode", value = settings.dryRunMode.toString())
            LogField(label = "Manual review mode", value = settings.manualReviewMode.toString())
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
private fun FilterCard(
    selectedFilter: LogFilter,
    onFilterSelected: (LogFilter) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogFilter.entries.forEach { filter ->
                    if (filter == selectedFilter) {
                        Button(onClick = { onFilterSelected(filter) }) {
                            Text(filter.label)
                        }
                    } else {
                        OutlinedButton(onClick = { onFilterSelected(filter) }) {
                            Text(filter.label)
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
    settings: AppSettings,
    onApproveOffer: (Long) -> Unit,
    onRejectOffer: (Long) -> Unit,
    onRerunDecision: (Long) -> Unit,
    onRetryConfirmationMatch: (Long) -> Unit,
    onCreateEventManually: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Sender: ${booking.sender}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = booking.rawSms,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            LogField(label = "Type", value = booking.messageType)
            LogField(label = "Date", value = booking.shiftDate)
            LogField(label = "Start", value = booking.startTime)
            LogField(label = "End", value = booking.endTime)
            LogField(label = "Details", value = booking.details)
            LogField(label = "Recommended reply", value = booking.recommendedReply)
            LogField(label = "Reply sent", value = booking.replySent)
            LogField(label = "Intended reply", value = booking.intendedReply)
            LogField(label = "Status", value = booking.status.name)
            LogField(label = "Review state", value = booking.reviewState.name)
            LogField(label = "Matched booking", value = booking.matchedBookingId?.toString())
            LogField(label = "Event ID", value = booking.eventId?.toString())
            LogField(label = "Dry run", value = booking.dryRun.toString())
            LogField(label = "Error", value = booking.errorMessage)

            if (booking.messageType == MESSAGE_TYPE_CONFIRMATION) {
                ConfirmationPreview(
                    booking = booking,
                    settings = settings,
                )
            }

            OfferActions(
                booking = booking,
                onApproveOffer = onApproveOffer,
                onRejectOffer = onRejectOffer,
                onRerunDecision = onRerunDecision,
            )

            ConfirmationActions(
                booking = booking,
                settings = settings,
                onRetryConfirmationMatch = onRetryConfirmationMatch,
                onCreateEventManually = onCreateEventManually,
            )
        }
    }
}

@Composable
private fun OfferActions(
    booking: BookingEntity,
    onApproveOffer: (Long) -> Unit,
    onRejectOffer: (Long) -> Unit,
    onRerunDecision: (Long) -> Unit,
) {
    val canReviewOffer =
        booking.messageType == MESSAGE_TYPE_OFFER &&
            booking.reviewState == ReviewState.PENDING_REVIEW &&
            booking.status != BookingStatus.DUPLICATE_IGNORED &&
            booking.replySent == null &&
            booking.intendedReply == null

    if (!canReviewOffer) {
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Manual Review",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Button(
            onClick = { onApproveOffer(booking.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Approve J")
        }
        Button(
            onClick = { onRejectOffer(booking.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reject N")
        }
        OutlinedButton(
            onClick = { onRerunDecision(booking.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Re-run decision")
        }
    }
}

@Composable
private fun ConfirmationPreview(
    booking: BookingEntity,
    settings: AppSettings,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Event Preview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            LogField(
                label = "Matching accepted offer",
                value = if (booking.matchedBookingId != null) {
                    "Yes (#${booking.matchedBookingId})"
                } else {
                    "No"
                },
            )
            LogField(label = "Calendar", value = settings.targetCalendarName)
            LogField(label = "Event title", value = settings.eventTitle)
            LogField(label = "Preview start", value = formatDateTime(booking.shiftDate, booking.startTime))
            LogField(label = "Preview end", value = formatDateTime(booking.shiftDate, booking.endTime))
            if (settings.dryRunMode) {
                LogField(label = "Mode", value = "Would create event only")
            }
        }
    }
}

@Composable
private fun ConfirmationActions(
    booking: BookingEntity,
    settings: AppSettings,
    onRetryConfirmationMatch: (Long) -> Unit,
    onCreateEventManually: (Long) -> Unit,
) {
    val canReviewConfirmation =
        booking.messageType == MESSAGE_TYPE_CONFIRMATION &&
            booking.status != BookingStatus.DUPLICATE_IGNORED &&
            booking.eventId == null &&
            booking.status != BookingStatus.EVENT_CREATED

    if (!canReviewConfirmation) {
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Confirmation Actions",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(
            onClick = { onRetryConfirmationMatch(booking.id) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Retry match")
        }

        if (booking.matchedBookingId != null) {
            Button(
                onClick = { onCreateEventManually(booking.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (settings.dryRunMode) "Create event manually (dry run)" else "Create event manually")
            }
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

private fun formatDateTime(
    date: String?,
    time: String?,
): String? =
    when {
        date == null -> null
        time == null -> date
        else -> "$date $time"
    }

private enum class LogFilter(
    val label: String,
) {
    ALL("All"),
    PENDING_REVIEW("Pending Review"),
    OFFERS("Offers"),
    CONFIRMATIONS("Confirmations"),
    FAILURES("Failures"),
    ;

    fun matches(booking: BookingEntity): Boolean =
        when (this) {
            ALL -> true
            PENDING_REVIEW -> booking.reviewState == ReviewState.PENDING_REVIEW
            OFFERS -> booking.messageType == MESSAGE_TYPE_OFFER
            CONFIRMATIONS -> booking.messageType == MESSAGE_TYPE_CONFIRMATION
            FAILURES -> booking.status == BookingStatus.FAILED
        }
}

private const val MESSAGE_TYPE_OFFER = "offer"
private const val MESSAGE_TYPE_CONFIRMATION = "confirmation"
