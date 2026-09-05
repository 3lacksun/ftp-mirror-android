package com.github.ftpmirror

import org.json.JSONObject

data class SyncPair(
    val id: String,
    var name: String,
    var treeUri: String,
    var host: String,
    var port: Int,
    var username: String,
    var password: String,
    var remoteDir: String,
    var passive: Boolean,
    var mode: String,
    var enabled: Boolean,
    var protocol: String = RemoteProtocol.FTP.storedValue,
    var hostKeySha256: String = "",
    var conflictPolicy: String = ConflictPolicy.NEWEST_WINS.storedValue,
    var deletePropagation: Boolean = false
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("treeUri", treeUri)
        o.put("host", host)
        o.put("port", port)
        o.put("username", username)
        o.put("password", password)
        o.put("remoteDir", RemotePaths.normalise(remoteDir))
        o.put("passive", passive)
        o.put("mode", mode)
        o.put("enabled", enabled)
        o.put("protocol", protocol)
        o.put("hostKeySha256", hostKeySha256.trim())
        o.put("conflictPolicy", conflictPolicy)
        o.put("deletePropagation", deletePropagation)
        return o
    }

    companion object {
        const val MODE_TWO_WAY = "two_way"
        const val MODE_UPLOAD_DELETE = "upload_delete"
        const val MODE_DOWNLOAD_DELETE = "download_delete"

        fun fromJson(o: JSONObject): SyncPair {
            val protocol = RemoteProtocol.fromStored(o.optString("protocol", RemoteProtocol.FTP.storedValue))
            return SyncPair(
                id = o.optString("id"),
                name = o.optString("name", "Pair"),
                treeUri = o.optString("treeUri"),
                host = o.optString("host"),
                port = o.optInt("port", protocol.defaultPort),
                username = o.optString("username"),
                password = o.optString("password"),
                remoteDir = o.optString("remoteDir", "/mirror"),
                passive = o.optBoolean("passive", true),
                mode = o.optString("mode", MODE_TWO_WAY),
                enabled = o.optBoolean("enabled", true),
                protocol = protocol.storedValue,
                hostKeySha256 = o.optString("hostKeySha256"),
                conflictPolicy = ConflictPolicy.fromStored(
                    o.optString("conflictPolicy", ConflictPolicy.NEWEST_WINS.storedValue)
                ).storedValue,
                deletePropagation = o.optBoolean("deletePropagation", false)
            )
        }

        fun modeLabel(mode: String): String {
            return when (mode) {
                MODE_UPLOAD_DELETE -> "Upload & delete local"
                MODE_DOWNLOAD_DELETE -> "Download & delete remote"
                else -> "Two-way sync"
            }
        }
    }
}
