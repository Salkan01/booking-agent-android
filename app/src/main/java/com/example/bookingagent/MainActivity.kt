package com.example.bookingagent.sms

import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.bookingagent.sms.settings.AppSettings
import com.example.bookingagent.sms.ui.LogScreen
import com.example.bookingagent.sms.ui.LogUiState
import com.example.bookingagent.sms.ui.LogViewModel
import com.example.bookingagent.sms.ui.SettingsScreen
import com.example.bookingagent.sms.ui.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val logViewModel: LogViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val logUiState by logViewModel.uiState.collectAsState()
            val settings by settingsViewModel.settings.collectAsState()
            BookingAgentApp(
                logUiState = logUiState,
                settings = settings,
                onTargetCalendarNameChange = settingsViewModel::updateTargetCalendarName,
                onEventTitleChange = settingsViewModel::updateEventTitle,
                onAutomationEnabledChange = settingsViewModel::updateAutomationEnabled,
            )
        }
    }
}

@Composable
private fun BookingAgentApp(
    logUiState: LogUiState,
    settings: AppSettings,
    onTargetCalendarNameChange: (String) -> Unit,
    onEventTitleChange: (String) -> Unit,
    onAutomationEnabledChange: (Boolean) -> Unit,
) {
    MaterialTheme {
        var showSettings by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showSettings) {
                    OutlinedButton(
                        onClick = { showSettings = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        androidx.compose.material3.Text("Log")
                    }
                    Button(
                        onClick = { showSettings = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        androidx.compose.material3.Text("Settings")
                    }
                } else {
                    Button(
                        onClick = { showSettings = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        androidx.compose.material3.Text("Log")
                    }
                    OutlinedButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        androidx.compose.material3.Text("Settings")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (showSettings) {
                    SettingsScreen(
                        settings = settings,
                        onTargetCalendarNameChange = onTargetCalendarNameChange,
                        onEventTitleChange = onEventTitleChange,
                        onAutomationEnabledChange = onAutomationEnabledChange,
                    )
                } else {
                    LogScreen(uiState = logUiState)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BookingAgentAppPreview() {
    BookingAgentApp(
        logUiState = LogUiState(
            isLoading = false,
            bookings = emptyList(),
        ),
        settings = AppSettings(),
        onTargetCalendarNameChange = {},
        onEventTitleChange = {},
        onAutomationEnabledChange = {},
    )
}
