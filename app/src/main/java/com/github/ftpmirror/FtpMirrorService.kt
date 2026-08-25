package com.github.ftpmirror

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FtpMirrorService(private val context: Context) {

    private val tag = "FtpMirror"

    data class Result(var uploaded: Int = 0, var failed: Int = 0, var skipped: Int = 0)

    fun performMirror(): Result {
        val result = Result()
        val prefs = securePrefs()
        prefs.edit().putBoolean("is_running", true).putBoolean("cancel_requested", false).apply()

        try {
            val treeUriString = prefs.getString("tree_uri", null)
            if (treeUriString.isNullOrEmpty()) {
                Log.e(tag, "No folder selected")
                return result
            }

            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))
            if (rootDoc == null) {
                Log.e(tag, "Invalid tree URI")
                return result
            }

            val host = prefs.getString("host", null) ?: return result
            val port = prefs.getInt("port", 21)
            val username = prefs.getString("username", null) ?: return result
            val password = prefs.getString("password", null) ?: return result
            val remoteBase = prefs.getString("remote_dir", "/mirror") ?: "/mirror"
            val deleteAfter = prefs.getBoolean("delete_after", true)
            val include = parsePatterns(prefs.getString("include_patterns", ""))
            val exclude = parsePatterns(prefs.getString("exclude_patterns", ""))

            val client = FTPClient()
            client.connectTimeout = 30000
            client.defaultTimeout = 30000

            try {
                client.connect(host, port)
                if (!client.login(username, password)) {
                    Log.e(tag, "FTP login failed")
                    result.failed++
                    return result
                }
                client.enterLocalPassiveMode()
                client.setFileType(FTP.BINARY_FILE_TYPE)

                ensureRemoteDirectory(client, remoteBase)
                syncDocumentFile(rootDoc, client, remoteBase, deleteAfter, include, exclude, result, prefs)

                Log.i(tag, "Mirror done: $result")
            } catch (e: Exception) {
                Log.e(tag, "Mirror failed", e)
                result.failed++
            } finally {
                try {
                    if (client.isConnected) {
                        client.logout()
                        client.disconnect()
                    }
                } catch (_: Exception) {}
            }
        } finally {
            val ts = System.currentTimeMillis()
            val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
            val line = "${fmt.format(Date(ts))} — ↑${result.uploaded} ✗${result.failed} ↻${result.skipped}"
            val prev = prefs.getString("run_history", "") ?: ""
            val lines = (listOf(line) + prev.lines().filter { it.isNotBlank() }).take(8)
            prefs.edit()
                .putBoolean("is_running", false)
                .putLong("last_run_ts", ts)
                .putInt("last_uploaded", result.uploaded)
                .putInt("last_failed", result.failed)
                .putInt("last_skipped", result.skipped)
                .putString("run_history", lines.joinToString("\n"))
                .apply()
        }
        return result
    }

    private fun parsePatterns(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    }

    private fun matches(name: String, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return false
        val n = name.lowercase()
        return patterns.any { pat ->
            when {
                pat.startsWith("*.") -> n.endsWith(pat.removePrefix("*"))
                pat.endsWith("*") -> n.startsWith(pat.removeSuffix("*"))
                else -> n == pat || n.contains(pat)
            }
        }
    }

    private fun shouldProcess(name: String, include: List<String>, exclude: List<String>): Boolean {
        if (exclude.isNotEmpty() && matches(name, exclude)) return false
        if (include.isNotEmpty()) return matches(name, include)
        return true
    }

    private fun securePrefs() = try {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context, "ftp_secure_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("ftp_prefs", Context.MODE_PRIVATE)
    }

    private fun ensureRemoteDirectory(client: FTPClient, path: String) {
        val parts = path.split("/").filter { it.isNotEmpty() }
        var current = ""
        for (part in parts) {
            current += "/$part"
            client.makeDirectory(current)
        }
        client.changeWorkingDirectory(path)
    }

    private fun syncDocumentFile(
        doc: DocumentFile,
        client: FTPClient,
        remoteBase: String,
        deleteAfter: Boolean,
        include: List<String>,
        exclude: List<String>,
        result: Result,
        prefs: android.content.SharedPreferences
    ) {
        if (prefs.getBoolean("cancel_requested", false)) return
        val files = doc.listFiles() ?: return
        for (file in files) {
            if (prefs.getBoolean("cancel_requested", false)) return
            val name = file.name ?: continue
            val remotePath = if (remoteBase.endsWith("/")) "$remoteBase$name" else "$remoteBase/$name"

            if (file.isDirectory) {
                client.makeDirectory(remotePath)
                syncDocumentFile(file, client, remotePath, deleteAfter, include, exclude, result, prefs)
            } else if (file.isFile) {
                if (!shouldProcess(name, include, exclude)) {
                    result.skipped++
                    continue
                }
                uploadDocumentFile(file, client, remoteBase, deleteAfter, result)
            }
        }
    }

    private fun uploadDocumentFile(
        docFile: DocumentFile,
        client: FTPClient,
        remoteBase: String,
        deleteAfter: Boolean,
        result: Result
    ) {
        val name = docFile.name ?: return

        if (!client.changeWorkingDirectory(remoteBase)) {
            client.makeDirectory(remoteBase)
            client.changeWorkingDirectory(remoteBase)
        }

        val existing = client.listNames(name)
        if (existing != null && existing.isNotEmpty()) {
            result.skipped++
            if (deleteAfter) docFile.delete()
            return
        }

        val inputStream: InputStream? = context.contentResolver.openInputStream(docFile.uri)
        if (inputStream == null) {
            result.failed++
            return
        }

        try {
            if (client.storeFile(name, inputStream)) {
                result.uploaded++
                if (deleteAfter) docFile.delete()
            } else {
                result.failed++
            }
        } catch (e: Exception) {
            Log.e(tag, "Upload exception: $name", e)
            result.failed++
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
        }
    }
}
