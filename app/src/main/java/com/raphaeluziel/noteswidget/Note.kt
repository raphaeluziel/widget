package com.raphaeluziel.noteswidget

import kotlinx.serialization.Serializable

/**
 * One entry of `GET <base>/widget/recent/`: the note text plus how to style it. The defaults let
 * older servers and cached state from before styling existed still decode. Any other fields the
 * server sends (older servers also sent `preview` and `updated`) are skipped by `ignoreUnknownKeys`.
 */
@Serializable
data class Note(
    val title: String,
    /** "small", "normal", "large" or "xlarge"; anything else renders as "normal". */
    val size: String = "normal",
    /** "" or "red", "orange", "green", "blue", "purple", "pink", "gray"; anything else renders as "". */
    val color: String = "",
    val bold: Boolean = false,
    val italic: Boolean = false,
)

@Serializable
data class NotesResponse(val notes: List<Note>)

/** Outcome of the most recent refresh attempt. */
@Serializable
enum class SyncStatus {
    /** Nothing fetched yet; the widget shows a loading state. */
    NeverSynced,
    Ok,

    /** Network unreachable; cached notes (if any) are still shown. */
    Offline,

    /** Server returned 403. Not retried until the next periodic run. */
    BadToken,

    /** Any other failure: 5xx, unexpected status, malformed JSON. */
    Error,

    /** Base URL or token missing from local.properties, or base URL is not HTTPS. */
    NotConfigured,
}

/** Everything the widget and MainActivity render, persisted as one JSON blob in DataStore. */
@Serializable
data class WidgetState(
    val notes: List<Note> = emptyList(),
    val status: SyncStatus = SyncStatus.NeverSynced,
    /** Epoch millis of the last successful fetch. */
    val lastSuccessMillis: Long? = null,
    /** Epoch millis of the last attempt, successful or not. */
    val lastAttemptMillis: Long? = null,
)
