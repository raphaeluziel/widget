package com.raphaeluziel.noteswidget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.appwidget.updateAll
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.first

/**
 * Sticky-note colors. The widget picks the light or dark set from its own toggle rather than the
 * phone's theme, so every color is a fixed ColorProvider instead of a day/night pair.
 */
private class Palette(
    background: Long,
    text: Long,
    secondaryText: Long,
    problemText: Long,
    refreshButton: Long,
    detailsButton: Long,
    toggleButton: Long,
    // Note text colors chosen on the website.
    red: Long,
    orange: Long,
    green: Long,
    blue: Long,
    purple: Long,
    pink: Long,
    gray: Long,
) {
    val background = ColorProvider(Color(background))
    val text = ColorProvider(Color(text))
    val secondaryText = ColorProvider(Color(secondaryText))
    val problemText = ColorProvider(Color(problemText))
    val refreshButton = ColorProvider(Color(refreshButton))
    val detailsButton = ColorProvider(Color(detailsButton))
    val toggleButton = ColorProvider(Color(toggleButton))
    val red = ColorProvider(Color(red))
    val orange = ColorProvider(Color(orange))
    val green = ColorProvider(Color(green))
    val blue = ColorProvider(Color(blue))
    val purple = ColorProvider(Color(purple))
    val pink = ColorProvider(Color(pink))
    val gray = ColorProvider(Color(gray))
}

/** Very light yellow; note colors are the website's own values. */
private val LightPalette = Palette(
    background = 0xFFFFF9C4, text = 0xFF2B2600, secondaryText = 0xFF5C5530, problemText = 0xFFB3261E,
    refreshButton = 0xFFF6C6C1, detailsButton = 0xFFC5E5C1, toggleButton = 0xFFF3E39A,
    red = 0xFFDC3545, orange = 0xFFE8590C, green = 0xFF198754, blue = 0xFF0D6EFD,
    purple = 0xFF6F42C1, pink = 0xFFD63384, gray = 0xFF6C757D,
)

/** Dark olive; note colors are lighter versions so they stay readable. */
private val DarkPalette = Palette(
    background = 0xFF3B3618, text = 0xFFF2EAC0, secondaryText = 0xFFCFC69A, problemText = 0xFFF2B8B5,
    refreshButton = 0xFF5E2E2A, detailsButton = 0xFF2E4E2B, toggleButton = 0xFF545029,
    red = 0xFFFF8E97, orange = 0xFFFFA36B, green = 0xFF75D7A1, blue = 0xFF8AB8FF,
    purple = 0xFFC3A6F5, pink = 0xFFF59AC8, gray = 0xFFBCC2C8,
)

class NotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = NotesRepository(context)
        // Read once before provideContent so the first frame has real data, not a blank flash.
        val initialState = repository.state.first()
        val initialDark = repository.darkMode.first()
        provideContent {
            // Glance keeps the session alive for a short while, so live DataStore changes show
            // directly; RefreshWorker's updateAll() covers the case where the session has ended.
            val state by repository.state.collectAsState(initialState)
            val dark by repository.darkMode.collectAsState(initialDark)
            WidgetContent(state, dark)
        }
    }
}

/** Tapping the refresh button. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshWorker.refreshNow(context)
    }
}

/** Tapping the light/dark button. Saved, so it survives refreshes and reboots. */
class ToggleDarkModeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        NotesRepository(context).toggleDarkMode()
        // A live session picks the change up from the flow; this covers one that has ended.
        NotesWidget().updateAll(context)
    }
}

@Composable
private fun WidgetContent(state: WidgetState, dark: Boolean) {
    val palette = if (dark) DarkPalette else LightPalette
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(palette.background)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Header(state, dark, palette)
        Spacer(GlanceModifier.height(18.dp))
        // defaultWeight: the list takes whatever height is left, keeping the buttons at the bottom.
        if (state.notes.isEmpty() && state.events.isEmpty()) {
            EmptyState(state.status, palette, GlanceModifier.defaultWeight())
        } else {
            // Today's events and the notes share one scrolling list, events first.
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(state.events) { event -> EventRow(event, palette) }
                if (state.events.isNotEmpty() && state.notes.isNotEmpty()) {
                    item { Spacer(GlanceModifier.height(8.dp)) }
                }
                items(state.notes) { note -> NoteRow(note, palette) }
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        ButtonRow(palette)
    }
}

/** Title centered between an empty slot and the light/dark button, which are the same width. */
@Composable
private fun Header(state: WidgetState, dark: Boolean, palette: Palette) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(GlanceModifier.width(TOGGLE_SIZE))
        Column(
            modifier = GlanceModifier.defaultWeight().padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Raphi's Notes",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = palette.text),
                maxLines = 1,
            )
            problemLine(state.status)?.let {
                Text(
                    text = it,
                    style = TextStyle(fontSize = 11.sp, color = palette.problemText),
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = GlanceModifier
                .size(TOGGLE_SIZE)
                .cornerRadius(TOGGLE_SIZE / 2)
                .background(palette.toggleButton)
                .clickable(actionRunCallback<ToggleDarkModeAction>()),
            contentAlignment = Alignment.Center,
        ) {
            // Shows what a tap switches to. U+FE0E asks for the plain text glyph, not a color emoji.
            Text(
                text = if (dark) "☀︎" else "☾︎",
                style = TextStyle(fontSize = 18.sp, color = palette.text),
                maxLines = 1,
            )
        }
    }
}

private val TOGGLE_SIZE = 36.dp

/** Refresh on the left, Details (opens the notes site) on the right. */
@Composable
private fun ButtonRow(palette: Palette) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        WidgetButton(
            "Refresh",
            actionRunCallback<RefreshAction>(),
            palette.refreshButton,
            palette,
            GlanceModifier.defaultWeight(),
        )
        NotesRepository.baseUrl()?.let { url ->
            Spacer(GlanceModifier.width(8.dp))
            WidgetButton(
                "Details",
                actionStartActivity(Intent(Intent.ACTION_VIEW, url.toString().toUri())),
                palette.detailsButton,
                palette,
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
    color: ColorProvider,
    palette: Palette,
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
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.text),
            maxLines = 1,
        )
    }
}

/** Shown under the title only when the last refresh failed; nothing when all is well. */
private fun problemLine(status: SyncStatus): String? = when (status) {
    SyncStatus.NeverSynced, SyncStatus.Ok -> null
    SyncStatus.Offline -> "Offline · showing cached notes"
    SyncStatus.BadToken -> "Check token (server returned 403)"
    SyncStatus.Error -> "Couldn't refresh"
    SyncStatus.NotConfigured -> "Not configured"
}

@Composable
private fun EmptyState(status: SyncStatus, palette: Palette, modifier: GlanceModifier) {
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
            style = TextStyle(fontSize = 13.sp, color = palette.secondaryText),
        )
    }
}

/** One of today's events: a dot in the event's colour, the time, then the title. Server order. */
@Composable
private fun EventRow(event: Event, palette: Palette) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .cornerRadius(4.dp)
                .background(eventDotColor(event.color, palette)),
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        if (event.time.isNotBlank()) {
            Text(
                text = event.time,
                style = TextStyle(fontSize = 14.sp, color = palette.secondaryText),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        Text(
            text = event.title.ifBlank { "Untitled" },
            style = TextStyle(fontSize = 14.sp, color = palette.text),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
    }
}

private val HEX_COLOR = Regex("#[0-9a-fA-F]{6}")

/**
 * The event's own "#rrggbb", used as is in both light and dark mode. Anything that isn't a valid
 * "#rrggbb" falls back to the secondary text colour.
 */
private fun eventDotColor(color: String, palette: Palette): ColorProvider {
    val hex = color.trim()
    if (!HEX_COLOR.matches(hex)) return palette.secondaryText
    return ColorProvider(Color(0xFF000000 or hex.substring(1).toLong(16)))
}

/** Notes render in the order the server sends them (my own arrangement), never re-sorted. */
@Composable
private fun NoteRow(note: Note, palette: Palette) {
    Text(
        text = note.title.ifBlank { "Untitled" },
        style = TextStyle(
            fontSize = noteFontSize(note.size),
            fontWeight = if (note.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (note.italic) FontStyle.Italic else FontStyle.Normal,
            color = noteColor(note.color, palette),
        ),
        maxLines = 1,
        modifier = GlanceModifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp, end = 6.dp),
    )
}

/** Unknown sizes render as "normal". */
private fun noteFontSize(size: String): TextUnit = when (size) {
    "small" -> 12.sp
    "large" -> 17.sp
    "xlarge" -> 20.sp
    else -> 14.sp
}

/** Unknown colors, and "", render in the default text color. */
private fun noteColor(color: String, palette: Palette): ColorProvider = when (color) {
    "red" -> palette.red
    "orange" -> palette.orange
    "green" -> palette.green
    "blue" -> palette.blue
    "purple" -> palette.purple
    "pink" -> palette.pink
    "gray" -> palette.gray
    else -> palette.text
}
