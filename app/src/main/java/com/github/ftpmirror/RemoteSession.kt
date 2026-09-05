package com.github.ftpmirror

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

enum class RemoteProtocol(
    val storedValue: String,
    val label: String,
    val defaultPort: Int
) {
    FTP("ftp", "FTP", 21),
    FTPS_EXPLICIT("ftps_explicit", "FTPS (explicit TLS)", 21),
    FTPS_IMPLICIT("ftps_implicit", "FTPS (implicit TLS)", 990),
    SFTP("sftp", "SFTP", 22),
    SMB("smb", "SMB", 445),
    WEBDAV("webdav", "WebDAV", 443);

    companion object {
        fun fromStored(value: String?): RemoteProtocol =
            entries.firstOrNull { it.storedValue == value } ?: FTP
    }
}

enum class ConflictPolicy(val storedValue: String, val label: String) {
    NEWEST_WINS("newest_wins", "Newest wins"),
    LOCAL_WINS("local_wins", "Local wins"),
    REMOTE_WINS("remote_wins", "Remote wins");

    companion object {
        fun fromStored(value: String?): ConflictPolicy =
            entries.firstOrNull { it.storedValue == value } ?: NEWEST_WINS
    }
}

data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val modifiedMillis: Long? = null
)

interface RemoteSession : Closeable {
    fun list(path: String): List<RemoteEntry>
    fun ensureDir(path: String)
    fun upload(path: String, input: InputStream)
    fun download(path: String, output: OutputStream)
    fun delete(path: String)
}

object RemotePaths {
    fun normalise(path: String): String {
        require(!path.contains('\u0000')) { "Remote path contains NUL" }
        require(path.none { it.code in 1..31 }) { "Remote path contains control characters" }

        val parts = path.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }
        require(parts.none { it == ".." }) { "Remote path traversal is not allowed" }
        return "/" + parts.joinToString("/")
    }

    fun join(base: String, child: String): String {
        require(child.isNotBlank() && child != "." && child != "..") { "Invalid remote child name" }
        return normalise(normalise(base).trimEnd('/') + "/" + child)
    }

    fun parent(path: String): String {
        val normal = normalise(path)
        val idx = normal.lastIndexOf('/')
        return if (idx <= 0) "/" else normal.substring(0, idx)
    }
}

object EndpointValidator {
    private val sftpSha256 = Regex("^(?:SHA256:)?[A-Za-z0-9+/]{43}=?$")

    fun validate(pair: SyncPair) {
        require(pair.host.isNotBlank()) { "Host is required" }
        require(pair.port in 1..65535) { "Port must be between 1 and 65535" }
        RemotePaths.normalise(pair.remoteDir)

        val protocol = RemoteProtocol.fromStored(pair.protocol)
        if (protocol == RemoteProtocol.WEBDAV) {
            validateWebDavHost(pair.host)
        } else {
            require(!pair.host.contains("/")) { "Host must not contain a path" }
            require(!pair.host.contains("@")) { "Host must not contain credentials" }
            require(!pair.host.contains("?")) { "Host must not contain a query" }
            require(!pair.host.contains("#")) { "Host must not contain a fragment" }
            require(!pair.host.contains("://")) { "Enter the host only for this protocol" }
        }

        if (protocol == RemoteProtocol.SFTP) {
            val pin = pair.hostKeySha256.trim()
            require(sftpSha256.matches(pin)) {
                "SFTP requires the server SHA-256 host-key fingerprint"
            }
        }

        if (protocol == RemoteProtocol.SMB) {
            val parts = RemotePaths.normalise(pair.remoteDir).trim('/').split('/').filter { it.isNotBlank() }
            require(parts.isNotEmpty()) { "SMB remote path must start with a share name" }
        }
    }

    private fun validateWebDavHost(host: String) {
        if (host.contains("://")) {
            val uri = java.net.URI(host)
            require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) {
                "WebDAV URL must use http or https"
            }
            require(!uri.host.isNullOrBlank()) { "WebDAV URL must include a host" }
            require(uri.userInfo == null) { "Do not embed credentials in the WebDAV URL" }
            require(uri.query == null && uri.fragment == null) { "WebDAV URL must not contain query or fragment data" }
        } else {
            require(!host.contains("/")) { "WebDAV host must be a hostname or an http(s) base URL" }
            require(!host.contains("@")) { "Host must not contain credentials" }
        }
    }
}

object RemoteSessionFactory {
    fun connect(pair: SyncPair): RemoteSession {
        EndpointValidator.validate(pair)
        return when (RemoteProtocol.fromStored(pair.protocol)) {
            RemoteProtocol.FTP,
            RemoteProtocol.FTPS_EXPLICIT,
            RemoteProtocol.FTPS_IMPLICIT -> FtpRemoteSession.connect(pair)

            RemoteProtocol.SFTP -> SftpRemoteSession.connect(pair)
            RemoteProtocol.SMB -> SmbRemoteSession.connect(pair)
            RemoteProtocol.WEBDAV -> WebDavRemoteSession.connect(pair)
        }
    }
}
