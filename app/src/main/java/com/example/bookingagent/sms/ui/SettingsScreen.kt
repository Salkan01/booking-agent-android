package com.example.bookingagent.sms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookingagent.sms.settings.AppSettings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onTargetCalendarNameChange: (String) -> Unit,
    onEventTitleChange: (String) -> Unit,
    onAutomationEnabledChange: (Boolean) -> Unit,
    onDryRunModeChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = settings.targetCalendarName,
            onValueChange = onTargetCalendarNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Target calendar name")
            },
            singleLine = true,
        )

        OutlinedTextField(
            value = settings.eventTitle,
            onValueChange = onEventTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Event title")
            },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Automation enabled",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Store whether automatic replies and events should run.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Switch(
                checked = settings.automationEnabled,
                onCheckedChange = onAutomationEnabledChange,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Dry Run Mode",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Process and log replies without sending real SMS.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Switch(
                checked = settings.dryRunMode,
                onCheckedChange = onDryRunModeChange,
            )
        }
    }
}
