package ai.zero

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Tier 2: agent loop (see screen -> think -> act) via Claude tool use. */
class Brain(private val c: Context) {
    private val sys = """You are Zero, a fast voice assistant that controls an Android phone.
Reply in the user's language (Arabic or English), very short.
Use read_screen to see the UI, then act with tools. Finish with done(message). Never do payments or delete data without asking."""

    private fun tool(n: String, d: String, props: JSONObject = JSONObject(), req: JSONArray = JSONArray()) =
        JSONObject().put("name", n).put("description", d)
            .put("input_schema", JSONObject().put("type", "object").put("properties", props).put("required", req))

    private val tools = JSONArray()
        .put(tool("read_screen", "Get current screen elements as indexed text"))
        .put(tool("open_app", "Open app by name", JSONObject().put("name", JSONObject().put("type", "string")), JSONArray().put("name")))
        .put(tool("click", "Click element by index", JSONObject().put("index", JSONObject().put("type", "integer")), JSONArray().put("index")))
        .put(tool("type", "Type text in the focused/editable field", JSONObject().put("text", JSONObject().put("type", "string")), JSONArray().put("text")))
        .put(tool("scroll", "Scroll down or up", JSONObject().put("down", JSONObject().put("type", "boolean")), JSONArray().put("down")))
        .put(tool("back", "Press back"))
        .put(tool("home", "Go home"))
        .put(tool("done", "Finish and speak this message", JSONObject().put("message", JSONObject().put("type", "string")), JSONArray().put("message")))

    /** Blocking — call from a background thread. */
    fun run(task: String): String {
        val key = Prefs.apiKey(c)
        if (key.isBlank()) return "ضيف مفتاح الـ API من إعدادات زيرو"
        val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", task))
        repeat(12) {
            val res = call(key, msgs) ?: return "ما في اتصال"
            val content = res.optJSONArray("content") ?: return "صار خطأ"
            msgs.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            for (i in 0 until content.length()) {
                val b = content.getJSONObject(i)
                if (b.optString("type") != "tool_use") continue
                val inp = b.getJSONObject("input")
                if (b.getString("name") == "done") return inp.optString("message", "تم")
                results.put(JSONObject().put("type", "tool_result")
                    .put("tool_use_id", b.getString("id")).put("content", exec(b.getString("name"), inp)))
            }
            if (results.length() == 0) {
                for (i in 0 until content.length()) {
                    val b = content.getJSONObject(i)
                    if (b.optString("type") == "text") return b.getString("text")
                }
                return "تم"
            }
            msgs.put(JSONObject().put("role", "user").put("content", results))
        }
        return "ما قدرت أكمل المهمة"
    }

    private fun exec(n: String, i: JSONObject): String {
        val a = ZeroAccessibility.inst ?: return "accessibility service not enabled"
        val r = when (n) {
            "read_screen" -> return a.dump()
            "open_app" -> return Router.handle(c, "open " + i.getString("name")) ?: "not found"
            "click" -> a.click(i.getInt("index"))
            "type" -> a.type(i.getString("text"))
            "scroll" -> a.scroll(i.optBoolean("down", true))
            "back" -> a.back()
            "home" -> a.home()
            else -> false
        }
        Thread.sleep(350) // let UI settle
        return if (r) "ok" else "failed"
    }

    private fun call(key: String, msgs: JSONArray): JSONObject? = try {
        val con = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        con.requestMethod = "POST"; con.doOutput = true
        con.setRequestProperty("x-api-key", key)
        con.setRequestProperty("anthropic-version", "2023-06-01")
        con.setRequestProperty("content-type", "application/json")
        val body = JSONObject().put("model", Prefs.model(c)).put("max_tokens", 600)
            .put("system", sys).put("tools", tools).put("messages", msgs)
        con.outputStream.use { it.write(body.toString().toByteArray()) }
        JSONObject((if (con.responseCode < 400) con.inputStream else con.errorStream).bufferedReader().readText())
    } catch (e: Exception) { null }
}
