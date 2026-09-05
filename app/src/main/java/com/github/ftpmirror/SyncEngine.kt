package com.github.ftpmirror

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

class SyncEngine(private val context: Context) {

    private val tag = "StoneSyncEngine"

    data class Result(
        var uploaded: Int = 0,
        var downloaded: Int = 0,
        var deleted: Int = 0,
        var skipped: Int = 0,
        var failed: Int = 0,
        var conflicts: Int = 0
    )

    fun syncAllEnabled(): Result {
        val store = PairStore(context)
        val total = Result()
        store.raw().edit().putBoolean("is_running", true).putBoolean("cancel_requested", false).apply()
        try {
            for (pair in store.list()) {
                if (!pair.enabled || cancelled(store)) continue
                val r = syncPair(pair, store)
                total.uploaded += r.uploaded
                total.downloaded += r.downloaded
                total.deleted += r.deleted
                total.skipped += r.skipped
                total.failed += r.failed
                total.conflicts += r.conflicts
            }
        } finally {
            val ts = System.currentTimeMillis()
            val line = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts)) +
                " — ↑${total.uploaded} ↓${total.downloaded} !${total.conflicts} ✗${total.failed}"
            val prev = store.raw().getString("run_history", "") ?: ""
            val lines = (listOf(line) + prev.lines().filter { it.isNotBlank() }).take(20)
            store.raw().edit()
                .putBoolean("is_running", false)
                .putLong("last_run_ts", ts)
                .putInt("last_uploaded", total.uploaded)
                .putInt("last_downloaded", total.downloaded)
                .putInt("last_failed", total.failed)
                .putInt("last_skipped", total.skipped)
                .putInt("last_conflicts", total.conflicts)
                .putString("run_history", lines.joinToString("\n"))
                .apply()
        }
        return total
    }

    fun syncPair(pair: SyncPair): Result = syncPair(pair, PairStore(context))

    private fun syncPair(pair: SyncPair, store: PairStore): Result {
        val result = Result()
        if (pair.treeUri.isBlank()) {
            result.failed++
            return result
        }
        val root = DocumentFile.fromTreeUri(context, Uri.parse(pair.treeUri))
        if (root == null || !root.exists() || !root.isDirectory) {
            result.failed++
            return result
        }

        val session = try {
            RemoteSessionFactory.connect(pair)
        } catch (e: Exception) {
            Log.e(tag, "Connect failed for ${pair.name}", e)
            result.failed++
            return result
        }

        session.use {
            try {
                val remoteRoot = RemotePaths.normalise(pair.remoteDir)
                session.ensureDir(remoteRoot)
                when (pair.mode) {
                    SyncPair.MODE_UPLOAD_DELETE -> uploadTree(root, session, remoteRoot, true, result, store)
                    SyncPair.MODE_DOWNLOAD_DELETE -> downloadTree(root, session, remoteRoot, true, result, store)
                    else -> syncTwoWay(root, session, remoteRoot, pair, result, store)
                }
            } catch (e: Exception) {
                Log.e(tag, "Sync failed for ${pair.name}", e)
                result.failed++
            }
        }
        return result
    }

    private fun syncTwoWay(
        localDir: DocumentFile,
        remote: RemoteSession,
        remoteDir: String,
        pair: SyncPair,
        result: Result,
        store: PairStore
    ) {
        if (cancelled(store)) return
        remote.ensureDir(remoteDir)

        val localByName = localDir.listFiles().mapNotNull { file -> file.name?.let { it to file } }.toMap()
        val remoteByName = remote.list(remoteDir).associateBy { it.name }
        val names = (localByName.keys + remoteByName.keys).toSortedSet()

        for (name in names) {
            if (cancelled(store)) return
            val local = localByName[name]
            val remoteEntry = remoteByName[name]
            when {
                local != null && remoteEntry == null -> {
                    if (local.isDirectory) {
                        val childRemote = RemotePaths.join(remoteDir, name)
                        remote.ensureDir(childRemote)
                        uploadTree(local, remote, childRemote, false, result, store)
                    } else {
                        uploadFile(local, remote, RemotePaths.join(remoteDir, name), false, result)
                    }
                }

                local == null && remoteEntry != null -> {
                    if (remoteEntry.isDirectory) {
                        val child = localDir.findFile(name)?.takeIf { it.isDirectory }
                            ?: localDir.createDirectory(name)
                        if (child == null) result.failed++
                        else downloadTree(child, remote, remoteEntry.path, false, result, store)
                    } else {
                        downloadFile(localDir, null, name, remote, remoteEntry, false, result)
                    }
                }

                local != null && remoteEntry != null -> {
                    if (local.isDirectory && remoteEntry.isDirectory) {
                        syncTwoWay(local, remote, remoteEntry.path, pair, result, store)
                    } else if (local.isFile && !remoteEntry.isDirectory) {
                        reconcileFile(localDir, local, remote, remoteEntry, pair, result)
                    } else {
                        // File/directory type changes are ambiguous and must not be destructive by default.
                        result.conflicts++
                    }
                }
            }
        }
    }

    private fun reconcileFile(
        parent: DocumentFile,
        local: DocumentFile,
        remote: RemoteSession,
        remoteEntry: RemoteEntry,
        pair: SyncPair,
        result: Result
    ) {
        val decision = SyncConflictResolver.decide(
            localSize = local.length(),
            localModifiedMillis = local.lastModified().takeIf { it > 0L },
            remoteSize = remoteEntry.size,
            remoteModifiedMillis = remoteEntry.modifiedMillis,
            policy = ConflictPolicy.fromStored(pair.conflictPolicy)
        )

        when (decision) {
            FileDecision.SKIP -> result.skipped++
            FileDecision.UPLOAD -> uploadFile(local, remote, remoteEntry.path, false, result)
            FileDecision.DOWNLOAD ->
                downloadFile(parent, local, remoteEntry.name, remote, remoteEntry, false, result)
            FileDecision.CONFLICT -> result.conflicts++
        }
    }

    private fun uploadTree(
        localDir: DocumentFile,
        remote: RemoteSession,
        remoteDir: String,
        deleteAfter: Boolean,
        result: Result,
        store: PairStore
    ) {
        if (cancelled(store)) return
        remote.ensureDir(remoteDir)
        val remoteByName = remote.list(remoteDir).associateBy { it.name }
        for (child in localDir.listFiles()) {
            if (cancelled(store)) return
            val name = child.name ?: continue
            val path = RemotePaths.join(remoteDir, name)
            if (child.isDirectory) {
                remote.ensureDir(path)
                uploadTree(child, remote, path, deleteAfter, result, store)
            } else if (child.isFile) {
                val existing = remoteByName[name]
                if (existing != null && !existing.isDirectory && existing.size == child.length()) {
                    // A same-size destination is not proof that this source was transferred by us.
                    // In delete-after mode, never delete the source unless an upload actually succeeds.
                    result.skipped++
                } else {
                    uploadFile(child, remote, path, deleteAfter, result)
                }
            }
        }
    }

    private fun downloadTree(
        localDir: DocumentFile,
        remote: RemoteSession,
        remoteDir: String,
        deleteAfter: Boolean,
        result: Result,
        store: PairStore
    ) {
        if (cancelled(store)) return
        val remoteEntries = remote.list(remoteDir)
        val localByName = localDir.listFiles().mapNotNull { file -> file.name?.let { it to file } }.toMap()
        for (entry in remoteEntries) {
            if (cancelled(store)) return
            if (entry.isDirectory) {
                val child = localByName[entry.name]?.takeIf { it.isDirectory }
                    ?: localDir.createDirectory(entry.name)
                if (child == null) result.failed++
                else downloadTree(child, remote, entry.path, deleteAfter, result, store)
            } else {
                val existing = localByName[entry.name]?.takeIf { it.isFile }
                if (existing != null && existing.length() == entry.size) {
                    // A same-size local file is not proof of a completed download. Never remove
                    // the remote source unless this run successfully downloaded it first.
                    result.skipped++
                } else {
                    downloadFile(localDir, existing, entry.name, remote, entry, deleteAfter, result)
                }
            }
        }
    }

    private fun uploadFile(
        local: DocumentFile,
        remote: RemoteSession,
        remotePath: String,
        deleteAfter: Boolean,
        result: Result
    ) {
        val input: InputStream = context.contentResolver.openInputStream(local.uri) ?: run {
            result.failed++
            return
        }
        try {
            input.use { remote.upload(remotePath, it) }
            result.uploaded++
            if (deleteAfter) {
                if (local.delete()) result.deleted++
                else result.failed++
            }
        } catch (e: Exception) {
            Log.e(tag, "Upload failed $remotePath", e)
            result.failed++
        }
    }

    private fun downloadFile(
        parent: DocumentFile,
        existing: DocumentFile?,
        name: String,
        remote: RemoteSession,
        remoteEntry: RemoteEntry,
        deleteAfter: Boolean,
        result: Result
    ) {
        val target = existing ?: parent.createFile("application/octet-stream", name)
        if (target == null) {
            result.failed++
            return
        }
        val output: OutputStream = context.contentResolver.openOutputStream(target.uri, "wt") ?: run {
            if (existing == null) target.delete()
            result.failed++
            return
        }
        try {
            output.use { remote.download(remoteEntry.path, it) }
            result.downloaded++
            if (deleteAfter) {
                remote.delete(remoteEntry.path)
                result.deleted++
            }
        } catch (e: Exception) {
            Log.e(tag, "Download failed ${remoteEntry.path}", e)
            if (existing == null) target.delete()
            result.failed++
        }
    }

    private fun cancelled(store: PairStore): Boolean =
        store.raw().getBoolean("cancel_requested", false)
}
