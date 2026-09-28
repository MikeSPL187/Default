/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import com.metrolist.music.constants.AppLanguageKey
import com.metrolist.music.constants.SYSTEM_DEFAULT
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Changes the app's language in place: the screen fades to its background, the activity is
 * rebuilt in the new language on the same screen, and it fades back in. The player keeps playing.
 */
object LanguageSwitcher {
    const val FADE_OUT_MS = 180
    const val FADE_IN_MS = 320

    private val veiled = MutableStateFlow(false)

    /** True while the screen is fading out before the switch. */
    val fadingOut = veiled.asStateFlow()

    @Volatile
    private var fadeInPending = false

    /** Whether the activity being built is the one that follows a switch, so it starts hidden and fades in. */
    fun consumeFadeIn(): Boolean {
        val pending = fadeInPending
        fadeInPending = false
        veiled.value = false
        return pending
    }

    /** The chosen language tag, or [SYSTEM_DEFAULT] when the app follows the system. */
    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context
                .getSystemService(LocaleManager::class.java)
                .applicationLocales
                .takeUnless { it.isEmpty }
                ?.get(0)
                ?.toLanguageTag()
                ?: SYSTEM_DEFAULT
        } else {
            context.dataStore[AppLanguageKey] ?: SYSTEM_DEFAULT
        }

    suspend fun switch(
        activity: Activity,
        tag: String,
    ) {
        if (tag == current(activity)) return
        activity.safeDataStoreEdit { it[AppLanguageKey] = tag }
        veiled.value = true
        delay(FADE_OUT_MS.toLong())
        fadeInPending = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system rebuilds every activity in the new language, the app's own context included.
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == SYSTEM_DEFAULT) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            // MainActivity applies the stored language when it is created.
            activity.recreate()
        }
        // Should nothing be rebuilt (the language resolved to the same one), do not leave the screen hidden.
        delay(1_000)
        fadeInPending = false
        veiled.value = false
    }
}
