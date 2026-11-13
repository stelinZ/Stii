# Stii

A Kotlin/Jetpack Compose Android music player foundation with an original dark-and-lime visual identity. Stii currently scans the device's audio library and supports local playback, search, a queue-backed player session, seeking, and a compact player.

## Build

- Android Studio (or Android SDK) with compile SDK 35
- JDK 17+

Open this repository in Android Studio and run the `app` configuration. The project is a standard Gradle Android project; no wrapper scripts are included yet.

## Current scope

Implemented in this starter: local MediaStore library scan, permission flow, filtering by song/artist/album, playback with Media3, basic previous/next/seek controls, and responsive Compose screens for Home, Search, Library, mini-player, and Now Playing.

Not yet implemented: streaming-service integrations, synced lyrics, downloads, background playback/media notification, Android Auto, equalizer, playlist persistence, account sync, Listen Together, casting, and TV UI. Those are separate features and need their own implementations and testing; this initial commit does not claim feature parity with the referenced apps.

## Design and attribution

Stii has its own layout, colors, and UI implementation. The listed music apps were used only as references for feature ideas and general product direction; no source code, artwork, or branded interface assets were copied. See the inspiration links:

- https://github.com/Yigder/Prism
- https://github.com/nkosanamahungela-dev/NexaMusic
- https://github.com/jlfuertes14/Aura-Music
- https://github.com/OreoMuncher45/Auris
- https://github.com/daddyyour0786-dev/Lossless
- https://github.com/kushagrasinghx/BitChord
- https://github.com/realidkroo/Mochy-Music

Stii is an independent project and is not affiliated with Google, YouTube, or any of the referenced projects.
