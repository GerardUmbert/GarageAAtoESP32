package com.dunnowsoftware.GarageAAtoESP32.data

import android.content.Context
import androidx.core.os.LocaleListCompat

private const val PREFS_NAME = "locale_prefs"
private const val KEY_LOCALE  = "selected_locale"

fun getSavedLocaleTag(context: Context): String? =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LOCALE, null)
        .takeIf { !it.isNullOrEmpty() }

fun saveLocaleTag(context: Context, tag: String?) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .apply { if (tag == null) remove(KEY_LOCALE) else putString(KEY_LOCALE, tag) }
        .apply()
}

fun localeListFromTag(tag: String?): LocaleListCompat =
    LocaleListCompat.forLanguageTags(tag ?: "")
