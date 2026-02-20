# Stash Screensaver for Android TV

A dynamic, scattered-collage screensaver for Android TV that fetches images directly from your Stash server via GraphQL.

## Features

- **Direct GraphQL Integration**: Fetches image metadata, including high-resolution URLs and dimensions, directly from your Stash server.
- **Dynamic Scattered Layout**: 
  - Intelligent global pre-planning ensures images fill the screen with minimal overlap.
  - Supports mixed aspect ratios (Portrait, Landscape, Square) using actual image resolution data.
  - Organic "scattered photo" look with random tilt and size variations.
- **Configurable Settings**:
  - **Server Connection**: Custom IP address and Port for your Stash instance.
  - **Image Filtering**: Filter by Orientation (Portrait, Landscape, Both) and Stash Tags (Include/Exclude by numeric IDs).
  - **Customizable Display**: Control the number of images to retrieve and the number of images to show on screen at once.
  - **Dynamic Timing**: Configurable refresh delay with a percentage-based variance for natural, non-simultaneous transitions.
- **Infinite Sequential Shuffling**: Uses a shuffled queue to ensure every image in a batch is shown before repeating, with silent background fetching for new batches.
- **Themed Splash Screen**: Black and white artistic loading screen with a selectable Male or Female background.

## How it works

1. **On Activation**: The screensaver shows a themed splash screen while connecting to your Stash server.
2. **GraphQL Query**: Retrieves a batch of images based on your filters (Tags, Orientation, Resolution > HD).
3. **Global Planning**: Calculates a non-overlapping layout for the first set of images before they fade in.
4. **Independent Rotation**: Each image container independently fades out and repositions to a new random location when its unique timer (Base Delay ± Variance) expires.
5. **Silent Refill**: As the current batch of images runs low, the app silently fetches more in the background to maintain an endless stream of content.

## Installation

### Prerequisites
- Android TV 11+ (API 30+) recommended (supports API 21+).
- Stash Server accessible on the local network.

### Build & Install
1. **Open in Android Studio**: Hedgehog (2023.1.1) or newer.
2. **Build APK**: `Build → Build APK(s)`.
3. **Install via ADB**:
   ```bash
   adb connect <your-tv-ip>
   adb install app/build/outputs/apk/debug/StashScreensaver.apk
   ```

## Setup on TV

1. **Open the App**: Launch "Stash Screensaver" from your TV's app list.
2. **Test Connection**: Use the "Test Stash Connection" button to ensure your IP and Port are correct.
3. **Configure Settings**: Click "Open Screensaver Settings" to customize your experience.
4. **Enable Screensaver**:
   - Go to TV `Settings → Device Preferences → Screen saver`.
   - Select **Stash Screensaver**.

## Project Structure

- `ScreensaverService.kt`: Core logic for GraphQL fetching, greedy layout planning, and animation cycles.
- `SettingsActivity.kt`: Configuration UI for user preferences.
- `MainActivity.kt`: Entry point for testing connection and opening system settings.
- `network_security_config.xml`: Configured to allow cleartext HTTP traffic to local network IPs.
