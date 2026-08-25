# FTP Mirror Android

**Commercial-grade native Android APK for scheduled one-way FTP file mirroring.**

Version **2.1.0**

### Features
- 6-digit PIN + optional **biometric unlock** (fingerprint / face)
- Storage Access Framework folder picker with persistable permissions
- **Encrypted credential storage** (EncryptedSharedPreferences)
- Status dashboard (last run, counters, recent history)
- Configurable schedule (15 min → daily)
- **Include / exclude file patterns** (e.g. `*.jpg,*.pdf`)
- Connection test
- Cancel running job
- Background scheduling via WorkManager + network constraints
- Recursive mirroring + automatic remote directory creation
- Optional delete-after-successful-upload
- Foreground service with detailed notifications
- Material 3 UI

### Build
```bash
git clone https://github.com/3lacksun/ftp-mirror-android.git
cd ftp-mirror-android
# Open in Android Studio (recommended)
./gradlew assembleDebug
```

APK is produced automatically by GitHub Actions (Artifacts tab).

### Usage
1. Open → set 6-digit PIN (biometric available on subsequent launches).
2. Select mirror folder.
3. Enter FTP details, optional include/exclude patterns, choose interval.
4. Test Connection → Save & Schedule.
5. Run Now or wait for the schedule. Use Cancel if needed.

**Reset PIN**: Android Settings → Apps → FTP Mirror → Storage → Clear data.

## License
MIT © 2026
