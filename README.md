# Stii

Personal Kotlin/Jetpack Compose Android music player with original design, background playback and online discovery of openly licensed audio.

## Music search and playback

- **On-device search:** MediaStore library, indexed on scan; filters locally and immediately by track, artist, and album.
- **Internet search:** Internet Archive audio catalog. Search results are resolved against item metadata and only entries declaring a Creative Commons or public-domain license are offered for playback. Licenses are shown on result rows. Availability and rights metadata are supplied by the catalog, and can be incomplete or incorrect; check the item's source record when in doubt.
- **Playback:** Media3 ExoPlayer in an Android MediaSessionService for background audio, notification/lock-screen media controls, audio focus and headset disconnect handling. Local and online results both play in the service.
- Local favorites, queue selection, seek, shuffle/repeat, album art and original UI.

## Build

Android Studio, Android SDK 35, and JDK 17+ are required. Open this repository in Android Studio and run the `app` configuration. Gradle wrapper scripts are not included yet.

## Limits and sources

Internet search is currently restricted to Internet Archive audio items with recognized CC/public-domain metadata. It is not a YouTube Music or Spotify catalog client, and does not extract streams, bypass access controls, or evade subscriptions. Internet Archive and LRCLIB availability depends on their services and network access. Respect each item's license and attribution terms.

Stii is an independent app. Other open-source music projects informed general UX ideas; their source files and branded assets were not copied.
