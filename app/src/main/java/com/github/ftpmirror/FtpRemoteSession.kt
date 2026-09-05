package com.github.ftpmirror

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class FtpRemoteSession private constructor(
    private val client: FTPClient
) : RemoteSession {

    override fun list(path: String): List<RemoteEntry> {
        val normal = RemotePaths.normalise(path)
        return (client.listFiles(normal) ?: emptyArray())
            .asSequence()
            .filter { it.name != "." && it.name != ".." }
            .map { it.toRemoteEntry(normal) }
            .toList()
    }

    override fun ensureDir(path: String) {
        val normal = RemotePaths.normalise(path)
        if (normal == "/") return
        var current = ""
        normal.trim('/').split('/').filter { it.isNotBlank() }.forEach { part ->
            current += "/$part"
            if (!client.changeWorkingDirectory(current)) {
                client.makeDirectory(current)
                if (!client.changeWorkingDirectory(current)) {
                    throw IOException("Unable to create remote directory: $current")
                }
            }
        }
    }

    override fun upload(path: String, input: InputStream) {
        val normal = RemotePaths.normalise(path)
        ensureDir(RemotePaths.parent(normal))
        if (!client.storeFile(normal, input)) {
            throw IOException(client.replyString?.trim().orEmpty().ifBlank { "FTP upload failed" })
        }
    }

    override fun download(path: String, output: OutputStream) {
        val normal = RemotePaths.normalise(path)
        if (!client.retrieveFile(normal, output)) {
            throw IOException(client.replyString?.trim().orEmpty().ifBlank { "FTP download failed" })
        }
    }

    override fun delete(path: String) {
        val normal = RemotePaths.normalise(path)
        if (!client.deleteFile(normal) && !client.removeDirectory(normal)) {
            throw IOException(client.replyString?.trim().orEmpty().ifBlank { "FTP delete failed" })
        }
    }

    override fun close() {
        try {
            if (client.isConnected) {
                try {
                    client.logout()
                } catch (_: Exception) {
                }
                client.disconnect()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        fun connect(pair: SyncPair): FtpRemoteSession {
            val protocol = RemoteProtocol.fromStored(pair.protocol)
            val client: FTPClient = when (protocol) {
                RemoteProtocol.FTPS_EXPLICIT -> FTPSClient(false).apply {
                    setEndpointCheckingEnabled(true)
                }
                RemoteProtocol.FTPS_IMPLICIT -> FTPSClient(true).apply {
                    setEndpointCheckingEnabled(true)
                }
                else -> FTPClient()
            }

            try {
                client.connectTimeout = 20_000
                client.defaultTimeout = 60_000
                client.setDataTimeout(60_000)
                client.controlKeepAliveTimeout = 30
                client.connect(pair.host, pair.port)

                if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                    throw IOException("Server refused connection (reply ${client.replyCode})")
                }
                if (!client.login(pair.username, pair.password)) {
                    throw IOException(client.replyString?.trim().orEmpty().ifBlank { "Login failed" })
                }

                if (client is FTPSClient) {
                    client.execPBSZ(0)
                    client.execPROT("P")
                }

                if (pair.passive) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
                client.setFileType(FTP.BINARY_FILE_TYPE)
                client.setFileTransferMode(FTP.STREAM_TRANSFER_MODE)
                return FtpRemoteSession(client)
            } catch (e: Exception) {
                try {
                    if (client.isConnected) client.disconnect()
                } catch (_: Exception) {
                }
                throw IOException("${protocol.label} connection failed: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }

        private fun FTPFile.toRemoteEntry(parent: String): RemoteEntry = RemoteEntry(
            name = name,
            path = RemotePaths.join(parent, name),
            isDirectory = isDirectory,
            size = if (isFile) size else 0L,
            modifiedMillis = timestamp?.timeInMillis
        )
    }
}
