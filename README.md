# Stii

A personal, Kotlin/Jetpack Compose Android music player with an original dark-and-lime design.

## Included now

- Local device library scanning via MediaStore and runtime permission flow.
- Immediate local search across track, artist, and album names, with exact and prefix matches ranked first. Search filtering is cancellable and runs off the UI thread.
- Media3 playback with audio focus, pause-on-headphone-unplug, queue, shuffle, repeat, seek scrubbing, and album artwork.
- Persisted local favorites and a favorites library tab.
- Smooth player/page transitions, animated play/pause, responsive album art, and animated search reveal.
- Optional lyrics lookup from LRCLIB by track title and artist. Lyrics are loaded only when requested; no app account is needed.

## Build

- Android Studio with Android SDK 35
- JDK 17+

Open the repository in Android Studio and run the `app` configuration. Gradle wrapper scripts are not included yet.

## Scope and next work

Playback currently covers local files; it is not an online streaming client. Lyrics availability depends on LRCLIB and network access. Background playback with a foreground media service/notification, downloads, Android Auto, equalizer, playlist editing, external source integrations, and TV layouts are not implemented yet.

Stii uses an original codebase and visual design. The cited apps informed general feature goals only; their branded assets and source files were not copied. Stii is not affiliated with those projects or with Google/YouTube.
