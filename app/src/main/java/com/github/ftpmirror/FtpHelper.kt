package com.github.ftpmirror

import android.util.Log
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.net.InetAddress

/** Shared FTP connection helper with better diagnostics. */
object FtpHelper {

    private const val TAG = "FtpHelper"

    data class ConnectResult(
        val success: Boolean,
        val message: String,
        val client: FTPClient? = null
    )

    fun connect(
        host: String,
        port: Int,
        username: String,
        password: String,
        passive: Boolean = true,
        connectTimeoutMs: Int = 20000,
        dataTimeoutMs: Int = 60000
    ): ConnectResult {
        val client = FTPClient()
        try {
            client.connectTimeout = connectTimeoutMs
            client.defaultTimeout = dataTimeoutMs
            client.setDataTimeout(dataTimeoutMs)
            client.controlKeepAliveTimeout = 30

            // Resolve host first so we get a clearer error
            val addr = try {
                InetAddress.getByName(host)
            } catch (e: Exception) {
                return ConnectResult(false, "DNS failed: ${e.message}")
            }

            client.connect(addr, port)
            val reply = client.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                client.disconnect()
                return ConnectResult(false, "Server refused connection (reply $reply)")
            }

            if (!client.login(username, password)) {
                val replyStr = client.replyString?.trim() ?: "login rejected"
                client.disconnect()
                return ConnectResult(false, "Login failed: $replyStr")
            }

            if (passive) {
                client.enterLocalPassiveMode()
            } else {
                client.enterLocalActiveMode()
            }
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client.setFileTransferMode(FTP.STREAM_TRANSFER_MODE)

            val sys = try { client.systemType } catch (_: Exception) { "unknown" }
            Log.i(TAG, "Connected to $host as $username (sys=$sys, passive=$passive)")
            return ConnectResult(true, "OK — $sys", client)
        } catch (e: Exception) {
            try { if (client.isConnected) client.disconnect() } catch (_: Exception) {}
            val msg = when {
                e.message?.contains("ECONNREFUSED", true) == true -> "Connection refused (check host/port)"
                e.message?.contains("ETIMEDOUT", true) == true -> "Timed out (firewall / wrong host?)"
                e.message?.contains("Network is unreachable", true) == true -> "Network unreachable"
                else -> e.message?.take(80) ?: e.javaClass.simpleName
            }
            Log.e(TAG, "Connect failed", e)
            return ConnectResult(false, msg)
        }
    }

    fun disconnectQuietly(client: FTPClient?) {
        if (client == null) return
        try {
            if (client.isConnected) {
                try { client.logout() } catch (_: Exception) {}
                client.disconnect()
            }
        } catch (_: Exception) {}
    }
}
