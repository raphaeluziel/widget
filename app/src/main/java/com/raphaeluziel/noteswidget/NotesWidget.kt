package com.raphaeluziel.noteswidget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first

/**
 * Sticky-note palette: very light yellow by day, dark olive at night. Fixed colors instead of
 * GlanceTheme's dynamic ones, because text must stay readable against the yellow either way.
 */
private object NoteColors {
    val background = ColorProvider(day = Color(0xFFFFF9C4), night = Color(0xFF3B3618))
    val text = ColorProvider(day = Color(0xFF2B2600), night = Color(0xFFF2EAC0))
    val secondaryText = ColorProvider(day = Color(0xFF5C5530), night = Color(0xFFCFC69A))
    val problemText = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFF2B8B5))
    val refreshButton = ColorProvider(day = Color(0xFFF6C6C1), night = Color(0xFF5E2E2A))
    val detailsButton = ColorProvider(day = Color(0xFFC5E5C1), night = Color(0xFF2E4E2B))
}

class NotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = NotesRepository(context)
        // Read once before provideContent so the first frame has real data, not a blank flash.
        val initial = repository.state.first()
        provideContent {
            // Glance keeps the session alive for a short while, so live DataStore changes show
            // directly; RefreshWorker's updateAll() covers the case where the session has ended.
            val state by repository.state.collectAsState(initial)
            WidgetContent(state)
        }
    }
}

/** Tapping the refresh button. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.refreshNow(context)
    }
}

@Composable
private fun WidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(NoteColors.background)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Header(state)
        Spacer(GlanceModifier.height(18.dp))
        // defaultWeight: the list takes whatever height is left, keeping the buttons at the bottom.
        if (state.notes.isEmpty()) {
            EmptyState(state.status, GlanceModifier.defaultWeight())
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(state.notes) { note -> NoteRow(note) }
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        ButtonRow()
    }
}

@Composable
private fun Header(state: WidgetState) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Raphi's Notes",
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = NoteColors.text),
            maxLines = 1,
        )
        problemLine(state.status)?.let {
            Text(
                text = it,
                style = TextStyle(fontSize = 11.sp, color = NoteColors.problemText),
                maxLines = 1,
            )
        }
    }
}

/** Refresh on the left, Details (opens the notes site) on the right. The only clickable elements. */
@Composable
private fun ButtonRow() {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        WidgetButton(
            "Refresh",
            actionRunCallback<RefreshAction>(),
            NoteColors.refreshButton,
            GlanceModifier.defaultWeight(),
        )
        NotesRepository.baseUrl()?.let { url ->
            Spacer(GlanceModifier.width(8.dp))
            WidgetButton(
                "Details",
                actionStartActivity(Intent(Intent.ACTION_VIEW, url.toString().toUri())),
                NoteColors.detailsButton,
                GlanceModifier.defaultWeight(),
            )
        }
    }
}

/** Rounded rectangle, 44dp tall: big enough to hit reliably with a thumb. */
@Composable
private fun WidgetButton(
    label: String,
    onClick: Action,
    // Glance's ColorProvider type; the ColorProvider imported above is its day/night factory function.
    color: androidx.glance.unit.ColorProvider,
    modifier: GlanceModifier,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .cornerRadius(10.dp)
            .background(color)
            .clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NoteColors.text),
            maxLines = 1,
        )
    }
}

/** Shown under "Notes" only when the last refresh failed; nothing when all is well. */
private fun problemLine(status: SyncStatus): String? = when (status) {
    SyncStatus.NeverSynced, SyncStatus.Ok -> null
    SyncStatus.Offline -> "Offline · showing cached notes"
    SyncStatus.BadToken -> "Check token (server returned 403)"
    SyncStatus.Error -> "Couldn't refresh"
    SyncStatus.NotConfigured -> "Not configured"
}

@Composable
private fun EmptyState(status: SyncStatus, modifier: GlanceModifier) {
    val message = when (status) {
        SyncStatus.NeverSynced -> "Loading…"
        SyncStatus.Ok -> "No notes yet"
        SyncStatus.Offline -> "Offline. Notes will appear once connected."
        SyncStatus.BadToken -> "The server rejected the token. Check notes.widgetToken."
        SyncStatus.Error -> "Couldn't load notes."
        SyncStatus.NotConfigured -> "Set notes.baseUrl and notes.widgetToken in local.properties, then rebuild."
    }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = TextStyle(fontSize = 13.sp, color = NoteColors.secondaryText),
        )
    }
}

@Composable
private fun NoteRow(note: Note) {
    Text(
        text = note.title.ifBlank { "Untitled" },
        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NoteColors.text),
        maxLines = 2,
        modifier = GlanceModifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp, end = 6.dp),
    )
}
