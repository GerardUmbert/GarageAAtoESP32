package com.dunnowsoftware.GarageAAtoESP32.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

private const val PREFS_NAME = "locale_prefs"
private const val KEY_LOCALE  = "selected_locale"

private val SUPPORTED_LOCALE_TAGS = setOf("ca", "de", "en", "es", "fi", "fr", "it", "pt-PT")

fun getSavedLocaleTag(context: Context): String? {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LOCALE, null)
        .takeIf { !it.isNullOrEmpty() }
    if (saved != null) return saved
    // Fall back to whatever the system/AppCompatDelegate has set (e.g. via
    // App Info → Language in Android Settings), so the in-app language list
    // reflects the OS-level selection even when nothing is stored in prefs.
    val systemSet = AppCompatDelegate.getApplicationLocales()
    if (systemSet.isEmpty) return null
    val full = systemSet[0]?.toLanguageTag() ?: return null
    // Match against the supported list: prefer an exact match, fall back to
    // language-prefix match (e.g. "ca-ES" → "ca"). This avoids hardcoding
    // any specific region variant.
    return SUPPORTED_LOCALE_TAGS.firstOrNull { it == full }
        ?: SUPPORTED_LOCALE_TAGS.firstOrNull { full.startsWith(it.substringBefore('-')) }
}

fun saveLocaleTag(context: Context, tag: String?) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .apply { if (tag == null) remove(KEY_LOCALE) else putString(KEY_LOCALE, tag) }
        .apply()
}

fun localeListFromTag(tag: String?): LocaleListCompat =
    LocaleListCompat.forLanguageTags(tag ?: "")
