package com.kayi.strictfocus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlin.math.ceil

class StrictBlockerService : AccessibilityService() {

    private var lastToast = 0L
    private var lastGuard = 0L
    private var lastKick = 0L

    private val guardedPackages = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Sirf tab app wapas kholo jab user abhi Settings se ON karke aaya ho
        if (BlockerPrefs.isWaitingEnable(this)) {
            BlockerPrefs.setWaitingEnable(this, false)
            try {
                startActivity(
                    Intent(this, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    )
                )
            } catch (e: Exception) {
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return

        val blocked = BlockerPrefs.blockedNow(this)
        if (blocked.isEmpty()) return                      // koi lock nahi chal raha

        // Settings / uninstall screens: Strict Focus ka naam dikhe to Home
        if (pkg in guardedPackages) {
            guardSettings()
            return
        }
        if (pkg !in blocked) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> kick()
            // Schedule start hone par app pehle se khula ho to bhi band ho
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                if (rootInActiveWindow?.packageName?.toString() == pkg) kick()
        }
    }

    private fun kick() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastKick < 300) return
        lastKick = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        showRemainingToast()
    }

    private fun guardSettings() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastGuard < 400) return
        lastGuard = now
        val root = rootInActiveWindow ?: return
        val hits = root.findAccessibilityNodeInfosByText(getString(R.string.app_name))
        if (!hits.isNullOrEmpty()) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun showRemainingToast() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastToast < 2000) return
        lastToast = now
        val mins = ceil(BlockerPrefs.lockRemainingMs(this) / 60_000.0).toInt()
        Toast.makeText(this, "Strict Focus: $mins min left", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}
}
