package com.github.ftpmirror

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
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
import com.google.android.material.textfield.TextInputLayout
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
    private lateinit var etHostKey: TextInputEditText
    private lateinit var hostKeyLayout: TextInputLayout
    private lateinit var tvFolder: TextView
    private lateinit var switchPassive: SwitchMaterial
    private lateinit var switchEnabled: SwitchMaterial
    private lateinit var spinnerMode: Spinner
    private lateinit var spinnerProtocol: Spinner
    private lateinit var spinnerConflict: Spinner

    private val modes = listOf(
        SyncPair.MODE_TWO_WAY,
        SyncPair.MODE_UPLOAD_DELETE,
        SyncPair.MODE_DOWNLOAD_DELETE
    )
    private val protocols = RemoteProtocol.entries.toList()
    private val conflictPolicies = ConflictPolicy.entries.toList()
    private var previousProtocol: RemoteProtocol? = null

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
            title = "STONE//SYNC endpoint"
            setNavigationOnClickListener { finish() }
        }

        etName = findViewById(R.id.etName)
        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etUser = findViewById(R.id.etUser)
        etPass = findViewById(R.id.etPass)
        etRemote = findViewById(R.id.etRemoteDir)
        etHostKey = findViewById(R.id.etHostKey)
        hostKeyLayout = findViewById(R.id.hostKeyLayout)
        tvFolder = findViewById(R.id.tvCurrentFolder)
        switchPassive = findViewById(R.id.switchPassive)
        switchEnabled = findViewById(R.id.switchEnabled)
        spinnerMode = findViewById(R.id.spinnerMode)
        spinnerProtocol = findViewById(R.id.spinnerProtocol)
        spinnerConflict = findViewById(R.id.spinnerConflict)

        spinnerMode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Two-way sync", "Upload & delete local", "Download & delete remote")
        )
        spinnerProtocol.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            protocols.map { it.label }
        )
        spinnerConflict.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            conflictPolicies.map { it.label }
        )

        etName.setText(pair.name)
        etHost.setText(pair.host)
        etPort.setText(pair.port.toString())
        etUser.setText(pair.username)
        etPass.setText(pair.password)
        etRemote.setText(pair.remoteDir)
        etHostKey.setText(pair.hostKeySha256)
        switchPassive.isChecked = pair.passive
        switchEnabled.isChecked = pair.enabled
        spinnerMode.setSelection(modes.indexOf(pair.mode).coerceAtLeast(0))
        val initialProtocol = RemoteProtocol.fromStored(pair.protocol)
        spinnerProtocol.setSelection(protocols.indexOf(initialProtocol).coerceAtLeast(0))
        spinnerConflict.setSelection(
            conflictPolicies.indexOf(ConflictPolicy.fromStored(pair.conflictPolicy)).coerceAtLeast(0)
        )
        previousProtocol = initialProtocol
        tvFolder.text = if (pair.treeUri.isBlank()) "No folder selected"
        else Uri.parse(pair.treeUri).lastPathSegment ?: pair.treeUri
        updateProtocolUi(initialProtocol)

        spinnerProtocol.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = protocols.getOrElse(position) { RemoteProtocol.FTP }
                val old = previousProtocol
                val currentPort = etPort.text?.toString()?.toIntOrNull()
                if (old != null && (currentPort == null || currentPort == old.defaultPort)) {
                    etPort.setText(selected.defaultPort.toString())
                }
                previousProtocol = selected
                updateProtocolUi(selected)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

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
            val error = validateEndpoint()
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, RemoteExplorerActivity::class.java).apply {
                putExtra("host", pair.host)
                putExtra("port", pair.port)
                putExtra("username", pair.username)
                putExtra("password", pair.password)
                putExtra("remote_dir", pair.remoteDir)
                putExtra("passive", pair.passive)
                putExtra("protocol", pair.protocol)
                putExtra("host_key_sha256", pair.hostKeySha256)
            })
        }
        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener { test() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            persistFromForm()
            val error = if (pair.enabled) validateEndpoint() else validateRemotePath()
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            try {
                store.upsert(pair)
                Toast.makeText(this, "Endpoint saved securely", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Unable to save endpoint", Toast.LENGTH_LONG).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnDelete).setOnClickListener {
            store.delete(pair.id)
            Toast.makeText(this, "Endpoint deleted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun persistFromForm() {
        pair.name = etName.text?.toString()?.trim().orEmpty().ifEmpty { "Pair" }
        pair.protocol = protocols.getOrElse(spinnerProtocol.selectedItemPosition) { RemoteProtocol.FTP }.storedValue
        pair.host = etHost.text?.toString()?.trim().orEmpty()
        pair.port = etPort.text?.toString()?.toIntOrNull()
            ?: RemoteProtocol.fromStored(pair.protocol).defaultPort
        pair.username = etUser.text?.toString()?.trim().orEmpty()
        pair.password = etPass.text?.toString().orEmpty()
        pair.remoteDir = etRemote.text?.toString()?.trim().orEmpty().ifEmpty { "/mirror" }
        pair.hostKeySha256 = etHostKey.text?.toString()?.trim().orEmpty()
        pair.passive = switchPassive.isChecked
        pair.enabled = switchEnabled.isChecked
        pair.mode = modes.getOrElse(spinnerMode.selectedItemPosition) { SyncPair.MODE_TWO_WAY }
        pair.conflictPolicy = conflictPolicies.getOrElse(spinnerConflict.selectedItemPosition) {
            ConflictPolicy.NEWEST_WINS
        }.storedValue
    }

    private fun updateProtocolUi(protocol: RemoteProtocol) {
        hostKeyLayout.visibility = if (protocol == RemoteProtocol.SFTP) View.VISIBLE else View.GONE
        switchPassive.visibility = if (
            protocol == RemoteProtocol.FTP ||
            protocol == RemoteProtocol.FTPS_EXPLICIT ||
            protocol == RemoteProtocol.FTPS_IMPLICIT
        ) View.VISIBLE else View.GONE
        etHost.hint = if (protocol == RemoteProtocol.WEBDAV) {
            "Host or http(s) base URL"
        } else {
            "Host or IP address"
        }
    }

    private fun validateEndpoint(): String? = try {
        EndpointValidator.validate(pair)
        null
    } catch (e: Exception) {
        e.message ?: "Invalid endpoint configuration"
    }

    private fun validateRemotePath(): String? = try {
        RemotePaths.normalise(pair.remoteDir)
        null
    } catch (e: Exception) {
        e.message ?: "Invalid remote path"
    }

    private fun test() {
        persistFromForm()
        val error = validateEndpoint()
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                RemoteSessionFactory.connect(pair).use { }
                "Connected securely using ${RemoteProtocol.fromStored(pair.protocol).label}"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@PairEditActivity,
                    result.fold(onSuccess = { "✓ $it" }, onFailure = { "✗ ${it.message ?: "Connection failed"}" }),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
