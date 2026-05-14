package com.noscroll.metadata

import android.content.Context

object MetadataLookupPrefs {
    private const val PREFS = "metadata_prefs"
    private const val KEY_ONLINE_LOOKUP = "online_lookup_enabled"

    fun isOnlineLookupEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONLINE_LOOKUP, false)

    fun setOnlineLookupEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONLINE_LOOKUP, enabled)
            .apply()
    }
}
