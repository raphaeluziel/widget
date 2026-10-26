package com.raphaeluziel.noteswidget

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Debug/status screen: shows the cached sync state and lets you force a refresh. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = NotesRepository(this)
        setContent {
            val context = LocalContext.current
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by repository.state.collectAsState(initial = null)
                    StatusScreen(state, onRefresh = { RefreshWorker.refreshNow(this) })
                }
            }
        }
    }
}

@Composable
private fun StatusScreen(state: WidgetState?, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Notes Widget", style = MaterialTheme.typography.headlineSmall)
        // Host only: never show the token or the full request URL.
        val host = NotesRepository.baseUrl()?.host
        Text(
            if (NotesRepository.isConfigured) "Server: $host" else "Not configured: set notes.baseUrl (https) and notes.widgetToken in local.properties",
        )
        if (state != null) {
            Text("Status: ${state.status}")
            Text("Last success: ${formatTime(state.lastSuccessMillis)}")
            Text("Last attempt: ${formatTime(state.lastAttemptMillis)}")
            Text("Cached notes: ${state.notes.size}")
        }
        Button(onClick = onRefresh) { Text("Refresh now") }
        Text(
            "Add the widget from the home screen: long-press → Widgets → Notes Widget.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatTime(millis: Long?): String =
    millis?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "never"
