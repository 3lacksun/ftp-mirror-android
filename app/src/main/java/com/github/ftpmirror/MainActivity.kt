package com.github.ftpmirror

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var etRemoteDir: EditText
    private lateinit var switchDelete: Switch
    private lateinit var btnSave: Button
    private lateinit var btnRunNow: Button
    private lateinit var btnSelectFolder: Button
    private lateinit var tvCurrentFolder: TextView

    private var selectedTreeUri: Uri? = null

    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            selectedTreeUri = it
            getSharedPreferences("ftp_prefs", MODE_PRIVATE).edit()
                .putString("tree_uri", it.toString())
                .apply()
            tvCurrentFolder.text = "Selected: ${it.lastPathSegment ?: it.toString()}"
            Toast.makeText(this, "Folder selected successfully", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etRemoteDir = findViewById(R.id.etRemoteDir)
        switchDelete = findViewById(R.id.switchDelete)
        btnSave = findViewById(R.id.btnSave)
        btnRunNow = findViewById(R.id.btnRunNow)
        btnSelectFolder = findViewById(R.id.btnSelectFolder)
        tvCurrentFolder = findViewById(R.id.tvCurrentFolder)

        loadConfig()

        btnSelectFolder.setOnClickListener { directoryPicker.launch(null) }

        btnSave.setOnClickListener {
            saveConfig()
            schedulePeriodicWork()
            Toast.makeText(this, "Settings saved and scheduled", Toast.LENGTH_LONG).show()
        }

        btnRunNow.setOnClickListener {
            if (getSharedPreferences("ftp_prefs", MODE_PRIVATE).getString("tree_uri", null) == null) {
                Toast.makeText(this, "Please select a folder first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            MirrorWorker.enqueueOneTime(this)
            Toast.makeText(this, "Manual mirror started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadConfig() {
        val prefs = getSharedPreferences("ftp_prefs", MODE_PRIVATE)
        etHost.setText(prefs.getString("host", "ftp.example.com"))
        etPort.setText(prefs.getInt("port", 21).toString())
        etUser.setText(prefs.getString("username", ""))
        etPass.setText(prefs.getString("password", ""))
        etRemoteDir.setText(prefs.getString("remote_dir", "/mirror"))
        switchDelete.isChecked = prefs.getBoolean("delete_after", true)

        val savedUri = prefs.getString("tree_uri", null)
        if (savedUri != null) {
            selectedTreeUri = Uri.parse(savedUri)
            tvCurrentFolder.text = "Selected: ${selectedTreeUri?.lastPathSegment ?: "Custom folder"}"
        } else {
            tvCurrentFolder.text = "No folder selected — tap Select Mirror Folder"
        }
    }

    private fun saveConfig() {
        val prefs = getSharedPreferences("ftp_prefs", MODE_PRIVATE).edit()
        prefs.putString("host", etHost.text.toString().trim())
        prefs.putInt("port", etPort.text.toString().toIntOrNull() ?: 21)
        prefs.putString("username", etUser.text.toString().trim())
        prefs.putString("password", etPass.text.toString())
        prefs.putString("remote_dir", etRemoteDir.text.toString().trim())
        prefs.putBoolean("delete_after", switchDelete.isChecked)
        prefs.apply()
    }

    private fun schedulePeriodicWork() {
        val request = PeriodicWorkRequestBuilder<MirrorWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("ftp_mirror", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
