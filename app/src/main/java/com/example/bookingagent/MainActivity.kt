package com.example.bookingagent.sms

import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.bookingagent.sms.ui.LogScreen
import com.example.bookingagent.sms.ui.LogUiState
import com.example.bookingagent.sms.ui.LogViewModel

class MainActivity : ComponentActivity() {
    private val logViewModel: LogViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by logViewModel.uiState.collectAsState()
            BookingAgentApp(uiState = uiState)
        }
    }
}

@Composable
private fun BookingAgentApp(
    uiState: LogUiState,
) {
    MaterialTheme {
        LogScreen(uiState = uiState)
    }
}

@Preview(showBackground = true)
@Composable
private fun BookingAgentAppPreview() {
    BookingAgentApp(
        uiState = LogUiState(
            isLoading = false,
            bookings = emptyList(),
        ),
    )
}
