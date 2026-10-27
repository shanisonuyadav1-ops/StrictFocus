package com.kayi.strictfocus

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin ON hone par Android is app ko uninstall nahi hone deta,
 * jab tak admin pehle deactivate na kiya jaye. Lock ke dauran Accessibility
 * service Settings ke us page ko bhi band kar deti hai.
 * Ye koi data/policy control nahi leta (uses-policies khali hai).
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? =
        if (BlockerPrefs.isLocked(context))
            "Strict lock is active. You cannot turn this off until the lock ends."
        else
            "Uninstall protection will be turned off."
}
