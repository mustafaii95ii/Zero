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
            Log.e(TAG, "router failed", e); "صار خطأ"
        }
        if (local != null) {
            Log.d(TAG, "router: '$text' -> $local")
            main.post { onFinal(local) }
            return
        }

        main.post { onInterim("لحظة") }
        thread {
            val result = try {
                Brain(c).run(text)
            } catch (e: Exception) {
                Log.e(TAG, "brain failed", e); "صار خطأ"
            }
            Log.d(TAG, "brain: '$text' -> $result")
            main.post { onFinal(result) }
        }
    }
}
