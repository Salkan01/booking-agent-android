package com.example.bookingagent.sms

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bookingagent.sms.settings.AppSettings
import com.example.bookingagent.sms.ui.LogScreen
import com.example.bookingagent.sms.ui.LogUiState
import com.example.bookingagent.sms.ui.LogViewModel
import com.example.bookingagent.sms.ui.PermissionUiState
import com.example.bookingagent.sms.ui.SettingsScreen
import com.example.bookingagent.sms.ui.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val logViewModel: LogViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private var permissionStates by mutableStateOf(emptyList<PermissionUiState>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionStates()

        setContent {
            val logUiState by logViewModel.uiState.collectAsState()
            val settings by settingsViewModel.settings.collectAsState()
            val requestPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {
                refreshPermissionStates()
            }

            BookingAgentApp(
                logUiState = logUiState,
                settings = settings,
                permissionStates = permissionStates,
                onRequestPermission = requestPermissionLauncher::launch,
                onTargetCalendarNameChange = settingsViewModel::updateTargetCalendarName,
                onEventTitleChange = settingsViewModel::updateEventTitle,
                onAutomationEnabledChange = settingsViewModel::updateAutomationEnabled,
                onDryRunModeChange = settingsViewModel::updateDryRunMode,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun refreshPermissionStates() {
        permissionStates = APP_PERMISSIONS.map { permissionSpec ->
            PermissionUiState(
                permission = permissionSpec.permission,
                label = permissionSpec.label,
                isGranted = ContextCompat.checkSelfPermission(
                    this,
                    permissionSpec.permission,
                ) == PackageManager.PERMISSION_GRANTED,
            )
        }
    }

    private companion object {
        val APP_PERMISSIONS = listOf(
            PermissionSpec(
                permission = Manifest.permission.RECEIVE_SMS,
                label = "Receive SMS",
            ),
            PermissionSpec(
                permission = Manifest.permission.SEND_SMS,
                label = "Send SMS",
            ),
            PermissionSpec(
                permission = Manifest.permission.READ_CALENDAR,
                label = "Read calendar",
            ),
            PermissionSpec(
                permission = Manifest.permission.WRITE_CALENDAR,
                label = "Write calendar",
            ),
        )
    }
}

@Composable
private fun BookingAgentApp(
    logUiState: LogUiState,
    settings: AppSettings,
    permissionStates: List<PermissionUiState>,
    onRequestPermission: (String) -> Unit,
    onTargetCalendarNameChange: (String) -> Unit,
    onEventTitleChange: (String) -> Unit,
    onAutomationEnabledChange: (Boolean) -> Unit,
    onDryRunModeChange: (Boolean) -> Unit,
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
                        Text("Log")
                    }
                    Button(
                        onClick = { showSettings = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Settings")
                    }
                } else {
                    Button(
                        onClick = { showSettings = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Log")
                    }
                    OutlinedButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Settings")
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
                        onDryRunModeChange = onDryRunModeChange,
                    )
                } else {
                    LogScreen(
                        uiState = logUiState,
                        settings = settings,
                        permissions = permissionStates,
                        onRequestPermission = onRequestPermission,
                    )
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
        permissionStates = listOf(
            PermissionUiState(
                permission = Manifest.permission.RECEIVE_SMS,
                label = "Receive SMS",
                isGranted = true,
            ),
            PermissionUiState(
                permission = Manifest.permission.SEND_SMS,
                label = "Send SMS",
                isGranted = false,
            ),
        ),
        onRequestPermission = {},
        onTargetCalendarNameChange = {},
        onEventTitleChange = {},
        onAutomationEnabledChange = {},
        onDryRunModeChange = {},
    )
}

private data class PermissionSpec(
    val permission: String,
    val label: String,
)
