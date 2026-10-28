package com.kayi.strictfocus

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Android's normal Device Admin flow adds an uninstall barrier: the user must
 * deactivate the admin first. Android does not let an ordinary app forbid that
 * deactivation, so this callback can only warn the user.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return if (BlockerPrefs.isLocked(context) || BlockerPrefs.hasSchedule(context)) {
            "Strict Focus lock or schedule is active. Deactivating Device Admin weakens uninstall protection. Android may still let you continue."
        } else {
            "Deactivating Device Admin removes Strict Focus's extra uninstall step."
        }
    }
}
