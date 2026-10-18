package com.romme.traductor

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val ctx: Context = application.applicationContext
    private val prefs = Prefs(ctx)
    private val engine = TranslationEngine()
    private val listener = SpeechListener(ctx)
    private val ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val speaker = Speaker(ctx)

    // Navegación
    var screen by mutableStateOf(if (prefs.setupDone) Screen.HOME else Screen.SETUP)
        private set

    // Opciones
    var online by mutableStateOf(prefs.online)
        private set
    var myLang by mutableStateOf(prefs.myLang)
        private set
    var apiKey by mutableStateOf(prefs.apiKey)
        private set
    var model by mutableStateOf(prefs.model)
        private set
    var speechRate by mutableStateOf(prefs.speechRate)
        private set
    var autoSpeak by mutableStateOf(prefs.autoSpeak)
        private set
    var sharedTop by mutableStateOf(Lang.EN)
        private set

    // Conversación
    var listeningLang by mutableStateOf<Lang?>(null)
        private set
    var partial by mutableStateOf("")
        private set
    var micLevel by mutableStateOf(0f)
        private set
    var workingLang by mutableStateOf<Lang?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
    val turns = mutableStateListOf<Turn>()

    // Idiomas sin internet
    var offlineReady by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(false)
        private set
    var downloadMsg by mutableStateOf<String?>(null)
        private set

    // Foto y texto
    var readFrom by mutableStateOf(Lang.EN)
        private set
    var readOriginal by mutableStateOf("")
        private set
    var readTranslated by mutableStateOf("")
        private set
    var readBusy by mutableStateOf(false)
        private set

    init {
        readFrom = prefs.myLang.other
        refreshOffline()
    }

    // ───────── Navegación y opciones ─────────
    fun go(s: Screen) {
        cancelListening()
        speaker.stop()
        screen = s
    }

    fun finishSetup() {
        prefs.setupDone = true
        go(Screen.HOME)
    }

    fun updateOnline(v: Boolean) {
        online = v; prefs.online = v
        if (v && apiKey.isBlank()) {
            notice = "Para el modo con internet falta la clave de IA (Ajustes)."
        }
    }

    fun updateMyLang(v: Lang) {
        myLang = v; prefs.myLang = v
        readFrom = v.other
    }

    fun updateApiKey(v: String) { apiKey = v.trim(); prefs.apiKey = v }
    fun updateModel(v: String) { model = v; prefs.model = v }
    fun updateSpeechRate(v: Float) { speechRate = v; prefs.speechRate = v }
    fun updateAutoSpeak(v: Boolean) { autoSpeak = v; prefs.autoSpeak = v }
    fun swapShared() { sharedTop = sharedTop.other }
    fun swapRead() { readFrom = readFrom.other; readOriginal = ""; readTranslated = "" }
    fun clearTurns() { turns.clear(); speaker.stop() }

    // ───────── Idiomas sin internet ─────────
    fun refreshOffline() {
        viewModelScope.launch {
            offlineReady = runCatching { engine.offlineReady() }.getOrDefault(false)
        }
    }

    fun downloadOffline() {
        if (downloading) return
        viewModelScope.launch {
            downloading = true
            downloadMsg = "Descargando idiomas… (puede tardar un par de minutos)"
            try {
                engine.downloadOffline(wifiOnly = false)
                offlineReady = engine.offlineReady()
                downloadMsg = if (offlineReady) "¡Listo! Ya podés traducir sin internet." else "No se pudo confirmar la descarga. Probá de nuevo."
            } catch (e: Exception) {
                downloadMsg = "No se pudo descargar. Conectate a wifi o datos y probá de nuevo."
            } finally {
                downloading = false
            }
        }
    }

    // ───────── Conexión ─────────
    fun hasInternet(): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun useOnlineNow(): Boolean = online && apiKey.isNotBlank() && hasInternet()

    // ───────── Escuchar ─────────
    fun toggleListening(lang: Lang) {
        if (listeningLang == lang) {
            listener.stop()
            return
        }
        startListening(lang)
    }

    private fun startListening(lang: Lang) {
        speaker.stop()
        if (!listener.isAvailable()) {
            notice = lang.t(
                "Este celular no tiene reconocimiento de voz. Instalá o actualizá la app Google.",
                "Speech recognition isn't available. Install or update the Google app."
            )
            return
        }
        listener.destroy()
        partial = ""
        micLevel = 0f
        listeningLang = lang
        listener.start(
            lang = lang,
            preferOffline = !useOnlineNow(),
            onPartial = { partial = it },
            onLevel = { micLevel = it },
            onResult = { text ->
                listeningLang = null
                micLevel = 0f
                partial = ""
                if (text.isNotBlank()) translateAndSpeak(text, lang)
            },
            onError = { code ->
                listeningLang = null
                micLevel = 0f
                partial = ""
                notice = errorText(code, lang)
            },
        )
    }

    fun cancelListening() {
        listener.destroy()
        listeningLang = null
        micLevel = 0f
        partial = ""
    }

    private fun errorText(code: Int, lang: Lang): String? = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            lang.t("No te escuché bien. Tocá el micrófono y probá de nuevo.", "I didn't catch that. Tap the mic and try again.")
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            lang.t(
                "Sin conexión: descargá la voz sin internet de \"${lang.spanishName}\" en Ajustes.",
                "No connection: download offline speech for \"${lang.englishName}\" in Settings."
            )
        12, 13 -> // idioma no disponible sin conexión
            lang.t(
                "Falta el paquete de voz sin internet de ${lang.spanishName}. Ver Ajustes.",
                "Offline speech pack for ${lang.englishName} is missing. See Settings."
            )
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            lang.t("Falta el permiso del micrófono.", "Microphone permission is needed.")
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            lang.t("El micrófono está ocupado, probá de nuevo.", "The mic is busy, try again.")
        else -> null // errores internos sin importancia
    }

    // ───────── Traducir ─────────
    private data class Result(val text: String, val online: Boolean)

    private suspend fun translate(text: String, from: Lang): Result? {
        if (useOnlineNow()) {
            try {
                return Result(engine.translateOnline(text, from, apiKey, model), true)
            } catch (e: Exception) {
                notice = from.t("La IA no respondió; usé la traducción sin internet.", "AI didn't answer; used offline translation.")
            }
        } else if (online && apiKey.isNotBlank()) {
            notice = from.t("Sin internet: traduciendo sin conexión.", "No internet: translating offline.")
        }
        return try {
            if (!offlineReady && hasInternet()) {
                engine.downloadOffline(wifiOnly = false)
                offlineReady = true
            }
            Result(engine.translateOffline(text, from), false)
        } catch (e: Exception) {
            notice = from.t(
                "Falta descargar los idiomas sin internet (Ajustes).",
                "Offline languages aren't downloaded yet (Settings)."
            )
            null
        }
    }

    fun translateAndSpeak(text: String, from: Lang) {
        viewModelScope.launch {
            workingLang = from
            val r = translate(text, from)
            workingLang = null
            if (r != null) {
                turns.add(Turn(from, text, r.text, r.online))
                if (autoSpeak) speaker.speak(r.text, from.other, speechRate)
            }
        }
    }

    fun replay(turn: Turn) = speaker.speak(turn.translated, turn.from.other, speechRate)

    fun speakText(text: String, lang: Lang) = speaker.speak(text, lang, speechRate)

    // ───────── Foto y texto ─────────
    fun translateTyped(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            readBusy = true
            readOriginal = text.trim()
            readTranslated = ""
            val r = translate(readOriginal, readFrom)
            readTranslated = r?.text ?: ""
            readBusy = false
            if (r != null && autoSpeak) speaker.speak(r.text, readFrom.other, speechRate)
        }
    }

    fun translatePhoto(uri: Uri) {
        viewModelScope.launch {
            readBusy = true
            readOriginal = ""
            readTranslated = ""
            try {
                val image = InputImage.fromFilePath(ctx, uri)
                val text = ocr.process(image).await().text.trim()
                if (text.isBlank()) {
                    notice = "No encontré texto en la foto. Probá más cerca y con buena luz."
                } else {
                    readOriginal = text
                    readTranslated = translate(text, readFrom)?.text ?: ""
                }
            } catch (e: Exception) {
                notice = "No pude leer la foto. Probá de nuevo."
            } finally {
                readBusy = false
            }
        }
    }

    override fun onCleared() {
        listener.destroy()
        speaker.shutdown()
        engine.close()
        ocr.close()
    }
}
