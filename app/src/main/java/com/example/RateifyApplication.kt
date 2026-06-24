package com.example

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class RateifyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val prefs = getSharedPreferences("rateify_prefs", Context.MODE_PRIVATE)
        val savedTheme = prefs.getString("theme_mode", "system")
        when (savedTheme) {
            "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
