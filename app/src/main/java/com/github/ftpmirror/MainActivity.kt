package com.github.ftpmirror

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var store: PairStore
    private lateinit var tvStatus: TextView
    private lateinit var tvLastRun: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvHistory: TextView
    private lateinit var pairContainer: LinearLayout
    private lateinit var spinnerInterval: Spinner
    private val intervalMinutes = listOf(15L, 30L, 60L, 120L, 360L, 720L, 1440L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = PairStore(this)

        findViewById<MaterialToolbar>(R.id.toolbar).title = "FTP Mirror"
        tvStatus = findViewById(R.id.tvStatus)
        tvLastRun = findViewById(R.id.tvLastRun)
        tvStats = findViewById(R.id.tvStats)
        tvHistory = findViewById(R.id.tvHistory)
        pairContainer = findViewById(R.id.pairContainer)
        spinnerInterval = findViewById(R.id.spinnerInterval)

        val adapter = ArrayAdapter.createFromResource(
            this, R.array.interval_labels, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerInterval.adapter = adapter
        val idx = intervalMinutes.indexOf(store.intervalMin()).coerceAtLeast(0)
        spinnerInterval.setSelection(idx)

        findViewById<MaterialButton>(R.id.btnAddPair).setOnClickListener {
            val p = store.newPair()
            store.upsert(p)
            openPair(p.id)
        }
        findViewById<MaterialButton>(R.id.btnRunNow).setOnClickListener {
            if (store.list().none { it.enabled }) {
                Toast.makeText(this, "Add and enable a pair first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            store.raw().edit().putBoolean("cancel_requested", false).apply()
            MirrorWorker.enqueueOneTime(this)
            tvStatus.text = getString(R.string.status_running)
            Toast.makeText(this, "Sync started", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnCancel).setOnClickListener {
            store.raw().edit().putBoolean("cancel_requested", true).apply()
            MirrorWorker.cancelAll(this)
            tvStatus.text = getString(R.string.status_idle)
            Toast.makeText(this, "Cancel requested", Toast.LENGTH_SHORT).show()
        }
        findViewById<MaterialButton>(R.id.btnSaveSchedule).setOnClickListener {
            val minutes = intervalMinutes.getOrElse(spinnerInterval.selectedItemPosition) { 15L }
            store.setIntervalMin(minutes)
            MirrorWorker.schedule(this, minutes)
            Toast.makeText(this, "Scheduled every $minutes min", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        renderPairs()
        refreshStatus()
    }

    private fun openPair(id: String) {
        val i = Intent(this, PairEditActivity::class.java)
        i.putExtra("pair_id", id)
        startActivity(i)
    }

    private fun renderPairs() {
        pairContainer.removeAllViews()
        val pairs = store.list()
        if (pairs.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No folder pairs yet. Tap Add pair."
            empty.setTextColor(getColor(R.color.md_theme_on_surface_variant))
            empty.textSize = 14f
            pairContainer.addView(empty)
            return
        }
        for (p in pairs) {
            val card = MaterialCardView(this)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (12 * resources.displayMetrics.density).toInt()
            card.layoutParams = lp
            card.radius = 16 * resources.displayMetrics.density
            card.cardElevation = 0f
            card.strokeWidth = 1
            card.strokeColor = getColor(R.color.md_theme_surface_variant)
            card.setCardBackgroundColor(getColor(R.color.md_theme_card))

            val inner = LinearLayout(this)
            inner.orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            inner.setPadding(pad, pad, pad, pad)

            val title = TextView(this)
            title.text = p.name
            title.textSize = 16f
            title.setTextColor(getColor(R.color.md_theme_on_surface))
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)

            val sub = TextView(this)
            val onOff = if (p.enabled) "On" else "Off"
            sub.text = "$onOff · ${SyncPair.modeLabel(p.mode)}\n${p.host}${p.remoteDir}"
            sub.textSize = 13f
            sub.setTextColor(getColor(R.color.md_theme_on_surface_variant))
            val top = (6 * resources.displayMetrics.density).toInt()
            sub.setPadding(0, top, 0, 0)

            inner.addView(title)
            inner.addView(sub)
            card.addView(inner)
            card.setOnClickListener { openPair(p.id) }
            pairContainer.addView(card)
        }
    }

    private fun refreshStatus() {
        val p = store.raw()
        val running = p.getBoolean("is_running", false)
        tvStatus.text = if (running) getString(R.string.status_running) else getString(R.string.status_idle)
        val lastTs = p.getLong("last_run_ts", 0L)
        tvLastRun.text = if (lastTs > 0) {
            val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            "Last run: ${fmt.format(Date(lastTs))}"
        } else {
            "Last run: Never"
        }
        val up = p.getInt("last_uploaded", 0)
        val down = p.getInt("last_downloaded", 0)
        val fail = p.getInt("last_failed", 0)
        tvStats.text = "↑ $up uploaded   ↓ $down downloaded   ✗ $fail failed"
        val history = p.getString("run_history", "") ?: ""
        tvHistory.text = if (history.isBlank()) getString(R.string.no_history) else history
    }
}
