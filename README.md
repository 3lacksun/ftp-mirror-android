# FTP Mirror Android

Version **3.0.0** — multi-pair two-way FTP folder sync.

### Features
- **Multiple folder pairs** (each with its own local folder + FTP destination)
- **Two-way sync** — upload missing remote files and download missing local files
- **Upload & delete local** after successful upload
- **Download & delete remote** after successful download
- **Scheduled sync** via WorkManager (15 min → daily)
- **Local file explorer** and **remote FTP explorer**
- Foreground **notifications** with upload/download summary
- PIN + optional biometric unlock
- Encrypted pair credentials

### How to use
1. Unlock with PIN / biometrics.
2. Tap **Add pair** → choose local folder, enter FTP details, pick a mode.
3. **Browse local** / **Browse remote** to inspect both sides.
4. Save the pair. Repeat for more pairs.
5. **Save schedule**, or tap **Sync now**.

### Modes
- Two-way sync — copy missing files both directions (existing names are skipped)
- Upload & delete local — send then remove local original
- Download & delete remote — pull then remove remote original

## License
MIT © 2026
