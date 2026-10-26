package com.raphaeluziel.noteswidget

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.flow.first

class NotesWidget : GlanceAppWidget() {

    // Responsive: Glance renders one layout per size up front and the launcher picks the closest
    // match, so resizing doesn't need a recomposition (unlike SizeMode.Exact).
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = NotesRepository(context)
        // Read once before provideContent so the first frame has real data, not a blank flash.
        val initial = repository.state.first()
        provideContent {
            // Glance keeps the session alive for a short while, so live DataStore changes show
            // directly; RefreshWorker's updateAll() covers the case where the session has ended.
            val state by repository.state.collectAsState(initial)
            GlanceTheme {
                WidgetContent(state)
            }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(250.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 250.dp)
    }
}

/** Tapping the refresh glyph in the header. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.refreshNow(context)
    }
}

@Composable
private fun WidgetContent(state: WidgetState) {
    val size = LocalSize.current
    val showPreview = size.width >= 200.dp
    val previewLines = if (size.height >= 200.dp) 2 else 1
    val showTime = size.width >= 200.dp
    val openSite = NotesRepository.baseUrl()?.let {
        actionStartActivity(Intent(Intent.ACTION_VIEW, it.toString().toUri()))
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Header(state, openSite)
        Spacer(GlanceModifier.height(6.dp))
        if (state.notes.isEmpty()) {
            EmptyState(state.status)
        } else {
            // LazyColumn is backed by a RemoteViews ListView, which swallows clicks meant for its
            // parent. Each row therefore needs its own clickable.
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(state.notes) { note ->
                    NoteRow(note, showPreview, previewLines, showTime, openSite)
                }
            }
        }
    }
}

@Composable
private fun Header(state: WidgetState, openSite: Action?) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight().let { if (openSite != null) it.clickable(openSite) else it },
        ) {
            Text(
                text = "Notes",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = GlanceTheme.colors.primary),
                maxLines = 1,
            )
            statusLine(state)?.let { (text, isProblem) ->
                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = if (isProblem) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                    ),
                    maxLines = 1,
                )
            }
        }
        Text(
            text = "↻",
            style = TextStyle(fontSize = 18.sp, color = GlanceTheme.colors.primary),
            modifier = GlanceModifier.padding(start = 8.dp, end = 2.dp).clickable(actionRunCallback<RefreshAction>()),
        )
    }
}

/** Subtitle under the header, and whether it describes a problem (rendered in the error color). */
private fun statusLine(state: WidgetState): Pair<String, Boolean>? = when (state.status) {
    SyncStatus.NeverSynced -> null
    SyncStatus.Ok -> state.lastSuccessMillis?.let { "Updated ${relativeTime(it)}" to false }
    SyncStatus.Offline -> "Offline · showing cached notes" to true
    SyncStatus.BadToken -> "Check token (server returned 403)" to true
    SyncStatus.Error -> "Couldn't refresh" to true
    SyncStatus.NotConfigured -> "Not configured" to true
}

@Composable
private fun EmptyState(status: SyncStatus) {
    val message = when (status) {
        SyncStatus.NeverSynced -> "Loading…"
        SyncStatus.Ok -> "No notes yet"
        SyncStatus.Offline -> "Offline. Notes will appear once connected."
        SyncStatus.BadToken -> "The server rejected the token. Check notes.widgetToken."
        SyncStatus.Error -> "Couldn't load notes."
        SyncStatus.NotConfigured -> "Set notes.baseUrl and notes.widgetToken in local.properties, then rebuild."
    }
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun NoteRow(
    note: Note,
    showPreview: Boolean,
    previewLines: Int,
    showTime: Boolean,
    openSite: Action?,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .let { if (openSite != null) it.clickable(openSite) else it },
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note.title.ifBlank { "Untitled" },
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GlanceTheme.colors.onSurface),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            if (showTime) {
                note.updatedMillis?.let {
                    Text(
                        text = relativeTime(it),
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                        maxLines = 1,
                        modifier = GlanceModifier.padding(start = 8.dp),
                    )
                }
            }
        }
        if (showPreview && note.preview.isNotBlank()) {
            Text(
                text = note.preview,
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = previewLines,
            )
        }
    }
}

/**
 * "5 min. ago" style. Computed at render time, so it only advances when the widget is re-rendered
 * (each refresh, roughly every 15 minutes). Good enough for a glanceable list.
 */
private fun relativeTime(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
