package ai.zero

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Android requires every voice-interaction (assistant) app to also declare a recognition service,
 * otherwise it is not offered in Settings > Digital assistant. Zero does its own recognition,
 * so this stub just reports an error if anyone ever selects it as the system recognizer.
 */
class ZeroRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
