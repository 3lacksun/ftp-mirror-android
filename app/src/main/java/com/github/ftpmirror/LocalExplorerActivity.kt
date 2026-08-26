package com.github.ftpmirror

import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView

class LocalExplorerActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var tvPath: TextView
    private val stack = ArrayDeque<DocumentFile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explorer)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = "Local folder"
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
        container = findViewById(R.id.listContainer)
        tvPath = findViewById(R.id.tvPath)

        val uri = intent.getStringExtra("tree_uri") ?: return finish()
        val root = DocumentFile.fromTreeUri(this, Uri.parse(uri)) ?: return finish()
        stack.addLast(root)
        render()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.size > 1) {
            stack.removeLast()
            render()
        } else {
            super.onBackPressed()
        }
    }

    private fun render() {
        val dir = stack.last()
        tvPath.text = dir.name ?: "Local"
        container.removeAllViews()
        val files = dir.listFiles()?.sortedWith(
            compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name ?: "" }
        ) ?: emptyList()
        if (files.isEmpty()) {
            val t = TextView(this)
            t.text = "Empty folder"
            t.setTextColor(getColor(R.color.md_theme_on_surface_variant))
            container.addView(t)
            return
        }
        for (f in files) {
            container.addView(row(if (f.isDirectory) "📁  ${f.name}" else "📄  ${f.name}") {
                if (f.isDirectory) {
                    stack.addLast(f)
                    render()
                }
            })
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
