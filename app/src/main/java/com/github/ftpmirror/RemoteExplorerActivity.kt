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
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile

class RemoteExplorerActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var tvPath: TextView
    private val stack = ArrayDeque<String>()
    private var client: FTPClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explorer)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = "Remote folder"
            setNavigationOnClickListener { finish() }
        }
        container = findViewById(R.id.listContainer)
        tvPath = findViewById(R.id.tvPath)

        val start = intent.getStringExtra("remote_dir") ?: "/"
        stack.addLast(start)
        connectAndLoad()
    }

    override fun onDestroy() {
        FtpHelper.disconnectQuietly(client)
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
        tvPath.text = "Connecting…"
        val host = intent.getStringExtra("host") ?: return finish()
        val port = intent.getIntExtra("port", 21)
        val user = intent.getStringExtra("username") ?: ""
        val pass = intent.getStringExtra("password") ?: ""
        val passive = intent.getBooleanExtra("passive", true)
        CoroutineScope(Dispatchers.IO).launch {
            val r = FtpHelper.connect(host, port, user, pass, passive)
            withContext(Dispatchers.Main) {
                if (!r.success || r.client == null) {
                    Toast.makeText(this@RemoteExplorerActivity, r.message, Toast.LENGTH_LONG).show()
                    finish()
                    return@withContext
                }
                client = r.client
                loadCurrent()
            }
        }
    }

    private fun loadCurrent() {
        val path = stack.last()
        tvPath.text = path
        val c = client ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val files = try {
                FtpHelper.list(c, path).toList()
                    .filter { it.name != "." && it.name != ".." }
                    .sortedWith(compareByDescending<FTPFile> { it.isDirectory }.thenBy { it.name ?: "" })
            } catch (e: Exception) {
                emptyList()
            }
            withContext(Dispatchers.Main) {
                container.removeAllViews()
                if (files.isEmpty()) {
                    val t = TextView(this@RemoteExplorerActivity)
                    t.text = "Empty or unreadable folder"
                    t.setTextColor(getColor(R.color.md_theme_on_surface_variant))
                    container.addView(t)
                    return@withContext
                }
                for (f in files) {
                    val label = if (f.isDirectory) "📁  ${f.name}" else "📄  ${f.name}  (${f.size} B)"
                    container.addView(row(label) {
                        if (f.isDirectory) {
                            val next = if (path.endsWith("/")) "$path${f.name}" else "$path/${f.name}"
                            stack.addLast(next)
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
