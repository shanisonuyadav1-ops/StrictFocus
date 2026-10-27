package com.kayi.strictfocus

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Saari state persistent storage mein:
 *  - Timer lock: absolute end time (epoch ms) -> restart/app-clear par bhi chalta hai
 *  - Daily schedule: start/end minute-of-day + apps, har din repeat hota hai
 */
object BlockerPrefs {
    private const val FILE = "strict_focus_state"
    private const val KEY_END = "end_time"
    private const val KEY_LAST_SEEN = "last_seen"
    private const val KEY_PKGS = "blocked_pkgs"
    private const val KEY_WAITING = "waiting_enable"
    private const val KEY_SCH_ON = "sch_on"
    private const val KEY_SCH_START = "sch_start"
    private const val KEY_SCH_END = "sch_end"
    private const val KEY_SCH_PKGS = "sch_pkgs"

    @Volatile private var cached: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences =
        cached ?: ctx.applicationContext
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .also { cached = it }

    // ---------------- Timer lock ----------------
    fun start(ctx: Context, pkgs: Set<String>, minutes: Long) {
        val now = System.currentTimeMillis()
        prefs(ctx).edit()
            .putStringSet(KEY_PKGS, HashSet(pkgs))
            .putLong(KEY_END, now + minutes * 60_000L)
            .putLong(KEY_LAST_SEEN, now)
            .commit()
    }

    fun isTimerActive(ctx: Context): Boolean {
        val p = prefs(ctx)
        val end = p.getLong(KEY_END, 0L)
        if (end == 0L) return false

        val now = System.currentTimeMillis()
        val last = p.getLong(KEY_LAST_SEEN, now)
        var newEnd = end

        // Anti-cheat: clock peeche kiya to utna time jod do
        if (now < last) newEnd += (last - now)

        if (now >= newEnd) {
            clearTimer(ctx)
            return false
        }
        if (newEnd != end || now < last || now - last > 15_000L) {
            p.edit().putLong(KEY_END, newEnd).putLong(KEY_LAST_SEEN, now).apply()
        }
        return true
    }

    fun timerRemainingMs(ctx: Context): Long {
        if (!isTimerActive(ctx)) return 0L
        return (prefs(ctx).getLong(KEY_END, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun timerPkgs(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_PKGS, emptySet()) ?: emptySet()

    private fun clearTimer(ctx: Context) {
        prefs(ctx).edit().remove(KEY_END).remove(KEY_LAST_SEEN).remove(KEY_PKGS).apply()
    }

    // ---------------- Daily schedule ----------------
    fun saveSchedule(ctx: Context, pkgs: Set<String>, startMin: Int, endMin: Int) {
        prefs(ctx).edit()
            .putBoolean(KEY_SCH_ON, true)
            .putInt(KEY_SCH_START, startMin)
            .putInt(KEY_SCH_END, endMin)
            .putStringSet(KEY_SCH_PKGS, HashSet(pkgs))
            .commit()
    }

    fun removeSchedule(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_SCH_ON).remove(KEY_SCH_START).remove(KEY_SCH_END).remove(KEY_SCH_PKGS)
            .commit()
    }

    fun hasSchedule(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SCH_ON, false)
    fun scheduleStart(ctx: Context): Int = prefs(ctx).getInt(KEY_SCH_START, 22 * 60)
    fun scheduleEnd(ctx: Context): Int = prefs(ctx).getInt(KEY_SCH_END, 6 * 60)
    fun schedulePkgs(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_SCH_PKGS, emptySet()) ?: emptySet()

    /** Overnight bhi chalta hai (jaise 22:00 se 06:00). */
    fun windowContains(m: Int, s: Int, e: Int): Boolean =
        if (s == e) false else if (s < e) m in s until e else (m >= s || m < e)

    fun isScheduleActive(ctx: Context): Boolean {
        if (!hasSchedule(ctx)) return false
        val c = Calendar.getInstance()
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return windowContains(m, scheduleStart(ctx), scheduleEnd(ctx))
    }

    fun scheduleRemainingMs(ctx: Context): Long {
        if (!isScheduleActive(ctx)) return 0L
        val c = Calendar.getInstance()
        val nowMs = ((c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) * 60L +
            c.get(Calendar.SECOND)) * 1000L + c.get(Calendar.MILLISECOND)
        var d = scheduleEnd(ctx) * 60_000L - nowMs
        if (d <= 0L) d += 86_400_000L
        return d
    }

    // ---------------- Combined ----------------
    fun isLocked(ctx: Context): Boolean = isTimerActive(ctx) || isScheduleActive(ctx)

    fun lockRemainingMs(ctx: Context): Long =
        maxOf(timerRemainingMs(ctx), scheduleRemainingMs(ctx))

    /** Is waqt jo apps block hone chahiye (khali = lock nahi chal raha). */
    fun blockedNow(ctx: Context): Set<String> {
        val out = HashSet<String>()
        if (isTimerActive(ctx)) out.addAll(timerPkgs(ctx))
        if (isScheduleActive(ctx)) out.addAll(schedulePkgs(ctx))
        return out
    }

    // ---------------- Settings se wapas aane ka flag ----------------
    fun setWaitingEnable(ctx: Context, value: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_WAITING, value).apply()
    }

    fun isWaitingEnable(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_WAITING, false)
}
