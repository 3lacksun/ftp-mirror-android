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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairEditActivity : AppCompatActivity() {

    private lateinit var store: PairStore
    private lateinit var pair: SyncPair
    private lateinit var etName: TextInputEditText
    private lateinit var etHost: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var etUser: TextInputEditText
    private lateinit var etPass: TextInputEditText
    private lateinit var etRemote: TextInputEditText
    private lateinit var tvFolder: TextView
    private lateinit var switchPassive: SwitchMaterial
    private lateinit var switchEnabled: SwitchMaterial
    private lateinit var spinnerMode: Spinner

    private val modes = listOf(
        SyncPair.MODE_TWO_WAY,
        SyncPair.MODE_UPLOAD_DELETE,
        SyncPair.MODE_DOWNLOAD_DELETE
    )

    private val directoryPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                pair.treeUri = it.toString()
                tvFolder.text = it.lastPathSegment ?: it.toString()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pair_edit)
        store = PairStore(this)

        val id = intent.getStringExtra("pair_id") ?: return finish()
        pair = store.get(id) ?: return finish()

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = "Edit pair"
            setNavigationOnClickListener { finish() }
        }

        etName = findViewById(R.id.etName)
        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etRemote = findViewById(R.id.etRemoteDir)
        tvFolder = findViewById(R.id.tvCurrentFolder)
        switchPassive = findViewById(R.id.switchPassive)
        switchEnabled = findViewById(R.id.switchEnabled)
        spinnerMode = findViewById(R.id.spinnerMode)

        val labels = listOf("Two-way sync", "Upload & delete local", "Download & delete remote")
        spinnerMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)

        etName.setText(pair.name)
        etHost.setText(pair.host)
        etPort.setText(pair.port.toString())
        etUser.setText(pair.username)
        etPass.setText(pair.password)
        etRemote.setText(pair.remoteDir)
        switchPassive.isChecked = pair.passive
        switchEnabled.isChecked = pair.enabled
        spinnerMode.setSelection(modes.indexOf(pair.mode).coerceAtLeast(0))
        tvFolder.text = if (pair.treeUri.isBlank()) "No folder selected"
        else Uri.parse(pair.treeUri).lastPathSegment ?: pair.treeUri

        findViewById<MaterialButton>(R.id.btnSelectFolder).setOnClickListener {
            directoryPicker.launch(null)
        }
        findViewById<MaterialButton>(R.id.btnBrowseLocal).setOnClickListener {
            persistFromForm()
            if (pair.treeUri.isBlank()) {
                Toast.makeText(this, "Choose a local folder first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, LocalExplorerActivity::class.java)
            i.putExtra("tree_uri", pair.treeUri)
            startActivity(i)
        }
        findViewById<MaterialButton>(R.id.btnBrowseRemote).setOnClickListener {
            persistFromForm()
            if (pair.host.isBlank()) {
                Toast.makeText(this, "Enter host first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val i = Intent(this, RemoteExplorerActivity::class.java)
            i.putExtra("host", pair.host)
            i.putExtra("port", pair.port)
            i.putExtra("username", pair.username)
            i.putExtra("password", pair.password)
            i.putExtra("remote_dir", pair.remoteDir)
            i.putExtra("passive", pair.passive)
            startActivity(i)
        }
        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener { test() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            persistFromForm()
            store.upsert(pair)
            Toast.makeText(this, "Pair saved", Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener {
            store.delete(pair.id)
            Toast.makeText(this, "Pair deleted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun persistFromForm() {
        pair.name = etName.text?.toString()?.trim().orEmpty().ifEmpty { "Pair" }
        pair.host = etHost.text?.toString()?.trim().orEmpty()
        pair.port = etPort.text?.toString()?.toIntOrNull() ?: 21
        pair.username = etUser.text?.toString()?.trim().orEmpty()
        pair.password = etPass.text?.toString().orEmpty()
        pair.remoteDir = etRemote.text?.toString()?.trim().orEmpty().ifEmpty { "/mirror" }
        pair.passive = switchPassive.isChecked
        pair.enabled = switchEnabled.isChecked
        pair.mode = modes.getOrElse(spinnerMode.selectedItemPosition) { SyncPair.MODE_TWO_WAY }
    }

    private fun test() {
        persistFromForm()
        val host = pair.host
        if (host.isEmpty()) {
            Toast.makeText(this, "Enter host first", Toast.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val r = FtpHelper.connect(host, pair.port, pair.username, pair.password, pair.passive)
            FtpHelper.disconnectQuietly(r.client)
            withContext(Dispatchers.Main) {
                val msg = if (r.success) "✓ ${r.message}" else "✗ ${r.message}"
                Toast.makeText(this@PairEditActivity, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
