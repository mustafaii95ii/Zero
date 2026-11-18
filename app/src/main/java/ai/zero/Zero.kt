package ai.zero

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.concurrent.thread

private const val TAG = "ZERO"

object Zero {
    private val main = Handler(Looper.getMainLooper())

    /**
     * onInterim: short "working on it" message (does NOT end the turn).
     * onFinal: the final reply, called exactly once, always on the main thread.
     */
    fun handle(c: Context, text: String, onInterim: (String) -> Unit, onFinal: (String) -> Unit) {
        val local = try {
            Router.handle(c, text)
        } catch (e: Exception) {
            Log.e(TAG, "router failed", e); Say.p(c, "صار خطأ", "Something went wrong, sir.")
        }
        if (local != null) {
            Log.d(TAG, "router: '$text' -> $local")
            main.post { onFinal(local) }
            return
        }

        if (Prefs.apiKey(c).isBlank()) {   // fully local mode: no AI key, no network
            Log.d(TAG, "no local match for '$text' and no API key")
            main.post { onFinal(Say.p(c, "ما فهمت الأمر", "I didn't understand that, sir.")) }
            return
        }

        main.post { onInterim(Say.p(c, "لحظة", "One moment, sir.")) }
        thread {
            val result = try {
                Brain(c).run(text)
            } catch (e: Exception) {
                Log.e(TAG, "brain failed", e); Say.p(c, "صار خطأ", "Something went wrong, sir.")
            }
            Log.d(TAG, "brain: '$text' -> $result")
            main.post { onFinal(result) }
        }
    }
}
