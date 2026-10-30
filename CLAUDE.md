# CLAUDE.md

## Project overview

A small personal Android app whose only job is to provide a **home-screen widget** that displays my most recent notes from my Django `notes` app. The Django app is already installable as a PWA via `django-pwa`; this Android project adds the widget that a PWA cannot provide.

- Single-user, personal use. Sideloaded onto my own phone. **No Play Store release.**
- The widget is read-only. Tapping it opens the notes PWA/site in the browser.
- The Django server exposes a small token-protected JSON endpoint. The widget fetches it periodically and renders a list.

## Tech stack

- Kotlin, Jetpack Compose tooling, **Jetpack Glance** (`androidx.glance:glance-appwidget`) for the widget UI
- **WorkManager** for periodic refresh
- `kotlinx.serialization` for JSON, `OkHttp` (or `HttpURLConnection`) for networking
- Gradle Kotlin DSL with a **version catalog** (`gradle/libs.versions.toml`)

### Versions: always use the latest stable

I want the most up-to-date versions of all software. Do **not** pin versions from memory. When creating or updating the project:

1. Look up the current stable releases of Android Studio's recommended Android Gradle Plugin (AGP), Kotlin, the Compose BOM, Glance, WorkManager, OkHttp, and kotlinx.serialization (official sources: developer.android.com/jetpack/androidx/releases, kotlinlang.org, Maven Central).
2. Set `compileSdk` and `targetSdk` to the latest stable API level; choose a reasonable `minSdk` for my phone (ask me for the Android version if it matters).
3. Put every version in `libs.versions.toml`. Prefer stable over alpha/beta unless a required Glance feature only exists in a pre-release (tell me if so).

## Django endpoint contract

The server side already exists (or is being added in the Django repo). The widget must consume exactly this:

```
GET https://<MY_DOMAIN>/<notes-url-prefix>/widget/recent/?token=<TOKEN>
200 OK
{
  "notes": [
    {"title": "string"}
  ]
}
403 -> bad/missing token
```

The widget only reads `title`. Older servers also sent `preview` and `updated`; the app ignores
unknown fields, so extra fields never break it, but removing `title` or `notes` would.

The Django side lives in WSL (`Ubuntu` distro) at `/home/raphaeluziel/claude/notes`
(from Windows: `\\wsl.localhost\Ubuntu\home\raphaeluziel\claude\notes`). Its own `CLAUDE.md`
describes the endpoint implementation. Do not edit the Django repo from here.

The real `Note` model has `text`, `details` and `created` (no title, no edit timestamp). The server
sends `title` = `note.text` for the 5 newest notes by `created`, so "most recent" means newest by
creation, not last edited. Keep the JSON contract above stable.

The notes pages require login (`@owner_required`); this endpoint deliberately doesn't, because the
widget can't log in. The token is its only auth. Unset `WIDGET_TOKEN` on the server makes it 404.

## Requirements

1. **Widget UI (Glance):** a list of the 5 most recent notes, showing only the note text (`title`, bold, up to 2 lines). No details/preview and no per-note time, even though the server still sends them. The header is "Raphi's Notes", centered, in a bigger font than the notes (22sp vs 14sp), with 18dp of space below it. No "updated X ago" line either; the header shows a subtle problem line only when a refresh failed ("offline", "couldn't refresh", "check token"). Handle empty, loading, and error states gracefully (keep showing last cached data).
2. **Refresh:** `PeriodicWorkRequest` via WorkManager (15 minutes is the minimum interval). Also refresh once when the widget is first added. The worker fetches JSON, saves it to Glance state/DataStore, then calls `GlanceAppWidget.update`.
3. **Caching:** persist the last successful response so the widget still shows notes with no network.
4. **Tap actions:** only two 44dp-tall rounded-rectangle buttons in a row at the bottom of the widget are clickable: "Refresh" (left, light red) and "Details" (right, light green), which opens the notes site (`notes.baseUrl`) in the browser via `Intent.ACTION_VIEW`. Tapping notes or the header does nothing.
5. **Config:** base URL and token come from `local.properties` (git-ignored) exposed through `BuildConfig` fields. A template `local.properties.example` should be committed with placeholder values.
6. **Widget sizing and colors:** works at any home-screen size (the list simply fills the space). Sticky-note look: very light yellow background (`#FFF9C4`) with dark text by day, dark olive with light text in dark mode. Fixed colors, not Material You dynamic colors.

## Security rules

- HTTPS only. Set `usesCleartextTraffic="false"`; never allow plain HTTP.
- The token is effectively a password. **Never commit it**, never log it, never print the full request URL in logs. Add `local.properties` to `.gitignore`.
- Never hardcode the real domain or token in source files.
- If the server returns 403, show a clear "check token" state instead of retrying aggressively.

## Project structure (target)

```
app/
  src/main/
    AndroidManifest.xml
    java/<package>/
      NotesWidget.kt            // GlanceAppWidget
      NotesWidgetReceiver.kt    // GlanceAppWidgetReceiver
      RefreshWorker.kt          // WorkManager worker (fetch + update)
      NotesRepository.kt        // network + parsing + cache
      Note.kt                   // @Serializable data classes
      MainActivity.kt           // minimal; shows status + "refresh now"
    res/xml/notes_widget_info.xml  // AppWidgetProviderInfo
gradle/libs.versions.toml
local.properties.example
```

## Conventions

- Idiomatic Kotlin; coroutines for async work; no blocking calls on the main thread.
- Keep dependencies minimal. This is a tiny app.
- Small, focused commits. Explain non-obvious Glance quirks in comments.
- Prefer official AndroidX/Glance APIs over third-party widget libraries.

## Build and run

- Open the project in the latest stable Android Studio.
- Run on my physical Android phone via USB or wireless debugging (widgets are easiest to test on a real device or a recent emulator image).
- After installing: long-press the home screen, Widgets, find the notes widget, and add it.
- Useful commands: `./gradlew assembleDebug`, `./gradlew installDebug`, `./gradlew lint`.

## Out of scope (for now)

- Editing or creating notes from the widget
- Play Store publishing, accounts, or multi-user support
- iOS
- Push-based instant updates (polling every ~15 min is fine)

## First tasks for Claude

1. Scaffold the Gradle project with the version catalog, using current stable versions (see above).
2. Implement `Note`, `NotesRepository` (fetch + cache), and `RefreshWorker`.
3. Implement `NotesWidget` and `NotesWidgetReceiver`, register them in the manifest, and add `notes_widget_info.xml`.
4. Wire up config via `local.properties` and `BuildConfig`, and add `local.properties.example` and `.gitignore` entries.
5. Add a minimal `MainActivity` with a "Refresh now" button and last-sync status to make debugging easy.
6. Test on-device, then polish the layout for different widget sizes and dark mode.
