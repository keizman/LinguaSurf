package org.mozilla.fenix.extension.linguasurf

import android.content.Context

object LinguaSurfAppSettings {
    private const val PREF_FILE = "linguasurf_app_settings"
    private const val KEY_DISABLE_SYSTEM_SELECTION_BANNER = "disable_system_selection_banner"

    fun isSystemSelectionBannerDisabled(context: Context): Boolean {
        return context
            .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISABLE_SYSTEM_SELECTION_BANNER, false)
    }

    fun setSystemSelectionBannerDisabled(context: Context, disabled: Boolean) {
        context
            .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISABLE_SYSTEM_SELECTION_BANNER, disabled)
            .apply()
    }
}
