package com.github.ftpmirror

import android.util.Base64
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.UserInfo
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

class SftpRemoteSession private constructor(
    private val session: Session,
    private val channel: ChannelSftp
) : RemoteSession {

    override fun list(path: String): List<RemoteEntry> {
        val normal = RemotePaths.normalise(path)
        @Suppress("UNCHECKED_CAST")
        val entries = channel.ls(normal) as java.util.Vector<ChannelSftp.LsEntry>
        return entries.asSequence()
            .filter { it.filename != "." && it.filename != ".." }
            .map { entry ->
                RemoteEntry(
                    name = entry.filename,
                    path = RemotePaths.join(normal, entry.filename),
                    isDirectory = entry.attrs.isDir,
                    size = if (entry.attrs.isDir) 0L else entry.attrs.size,
                    modifiedMillis = entry.attrs.mTime.toLong() * 1000L
                )
            }
            .toList()
    }

    override fun ensureDir(path: String) {
        val normal = RemotePaths.normalise(path)
        if (normal == "/") return
        var current = ""
        normal.trim('/').split('/').filter { it.isNotBlank() }.forEach { part ->
            current += "/$part"
            try {
                channel.stat(current)
            } catch (_: SftpException) {
                channel.mkdir(current)
            }
        }
    }

    override fun upload(path: String, input: InputStream) {
        val normal = RemotePaths.normalise(path)
        ensureDir(RemotePaths.parent(normal))
        channel.put(input, normal, ChannelSftp.OVERWRITE)
    }

    override fun download(path: String, output: OutputStream) {
        channel.get(RemotePaths.normalise(path), output)
    }

    override fun delete(path: String) {
        val normal = RemotePaths.normalise(path)
        try {
            channel.rm(normal)
        } catch (_: SftpException) {
            channel.rmdir(normal)
        }
    }

    override fun close() {
        try {
            channel.disconnect()
        } catch (_: Exception) {
        }
        try {
            session.disconnect()
        } catch (_: Exception) {
        }
    }

    companion object {
        fun connect(pair: SyncPair): SftpRemoteSession {
            val jsch = JSch()
            jsch.hostKeyRepository = PinnedHostKeyRepository(pair.hostKeySha256)
            val session = jsch.getSession(pair.username, pair.host, pair.port)
            session.setPassword(pair.password)
            session.setConfig("StrictHostKeyChecking", "yes")
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive")

            try {
                session.connect(20_000)
                val channel = session.openChannel("sftp") as ChannelSftp
                channel.connect(20_000)
                return SftpRemoteSession(session, channel)
            } catch (e: Exception) {
                try {
                    session.disconnect()
                } catch (_: Exception) {
                }
                throw IOException("SFTP connection failed: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }
    }

    private class PinnedHostKeyRepository(expectedFingerprint: String) : HostKeyRepository {
        private val expected = normalise(expectedFingerprint)

        override fun check(host: String?, key: ByteArray?): Int {
            if (key == null) return HostKeyRepository.NOT_INCLUDED
            val digest = MessageDigest.getInstance("SHA-256").digest(key)
            val actual = Base64.encodeToString(digest, Base64.NO_WRAP or Base64.NO_PADDING)
            return if (MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())) {
                HostKeyRepository.OK
            } else {
                HostKeyRepository.CHANGED
            }
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
        override fun remove(host: String?, type: String?) = Unit
        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "STONE//SYNC SHA-256 host-key pin"
        override fun getHostKey(): Array<HostKey> = emptyArray()
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()

        companion object {
            private fun normalise(value: String): String = value.trim()
                .removePrefix("SHA256:")
                .trimEnd('=')
        }
    }
}
