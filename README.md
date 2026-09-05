# STONE//SYNC Android

**COVERT SYSTEMS DATA LABORATORY**

STONE//SYNC is the active development line of the native Android folder synchroniser in this repository. The preserved `main` branch remains the prior FTP Mirror baseline; active convergence work is isolated on `stone-sync-development` until Android verification passes.

## Direct endpoint protocols

User-assigned endpoints are connected directly. The sync engine does not require a vendor relay or fixed provider.

- FTP
- FTPS — explicit TLS
- FTPS — implicit TLS
- SFTP — mandatory SHA-256 host-key pin
- SMB 2.0.2 through SMB 3.1.1
- WebDAV — HTTPS by default; an explicit `http://` base URL may be supplied for a user-controlled endpoint

## Core behaviour

- Multiple independent sync endpoints
- Android Storage Access Framework folder selection with persisted tree access
- Two-way sync with Newest Wins / Local Wins / Remote Wins conflict policies
- One-way upload-and-delete-local and download-and-delete-remote modes retained
- Recursive directory transfer
- Direct connection test and remote explorer
- WorkManager background scheduling with connected-network constraint
- Foreground notification during long-running synchronisation
- Cancellation checks during recursive runs

## Security and persistence

- Endpoint/profile data and credentials are stored with Android Keystore-backed `EncryptedSharedPreferences`.
- Plaintext credential fallback is prohibited. A legacy plaintext preference file is migrated into encrypted storage when secure storage becomes available, then cleared.
- SFTP refuses connections without the user-supplied SHA-256 server host-key fingerprint.
- FTPS enables TLS endpoint identification and encrypted data-channel protection (`PROT P`).
- SMB1 is disabled by protocol policy.
- Remote paths reject parent traversal and control characters.
- WebDAV uses the platform trust store for TLS validation; no permissive trust manager is installed.

## Verification

GitHub Actions on the development branch executes:

```text
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

A successful workflow produces the `stone-sync-debug` APK artifact. Live FTP/FTPS/SFTP/SMB/WebDAV acceptance still requires user-authorised test endpoints and credentials and must not be inferred from compilation alone.

## Preserved Android identity

The existing application ID/package remains `com.github.ftpmirror` during development so installed-data/update compatibility is not silently broken. A package-identity migration requires an explicit release decision.

## License

MIT © 2026
