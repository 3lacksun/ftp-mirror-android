package com.github.ftpmirror

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.util.UUID

class PairStore(context: Context) {

    private val prefs: SharedPreferences = try {
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
        prefs.edit().putString("pairs_json", arr.toString()).apply()
    }

    fun get(id: String): SyncPair? {
        return list().firstOrNull { it.id == id }
    }

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
            port = 21,
            username = "",
            password = "",
            remoteDir = "/mirror",
            passive = true,
            mode = SyncPair.MODE_TWO_WAY,
            enabled = true
        )
    }

    fun intervalMin(): Long = prefs.getLong("interval_min", 15L)

    fun setIntervalMin(v: Long) {
        prefs.edit().putLong("interval_min", v).apply()
    }
}
