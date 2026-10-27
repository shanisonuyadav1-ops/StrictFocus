package com.kayi.strictfocus

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private data class AppItem(val label: String, val pkg: String)

    private val apps = mutableListOf<AppItem>()
    private var countDown: CountDownTimer? = null

    private lateinit var tvStatus: TextView
    private lateinit var btnAccess: Button
    private lateinit var panelSetup: View
    private lateinit var panelLocked: View
    private lateinit var listView: ListView
    private lateinit var etMinutes: EditText
    private lateinit var btnStart: Button
    private lateinit var tvTimer: TextView
    private lateinit var tvBlockedList: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnAccess = findViewById(R.id.btnAccessibility)
        panelSetup = findViewById(R.id.panelSetup)
        panelLocked = findViewById(R.id.panelLocked)
        listView = findViewById(R.id.appList)
        etMinutes = findViewById(R.id.etMinutes)
        btnStart = findViewById(R.id.btnStart)
        tvTimer = findViewById(R.id.tvTimer)
        tvBlockedList = findViewById(R.id.tvBlockedList)

        btnAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnStart.setOnClickListener { confirmAndStart() }

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onPause() {
        super.onPause()
        countDown?.cancel()
    }

    // ---------- Apps ki list (background thread, UI hang nahi hoga) ----------
    private fun loadApps() {
        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(intent, 0)
                .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                apps.clear()
                apps.addAll(list)
                listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE
                listView.adapter = ArrayAdapter(
                    this, android.R.layout.simple_list_item_multiple_choice,
                    list.map { it.label }
                )
            }
        }.start()
    }

    // ---------- Lock start ----------
    private fun confirmAndStart() {
        if (!isServiceEnabled()) {
            Toast.makeText(this, "Pehle Accessibility Service ON karo", Toast.LENGTH_LONG).show()
            return
        }
        val mins = etMinutes.text.toString().toLongOrNull() ?: 0L
        if (mins <= 0L) {
            Toast.makeText(this, "Sahi minutes daalo", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = apps.indices.filter { listView.isItemChecked(it) }.map { apps[it].pkg }.toSet()
        if (selected.isEmpty()) {
            Toast.makeText(this, "Kam se kam ek app chuno", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Pakka lock karna hai?")
            .setMessage("${selected.size} app(s) $mins minute ke liye lock honge.\nBeech mein unlock NAHI hoga.")
            .setPositiveButton("Haan, lock karo") { _, _ ->
                BlockerPrefs.start(this, selected, mins)
                refreshUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- UI state ----------
    private fun refreshUi() {
        countDown?.cancel()
        val serviceOn = isServiceEnabled()
        tvStatus.text = if (serviceOn) "Service: ON ✅" else "Service: OFF ❌ (neeche button dabao)"
        btnAccess.visibility = if (serviceOn) View.GONE else View.VISIBLE

        if (BlockerPrefs.isActive(this)) {
            panelSetup.visibility = View.GONE
            panelLocked.visibility = View.VISIBLE
            tvBlockedList.text = "Locked apps:\n" + BlockerPrefs.blocked(this).joinToString("\n") { labelOf(it) }
            countDown = object : CountDownTimer(BlockerPrefs.remainingMs(this), 1000) {
                override fun onTick(ms: Long) { tvTimer.text = format(ms) }
                override fun onFinish() { refreshUi() }
            }.start()
        } else {
            panelLocked.visibility = View.GONE
            panelSetup.visibility = View.VISIBLE
        }
    }

    private fun labelOf(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun format(ms: Long): String {
        val s = ms / 1000
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(this, StrictBlockerService::class.java)
        return enabled.split(':').any {
            it.equals(cn.flattenToString(), true) || it.equals(cn.flattenToShortString(), true)
        }
    }
}
