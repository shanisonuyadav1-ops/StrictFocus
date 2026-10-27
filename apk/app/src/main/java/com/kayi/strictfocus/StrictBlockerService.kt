package com.kayi.strictfocus

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlin.math.ceil

/**
 * Event-driven hai (koi polling/loop nahi) -> purane phone par bhi halka.
 * Phone restart ke baad Android khud is service ko dobara start kar deta hai
 * (agar Accessibility settings mein ON hai), isliye alag BootReceiver ki zaroorat nahi.
 */
class StrictBlockerService : AccessibilityService() {

    private var lastToast = 0L
    private var lastGuard = 0L

    private val guardedPackages = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return                    // apna app kabhi block nahi
        if (!BlockerPrefs.isActive(this)) return          // lock nahi chal raha

        // 1) Settings/uninstall se service band karne ya app hatane ki koshish rokna
        if (pkg in guardedPackages) {
            guardSettings()
            return
        }

        // 2) Chune hue apps ko block karna
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            pkg in BlockerPrefs.blocked(this)
        ) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            showRemainingToast()
        }
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
        val mins = ceil(BlockerPrefs.remainingMs(this) / 60_000.0).toInt()
        Toast.makeText(this, "Strict Focus: $mins min baaki hain", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {}
}
