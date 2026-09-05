package com.github.ftpmirror

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteExplorerActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var tvPath: TextView
    private val stack = ArrayDeque<String>()
    private var session: RemoteSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explorer)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = "Remote endpoint"
            setNavigationOnClickListener { finish() }
        }
        container = findViewById(R.id.listContainer)
        tvPath = findViewById(R.id.tvPath)

        val start = intent.getStringExtra("remote_dir") ?: "/"
        stack.addLast(RemotePaths.normalise(start))
        connectAndLoad()
    }

    override fun onDestroy() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.size > 1) {
            stack.removeLast()
            loadCurrent()
        } else {
            super.onBackPressed()
        }
    }

    private fun connectAndLoad() {
        tvPath.text = "Connecting directly…"
        val protocol = RemoteProtocol.fromStored(intent.getStringExtra("protocol"))
        val pair = SyncPair(
            id = "explorer",
            name = "Remote explorer",
            treeUri = "",
            host = intent.getStringExtra("host").orEmpty(),
            port = intent.getIntExtra("port", protocol.defaultPort),
            username = intent.getStringExtra("username").orEmpty(),
            password = intent.getStringExtra("password").orEmpty(),
            remoteDir = stack.last(),
            passive = intent.getBooleanExtra("passive", true),
            mode = SyncPair.MODE_TWO_WAY,
            enabled = true,
            protocol = protocol.storedValue,
            hostKeySha256 = intent.getStringExtra("host_key_sha256").orEmpty()
        )

        CoroutineScope(Dispatchers.IO).launch {
            val opened = runCatching { RemoteSessionFactory.connect(pair) }
            withContext(Dispatchers.Main) {
                opened.onSuccess {
                    session = it
                    findViewById<MaterialToolbar>(R.id.toolbar).subtitle = protocol.label
                    loadCurrent()
                }.onFailure {
                    Toast.makeText(
                        this@RemoteExplorerActivity,
                        it.message ?: "Unable to connect",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    private fun loadCurrent() {
        val path = stack.last()
        tvPath.text = path
        val remote = session ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                remote.list(path)
                    .sortedWith(compareByDescending<RemoteEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
            }
            withContext(Dispatchers.Main) {
                container.removeAllViews()
                result.onFailure {
                    Toast.makeText(
                        this@RemoteExplorerActivity,
                        it.message ?: "Unable to list remote folder",
                        Toast.LENGTH_LONG
                    ).show()
                }
                val files = result.getOrDefault(emptyList())
                if (files.isEmpty()) {
                    val text = TextView(this@RemoteExplorerActivity)
                    text.text = if (result.isFailure) "Folder unavailable" else "Empty folder"
                    text.setTextColor(getColor(R.color.md_theme_on_surface_variant))
                    container.addView(text)
                    return@withContext
                }
                for (entry in files) {
                    val label = if (entry.isDirectory) {
                        "📁  ${entry.name}"
                    } else {
                        "📄  ${entry.name}  (${entry.size} B)"
                    }
                    container.addView(row(label) {
                        if (entry.isDirectory) {
                            stack.addLast(entry.path)
                            loadCurrent()
                        }
                    })
                }
            }
        }
    }

    private fun row(label: String, onClick: () -> Unit): MaterialCardView {
        val card = MaterialCardView(this)
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = (8 * resources.displayMetrics.density).toInt()
        card.layoutParams = lp
        card.radius = 12 * resources.displayMetrics.density
        card.cardElevation = 0f
        card.setCardBackgroundColor(getColor(R.color.md_theme_card))
        val tv = TextView(this)
        val pad = (14 * resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad, pad, pad)
        tv.text = label
        tv.textSize = 15f
        tv.setTextColor(getColor(R.color.md_theme_on_surface))
        card.addView(tv)
        card.setOnClickListener { onClick() }
        return card
    }
}
