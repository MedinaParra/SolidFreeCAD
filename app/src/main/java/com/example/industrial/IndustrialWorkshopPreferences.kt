package com.example.industrial

import android.content.Context

object IndustrialWorkshopPreferences {
    private const val FILE = "solidfreecad_industrial_preferences"
    private const val GLOVE_MODE = "glove_mode"

    fun gloveMode(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(GLOVE_MODE, false)

    fun setGloveMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(GLOVE_MODE, enabled).apply()
    }
}
