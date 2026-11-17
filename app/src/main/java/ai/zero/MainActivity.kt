package ai.zero

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS), 1)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 80, 40, 40) }
        fun btn(t: String, f: () -> Unit) = col.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        col.addView(TextView(this).apply { text = "Zero — زيرو"; textSize = 28f })
        val key = EditText(this).apply { hint = "Anthropic API key"; setText(Prefs.apiKey(context)) }
        val model = EditText(this).apply { hint = "Model"; setText(Prefs.model(context)) }
        col.addView(key); col.addView(model)
        btn("حفظ") { Prefs.save(this, key.text.toString().trim(), model.text.toString().trim()) }
        btn("1) فعّل خدمة إمكانية الوصول") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("2) اجعل زيرو المساعد الافتراضي") { startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
        btn("3) شغّل الاستماع (قل: زيرو ...)") {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS), 1)
                Toast.makeText(this, "اسمح بالميكروفون ثم اضغط مرة ثانية", Toast.LENGTH_LONG).show()
            } else {
                startForegroundService(Intent(this, ZeroListener::class.java))
            }
        }
        btn("اللغة: عربي / English") {
            Prefs.setLang(this, if (Prefs.lang(this).startsWith("ar")) "en-US" else "ar-SA")
            Toast.makeText(this, Prefs.lang(this), Toast.LENGTH_SHORT).show()
        }
        setContentView(ScrollView(this).apply { addView(col, ViewGroup.LayoutParams(-1, -2)) })
    }
}
