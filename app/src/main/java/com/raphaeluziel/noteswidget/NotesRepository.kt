package com.raphaeluziel.noteswidget

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "NotesRepository"

// DataStore must be a single instance per file, hence the top-level delegate.
private val Context.notesDataStore: DataStore<Preferences> by preferencesDataStore("notes_cache")
private val STATE_KEY = stringPreferencesKey("widget_state")
// Kept apart from STATE_KEY because a successful refresh replaces the whole WidgetState.
private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")

private val json = Json {
    ignoreUnknownKeys = true
    // A null for a field with a default (e.g. "color": null) falls back to the default
    // instead of failing the whole refresh.
    coerceInputValues = true
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

/** Fetches recent notes from the Django endpoint and caches the last result in DataStore. */
class NotesRepository(context: Context) {
    private val dataStore = context.applicationContext.notesDataStore

    val state: Flow<WidgetState> = dataStore.data.map { prefs ->
        prefs[STATE_KEY]?.let { decodeState(it) } ?: WidgetState()
    }

    /** The widget's own light/dark choice, independent of the phone's dark theme. Light by default. */
    val darkMode: Flow<Boolean> = dataStore.data.map { prefs -> prefs[DARK_MODE_KEY] ?: false }

    suspend fun toggleDarkMode() {
        dataStore.edit { prefs -> prefs[DARK_MODE_KEY] = !(prefs[DARK_MODE_KEY] ?: false) }
    }

    /** Fetches and stores the result. Returns the new status; never throws for network errors. */
    suspend fun refresh(): SyncStatus {
        val result = fetch()
        val now = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val previous = prefs[STATE_KEY]?.let { decodeState(it) } ?: WidgetState()
            val next = when (result) {
                is FetchResult.Success -> WidgetState(
                    notes = result.notes,
                    status = SyncStatus.Ok,
                    lastSuccessMillis = now,
                    lastAttemptMillis = now,
                )
                // Keep the cached notes so the widget still has something to show.
                is FetchResult.Failure -> previous.copy(status = result.status, lastAttemptMillis = now)
            }
            prefs[STATE_KEY] = json.encodeToString(next)
        }
        return when (result) {
            is FetchResult.Success -> SyncStatus.Ok
            is FetchResult.Failure -> result.status
        }
    }

    private suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val url = endpointUrl() ?: return@withContext FetchResult.Failure(SyncStatus.NotConfigured)
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 403 -> FetchResult.Failure(SyncStatus.BadToken)
                    !response.isSuccessful -> {
                        // Log only the status code: the URL carries the token.
                        Log.w(TAG, "Refresh failed: HTTP ${response.code}")
                        FetchResult.Failure(SyncStatus.Error)
                    }
                    else -> FetchResult.Success(json.decodeFromString<NotesResponse>(response.body.string()).notes)
                }
            }
        } catch (e: IOException) {
            // Exception messages can include the request URL, so only log the type.
            Log.w(TAG, "Refresh failed: ${e.javaClass.simpleName}")
            FetchResult.Failure(SyncStatus.Offline)
        } catch (e: SerializationException) {
            Log.w(TAG, "Refresh failed: unexpected JSON (${e.javaClass.simpleName})")
            FetchResult.Failure(SyncStatus.Error)
        }
    }

    private fun decodeState(raw: String): WidgetState? =
        try {
            json.decodeFromString<WidgetState>(raw)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    private sealed interface FetchResult {
        data class Success(val notes: List<Note>) : FetchResult
        data class Failure(val status: SyncStatus) : FetchResult
    }

    companion object {
        /** The notes site root from config, or null if missing or not HTTPS. Also the tap target. */
        fun baseUrl(): HttpUrl? {
            val raw = BuildConfig.NOTES_BASE_URL.trim()
            if (raw.isEmpty()) return null
            val url = (if (raw.endsWith("/")) raw else "$raw/").toHttpUrlOrNull() ?: return null
            return url.takeIf { it.isHttps }
        }

        val isConfigured: Boolean
            get() = baseUrl() != null && BuildConfig.NOTES_WIDGET_TOKEN.isNotBlank()

        private fun endpointUrl(): HttpUrl? {
            if (!isConfigured) return null
            return baseUrl()!!.newBuilder()
                .addPathSegments("widget/recent/")
                .addQueryParameter("token", BuildConfig.NOTES_WIDGET_TOKEN)
                .build()
        }
    }
}
