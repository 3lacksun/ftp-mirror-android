package com.github.ftpmirror

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncEngine(private val context: Context) {

    private val tag = "SyncEngine"

    data class Result(
        var uploaded: Int = 0,
        var downloaded: Int = 0,
        var deleted: Int = 0,
        var skipped: Int = 0,
        var failed: Int = 0
    )

    fun syncAllEnabled(): Result {
        val store = PairStore(context)
        val total = Result()
        store.raw().edit().putBoolean("is_running", true).putBoolean("cancel_requested", false).apply()
        try {
            for (pair in store.list()) {
                if (!pair.enabled) continue
                if (store.raw().getBoolean("cancel_requested", false)) break
                val r = syncPair(pair)
                total.uploaded += r.uploaded
                total.downloaded += r.downloaded
                total.deleted += r.deleted
                total.skipped += r.skipped
                total.failed += r.failed
            }
        } finally {
            val ts = System.currentTimeMillis()
            val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
            val line = "${fmt.format(Date(ts))} — ↑${total.uploaded} ↓${total.downloaded} ✗${total.failed}"
            val prev = store.raw().getString("run_history", "") ?: ""
            val lines = (listOf(line) + prev.lines().filter { it.isNotBlank() }).take(12)
            store.raw().edit()
                .putBoolean("is_running", false)
                .putLong("last_run_ts", ts)
                .putInt("last_uploaded", total.uploaded)
                .putInt("last_downloaded", total.downloaded)
                .putInt("last_failed", total.failed)
                .putInt("last_skipped", total.skipped)
                .putString("run_history", lines.joinToString("\n"))
                .apply()
        }
        return total
    }

    fun syncPair(pair: SyncPair): Result {
        val result = Result()
        if (pair.host.isBlank() || pair.treeUri.isBlank()) {
            result.failed++
            return result
        }
        val root = DocumentFile.fromTreeUri(context, Uri.parse(pair.treeUri))
        if (root == null) {
            result.failed++
            return result
        }

        val connect = FtpHelper.connect(pair.host, pair.port, pair.username, pair.password, pair.passive)
        val client = connect.client
        if (!connect.success || client == null) {
            Log.e(tag, "Connect failed for ${pair.name}: ${connect.message}")
            result.failed++
            return result
        }

        try {
            FtpHelper.ensureDir(client, pair.remoteDir)
            when (pair.mode) {
                SyncPair.MODE_UPLOAD_DELETE -> uploadSide(root, client, pair.remoteDir, true, result)
                SyncPair.MODE_DOWNLOAD_DELETE -> downloadSide(root, client, pair.remoteDir, true, result)
                else -> {
                    uploadSide(root, client, pair.remoteDir, false, result)
                    downloadSide(root, client, pair.remoteDir, false, result)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Sync failed ${pair.name}", e)
            result.failed++
        } finally {
            FtpHelper.disconnectQuietly(client)
        }
        return result
    }

    private fun uploadSide(
        localDir: DocumentFile,
        client: FTPClient,
        remoteDir: String,
        deleteAfter: Boolean,
        result: Result
    ) {
        FtpHelper.ensureDir(client, remoteDir)
        val remoteNames = remoteFileMap(client, remoteDir)
        val children = localDir.listFiles() ?: return
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                val nextRemote = joinRemote(remoteDir, name)
                client.makeDirectory(nextRemote)
                uploadSide(child, client, nextRemote, deleteAfter, result)
            } else if (child.isFile) {
                if (remoteNames.containsKey(name)) {
                    result.skipped++
                    if (deleteAfter) child.delete()
                    continue
                }
                val input: InputStream? = context.contentResolver.openInputStream(child.uri)
                if (input == null) {
                    result.failed++
                    continue
                }
                try {
                    client.changeWorkingDirectory(remoteDir)
                    val ok = client.storeFile(name, input)
                    if (ok) {
                        result.uploaded++
                        if (deleteAfter) child.delete()
                    } else {
                        result.failed++
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Upload $name", e)
                    result.failed++
                } finally {
                    try {
                        input.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun downloadSide(
        localDir: DocumentFile,
        client: FTPClient,
        remoteDir: String,
        deleteAfter: Boolean,
        result: Result
    ) {
        val files = try {
            FtpHelper.list(client, remoteDir)
        } catch (e: Exception) {
            result.failed++
            return
        }
        val localNames = localNameSet(localDir)
        for (file in files) {
            val name = file.name ?: continue
            if (name == "." || name == "..") continue
            if (file.isDirectory) {
                var nextLocal = localDir.findFile(name)
                if (nextLocal == null || !nextLocal.isDirectory) {
                    nextLocal = localDir.createDirectory(name)
                }
                if (nextLocal != null) {
                    downloadSide(nextLocal, client, joinRemote(remoteDir, name), deleteAfter, result)
                }
            } else {
                if (localNames.contains(name)) {
                    result.skipped++
                    if (deleteAfter) {
                        client.changeWorkingDirectory(remoteDir)
                        client.deleteFile(name)
                        result.deleted++
                    }
                    continue
                }
                val created = localDir.createFile("application/octet-stream", name)
                if (created == null) {
                    result.failed++
                    continue
                }
                val out: OutputStream? = context.contentResolver.openOutputStream(created.uri)
                if (out == null) {
                    result.failed++
                    continue
                }
                try {
                    client.changeWorkingDirectory(remoteDir)
                    val ok = client.retrieveFile(name, out)
                    if (ok) {
                        result.downloaded++
                        if (deleteAfter) {
                            client.deleteFile(name)
                            result.deleted++
                        }
                    } else {
                        result.failed++
                        created.delete()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Download $name", e)
                    result.failed++
                } finally {
                    try {
                        out.close()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private fun remoteFileMap(client: FTPClient, remoteDir: String): Map<String, FTPFile> {
        val map = mutableMapOf<String, FTPFile>()
        val files = try {
            FtpHelper.list(client, remoteDir)
        } catch (_: Exception) {
            return map
        }
        for (f in files) {
            val n = f.name ?: continue
            if (n == "." || n == "..") continue
            if (f.isFile) map[n] = f
        }
        return map
    }

    private fun localNameSet(dir: DocumentFile): Set<String> {
        val set = mutableSetOf<String>()
        val files = dir.listFiles() ?: return set
        for (f in files) {
            val n = f.name ?: continue
            set.add(n)
        }
        return set
    }

    private fun joinRemote(base: String, name: String): String {
        return if (base.endsWith("/")) "$base$name" else "$base/$name"
    }
}
