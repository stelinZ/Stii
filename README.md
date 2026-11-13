# Stii

Personal Kotlin/Jetpack Compose Android music player with an original dark/lime UI.

## Architecture

- `data/MusicItem.kt` — shared local/online audio model and normalized search text.
- `data/MusicRepository.kt` — MediaStore library access and open-catalog boundary.
- `InternetArchiveCatalog.kt` — bounded concurrent metadata lookups for openly licensed Internet Archive audio.
- `ui/StiiViewModel.kt` — lifecycle-aware UI state, local search, debounced internet search, scan state and persistent favorites.
- `MainActivity.kt` — Compose screens and MediaController connection.
- `StiiPlaybackService.kt` — Media3 playback session for background audio and system controls.

## Features

- Search and play local tracks and Internet Archive audio with recognized CC/public-domain metadata.
- Local search is accent-insensitive and runs off the UI thread; online search is debounced and stale queries are canceled.
- MediaSessionService playback, audio focus, headphone disconnect handling, notification/lock-screen controls, play/pause, seek, next/previous, shuffle/repeat.
- Persistent favorites, artwork, mini-player, full player and animated screen transitions.

## Build and limits

Open in Android Studio with Android SDK 35 and JDK 17+. Gradle wrapper scripts are not included yet. Internet availability and license metadata depend on the catalog. Review the item's source record and follow its specific license. This app does not search/extract from YouTube Music or Spotify and does not bypass access controls or paid subscriptions.

Stii is an independent project. Other music apps informed general feature goals only; no branded screen assets or complete source files were copied.
