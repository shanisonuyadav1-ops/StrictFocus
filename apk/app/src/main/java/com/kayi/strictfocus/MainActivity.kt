package com.kayi.strictfocus

import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private data class AppItem(val label: String, val pkg: String)

    private val apps = mutableListOf<AppItem>()
    private var countDown: CountDownTimer? = null
    private var askedPermission = false
    private var askedAdmin = false
    private var schStart = 22 * 60
    private var schEnd = 6 * 60

    private lateinit var tvStatus: TextView
    private lateinit var btnAccess: Button
    private lateinit var btnAdmin: Button
    private lateinit var panelSetup: View
    private lateinit var panelLocked: View
    private lateinit var listView: ListView
    private lateinit var rgMode: RadioGroup
    private lateinit var secTimer: View
    private lateinit var secSchedule: View
    private lateinit var etMinutes: EditText
    private lateinit var btnFrom: Button
    private lateinit var btnTo: Button
    private lateinit var panelScheduleInfo: LinearLayout
    private lateinit var tvScheduleInfo: TextView
    private lateinit var btnRemoveSchedule: Button
    private lateinit var btnStart: Button
    private lateinit var tvLockTitle: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvBlockedList: TextView

    private val adminCn by lazy { ComponentName(this, AdminReceiver::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnAccess = findViewById(R.id.btnAccessibility)
        btnAdmin = findViewById(R.id.btnAdmin)
        panelSetup = findViewById(R.id.panelSetup)
        panelLocked = findViewById(R.id.panelLocked)
        listView = findViewById(R.id.appList)
        rgMode = findViewById(R.id.rgMode)
        secTimer = findViewById(R.id.secTimer)
        secSchedule = findViewById(R.id.secSchedule)
        etMinutes = findViewById(R.id.etMinutes)
        btnFrom = findViewById(R.id.btnFrom)
        btnTo = findViewById(R.id.btnTo)
        panelScheduleInfo = findViewById(R.id.panelScheduleInfo)
        tvScheduleInfo = findViewById(R.id.tvScheduleInfo)
        btnRemoveSchedule = findViewById(R.id.btnRemoveSchedule)
        btnStart = findViewById(R.id.btnStart)
        tvLockTitle = findViewById(R.id.tvLockTitle)
        tvTimer = findViewById(R.id.tvTimer)
        tvBlockedList = findViewById(R.id.tvBlockedList)

        if (BlockerPrefs.hasSchedule(this)) {
            schStart = BlockerPrefs.scheduleStart(this)
            schEnd = BlockerPrefs.scheduleEnd(this)
        }
        updateTimeButtons()
        updateModeUi()

        btnAccess.setOnClickListener { openAccessibilitySettings() }
        btnAdmin.setOnClickListener { requestAdmin() }
        btnStart.setOnClickListener { confirmAndStart() }
        btnFrom.setOnClickListener { pickTime(schStart) { schStart = it; updateTimeButtons() } }
        btnTo.setOnClickListener { pickTime(schEnd) { schEnd = it; updateTimeButtons() } }
        btnRemoveSchedule.setOnClickListener { removeSchedule() }
        rgMode.setOnCheckedChangeListener { _, _ -> updateModeUi() }
        findViewById<TextView>(R.id.tvInstagram).setOnClickListener { openInstagram() }

        loadApps()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        if (!isServiceEnabled()) {
            if (!askedPermission) {
                askedPermission = true
                showPermissionDialog()
            }
        } else if (!isAdminActive() && !askedAdmin) {
            askedAdmin = true
            showAdminDialog()
        }
    }

    override fun onPause() {
        super.onPause()
        countDown?.cancel()
    }

    // ---------------- Permissions ----------------
    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission needed")
            .setMessage(
                "Strict Focus needs the Accessibility permission to detect which app is open " +
                    "and close the apps you locked.\n\n" +
                    "It only reads the app name. It never sends any data anywhere.\n\n" +
                    "When Settings opens, turn ON 'Strict Focus'."
            )
            .setPositiveButton("Open Settings") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun showAdminDialog() {
        AlertDialog.Builder(this)
            .setTitle("Uninstall protection")
            .setMessage(
                "Device Admin adds an extra step before Strict Focus can be uninstalled: Android asks you to deactivate Device Admin first.\n\n" +
                    "Important: Android still allows you to deactivate Device Admin, even during a lock. A normal app cannot completely block that system action.\n\n" +
                    "This does not give Strict Focus access to your files or personal data. On the next screen, tap 'Activate'."
            )
            .setPositiveButton("Continue") { _, _ -> requestAdmin() }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminCn)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Adds an extra system confirmation step before Strict Focus can be uninstalled."
            )
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open the device admin screen", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAdminActive(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(adminCn)
    }

    private fun openAccessibilitySettings() {
        BlockerPrefs.setWaitingEnable(this, true)
        val cn = ComponentName(this, StrictBlockerService::class.java).flattenToString()
        val args = Bundle().apply { putString(":settings:fragment_args_key", cn) }
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", cn)
            .putExtra(":settings:show_fragment_args", args)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun openInstagram() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/army.boy_hu")))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open Instagram", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- Apps list ----------------
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

    // ---------------- Mode / time pickers ----------------
    private fun isScheduleMode() = rgMode.checkedRadioButtonId == R.id.rbSchedule

    private fun updateModeUi() {
        val schedule = isScheduleMode()
        secTimer.visibility = if (schedule) View.GONE else View.VISIBLE
        secSchedule.visibility = if (schedule) View.VISIBLE else View.GONE
        btnStart.text = if (schedule) "📅  Save Daily Schedule" else "🔒  Start Strict Lock"
    }

    private fun updateTimeButtons() {
        btnFrom.text = "From  ${fmt(schStart)}"
        btnTo.text = "To  ${fmt(schEnd)}"
    }

    private fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, true).show()
    }

    private fun fmt(min: Int) = String.format("%02d:%02d", min / 60, min % 60)

    // ---------------- Start lock ----------------
    private fun confirmAndStart() {
        if (!isServiceEnabled()) {
            Toast.makeText(this, "Please turn on the Accessibility Service first", Toast.LENGTH_LONG).show()
            showPermissionDialog()
            return
        }
        if (!isAdminActive()) {
            Toast.makeText(this, "Please turn on uninstall protection first", Toast.LENGTH_LONG).show()
            showAdminDialog()
            return
        }
        val selected = apps.indices.filter { listView.isItemChecked(it) }.map { apps[it].pkg }.toSet()
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one app", Toast.LENGTH_SHORT).show()
            return
        }
        if (isScheduleMode()) confirmSchedule(selected) else confirmTimer(selected)
    }

    private fun confirmTimer(selected: Set<String>) {
        val mins = etMinutes.text.toString().toLongOrNull() ?: 0L
        if (mins <= 0L) {
            Toast.makeText(this, "Enter a valid number of minutes", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Start strict lock?")
            .setMessage(
                "${selected.size} app(s) will be locked for $mins minute(s).\n" +
                    "You will NOT be able to unlock them or uninstall Strict Focus until the timer ends."
            )
            .setPositiveButton("Yes, lock") { _, _ ->
                BlockerPrefs.start(this, selected, mins)
                refreshUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmSchedule(selected: Set<String>) {
        if (schStart == schEnd) {
            Toast.makeText(this, "Start and end time cannot be the same", Toast.LENGTH_SHORT).show()
            return
        }
        val c = Calendar.getInstance()
        val nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val startsNow = BlockerPrefs.windowContains(nowMin, schStart, schEnd)
        AlertDialog.Builder(this)
            .setTitle("Save daily schedule?")
            .setMessage(
                "${selected.size} app(s) will be locked every day from ${fmt(schStart)} to ${fmt(schEnd)}.\n\n" +
                    "During that time you cannot unlock them, uninstall Strict Focus, or change the schedule." +
                    if (startsNow) "\n\nThe lock starts right now." else ""
            )
            .setPositiveButton("Yes, save") { _, _ ->
                BlockerPrefs.saveSchedule(this, selected, schStart, schEnd)
                refreshUi()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeSchedule() {
        if (BlockerPrefs.isLocked(this)) {
            Toast.makeText(this, "Locked right now", Toast.LENGTH_SHORT).show()
            return
        }
        BlockerPrefs.removeSchedule(this)
        refreshUi()
    }

    // ---------------- UI state ----------------
    private fun refreshUi() {
        countDown?.cancel()
        val a = isServiceEnabled()
        val d = isAdminActive()
        tvStatus.text = "Accessibility: " + (if (a) "ON ✅" else "OFF ❌") +
            "\nUninstall protection: " + (if (d) "ON ✅" else "OFF ❌")
        btnAccess.visibility = if (a) View.GONE else View.VISIBLE
        btnAdmin.visibility = if (d) View.GONE else View.VISIBLE

        if (BlockerPrefs.isLocked(this)) {
            panelSetup.visibility = View.GONE
            panelLocked.visibility = View.VISIBLE
            tvLockTitle.text = if (BlockerPrefs.isTimerActive(this)) "🔒 Strict Lock ON" else "📅 Scheduled Lock ON"
            tvBlockedList.text = "Locked apps:\n" +
                BlockerPrefs.blockedNow(this).joinToString("\n") { labelOf(it) }
            val remaining = BlockerPrefs.lockRemainingMs(this).coerceAtLeast(500L)
            tvTimer.text = format(remaining)
            countDown = object : CountDownTimer(remaining, 1000) {
                override fun onTick(ms: Long) { tvTimer.text = format(ms) }
                override fun onFinish() { refreshUi() }
            }.start()
        } else {
            panelLocked.visibility = View.GONE
            panelSetup.visibility = View.VISIBLE
            if (BlockerPrefs.hasSchedule(this)) {
                panelScheduleInfo.visibility = View.VISIBLE
                tvScheduleInfo.text = "📅 Daily lock: ${fmt(BlockerPrefs.scheduleStart(this))} – " +
                    "${fmt(BlockerPrefs.scheduleEnd(this))} (${BlockerPrefs.schedulePkgs(this).size} apps)"
            } else {
                panelScheduleInfo.visibility = View.GONE
            }
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
