package com.romme.traductor

import android.content.Context
import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/** Los dos idiomas de la app. */
enum class Lang(
    val tag: String,
    val locale: Locale,
    val label: String,
    val flag: String,
    val mlkit: String,
    val spanishName: String,
    val englishName: String,
) {
    ES("es-AR", Locale.forLanguageTag("es-AR"), "Español", "🇦🇷", TranslateLanguage.SPANISH, "español", "Spanish"),
    EN("en-US", Locale.US, "English", "🇺🇸", TranslateLanguage.ENGLISH, "inglés", "English");

    val other: Lang get() = if (this == ES) EN else ES

    /** Elige el texto en el idioma de esta persona. */
    fun t(es: String, en: String): String = if (this == ES) es else en
}

enum class Screen { SETUP, HOME, INDIVIDUAL, SHARED, READ, SETTINGS }

data class Turn(
    val from: Lang,
    val original: String,
    val translated: String,
    val online: Boolean,
    val id: Long = System.nanoTime(),
)

/** Guarda las opciones en el celular. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("opciones", Context.MODE_PRIVATE)

    var setupDone: Boolean
        get() = sp.getBoolean("setupDone", false)
        set(v) = sp.edit().putBoolean("setupDone", v).apply()

    var online: Boolean
        get() = sp.getBoolean("online", true)
        set(v) = sp.edit().putBoolean("online", v).apply()

    var myLang: Lang
        get() {
            // La primera vez se elige solo, según el idioma del celular
            val saved = sp.getString("myLang", null)
                ?: if (Locale.getDefault().language == "es") "es" else "en"
            return if (saved == "en") Lang.EN else Lang.ES
        }
        set(v) = sp.edit().putString("myLang", if (v == Lang.EN) "en" else "es").apply()

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v.trim()).apply()

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString("model", v.trim().ifBlank { DEFAULT_MODEL }).apply()

    var speechRate: Float
        get() = sp.getFloat("speechRate", 1.0f)
        set(v) = sp.edit().putFloat("speechRate", v).apply()

    var autoSpeak: Boolean
        get() = sp.getBoolean("autoSpeak", true)
        set(v) = sp.edit().putBoolean("autoSpeak", v).apply()

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    }
}
