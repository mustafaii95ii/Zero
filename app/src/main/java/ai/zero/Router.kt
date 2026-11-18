package ai.zero

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.util.Log

private const val TAG = "ZERO"

/** Tier 1: instant local commands (no network). Returns reply or null -> falls to Brain. */
object Router {

    private val aliases = mapOf(
        "واتساب" to "whatsapp", "واتس اب" to "whatsapp", "واتس" to "whatsapp",
        "يوتيوب" to "youtube", "كروم" to "chrome", "تيك توك" to "tiktok",
        "انستغرام" to "instagram", "انستقرام" to "instagram", "انستا" to "instagram",
        "فيسبوك" to "facebook", "فيس بوك" to "facebook",
        "تلغرام" to "telegram", "تيليجرام" to "telegram", "تليجرام" to "telegram",
        "سناب شات" to "snapchat", "سناب" to "snapchat", "ماسنجر" to "messenger",
        "الكاميرا" to "camera", "كاميرا" to "camera",
        "الاعدادات" to "settings", "اعدادات" to "settings",
        "الصور" to "photos", "المعرض" to "gallery",
        "الساعة" to "clock", "المنبه" to "clock", "الحاسبة" to "calculator",
        "الخرائط" to "maps", "خرائط" to "maps",
        "جيميل" to "gmail", "البريد" to "gmail",
        "الهاتف" to "phone", "الاتصال" to "phone",
        "الرسائل" to "messages", "جهات الاتصال" to "contacts",
        "المتجر" to "play store", "بلاي ستور" to "play store",
        "سبوتيفاي" to "spotify", "نتفلكس" to "netflix", "تويتر" to "twitter"
    )
    private val aliasMap by lazy { aliases.mapKeys { Text.norm(it.key) } }

    fun handle(c: Context, raw: String): String? {
        val t = Text.norm(raw)
        if (t.isBlank()) return null
        val a = ZeroAccessibility.inst
        // Global commands only when the sentence is short; longer sentences go to Brain.
        val short = t.split(" ").size <= 4
        fun has(vararg k: String) = short && k.any { t.contains(Text.norm(it)) }
        fun act(f: (ZeroAccessibility) -> Boolean): String =
            if (a == null) Say.p(c, "فعّل خدمة إمكانية الوصول أولاً", "Please enable the accessibility service, sir.")
            else if (f(a)) Say.p(c, "تم", "Done, sir.")
            else Say.p(c, "ما قدرت أنفذ الأمر", "I couldn't do that, sir.")

        when {
            has("رجوع", "ارجع", "go back") -> return act { it.back() }
            has("الرئيسية", "go home") -> return act { it.home() }
            has("التطبيقات الأخيرة", "recent apps") -> return act { it.recents() }
            has("الإشعارات", "الاشعارات", "notifications") -> return act { it.notifications() }
            has("الإعدادات السريعة", "quick settings") -> return act { it.quickSettings() }
            has("قفل الشاشة", "lock screen") -> return act { it.lock() }
            has("لقطة شاشة", "screenshot") -> return act { it.screenshot() }
            has("شغل الفلاش", "افتح الفلاش", "flashlight on") -> return flash(c, true)
            has("اطفي الفلاش", "اطفئ الفلاش", "flashlight off") -> return flash(c, false)
            has("ارفع الصوت", "volume up") -> { vol(c, true); return Say.p(c, "تم", "Done, sir.") }
            has("اخفض الصوت", "volume down") -> { vol(c, false); return Say.p(c, "تم", "Done, sir.") }
        }
        val m = Regex("^(?:افتح|شغل|open|launch)\\s+(.+)$").find(t)
        if (m != null) return openApp(c, m.groupValues[1].trim())
        return null
    }

    private fun openApp(c: Context, spoken: String): String {
        val name = spoken.removePrefix("تطبيق ").trim()
        val bare = name.removePrefix("ال")
        val cands = linkedSetOf(name, bare)
        aliasMap[name]?.let { cands.add(it) }
        aliasMap[bare]?.let { cands.add(it) }

        val pm = c.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val labeled = apps.map { it to Text.norm(it.loadLabel(pm).toString()) }
        val hit = labeled.firstOrNull { it.second in cands }
            ?: labeled.firstOrNull { (_, l) -> cands.any { it.length >= 3 && l.contains(it) } }
            ?: return Say.p(c, "ما لقيت تطبيق $spoken", "I couldn't find $spoken, sir.")

        val pkg = hit.first.activityInfo.packageName
        val i = pm.getLaunchIntentForPackage(pkg) ?: return Say.p(c, "ما قدرت أفتحه", "I couldn't open it, sir.")
        return try {
            c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Log.d(TAG, "opened $pkg for '$spoken'")
            Say.p(c, "فتحت ${hit.first.loadLabel(pm)}", "Opening ${hit.first.loadLabel(pm)}, sir.")
        } catch (e: Exception) {
            Log.e(TAG, "launch failed for $pkg", e)
            Say.p(c, "ما قدرت أفتح التطبيق", "I couldn't launch the app, sir.")
        }
    }

    private fun flash(c: Context, on: Boolean): String = try {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            val ch = cm.getCameraCharacteristics(it)
            ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return Say.p(c, "ما لقيت فلاش", "No flashlight found, sir.")
        cm.setTorchMode(id, on)
        Say.p(c, "تم", "Done, sir.")
    } catch (e: Exception) {
        Log.e(TAG, "flash failed", e)
        Say.p(c, "ما قدرت أشغل الفلاش", "I couldn't toggle the flashlight, sir.")
    }

    private fun vol(c: Context, up: Boolean) {
        (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI)
    }
}
