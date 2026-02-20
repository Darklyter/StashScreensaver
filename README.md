# Stash Screensaver

An Android TV screensaver (DreamService) that fetches images from your Stash RSS feed and displays them in a 3×2 rotating collage.

## How it works

- On activation, fetches `http://192.168.1.75/stash_images.rss`
- Parses image URLs from `<enclosure>` tags
- Displays 6 images simultaneously in a 3-column × 2-row grid
- Each image slot independently rotates to a new random image after 10–30 seconds
- Images crossfade over 800ms when replaced
- Slots are staggered by 600ms at startup so they don't all swap at once

## Build & Install

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- A device or emulator running Android 5.0+ (API 21+)

### Steps

1. **Open the project in Android Studio**
   - File → Open → select the `StashScreensaver` folder

2. **Let Gradle sync** — it will download all dependencies automatically

3. **Build the APK**
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

4. **Install on your Android TV**
   ```bash
   adb connect <tv-ip-address>
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

5. **Enable the screensaver on Android TV**
   - Settings → Device Preferences → Screen saver
   - Select **Stash Screensaver**
   - Optionally set "When to start" and "Start now" to test it

### Sideloading without Android Studio

If you only have the APK file and want to sideload via ADB:
```bash
adb connect <tv-ip-address>
adb install app-debug.apk
```
Then enable in Settings as described above.

## Configuration

To change the RSS URL or timing, edit the constants at the top of
`app/src/main/java/com/stash/screensaver/ScreensaverService.kt`:

```kotlin
private const val RSS_URL = "http://192.168.1.75/stash_images.rss"
private const val MIN_DELAY_MS = 10_000L   // 10 seconds
private const val MAX_DELAY_MS = 30_000L   // 30 seconds
private const val CROSSFADE_MS = 800       // crossfade duration
```

If your Stash instance IP changes, also update `app/src/main/res/xml/network_security_config.xml`
to add the new IP address.

## Project Structure

```
app/src/main/
├── AndroidManifest.xml               — registers the DreamService
├── java/com/stash/screensaver/
│   └── ScreensaverService.kt         — all screensaver logic
└── res/
    ├── layout/screensaver_layout.xml — 3×2 image grid
    ├── xml/network_security_config.xml — allows HTTP to local IPs
    └── values/strings.xml
```
