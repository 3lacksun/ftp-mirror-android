# FTP Mirror Android

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-%230095D5.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-14-%233DDC84.svg)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Native Android APK for scheduled one-way FTP file mirroring with PIN protection and folder picker.**

### Features
- 6-digit PIN set on first launch (required every launch)
- Storage Access Framework folder picker (any folder)
- Background scheduled mirroring via WorkManager (default every 15 min)
- Recursive mirroring with automatic remote directory creation
- Optional "delete after successful upload"
- Foreground service with notification
- aarch64 / arm64-v8a compatible (minSdk 24)

### Build
```bash
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

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
- Cleartext traffic allowed (typical for FTP). For FTPS/SFTP, further work is required.
- Icons are system placeholders; replace with proper adaptive icons for production.

## License
MIT © 2026
