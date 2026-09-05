package com.github.ftpmirror

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import jcifs.smb.SmbFileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

class SmbRemoteSession private constructor(
    private val context: CIFSContext,
    private val host: String,
    private val port: Int
) : RemoteSession {

    override fun list(path: String): List<RemoteEntry> {
        val normal = RemotePaths.normalise(path)
        val dir = SmbFile(url(normal, directory = true), context)
        return dir.listFiles().asSequence().map { file ->
            val cleanName = file.name.trimEnd('/')
            RemoteEntry(
                name = cleanName,
                path = RemotePaths.join(normal, cleanName),
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) 0L else file.length(),
                modifiedMillis = file.lastModified()
            )
        }.toList()
    }

    override fun ensureDir(path: String) {
        val normal = RemotePaths.normalise(path)
        if (normal == "/") return
        val dir = SmbFile(url(normal, directory = true), context)
        if (!dir.exists()) dir.mkdirs()
        if (!dir.exists() || !dir.isDirectory) {
            throw IllegalStateException("Unable to create SMB directory: $normal")
        }
    }

    override fun upload(path: String, input: InputStream) {
        val normal = RemotePaths.normalise(path)
        ensureDir(RemotePaths.parent(normal))
        val file = SmbFile(url(normal, directory = false), context)
        SmbFileOutputStream(file).use { output -> input.copyTo(output) }
    }

    override fun download(path: String, output: OutputStream) {
        val file = SmbFile(url(RemotePaths.normalise(path), directory = false), context)
        SmbFileInputStream(file).use { input -> input.copyTo(output) }
    }

    override fun delete(path: String) {
        val file = SmbFile(url(RemotePaths.normalise(path), directory = false), context)
        file.delete()
    }

    override fun close() = Unit

    private fun url(path: String, directory: Boolean): String {
        val normal = RemotePaths.normalise(path).trimStart('/')
        val server = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        val authority = if (port == 445) server else "$server:$port"
        val suffix = if (directory && normal.isNotEmpty()) "$normal/" else normal
        return "smb://$authority/$suffix"
    }

    companion object {
        fun connect(pair: SyncPair): SmbRemoteSession {
            val props = Properties().apply {
                setProperty("jcifs.smb.client.minVersion", "SMB202")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
                setProperty("jcifs.smb.client.responseTimeout", "60000")
                setProperty("jcifs.smb.client.soTimeout", "60000")
            }
            val base = BaseContext(PropertyConfiguration(props))
            val (domain, user) = splitDomain(pair.username)
            val credentials = NtlmPasswordAuthenticator(domain, user, pair.password)
            val authenticated = base.withCredentials(credentials)
            val remote = SmbRemoteSession(authenticated, pair.host, pair.port)

            // Force a real network/authentication check before returning the session.
            val root = SmbFile(remote.url(RemotePaths.normalise(pair.remoteDir), directory = true), authenticated)
            root.exists()
            return remote
        }

        private fun splitDomain(username: String): Pair<String, String> {
            val idx = username.indexOf('\\')
            return if (idx > 0 && idx < username.lastIndex) {
                username.substring(0, idx) to username.substring(idx + 1)
            } else {
                "" to username
            }
        }
    }
}
