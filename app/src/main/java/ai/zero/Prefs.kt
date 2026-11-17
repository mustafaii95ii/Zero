package ai.zero

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("zero", Context.MODE_PRIVATE)
    fun apiKey(c: Context) = p(c).getString("key", "") ?: ""
    fun model(c: Context) = p(c).getString("model", "claude-haiku-5-5") ?: "claude-haiku-5-5"
    fun lang(c: Context) = p(c).getString("lang", "ar-SA") ?: "ar-SA"
    fun save(c: Context, key: String, model: String) =
        p(c).edit().putString("key", key).putString("model", model).apply()
    fun setLang(c: Context, l: String) = p(c).edit().putString("lang", l).apply()
}
