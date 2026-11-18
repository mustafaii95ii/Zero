package ai.zero

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

private const val TAG = "ZERO"

/**
 * Offline wake-word detector (Vosk, free). Listens continuously and quietly for the single
 * word "zero"; nothing leaves the phone. When heard it releases the microphone and calls onWake.
 */
class WakeWord(private val ctx: Context, private val onWake: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    @Volatile private var triggered = false

    /** Copies the bundled model to internal storage (first run only) and loads it. */
    fun load(onReady: () -> Unit, onError: (String) -> Unit) {
        if (model != null) { onReady(); return }
        StorageService.unpack(ctx, "model", "model",
            { m -> main.post { model = m; Log.d(TAG, "wake-word model loaded"); onReady() } },
            { e -> main.post { Log.e(TAG, "wake-word model failed", e); onError(e.message ?: "unknown") } })
    }

    @Synchronized
    fun start(): Boolean {
        if (service != null) return true
        val m = model ?: return false
        return try {
            triggered = false
            val rec = Recognizer(m, 16000f, "[\"zero\", \"[unk]\"]")
            val s = SpeechService(rec, 16000f)
            recognizer = rec
            service = s
            s.startListening(object : RecognitionListener {
                override fun onPartialResult(h: String?) = inspect(h)
                override fun onResult(h: String?) = inspect(h)
                override fun onFinalResult(h: String?) {}
                override fun onError(e: Exception?) { Log.e(TAG, "wake-word error", e) }
                override fun onTimeout() {}
            })
            true
        } catch (e: Exception) {
            Log.e(TAG, "wake-word start failed", e)
            stop()
            false
        }
    }

    private fun inspect(h: String?) {
        if (h.isNullOrBlank() || triggered) return
        if (h.contains("zero")) {
            triggered = true
            Log.d(TAG, "wake hypothesis: $h")
            // stop() joins the recognizer thread, so never call it from that thread.
            main.post { stop(); onWake() }
        }
    }

    /** Releases the microphone so the system speech recognizer can use it. */
    @Synchronized
    fun stop() {
        try { service?.stop(); service?.shutdown() } catch (e: Exception) { Log.w(TAG, "stop failed", e) }
        service = null
        try { recognizer?.close() } catch (e: Exception) { Log.w(TAG, "close failed", e) }
        recognizer = null
    }

    fun release() {
        stop()
        try { model?.close() } catch (e: Exception) { Log.w(TAG, "model close failed", e) }
        model = null
    }
}
