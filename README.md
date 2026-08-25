# FTP Mirror Android

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-%230095D5.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-14-%233DDC84.svg)](https://developer.android.com/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Build](https://github.com/3lacksun/ftp-mirror-android/actions/workflows/build.yml/badge.svg)](https://github.com/3lacksun/ftp-mirror-android/actions/workflows/build.yml)

**Commercial-grade native Android APK for scheduled one-way FTP file mirroring.**

Version **2.0.0**

### Features
- 6-digit PIN protection (required on every launch)
- Storage Access Framework folder picker with persistable permissions
- **Encrypted credential storage** (EncryptedSharedPreferences)
- Status dashboard (last run, uploaded / failed / skipped counts)
- Configurable schedule interval (15 min → daily)
- Connection test button
- Background scheduling via WorkManager with network constraints
- Recursive mirroring + automatic remote directory creation
- Optional delete-after-successful-upload
- Foreground service with detailed notifications
- Material 3 UI

### Build
```bash
git clone https://github.com/3lacksun/ftp-mirror-android.git
cd ftp-mirror-android
# Open in Android Studio (recommended) or:
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

APK is also produced automatically by GitHub Actions (Artifacts tab).

### Usage
1. Open app → set 6-digit PIN on first launch.
2. Enter PIN on every subsequent launch.
3. Select a mirror folder.
4. Enter FTP details, choose interval, optionally Test Connection.
5. Tap **Save & Schedule**.
6. Use **Run Now** for an immediate mirror.

**Reset PIN**: Android Settings → Apps → FTP Mirror → Storage → Clear data.

### Notes
- Uses Apache Commons Net (passive mode, binary).
- Cleartext traffic allowed (typical for classic FTP). FTPS can be added later.
- Icons are system placeholders — replace with proper adaptive icons for store release.

## License
MIT © 2026
