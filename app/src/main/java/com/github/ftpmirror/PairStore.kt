package com.github.ftpmirror

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.util.UUID

class PairStore(private val context: Context) {

    private val prefs: SharedPreferences = createSecurePreferences(context).also {
        migrateLegacyPlaintextIfPresent(context, it)
    }

    fun raw(): SharedPreferences = prefs

    fun list(): MutableList<SyncPair> {
        val raw = prefs.getString("pairs_json", "[]") ?: "[]"
        val arr = JSONArray(raw)
        val out = mutableListOf<SyncPair>()
        for (i in 0 until arr.length()) {
            out.add(SyncPair.fromJson(arr.getJSONObject(i)))
        }
        return out
    }

    fun saveAll(pairs: List<SyncPair>) {
        val arr = JSONArray()
        for (p in pairs) arr.put(p.toJson())
        requireCommit(prefs.edit().putString("pairs_json", arr.toString()), "profiles")
    }

    fun get(id: String): SyncPair? = list().firstOrNull { it.id == id }

    fun upsert(pair: SyncPair) {
        val pairs = list()
        val idx = pairs.indexOfFirst { it.id == pair.id }
        if (idx >= 0) pairs[idx] = pair else pairs.add(pair)
        saveAll(pairs)
    }

    fun delete(id: String) {
        saveAll(list().filter { it.id != id })
    }

    fun newPair(): SyncPair {
        return SyncPair(
            id = UUID.randomUUID().toString(),
            name = "New pair",
            treeUri = "",
            host = "",
            port = RemoteProtocol.FTP.defaultPort,
            username = "",
            password = "",
            remoteDir = "/mirror",
            passive = true,
            mode = SyncPair.MODE_TWO_WAY,
            enabled = true,
            protocol = RemoteProtocol.FTP.storedValue,
            hostKeySha256 = "",
            conflictPolicy = ConflictPolicy.NEWEST_WINS.storedValue,
            deletePropagation = false
        )
    }

    fun intervalMin(): Long = prefs.getLong("interval_min", 15L)

    fun setIntervalMin(v: Long) {
        requireCommit(prefs.edit().putLong("interval_min", v), "schedule")
    }

    private fun requireCommit(editor: SharedPreferences.Editor, label: String) {
        check(editor.commit()) { "Unable to persist STONE//SYNC $label" }
    }

    companion object {
        private fun createSecurePreferences(context: Context): SharedPreferences {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    "ftp_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Android Keystore-backed credential storage is unavailable; refusing plaintext fallback",
                    e
                )
            }
        }

        private fun migrateLegacyPlaintextIfPresent(context: Context, secure: SharedPreferences) {
            val legacy = context.getSharedPreferences("ftp_prefs", Context.MODE_PRIVATE)
            if (secure.contains("pairs_json") || !legacy.contains("pairs_json")) return

            val rawPairs = legacy.getString("pairs_json", null) ?: return
            val interval = legacy.getLong("interval_min", 15L)
            val migrated = secure.edit()
                .putString("pairs_json", rawPairs)
                .putLong("interval_min", interval)
                .commit()
            if (!migrated) {
                throw IllegalStateException("Unable to migrate legacy credentials into encrypted storage")
            }
            legacy.edit().clear().commit()
        }
    }
}
