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
    var mode: String, // two_way | upload_delete | download_delete
    var enabled: Boolean
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
        o.put("remoteDir", remoteDir)
        o.put("passive", passive)
        o.put("mode", mode)
        o.put("enabled", enabled)
        return o
    }

    companion object {
        const val MODE_TWO_WAY = "two_way"
        const val MODE_UPLOAD_DELETE = "upload_delete"
        const val MODE_DOWNLOAD_DELETE = "download_delete"

        fun fromJson(o: JSONObject): SyncPair {
            return SyncPair(
                id = o.optString("id"),
                name = o.optString("name", "Pair"),
                treeUri = o.optString("treeUri"),
                host = o.optString("host"),
                port = o.optInt("port", 21),
                username = o.optString("username"),
                password = o.optString("password"),
                remoteDir = o.optString("remoteDir", "/mirror"),
                passive = o.optBoolean("passive", true),
                mode = o.optString("mode", MODE_TWO_WAY),
                enabled = o.optBoolean("enabled", true)
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
