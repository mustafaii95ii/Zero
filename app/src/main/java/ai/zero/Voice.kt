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
        val res = tts?.setLanguage(if (isAr) Locale("ar") else Locale.US)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS language problem ($res) for: $text")
        }
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
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(b: Bundle?) {
                        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        Log.d(TAG, "heard: $t"); android.widget.Toast.makeText(ctx, "سمعت: $t", android.widget.Toast.LENGTH_LONG).show()
                        if (t.isNullOrBlank()) onFail() else onText(t)
                    }
                    override fun onError(e: Int) {
                        Log.d(TAG, "STT error $e (offline=$offline)"); android.widget.Toast.makeText(ctx, "STT error $e", android.widget.Toast.LENGTH_SHORT).show()
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

    fun stop() { main.post { sr?.destroy(); sr = null } }

    fun shutdown() {
        main.removeCallbacks(watchdog)
        onSpeakDone = null
        sr?.destroy(); sr = null
        tts?.stop(); tts?.shutdown(); tts = null
    }
}
