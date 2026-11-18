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
    fun voiceName(c: Context, code: String) = p(c).getString("voice_$code", "") ?: ""
    fun setVoiceName(c: Context, code: String, name: String) =
        p(c).edit().putString("voice_$code", name).apply()
    fun pitch(c: Context) = p(c).getFloat("pitch", 0.85f)
    fun rate(c: Context) = p(c).getFloat("rate", 0.95f)
    fun setTuning(c: Context, pitch: Float, rate: Float) =
        p(c).edit().putFloat("pitch", pitch).putFloat("rate", rate).apply()
}
