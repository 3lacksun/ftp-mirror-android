package com.github.ftpmirror

import android.content.Intent
import android.os.Bundle
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

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            title = "STONE//SYNC"
            subtitle = "COVERT SYSTEMS DATA LABORATORY"
        }
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
            val pair = store.newPair()
            store.upsert(pair)
            openPair(pair.id)
        }
        findViewById<MaterialButton>(R.id.btnRunNow).setOnClickListener {
            val enabled = store.list().filter { it.enabled }
            if (enabled.isEmpty()) {
                Toast.makeText(this, "Add and enable an endpoint first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val invalid = enabled.firstNotNullOfOrNull { pair ->
                runCatching { EndpointValidator.validate(pair) }.exceptionOrNull()?.let { pair.name to it }
            }
            if (invalid != null) {
                Toast.makeText(
                    this,
                    "${invalid.first}: ${invalid.second.message ?: "invalid endpoint"}",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            store.raw().edit().putBoolean("cancel_requested", false).apply()
            MirrorWorker.enqueueOneTime(this)
            tvStatus.text = getString(R.string.status_running)
            Toast.makeText(this, "Sync queued", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Background sync every $minutes min", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        renderPairs()
        refreshStatus()
    }

    private fun openPair(id: String) {
        startActivity(Intent(this, PairEditActivity::class.java).putExtra("pair_id", id))
    }

    private fun renderPairs() {
        pairContainer.removeAllViews()
        val pairs = store.list()
        if (pairs.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No sync endpoints yet. Tap Add endpoint."
            empty.setTextColor(getColor(R.color.md_theme_on_surface_variant))
            empty.textSize = 14f
            pairContainer.addView(empty)
            return
        }
        for (pair in pairs) {
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
            title.text = pair.name
            title.textSize = 16f
            title.setTextColor(getColor(R.color.md_theme_on_surface))
            title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)

            val protocol = RemoteProtocol.fromStored(pair.protocol)
            val sub = TextView(this)
            val onOff = if (pair.enabled) "ARMED" else "STANDBY"
            val authority = if (pair.host.isBlank()) "Not configured" else "${pair.host}:${pair.port}${pair.remoteDir}"
            sub.text = "$onOff · ${protocol.label} · ${SyncPair.modeLabel(pair.mode)}\n$authority"
            sub.textSize = 13f
            sub.setTextColor(getColor(R.color.md_theme_on_surface_variant))
            val top = (6 * resources.displayMetrics.density).toInt()
            sub.setPadding(0, top, 0, 0)

            inner.addView(title)
            inner.addView(sub)
            card.addView(inner)
            card.setOnClickListener { openPair(pair.id) }
            pairContainer.addView(card)
        }
    }

    private fun refreshStatus() {
        val prefs = store.raw()
        val running = prefs.getBoolean("is_running", false)
        tvStatus.text = if (running) getString(R.string.status_running) else getString(R.string.status_idle)
        val lastTs = prefs.getLong("last_run_ts", 0L)
        tvLastRun.text = if (lastTs > 0) {
            val fmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
            "Last run: ${fmt.format(Date(lastTs))}"
        } else {
            "Last run: Never"
        }
        val up = prefs.getInt("last_uploaded", 0)
        val down = prefs.getInt("last_downloaded", 0)
        val fail = prefs.getInt("last_failed", 0)
        val conflicts = prefs.getInt("last_conflicts", 0)
        tvStats.text = "↑ $up   ↓ $down   ! $conflicts conflicts   ✗ $fail"
        val history = prefs.getString("run_history", "") ?: ""
        tvHistory.text = if (history.isBlank()) getString(R.string.no_history) else history
    }
}
