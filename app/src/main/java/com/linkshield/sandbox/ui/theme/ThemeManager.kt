package com.linkshield.sandbox.ui.theme

import android.content.Context
import android.content.SharedPreferences

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_THEME = "app_theme"
        const val THEME_DARK = "dark"
        const val THEME_LIGHT = "light"
    }
    
    fun getTheme(): String = prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    
    fun setTheme(theme: String) {
        prefs.edit().putString(KEY_THEME, theme).apply()
    }
    
    fun isDarkTheme(): Boolean = getTheme() == THEME_DARK
}
