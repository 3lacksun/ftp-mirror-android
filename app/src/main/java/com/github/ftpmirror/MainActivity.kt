package com.github.ftpmirror

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvLastRun: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvCurrentFolder: TextView
    private lateinit var etHost: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var etUser: TextInputEditText
    private lateinit var etPass: TextInputEditText
    private lateinit var etRemoteDir: TextInputEditText
    private lateinit var switchDelete: SwitchMaterial
    private lateinit var spinnerInterval: Spinner
    private lateinit var btnSelectFolder: MaterialButton
    private lateinit var btnTest: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnRunNow: MaterialButton

    // minutes for each spinner position
    private val intervalMinutes = listOf(15L, 30L, 60L, 120L, 360L, 720L, 1440L)

    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs().edit().putString("tree_uri", it.toString()).apply()
            tvCurrentFolder.text = it.lastPathSegment ?: it.toString()
            Toast.makeText(this, "Folder selected", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvLastRun = findViewById(R.id.tvLastRun)
        tvStats = findViewById(R.id.tvStats)
        tvCurrentFolder = findViewById(R.id.tvCurrentFolder)
        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etRemoteDir = findViewById(R.id.etRemoteDir)
        switchDelete = findViewById(R.id.switchDelete)
        spinnerInterval = findViewById(R.id.spinnerInterval)
        btnSelectFolder = findViewById(R.id.btnSelectFolder)
        btnTest = findViewById(R.id.btnTest)
        btnSave = findViewById(R.id.btnSave)
        btnRunNow = findViewById(R.id.btnRunNow)

        val adapter = ArrayAdapter.createFromResource(
            this, R.array.interval_labels, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerInterval.adapter = adapter

        loadConfig()
        refreshStatus()

        btnSelectFolder.setOnClickListener { directoryPicker.launch(null) }

        btnTest.setOnClickListener { testConnection() }

        btnSave.setOnClickListener {
            saveConfig()
            schedulePeriodicWork()
            Toast.makeText(this, "Saved and scheduled", Toast.LENGTH_LONG).show()
        }

        btnRunNow.setOnClickListener {
            if (prefs().getString("tree_uri", null) == null) {
                Toast.makeText(this, "Select a folder first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            MirrorWorker.enqueueOneTime(this)
            tvStatus.text = getString(R.string.status_running)
            Toast.makeText(this, "Mirror started", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun prefs() = try {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            this,
            "ftp_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for rare devices where crypto fails
        getSharedPreferences("ftp_prefs", MODE_PRIVATE)
    }

    private fun loadConfig() {
        val p = prefs()
        etHost.setText(p.getString("host", ""))
        etPort.setText(p.getInt("port", 21).toString())
        etUser.setText(p.getString("username", ""))
        etPass.setText(p.getString("password", ""))
        etRemoteDir.setText(p.getString("remote_dir", "/mirror"))
        switchDelete.isChecked = p.getBoolean("delete_after", true)

        val idx = intervalMinutes.indexOf(p.getLong("interval_min", 15L)).coerceAtLeast(0)
        spinnerInterval.setSelection(idx)

        val uri = p.getString("tree_uri", null)
        tvCurrentFolder.text = if (uri != null) {
            Uri.parse(uri).lastPathSegment ?: "Custom folder"
        } else {
            "No folder selected"
        }
    }

    private fun saveConfig() {
        val minutes = intervalMinutes.getOrElse(spinnerInterval.selectedItemPosition) { 15L }
        prefs().edit()
            .putString("host", etHost.text.toString().trim())
            .putInt("port", etPort.text.toString().toIntOrNull() ?: 21)
            .putString("username", etUser.text.toString().trim())
            .putString("password", etPass.text.toString())
            .putString("remote_dir", etRemoteDir.text.toString().trim().ifEmpty { "/mirror" })
            .putBoolean("delete_after", switchDelete.isChecked)
            .putLong("interval_min", minutes)
            .apply()
    }

    private fun schedulePeriodicWork() {
        val minutes = intervalMinutes.getOrElse(spinnerInterval.selectedItemPosition) { 15L }
        // WorkManager minimum periodic is 15 minutes
        val interval = minutes.coerceAtLeast(15L)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<MirrorWorker>(interval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("ftp_mirror", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun refreshStatus() {
        val p = prefs()
        val lastTs = p.getLong("last_run_ts", 0L)
        val uploaded = p.getInt("last_uploaded", 0)
        val failed = p.getInt("last_failed", 0)
        val skipped = p.getInt("last_skipped", 0)
        val running = p.getBoolean("is_running", false)

        tvStatus.text = if (running) getString(R.string.status_running) else getString(R.string.status_idle)

        tvLastRun.text = if (lastTs > 0) {
            val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            "Last run: ${fmt.format(Date(lastTs))}"
        } else {
            "Last run: Never"
        }

        tvStats.text = "Uploaded: $uploaded  ·  Failed: $failed  ·  Skipped: $skipped"
    }

    private fun testConnection() {
        val host = etHost.text.toString().trim()
        val port = etPort.text.toString().toIntOrNull() ?: 21
        val user = etUser.text.toString().trim()
        val pass = etPass.text.toString()

        if (host.isEmpty()) {
            Toast.makeText(this, "Enter host first", Toast.LENGTH_SHORT).show()
            return
        }

        btnTest.isEnabled = false
        btnTest.text = "Testing…"

        CoroutineScope(Dispatchers.IO).launch {
            var message: String
            try {
                val client = FTPClient()
                client.connectTimeout = 10000
                client.defaultTimeout = 10000
                client.connect(host, port)
                val ok = client.login(user, pass)
                if (ok) {
                    client.enterLocalPassiveMode()
                    message = "Connection successful"
                    client.logout()
                } else {
                    message = "Login failed"
                }
                if (client.isConnected) client.disconnect()
            } catch (e: Exception) {
                message = "Failed: ${e.message?.take(60) ?: "error"}"
            }

            withContext(Dispatchers.Main) {
                btnTest.isEnabled = true
                btnTest.text = getString(R.string.test_connection)
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
