package com.vitaltriage.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Apply the independently selected display/result language to the source XML screens too. */
internal object LegacyLocale {
    fun wrap(base:Context):Context {
        val configuration=Configuration(base.resources.configuration)
        val locale=Locale.forLanguageTag(UserPreferences(base).language.tag)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
