# Stii

Personal Kotlin/Jetpack Compose Android music player with original design, background playback and online discovery of openly licensed audio.

## Features implemented

- Local Android MediaStore library, permission flow, and instant offline search by track, artist, and album.
- Internet Archive audio search in the Search tab. Search requests are debounced and item metadata is resolved concurrently with a bounded worker pool. Only audio entries whose catalog metadata declares a recognized Creative Commons or public-domain license are offered; the license is displayed on each result.
- Media3 `MediaSessionService` playback for local and online audio, background continuation, system media controls, audio focus, pause-on-headphone-disconnect, queue, seek, shuffle/repeat, and artwork metadata.
- Local favorites and an original dark/lime Compose UI with mini-player and full player.

## Build

Android Studio, Android SDK 35, and JDK 17+ are required. Open this repository in Android Studio and run the `app` configuration. Gradle wrapper scripts are not included yet.

## Important source and licensing notes

The online catalog currently searches Internet Archive only, and playback is limited to items with recognized license metadata. Catalog rights metadata may be incomplete or incorrect: review the original item and comply with its license and attribution requirements. Search and playback of YouTube Music, Spotify, or other protected catalogs are not implemented. Stii does not extract streams, bypass access controls, or evade subscriptions.

Other open-source music apps informed general product ideas. Stii uses its own implementation and design rather than copying their branded assets or complete screens.
