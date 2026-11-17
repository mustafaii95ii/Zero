package ai.zero

/** Arabic/English text helpers: normalization + wake word. */
object Text {
    private val tashkeel = Regex("[\\u064B-\\u0652\\u0640]")
    private val punct = Regex("[\\p{Punct}،؟؛]")
    private val spaces = Regex("\\s+")

    private val wake = setOf("زيرو", "zero", "سيرو")
    private val filler = setOf("يا", "hey", "هاي", "hi", "اوكي", "ok", "okay")

    /** Lowercase, drop diacritics/punctuation, unify hamza/ya/ta-marbuta forms. */
    fun norm(s: String): String = s.lowercase()
        .replace(tashkeel, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(punct, " ")
        .replace(spaces, " ")
        .trim()

    /** Returns the command with the wake word removed, or null if no wake word was said. */
    fun stripWake(raw: String): String? {
        val tokens = raw.trim().split(spaces).filter { it.isNotBlank() }
        val idx = tokens.indexOfFirst { norm(it) in wake }
        if (idx < 0) return null
        return tokens
            .filterIndexed { i, tk -> i != idx && norm(tk) !in filler }
            .joinToString(" ")
    }
}
