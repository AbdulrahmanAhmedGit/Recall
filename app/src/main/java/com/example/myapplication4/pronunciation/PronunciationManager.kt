package com.example.myapplication4.pronunciation

import com.example.myapplication4.R

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.myapplication4.domain.SpeechVoice
import com.example.myapplication4.domain.selectSpeechVoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class SpeechStatus { Idle, Preparing, Speaking, Unavailable }

/** One application-context engine per app ViewModel; no review state is accessed here. */
class PronunciationManager(context: Context, private val uiLanguage: () -> String = { "system" }) {
    private val app = context.applicationContext
    private fun ui(id: Int, vararg args: Any): String = com.example.myapplication4.util.RecallLocale.context(app, uiLanguage()).getString(id, *args)
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var pending: Request? = null
    private var utterance: String? = null
    private var generation = 0
    private val mutableStatus = MutableStateFlow(SpeechStatus.Idle)
    val status = mutableStatus.asStateFlow()
    private val mutableMessage = MutableStateFlow<String?>(null)
    val message = mutableMessage.asStateFlow()
    private data class Request(val text: String, val language: String, val rate: Float)

    fun speak(text: String, language: String, rate: Float) {
        if (closed) return
        val request = Request(text, language, rate)
        if (!ready) {
            pending = request
            mutableStatus.value = SpeechStatus.Preparing
            mutableMessage.value = ui(R.string.ui_tts_preparing)
            if (engine == null) initialize()
            return
        }
        play(request)
    }

    private fun initialize() {
        val attempt = ++generation
        main.postDelayed({
            if (!closed && !ready && generation == attempt) {
                initializationFailed(ui(R.string.ui_tts_timeout))
            }
        }, 10_000L)
        try {
            engine = TextToSpeech(app) { status ->
                // Posting also handles engines that invoke onInit before construction returns.
                main.post {
                    if (closed || generation != attempt) return@post
                    if (status != TextToSpeech.SUCCESS) {
                        initializationFailed(ui(R.string.ui_tts_unavailable))
                    } else {
                        ready = true
                        if (pending == null) mutableStatus.value = SpeechStatus.Idle
                        engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(id: String?) = Unit
                            override fun onDone(id: String?) { main.post { if (!closed && id == utterance) mutableStatus.value = SpeechStatus.Idle } }
                            @Deprecated("Required by Android") override fun onError(id: String?) {
                                main.post { if (!closed && id == utterance) unavailable(ui(R.string.ui_tts_play_error)) }
                            }
                        })
                        pending?.let { pending = null; play(it) }
                    }
                }
            }
        } catch (_: Exception) {
            initializationFailed(ui(R.string.ui_tts_device_unavailable))
        }
    }

    private fun initializationFailed(message: String) {
        val requested = pending != null
        generation++
        pending = null
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
        // A cancelled tap must not surface a delayed error on a different screen.
        if (requested) unavailable(message) else mutableStatus.value = SpeechStatus.Idle
    }

    private fun play(request: Request) {
        try {
            val tts = engine ?: return
            tts.stop()
            val voices = tts.voices.orEmpty()
            val selected = selectSpeechVoice(request.language, voices.map {
                SpeechVoice(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired,
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty())
            }, tts.defaultVoice?.name)
            val voice = voices.firstOrNull { it.name == selected?.name }
            if (voice == null || tts.setVoice(voice) == TextToSpeech.ERROR) {
                val language = Locale.forLanguageTag(request.language).getDisplayName(com.example.myapplication4.util.RecallLocale.resolve(uiLanguage()))
                unavailable(ui(R.string.ui_offline_voice_missing, language))
                return
            }
            tts.setSpeechRate(request.rate.coerceIn(.75f, 1.25f))
            utterance = UUID.randomUUID().toString()
            mutableMessage.value = null
            mutableStatus.value = SpeechStatus.Speaking
            if (tts.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, utterance) == TextToSpeech.ERROR) {
                unavailable(ui(R.string.ui_tts_play_error))
            }
        } catch (_: Exception) {
            unavailable(ui(R.string.ui_pronunciation_unavailable))
        }
    }

    private fun unavailable(message: String) { mutableStatus.value = SpeechStatus.Unavailable; mutableMessage.value = message }
    fun dismissMessage() { mutableMessage.value = null }
    fun stop() { pending = null; utterance = null; mutableStatus.value = SpeechStatus.Idle; mutableMessage.value = null; runCatching { engine?.stop() } }
    fun close() { closed = true; generation++; stop(); runCatching { engine?.shutdown() }; engine = null; ready = false }

    fun manageVoices(context: Context) {
        val intents = listOf(Intent("com.android.settings.TTS_SETTINGS"), Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        for (intent in intents) {
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
        mutableMessage.value = ui(R.string.ui_manage_voices_hint)
    }
}
