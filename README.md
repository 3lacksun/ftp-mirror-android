# FTP Mirror Android

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-%230095D5.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-14-%233DDC84.svg)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Build](https://github.com/3lacksun/ftp-mirror-android/actions/workflows/build.yml/badge.svg)](https://github.com/3lacksun/ftp-mirror-android/actions/workflows/build.yml)

**Native Android APK for scheduled one-way FTP file mirroring with PIN protection and folder picker.**

### Features
- 6-digit PIN set on first launch (required every launch)
- Storage Access Framework folder picker (any folder)
- Background scheduled mirroring via WorkManager (default every 15 min)
- Recursive mirroring with automatic remote directory creation
- Optional "delete after successful upload"
- Foreground service with notification
- aarch64 / arm64-v8a compatible (minSdk 24)

### Quick Start (recommended)

**Option A – Android Studio (easiest)**
1. Clone the repo
2. Open the project in Android Studio
3. Let it sync Gradle (it will download the wrapper automatically)
4. Build → Build Bundle(s) / APK(s) → Build APK(s)

**Option B – Command line**
```bash
git clone https://github.com/3lacksun/ftp-mirror-android.git
cd ftp-mirror-android

# Generate the Gradle wrapper (one-time)
gradle wrapper --gradle-version 8.7

# Build
./gradlew assembleDebug
```
APK location: `app/build/outputs/apk/debug/app-debug.apk`

**Option C – Download the APK from Actions**
Every push runs a GitHub Action that builds the debug APK.  
Go to the **Actions** tab → latest successful workflow → Artifacts → download `app-debug`.

### Usage
1. Install and open → set 6-digit PIN on first run.
2. Enter PIN on every launch.
3. Tap **Select Mirror Folder** and choose a directory.
4. Enter FTP details → **Save & Schedule**.
5. Add files to the selected folder — they will be mirrored automatically.

**To reset PIN**: Clear app data in Android Settings → Apps → FTP Mirror → Storage → Clear data.

### Notes
- Uses Apache Commons Net for FTP (passive mode, binary).
- SAF + persistable URI permissions — no legacy storage permissions needed.
- Cleartext traffic allowed (typical for FTP). For FTPS/SFTP further work is required.
- Icons are system placeholders; replace with proper adaptive icons for production.

## License
MIT © 2026
