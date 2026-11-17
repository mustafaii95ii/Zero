package ai.zero

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.os.Handler
import android.os.Looper
import java.util.Locale

/** Ears and mouth. Arabic + English. Must be used from the main thread. */
class Voice(private val ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init { tts = TextToSpeech(ctx) { ttsReady = it == TextToSpeech.SUCCESS } }

    fun speak(text: String) {
        if (!ttsReady) return
        val isAr = text.any { it in '\u0600'..'\u06FF' }
        tts?.language = if (isAr) Locale("ar") else Locale.US
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "z")
    }

    fun listen(lang: String = Prefs.lang(ctx), onText: (String) -> Unit, onFail: () -> Unit = {}) {
        main.post {
            sr?.destroy()
            sr = SpeechRecognizer.createSpeechRecognizer(ctx).also { r ->
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onResults(b: Bundle?) {
                        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (t.isNullOrBlank()) onFail() else onText(t)
                    }
                    override fun onError(e: Int) = onFail()
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
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                })
            }
        }
    }

    fun stop() { main.post { sr?.destroy(); sr = null } }
}
