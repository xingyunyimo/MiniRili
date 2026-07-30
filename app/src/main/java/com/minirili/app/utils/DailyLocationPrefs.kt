package com.minirili.app.utils

import android.content.Context

object DailyLocationPrefs {
    private const val PREFS = "daily_location_prefs"
    private const val KEY_LAST_AUTO_DATE = "last_auto_date"

    fun getLastAutoDate(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_AUTO_DATE, null)

    fun setLastAutoDate(context: Context, date: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_AUTO_DATE, date).apply()
    }
}
