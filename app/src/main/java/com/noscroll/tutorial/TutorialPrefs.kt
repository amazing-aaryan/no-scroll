package com.noscroll.tutorial

import android.content.Context

class TutorialPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("tutorial", Context.MODE_PRIVATE)

    // opt-in gate
    fun hasMadeChoice() = p.contains("opted_in")
    fun hasOptedIn()    = p.getBoolean("opted_in", false)
    fun setOptedIn(v: Boolean) = p.edit().putBoolean("opted_in", v).apply()

    // per-flow done flags
    fun isSetupDone()    = p.getBoolean("setup_done",    false)
    fun isLibraryDone()  = p.getBoolean("library_done",  false)
    fun isReaderDone()   = p.getBoolean("reader_done",   false)
    fun isNotebookDone() = p.getBoolean("notebook_done", false)
    fun isReelsDone()    = p.getBoolean("reels_done",    false)

    fun markSetupDone()    = p.edit().putBoolean("setup_done",    true).apply()
    fun markLibraryDone()  = p.edit().putBoolean("library_done",  true).apply()
    fun markReaderDone()   = p.edit().putBoolean("reader_done",   true).apply()
    fun markNotebookDone() = p.edit().putBoolean("notebook_done", true).apply()
    fun markReelsDone()    = p.edit().putBoolean("reels_done",    true).apply()

    // skip everything — user said no
    fun skipAll() = p.edit()
        .putBoolean("opted_in",     false)
        .putBoolean("setup_done",    true)
        .putBoolean("library_done",  true)
        .putBoolean("reader_done",   true)
        .putBoolean("notebook_done", true)
        .putBoolean("reels_done",    true)
        .apply()

    // restart from library (help icon handler)
    fun restartFrom() = p.edit()
        .putBoolean("opted_in",     true)
        .putBoolean("library_done",  false)
        .putBoolean("reader_done",   false)
        .putBoolean("notebook_done", false)
        .putBoolean("reels_done",    false)
        .apply()

    fun resetAll() = p.edit().clear().apply()
}
