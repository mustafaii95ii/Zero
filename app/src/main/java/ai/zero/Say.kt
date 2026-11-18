package ai.zero

import android.content.Context

/** Picks the Arabic or English version of a reply (English = Jarvis style, with "sir"). */
object Say {
    fun p(c: Context, ar: String, en: String): String = if (Prefs.replyEn(c)) en else ar
}
