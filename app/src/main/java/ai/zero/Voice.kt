package ai.zero

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

private const val TAG = "ZERO"

/** Ears and mouth. Arabic + English. Must be used from the main thread. */
class Voice(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var onSpeakDone: (() -> Unit)? = null
    private val watchdog = Runnable { fireDone() }

    init {
        tts = TextToSpeech(ctx) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { main.post { fireDone() } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { main.post { fireDone() } }
                })
            } else {
                Log.e(TAG, "TTS init failed: $status")
            }
        }
    }

    private fun fireDone() {
        main.removeCallbacks(watchdog)
        val cb = onSpeakDone
        onSpeakDone = null
        cb?.invoke()
    }

    /** [done] runs after speech ends (or after a safety timeout), so listening never stalls. */
    fun speak(text: String, done: (() -> Unit)? = null) {
        if (!ttsReady) {
            Log.w(TAG, "TTS not ready, skipping: $text")
            if (done != null) main.post { done() }
            return
        }
        val isAr = text.any { it in '\u0600'..'\u06FF' }
        val code = if (isAr) "ar" else "en"
        val saved = Prefs.voiceName(ctx, code)
        val chosen = if (saved.isNotEmpty()) tts?.voices?.firstOrNull { it.name == saved } else null
        if (chosen != null) {
            tts?.setVoice(chosen)
        } else {
            val res = tts?.setLanguage(if (isAr) Locale("ar") else Locale.UK)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language problem ($res) for: $text")
            }
        }
        tts?.setPitch(Prefs.pitch(ctx))
        tts?.setSpeechRate(Prefs.rate(ctx))
        onSpeakDone = done
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, 4000L + text.length * 100L)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "z")
    }

    fun listen(
        lang: String = Prefs.lang(ctx),
        offline: Boolean = true,
        onText: (String) -> Unit,
        onFail: () -> Unit = {}
    ) {
        main.post {
            if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                Log.e(TAG, "no speech recognition service on this phone")
                onFail()
                return@post
            }
            sr?.destroy()
            sr = SpeechRecognizer.createSpeechRecognizer(ctx).also { r ->
                fun release() { main.post { if (sr === r) { r.destroy(); sr = null } } }
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(b: Bundle?) {
                        release()
                        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        Log.d(TAG, "heard: $t"); android.widget.Toast.makeText(ctx, "سمعت: $t", android.widget.Toast.LENGTH_LONG).show()
                        if (t.isNullOrBlank()) onFail() else onText(t)
                    }
                    override fun onError(e: Int) {
                        release()
                        Log.d(TAG, "STT error $e (offline=$offline)")
                        val noLang = e == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                            e == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
                        if (offline && noLang) listen(lang, false, onText, onFail) else onFail()
                    }
                    override fun onReadyForSpeech(p: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(v: Float) {}
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(b: Bundle?) {}
                    override fun onEvent(t: Int, b: Bundle?) {}
                })
                r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
                })
            }
        }
    }

    /** Cycles to the next installed voice for a language ("en" or "ar"), saves it, plays a sample. */
    fun nextVoice(code: String, report: (String) -> Unit) {
        if (!ttsReady) { report("الصوت غير جاهز، جرب بعد ثانية"); return }
        val all = (tts?.voices ?: emptySet<android.speech.tts.Voice>())
            .filter { it.locale.language == code && !it.features.contains("notInstalled") }
            .sortedWith(compareBy<android.speech.tts.Voice>({ it.locale.toLanguageTag() != "en-GB" }, { it.name }))
        if (all.isEmpty()) { report("ما في أصوات مثبّتة لهذه اللغة"); return }
        val idx = all.indexOfFirst { it.name == Prefs.voiceName(ctx, code) }
        val pos = (idx + 1) % all.size
        val next = all[pos]
        Prefs.setVoiceName(ctx, code, next.name)
        report("${next.name} (${next.locale.toLanguageTag()}) ${pos + 1}/${all.size}")
        speak(if (code == "ar") "تحت أمرك" else "At your service.")
    }

    /** Cycles pitch/speed presets, plays a sample. */
    fun nextTuning(report: (String) -> Unit) {
        val presets = listOf(
            Triple("عميق وهادئ", 0.75f, 0.9f),
            Triple("جارفس", 0.85f, 0.95f),
            Triple("عادي", 1.0f, 1.0f),
            Triple("حاد", 1.15f, 1.05f)
        )
        val cur = presets.indexOfFirst { it.second == Prefs.pitch(ctx) }
        val n = presets[(cur + 1) % presets.size]
        Prefs.setTuning(ctx, n.second, n.third)
        report(n.first)
        speak("At your service.")
    }

    fun stop() { main.post { sr?.destroy(); sr = null } }

    fun shutdown() {
        main.removeCallbacks(watchdog)
        onSpeakDone = null
        sr?.destroy(); sr = null
        tts?.stop(); tts?.shutdown(); tts = null
    }
}
