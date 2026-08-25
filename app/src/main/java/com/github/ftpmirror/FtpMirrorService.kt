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

class FtpMirrorService(private val context: Context) {

    private val tag = "FtpMirror"

    data class Result(var uploaded: Int = 0, var failed: Int = 0, var skipped: Int = 0)

    fun performMirror(): Result {
        val result = Result()
        val prefs = securePrefs()

        prefs.edit().putBoolean("is_running", true).apply()

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

            Log.i(tag, "Starting mirror → $remoteBase")

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
                syncDocumentFile(rootDoc, client, remoteBase, deleteAfter, result)

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
            prefs.edit()
                .putBoolean("is_running", false)
                .putLong("last_run_ts", System.currentTimeMillis())
                .putInt("last_uploaded", result.uploaded)
                .putInt("last_failed", result.failed)
                .putInt("last_skipped", result.skipped)
                .apply()
        }

        return result
    }

    private fun securePrefs() = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ftp_secure_prefs",
            masterKey,
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
        result: Result
    ) {
        val files = doc.listFiles() ?: return
        for (file in files) {
            val name = file.name ?: continue
            val remotePath = if (remoteBase.endsWith("/")) "$remoteBase$name" else "$remoteBase/$name"

            if (file.isDirectory) {
                client.makeDirectory(remotePath)
                syncDocumentFile(file, client, remotePath, deleteAfter, result)
            } else if (file.isFile) {
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
            Log.d(tag, "Skip existing: $name")
            result.skipped++
            if (deleteAfter) docFile.delete()
            return
        }

        val inputStream: InputStream? = context.contentResolver.openInputStream(docFile.uri)
        if (inputStream == null) {
            Log.e(tag, "Cannot open: $name")
            result.failed++
            return
        }

        try {
            val success = client.storeFile(name, inputStream)
            if (success) {
                Log.i(tag, "Uploaded: $name")
                result.uploaded++
                if (deleteAfter) docFile.delete()
            } else {
                Log.e(tag, "Upload failed: $name — ${client.replyString}")
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
