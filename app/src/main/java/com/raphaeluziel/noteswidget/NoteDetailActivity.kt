package com.raphaeluziel.noteswidget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import kotlin.math.max

/**
 * PROTOTYPE: the pop-up shown when a note or event in the widget is tapped. The details are made
 * up here, because the server doesn't send any yet; this is only for trying out the look and feel.
 *
 * Widgets can't show real dialogs, so this is a translucent activity that draws a dimmed backdrop
 * and a sticky-note card over the home screen. Tapping outside the card or Close dismisses it.
 */
class NoteDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_NOTE
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Untitled" }
        val time = intent.getStringExtra(EXTRA_TIME).orEmpty()
        val color = intent.getStringExtra(EXTRA_COLOR).orEmpty()
        val repository = NotesRepository(this)
        setContent {
            // Follows the widget's own light/dark toggle, not the phone's theme.
            val dark by repository.darkMode.collectAsState(initial = false)
            DetailPopup(
                kind = kind,
                title = title,
                time = time,
                eventColor = color,
                colors = if (dark) DarkDetailColors else LightDetailColors,
                onOpenSite = {
                    NotesRepository.baseUrl()?.let {
                        startActivity(Intent(Intent.ACTION_VIEW, it.toString().toUri()))
                    }
                    finish()
                },
                onClose = ::finish,
            )
        }
    }

    companion object {
        // Glance's actionStartActivity puts its ActionParameters into the intent as extras,
        // keyed by name, so these names are shared with NotesWidget.
        const val EXTRA_KIND = "kind"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TIME = "time"
        const val EXTRA_COLOR = "color"
        const val KIND_NOTE = "note"
        const val KIND_EVENT = "event"
    }
}

/** Same sticky-note colours as the widget (see Palette in NotesWidget.kt). */
private class DetailColors(
    val card: Color,
    val text: Color,
    val secondaryText: Color,
    val closeButton: Color,
    val siteButton: Color,
)

private val LightDetailColors = DetailColors(
    card = Color(0xFFFFF9C4), text = Color(0xFF2B2600), secondaryText = Color(0xFF5C5530),
    closeButton = Color(0xFFF3E39A), siteButton = Color(0xFFC5E5C1),
)

private val DarkDetailColors = DetailColors(
    card = Color(0xFF3B3618), text = Color(0xFFF2EAC0), secondaryText = Color(0xFFCFC69A),
    closeButton = Color(0xFF545029), siteButton = Color(0xFF2E4E2B),
)

@Composable
private fun DetailPopup(
    kind: String,
    title: String,
    time: String,
    eventColor: String,
    colors: DetailColors,
    onOpenSite: () -> Unit,
    onClose: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    // Dimmed backdrop: a tap anywhere outside the card closes the pop-up. The card is centred at
    // CARD_CENTER_FRACTION of the screen height (about where the middle of the widget sits on the
    // home screen) rather than the middle of the screen, but always kept fully on screen.
    Layout(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable(interactionSource = noRipple, indication = null, onClick = onClose),
        content = {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.card)
                // Swallow taps on the card so they don't reach the backdrop.
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                .padding(20.dp),
        ) {
            Text(text = title, color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (kind == NoteDetailActivity.KIND_EVENT && time.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(parseHex(eventColor) ?: colors.secondaryText),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = time, color = colors.secondaryText, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            // Long details scroll inside the card instead of pushing the buttons off screen.
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = fakeDetails(kind, title),
                    color = colors.text,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PopupButton("Open", colors.siteButton, colors.text, Modifier.weight(1f), onOpenSite)
                PopupButton("Close", colors.closeButton, colors.text, Modifier.weight(1f), onClose)
            }
        }
        },
    ) { measurables, constraints ->
        val card = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val centerY = (constraints.maxHeight * CARD_CENTER_FRACTION).toInt()
            val top = (centerY - card.height / 2).coerceIn(0, max(0, constraints.maxHeight - card.height))
            card.place((constraints.maxWidth - card.width) / 2, top)
        }
    }
}

/**
 * Where the card's middle sits, as a fraction of the screen height. 0.41 matches the middle of
 * the widget as it's placed on my home screen (the screen's middle would be 0.5). The widget's
 * own position isn't available to the app, hence a fixed value.
 */
private const val CARD_CENTER_FRACTION = 0.41f

/** Same shape as the widget's buttons: 44dp tall, rounded rectangle. */
@Composable
private fun PopupButton(label: String, background: Color, textColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

private fun parseHex(color: String): Color? {
    val hex = color.trim()
    if (!Regex("#[0-9a-fA-F]{6}").matches(hex)) return null
    return Color(0xFF000000 or hex.substring(1).toLong(16))
}

/** Placeholder text, so the pop-up can be judged with a realistic amount of content. */
private fun fakeDetails(kind: String, title: String): String =
    if (kind == NoteDetailActivity.KIND_EVENT) {
        "Location: 123 Example Street\n\n" +
            "Reminder 30 minutes before. Bring the paperwork from last time and arrive a few minutes early. " +
            "These details are made up so you can see how \"$title\" would look with more information."
    } else {
        "This is where the note's details would go. Right now the widget only receives the note's text, " +
            "so these lines are made up so you can judge the look of the pop-up.\n\n" +
            "A second paragraph shows how longer details wrap and scroll: lorem ipsum dolor sit amet, " +
            "consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua."
    }
