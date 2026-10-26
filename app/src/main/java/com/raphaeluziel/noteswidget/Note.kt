package com.raphaeluziel.noteswidget

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** One entry of `GET <base>/widget/recent/`. Field names match the Django JSON contract. */
@Serializable
data class Note(
    val title: String,
    val preview: String,
    /** ISO-8601 from Django's `isoformat()`, e.g. `2026-10-26T09:15:02.123456+00:00`. */
    val updated: String,
) {
    /** Epoch millis of [updated], or null if the server sent something unparseable. */
    val updatedMillis: Long?
        get() = try {
            OffsetDateTime.parse(updated).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            // Django emits naive datetimes when USE_TZ = False; assume device time zone.
            try {
                LocalDateTime.parse(updated).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
}

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
