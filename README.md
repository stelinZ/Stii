# Stii

Personal Kotlin/Jetpack Compose Android music player with original dark/lime UI.

## Install on your Android phone

The repository currently does not include a prebuilt APK or Gradle wrapper. Build a debug APK on a computer:

1. Install Android Studio and open this repository (`stelinZ/Stii`). Install Android SDK Platform 35 when prompted; use JDK 17 (Android Studio's bundled JDK is suitable).
2. In Android Studio's Terminal, install/use Gradle 8.9 and generate the wrapper once:
   ```bash
   gradle wrapper --gradle-version 8.9
   ```
   If `gradle` is not available, install Gradle 8.9 and add it to PATH first.
3. Build the APK:
   - Windows: `gradlew.bat assembleDebug`
   - macOS/Linux: `./gradlew assembleDebug`
4. APK output: `app/build/outputs/apk/debug/app-debug.apk`.
5. Copy the APK to your phone and open it. Android may ask you to allow “Install unknown apps” for the file manager/browser you used. Approve only if you trust the APK source.

Alternatively, connect the phone with USB debugging enabled and run `adb install -r app/build/outputs/apk/debug/app-debug.apk` after building.

## Implemented foundation

- Local Android MediaStore library, instant offline search, and persistent favorites.
- Internet Archive audio search for entries with recognized CC/public-domain metadata.
- Media3 `MediaSessionService` background playback and system media controls.
- Original Compose screens, mini-player and full player, with queue controls, seek, shuffle/repeat.

## Limitations

The online catalog is not YouTube Music or Spotify. Availability and rights metadata depend on the catalog; review the original item and obey its license. Stii does not extract streams, bypass access controls, or evade subscriptions. Gradle wrapper scripts are not checked in, so generate them as above before command-line builds.
