package io.github.galis228.materialereader.data

import android.content.Context

/**
 * The reader's own pace. Every page turn forward gives one sample (words read / time spent);
 * after the first few pages the estimate replaces the generic 200 words per minute and keeps refining.
 * Pauses (too short or too long) are ignored.
 */
object ReadingSpeed {
    const val DEFAULT_WPM = 200f
    const val CALIBRATION_PAGES = 5
    private const val PREFS = "reading_speed"

    fun wpm(ctx: Context): Float {
        val p = ctx.getSharedPreferences(PREFS, 0)
        return if (p.getInt("n", 0) >= CALIBRATION_PAGES) p.getFloat("wpm", DEFAULT_WPM) else DEFAULT_WPM
    }

    fun calibrated(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getInt("n", 0) >= CALIBRATION_PAGES
    fun samples(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getInt("n", 0)

    /** Returns true if the sample was used. */
    fun record(ctx: Context, words: Int, millis: Long): Boolean {
        if (words < 15 || millis < 2_500 || millis > 4 * 60_000) return false
        val wpm = words * 60_000f / millis
        if (wpm < 40f || wpm > 1500f) return false // skimming or a page left open
        val p = ctx.getSharedPreferences(PREFS, 0)
        val n = p.getInt("n", 0)
        val old = p.getFloat("wpm", wpm)
        // plain average while calibrating, then an exponential moving average that follows the reader's pace
        val next = if (n < CALIBRATION_PAGES) (old * n + wpm) / (n + 1) else old * 0.9f + wpm * 0.1f
        p.edit().putInt("n", n + 1).putFloat("wpm", next).apply()
        return true
    }

    fun reset(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).edit().clear().apply()
}
