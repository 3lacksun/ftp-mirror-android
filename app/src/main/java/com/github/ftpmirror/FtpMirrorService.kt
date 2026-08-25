package com.github.ftpmirror

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream

class FtpMirrorService(private val context: Context) {

    private val tag = "FtpMirror"

    fun performMirror() {
        val prefs = context.getSharedPreferences("ftp_prefs", Context.MODE_PRIVATE)
        val treeUriString = prefs.getString("tree_uri", null)
        if (treeUriString.isNullOrEmpty()) {
            Log.e(tag, "No folder selected")
            return
        }

        val treeUri = Uri.parse(treeUriString)
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: run {
            Log.e(tag, "Invalid tree URI")
            return
        }

        val host = prefs.getString("host", null) ?: return
        val port = prefs.getInt("port", 21)
        val username = prefs.getString("username", null) ?: return
        val password = prefs.getString("password", null) ?: return
        val remoteBase = prefs.getString("remote_dir", "/mirror") ?: "/mirror"
        val deleteAfter = prefs.getBoolean("delete_after", true)

        Log.i(tag, "Starting mirror from ${rootDoc.name} to $remoteBase")

        val client = FTPClient().apply {
            connectTimeout = 30000
            defaultTimeout = 30000
        }

        try {
            client.connect(host, port)
            if (!client.login(username, password)) {
                Log.e(tag, "FTP login failed")
                return
            }
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            ensureRemoteDirectory(client, remoteBase)
            syncDocumentFile(rootDoc, client, remoteBase, deleteAfter)

            Log.i(tag, "Mirror completed successfully")
        } catch (e: Exception) {
            Log.e(tag, "Mirror failed", e)
        } finally {
            try {
                if (client.isConnected) {
                    client.logout()
                    client.disconnect()
                }
            } catch (e: Exception) {
                Log.w(tag, "Disconnect error", e)
            }
        }
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

    private fun syncDocumentFile(doc: DocumentFile, client: FTPClient, remoteBase: String, deleteAfter: Boolean) {
        val files = doc.listFiles() ?: return
        for (file in files) {
            val name = file.name ?: continue
            val remotePath = if (remoteBase.endsWith("/")) "$remoteBase$name" else "$remoteBase/$name"

            if (file.isDirectory) {
                client.makeDirectory(remotePath)
                syncDocumentFile(file, client, remotePath, deleteAfter)
            } else if (file.isFile) {
                uploadDocumentFile(file, client, remoteBase, deleteAfter)
            }
        }
    }

    private fun uploadDocumentFile(docFile: DocumentFile, client: FTPClient, remoteBase: String, deleteAfter: Boolean) {
        val name = docFile.name ?: return

        // Change to correct directory before checking/uploading
        if (!client.changeWorkingDirectory(remoteBase)) {
            Log.w(tag, "Could not CWD to $remoteBase, attempting make")
            client.makeDirectory(remoteBase)
            client.changeWorkingDirectory(remoteBase)
        }

        val existing = client.listNames(name)
        if (existing != null && existing.isNotEmpty()) {
            Log.d(tag, "File exists on remote (skipping): $name")
            if (deleteAfter) {
                docFile.delete()
            }
            return
        }

        val inputStream = context.contentResolver.openInputStream(docFile.uri)
        if (inputStream == null) {
            Log.e(tag, "Could not open input stream for $name")
            return
        }

        inputStream.use { input: InputStream ->
            val success = client.storeFile(name, input)
            if (success) {
                Log.i(tag, "Successfully uploaded: $name")
                if (deleteAfter) {
                    docFile.delete()
                }
            } else {
                Log.e(tag, "Upload failed for: $name (reply: ${client.replyString})")
            }
        }
    }
}
