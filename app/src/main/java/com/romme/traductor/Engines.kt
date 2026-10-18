package com.romme.traductor

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// ─────────────────────────────────────────────────────────────
// TRADUCCIÓN: sin internet (ML Kit) y con internet (IA de Gemini)
// ─────────────────────────────────────────────────────────────
class TranslationEngine {

    private val esToEn = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )
    private val enToEs = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
    )

    /** ¿Ya está descargado el paquete de español? (el inglés viene incluido) */
    suspend fun offlineReady(): Boolean {
        val models = RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java).await()
        return models.any { it.language == TranslateLanguage.SPANISH }
    }

    /** Descarga los idiomas para usar sin internet (una sola vez). */
    suspend fun downloadOffline(wifiOnly: Boolean) {
        val cond = DownloadConditions.Builder().apply { if (wifiOnly) requireWifi() }.build()
        esToEn.downloadModelIfNeeded(cond).await()
        enToEs.downloadModelIfNeeded(cond).await()
    }

    suspend fun translateOffline(text: String, from: Lang): String {
        val client = if (from == Lang.ES) esToEn else enToEs
        return client.translate(text).await()
    }

    /** Traducción con IA: entiende lunfardo, modismos y tono. */
    suspend fun translateOnline(text: String, from: Lang, apiKey: String, model: String): String =
        withContext(Dispatchers.IO) {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 15000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey.trim())
            }
            val body = JSONObject().apply {
                put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt(from)))))
                put(
                    "contents", JSONArray().put(
                        JSONObject().put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", text)))
                    )
                )
            }
            try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val resp = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw IOException("HTTP $code ${resp.take(160)}")
                val parts = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    if (!p.optBoolean("thought", false)) sb.append(p.optString("text"))
                }
                val out = sb.toString().trim().trim('"', '“', '”').trim()
                if (out.isBlank()) throw IOException("respuesta vacía")
                out
            } finally {
                conn.disconnect()
            }
        }

    private fun prompt(from: Lang): String {
        val src = if (from == Lang.ES) "español rioplatense (Argentina)" else "inglés (Estados Unidos)"
        val dst = if (from == Lang.ES) "inglés natural de Estados Unidos" else "español rioplatense de Argentina, con voseo"
        return """
            Sos un intérprete en una charla familiar entre argentinos y estadounidenses.
            Traducí el mensaje del $src al $dst, como lo diría una persona nativa al hablar.
            Entendé el lunfardo y los modismos argentinos (che, re, posta, boludo, laburo, morfar, birra, mina, chabón, etc.)
            y traducí su sentido real, no palabra por palabra. Mantené el tono (cariñoso, gracioso, serio).
            El texto viene de un reconocedor de voz: corregí errores obvios de dictado.
            Respondé SOLO con la traducción, sin comillas, sin notas y sin explicaciones.
        """.trimIndent()
    }

    fun close() {
        esToEn.close()
        enToEs.close()
    }
}

// ─────────────────────────────────────────────────────────────
// ESCUCHAR: convierte la voz en texto
// ─────────────────────────────────────────────────────────────
class SpeechListener(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        lang: Lang,
        preferOffline: Boolean,
        onPartial: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onResult: (String) -> Unit,
        onError: (Int) -> Unit,
    ) {
        // Sin internet: primero probamos la voz offline del sistema (Android 12+);
        // si no tiene el idioma, probamos el reconocedor común en modo offline.
        val onDevice = preferOffline && Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        begin(lang, preferOffline, onDevice, onPartial, onLevel, onResult) { code ->
            if (onDevice && code in listOf(SpeechRecognizer.ERROR_CLIENT, 12, 13)) {
                begin(lang, true, false, onPartial, onLevel, onResult, onError)
            } else {
                onError(code)
            }
        }
    }

    private fun begin(
        lang: Lang,
        preferOffline: Boolean,
        onDevice: Boolean,
        onPartial: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onResult: (String) -> Unit,
        onError: (Int) -> Unit,
    ) {
        destroy()
        val rec = if (onDevice && Build.VERSION.SDK_INT >= 31) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = rec
        var finished = false
        var heardSomething = false
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (finished) return
                finished = true
                // Si ya había texto parcial, lo usamos en vez de perderlo
                if (heardSomething && lastPartial.isNotBlank()) onResult(lastPartial) else onError(error)
            }
            override fun onResults(results: Bundle?) {
                if (finished) return
                finished = true
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.ifBlank { null } ?: lastPartial
                onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    heardSomething = true
                    lastPartial = text
                    onPartial(text)
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        lastPartial = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        rec.startListening(intent)
    }

    private var lastPartial = ""

    /** Termina de escuchar y procesa lo que se dijo. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
    }
}

// ─────────────────────────────────────────────────────────────
// VOZ SIN INTERNET: pide al celular que descargue el idioma solo
// (Android 13 o más nuevo). En celulares más viejos no hace nada.
// ─────────────────────────────────────────────────────────────
object OfflineSpeech {
    fun ensure(context: Context, lang: Lang) {
        if (Build.VERSION.SDK_INT < 33) return
        Handler(Looper.getMainLooper()).post { ensure33(context, lang) }
    }

    @RequiresApi(33)
    private fun ensure33(context: Context, lang: Lang) {
        try {
            if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return
            val rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val base = lang.tag.substring(0, 2)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
            val main = Handler(Looper.getMainLooper())
            rec.checkRecognitionSupport(intent, ContextCompat.getMainExecutor(context), object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    try {
                        fun List<String>.has() = any { it.startsWith(base, ignoreCase = true) }
                        // Android 14 cambió los nombres de estas listas
                        @Suppress("DEPRECATION")
                        val installed = if (Build.VERSION.SDK_INT >= 34) support.installedOnDeviceLanguages else support.installedLanguages
                        @Suppress("DEPRECATION")
                        val pending = if (Build.VERSION.SDK_INT >= 34) support.pendingOnDeviceLanguages else support.pendingLanguages
                        @Suppress("DEPRECATION")
                        val supported = if (Build.VERSION.SDK_INT >= 34) support.supportedOnDeviceLanguages else support.supportedLanguages
                        val ready = installed.has() || pending.has()
                        if (!ready && supported.has()) {
                            val pick = supported.firstOrNull { it.equals(lang.tag, ignoreCase = true) }
                                ?: (if (lang == Lang.ES) supported.firstOrNull { it.equals("es-US", true) || it.equals("es-419", true) } else null)
                                ?: supported.first { it.startsWith(base, ignoreCase = true) }
                            rec.triggerModelDownload(Intent(intent).putExtra(RecognizerIntent.EXTRA_LANGUAGE, pick))
                        }
                    } catch (_: Throwable) {
                    }
                    main.postDelayed({ rec.destroy() }, 15000)
                }

                override fun onError(error: Int) {
                    rec.destroy()
                }
            })
        } catch (_: Throwable) {
        }
    }
}

// ─────────────────────────────────────────────────────────────
// HABLAR: lee la traducción en voz alta
// ─────────────────────────────────────────────────────────────
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val main = Handler(Looper.getMainLooper())
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    var speaking by mutableStateOf(false)
        private set

    /** Idiomas para los que el celular no tiene voz instalada. */
    var missingVoices by mutableStateOf<Set<Lang>>(emptySet())
        private set

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { speaking = true } }
            override fun onDone(utteranceId: String?) { main.post { speaking = false } }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { main.post { speaking = false } }
        })
        val missing = mutableSetOf<Lang>()
        Lang.entries.forEach { if (bestLocale(it) == null) missing.add(it) }
        main.post { missingVoices = missing }
    }

    private fun bestLocale(lang: Lang): Locale? {
        val options = if (lang == Lang.ES) {
            listOf("es-AR", "es-US", "es-419", "es-MX", "es-ES", "es")
        } else {
            listOf("en-US", "en-GB", "en")
        }.map { Locale.forLanguageTag(it) }
        return options.firstOrNull {
            val r = tts.isLanguageAvailable(it)
            r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun speak(text: String, lang: Lang, rate: Float) {
        if (!ready || text.isBlank()) return
        val loc = bestLocale(lang) ?: lang.locale
        tts.language = loc
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "t" + System.nanoTime())
    }

    fun stop() {
        if (ready) tts.stop()
        speaking = false
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
