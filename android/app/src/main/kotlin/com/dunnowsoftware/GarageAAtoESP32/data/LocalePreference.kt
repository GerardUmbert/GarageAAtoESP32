package com.dunnowsoftware.GarageAAtoESP32.data

import android.content.Context
import androidx.core.os.LocaleListCompat

private const val PREFS_NAME = "locale_prefs"
private const val KEY_LOCALE  = "selected_locale"

private val SUPPORTED_LOCALE_TAGS = setOf("ca", "de", "en", "es", "fi", "fr", "it", "pt-PT")

fun getSavedLocaleTag(context: Context): String? {
    val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LOCALE, null)
        .takeIf { !it.isNullOrEmpty() }
    if (saved != null) return saved
    // No in-app preference saved — reflect whatever locale is active on the
    // context (covers both the OS per-app language setting and AppCompat).
    val full = context.resources.configuration.locales[0]?.toLanguageTag() ?: return null
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
