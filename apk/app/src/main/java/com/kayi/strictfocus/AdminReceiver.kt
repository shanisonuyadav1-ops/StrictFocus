package com.kayi.strictfocus

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin makes Android require deactivation before uninstalling this app.
 * Android does not let an ordinary app block the user from deactivating Device Admin.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return if (BlockerPrefs.isLocked(context) || BlockerPrefs.hasSchedule(context)) {
            "Strict Focus lock or schedule is active. If you continue, Device Admin protection will be disabled and the app may then be uninstalled. Android does not allow an ordinary app to block this deactivation."
        } else {
            "Device Admin protection will be disabled. Android will then allow Strict Focus to be uninstalled normally."
        }
    }
}
