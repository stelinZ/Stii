# Stii

A personal Kotlin/Jetpack Compose Android music player with original UI and Media3 playback.

## Current functionality

- Scans local audio files through Android MediaStore after a runtime permission prompt.
- Immediate, offline search across track, artist, and album names. Search uses normalized, precomputed fields, ignores accent differences, ranks exact/prefix matches first, and performs cancellable filtering off the UI thread.
- Media3 ExoPlayer owned by a `MediaSessionService`, so playback and system media controls can continue when the Activity is backgrounded. Supports audio focus, headphone-unplug pause, system notification/lock-screen controls, queue, seek, shuffle, repeat, and album artwork metadata.
- Local favorites persist on the device; the Library tab filters to liked tracks.
- LRCLIB lyrics lookup on demand. Lyrics require internet and may not be available for every song.
- Animated player transitions and original dark/lime UI.

## Build

Android Studio, Android SDK 35, and JDK 17+ are required. Open this repository in Android Studio and run the `app` configuration. Gradle wrapper scripts are not included yet.

## Scope

Music discovery/playback currently searches and plays music files present on the device. It does **not** search or stream the YouTube Music/Spotify catalogs. The network feature is optional lyrics lookup through LRCLIB. No bypassing of paid services or protected stream extraction is included.

The implementation is original and uses common Android Media3 patterns. Other music repositories were references for product ideas; their source files and branded assets were not copied. Stii is independent and unaffiliated with those projects or Google/YouTube.
