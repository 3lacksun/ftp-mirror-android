# FTP Mirror Android

**Commercial-grade native Android app for scheduled one-way FTP mirroring.**

Version **2.2.0**

### Features
- 6-digit PIN + optional biometric unlock
- Encrypted credential storage
- Status dashboard + transfer history
- Configurable schedule (15 min → daily)
- Include / exclude file patterns
- **Passive / Active mode toggle**
- **Connection diagnostics** (DNS, reply codes, clear error messages)
- Cancel running job
- WorkManager + network constraints
- Recursive mirror + delete-after-upload
- Material 3 UI with toolbar

### Connection tips
- Prefer **Passive mode** on mobile networks.
- Use IP if DNS fails.
- Test Connection before scheduling.
- Classic FTP is cleartext — only use on trusted networks. FTPS can be added later.

### Build
Open in Android Studio or:
```bash
./gradlew assembleDebug
```
APK also available from GitHub Actions Artifacts.

## License
MIT © 2026
