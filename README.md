# Stii

A Kotlin/Jetpack Compose Android music player foundation with an original dark-and-lime visual identity.

## Current features

- Scans the on-device music library with Android MediaStore.
- Immediate local search across track, artist, and album names. Search work runs on a cancellable background coroutine; exact/prefix matches rank first and stale searches are discarded.
- Media3 playback with audio-focus handling, headset unplug pause, queue selection, seek scrubbing, shuffle, repeat, and album artwork.
- Local favorites persist on the device and appear in the Library tab.
- Animated screen transitions, search-field reveal, track-title changes, play/pause morph, and subtle playing-cover motion.

## Build

- Android Studio with Android SDK 35
- JDK 17+

Open this repository in Android Studio and run the `app` configuration. The project is a standard Gradle Android project; Gradle wrapper scripts are not included yet.

## Limitations

This version plays local device files. It does not yet include streaming-service integrations, lyrics, background playback/media notification, downloads, Android Auto, equalizer, playlist persistence, account sync, Listen Together, casting, or TV UI. Those need dedicated implementations and verification; the project does not claim feature parity with referenced apps.

## Inspiration and attribution

Stii uses general product ideas as inspiration, while its source code and visual implementation are original. No source files, artwork, or branded interface assets from these projects were copied:

- https://github.com/Yigder/Prism
- https://github.com/nkosanamahungela-dev/NexaMusic
- https://github.com/jlfuertes14/Aura-Music
- https://github.com/OreoMuncher45/Auris
- https://github.com/daddyyour0786-dev/Lossless
- https://github.com/kushagrasinghx/BitChord
- https://github.com/realidkroo/Mochy-Music

Stii is an independent project and is not affiliated with Google, YouTube, or the referenced apps.
