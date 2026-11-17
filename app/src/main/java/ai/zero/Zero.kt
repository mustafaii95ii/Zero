package ai.zero

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlin.concurrent.thread

object Zero {
    private val main = Handler(Looper.getMainLooper())

    fun handle(c: Context, text: String, say: (String) -> Unit) {
        Router.handle(c, text)?.let {
            main.post { say(it) }
            return
        }

        main.post { say("لحظة") }

        thread {
            val result = Brain(c).run(text)
            main.post { say(result) }
        }
    }
}
