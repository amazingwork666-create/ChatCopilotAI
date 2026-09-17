package com.chatcopilot.service

import android.content.Context
import android.content.SharedPreferences
import com.chatcopilot.BuildConfig
import com.chatcopilot.llm.Tone

object PreferencesManager {

    private const val PREFS_NAME = "chatcopilot_prefs"
    private const val KEY_TONE = "tone"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_LANGUAGE = "language"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // API key: prefers runtime env var (CI/CD), then user-entered, then BuildConfig
    fun getApiKey(context: Context): String? {
        val fromBuild = BuildConfig.CLAUDE_API_KEY
        if (fromBuild.isNotBlank()) return fromBuild
        val fromPrefs = prefs(context).getString(KEY_API_KEY, null)
        if (!fromPrefs.isNullOrBlank()) return fromPrefs
        return null
    }

    fun saveApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_KEY, key).apply()
    }

    fun getTone(context: Context): Tone {
        val name = prefs(context).getString(KEY_TONE, Tone.WARM.name) ?: Tone.WARM.name
        return runCatching { Tone.valueOf(name) }.getOrDefault(Tone.WARM)
    }

    fun saveTone(context: Context, tone: Tone) {
        prefs(context).edit().putString(KEY_TONE, tone.name).apply()
    }

    fun getLanguage(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE, "auto") ?: "auto"

    fun saveLanguage(context: Context, language: String) {
        prefs(context).edit().putString(KEY_LANGUAGE, language).apply()
    }
}
