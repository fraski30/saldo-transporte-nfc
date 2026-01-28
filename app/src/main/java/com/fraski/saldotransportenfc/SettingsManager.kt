package com.fraski.saldotransportenfc

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mifare_settings", Context.MODE_PRIVATE)

    companion object {
        const val THEME_KEY = "theme_mode"
        const val VIBRATE_KEY = "vibration_enabled"
        const val BIOMETRIC_KEY = "biometric_enabled"
        
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_AUTO = 2
    }

    var themeMode: Int
        get() = prefs.getInt(THEME_KEY, THEME_AUTO)
        set(value) {
            prefs.edit().putInt(THEME_KEY, value).apply()
            applyTheme(value)
        }

    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(VIBRATE_KEY, true)
        set(value) = prefs.edit().putBoolean(VIBRATE_KEY, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(BIOMETRIC_KEY, false)
        set(value) = prefs.edit().putBoolean(BIOMETRIC_KEY, value).apply()

    fun applyTheme(mode: Int) {
        val targetMode = when (mode) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }
}
