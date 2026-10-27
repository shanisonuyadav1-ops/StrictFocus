package com.kayi.strictfocus

import android.content.Context
import android.content.SharedPreferences

/**
 * Lock ki poori state yahan persistent storage mein rehti hai.
 * End time ABSOLUTE wall-clock (epoch ms) mein save hota hai, isliye
 * app clear karne ya phone restart karne par bhi timer chalta rehta hai.
 */
object BlockerPrefs {
    private const val FILE = "strict_focus_state"
    private const val KEY_END = "end_time"
    private const val KEY_LAST_SEEN = "last_seen"
    private const val KEY_PKGS = "blocked_pkgs"
    private const val KEY_WAITING = "waiting_enable"

    @Volatile private var cached: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences =
        cached ?: ctx.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .also { cached = it }

    fun start(ctx: Context, pkgs: Set<String>, minutes: Long) {
        val now = System.currentTimeMillis()
        prefs(ctx).edit()
            .putStringSet(KEY_PKGS, HashSet(pkgs))
            .putLong(KEY_END, now + minutes * 60_000L)
            .putLong(KEY_LAST_SEEN, now)
            .commit()
    }

    fun isActive(ctx: Context): Boolean {
        val p = prefs(ctx)
        val end = p.getLong(KEY_END, 0L)
        if (end == 0L) return false

        val now = System.currentTimeMillis()
        val last = p.getLong(KEY_LAST_SEEN, now)
        var newEnd = end

        // Anti-cheat: clock peeche kiya to utna time jod do
        if (now < last) newEnd += (last - now)

        if (now >= newEnd) {
            clear(ctx)
            return false
        }
        if (newEnd != end || now < last || now - last > 15_000L) {
            p.edit().putLong(KEY_END, newEnd).putLong(KEY_LAST_SEEN, now).apply()
        }
        return true
    }

    fun remainingMs(ctx: Context): Long {
        if (!isActive(ctx)) return 0L
        return (prefs(ctx).getLong(KEY_END, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    fun blocked(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_PKGS, emptySet()) ?: emptySet()

    // User Settings kholne gaya hai -> service ON hote hi app wapas khulega
    fun setWaitingEnable(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_WAITING, value).apply()
    }

    fun isWaitingEnable(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_WAITING, false)

    private fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }
}
