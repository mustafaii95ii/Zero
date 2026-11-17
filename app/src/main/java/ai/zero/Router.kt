package ai.zero

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager

/** Tier 1: instant local commands (no network, ~0ms). Returns reply or null -> falls to Brain. */
object Router {
    private var torch = false

    fun handle(c: Context, raw: String): String? {
        val t = raw.trim().lowercase()
        val a = ZeroAccessibility.inst
        fun has(vararg k: String) = k.any { t.contains(it) }

        when {
            has("رجوع", "ارجع", "go back") -> { a?.back(); return "تم" }
            has("الرئيسية", "go home") -> { a?.home(); return "تم" }
            has("التطبيقات الأخيرة", "recent apps") -> { a?.recents(); return "تم" }
            has("الإشعارات", "الاشعارات", "notifications") -> { a?.notifications(); return "تم" }
            has("الإعدادات السريعة", "quick settings") -> { a?.quickSettings(); return "تم" }
            has("قفل الشاشة", "lock screen") -> { a?.lock(); return "تم" }
            has("لقطة شاشة", "screenshot") -> { a?.screenshot(); return "تم" }
            has("شغل الفلاش", "افتح الفلاش", "flashlight on") -> return flash(c, true)
            has("اطفي الفلاش", "اطفئ الفلاش", "flashlight off") -> return flash(c, false)
            has("ارفع الصوت", "volume up") -> { vol(c, true); return "تم" }
            has("اخفض الصوت", "volume down") -> { vol(c, false); return "تم" }
        }
        val m = Regex("^(?:افتح|شغل|open|launch)\\s+(.+)$").find(t)
        if (m != null) return openApp(c, m.groupValues[1].trim())
        return null
    }

    private fun openApp(c: Context, name: String): String {
        val pm = c.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == name }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
            ?: return "ما لقيت تطبيق $name"
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "ما قدرت أفتحه"
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "فتحت ${hit.loadLabel(pm)}"
    }

    private fun flash(c: Context, on: Boolean): String {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.setTorchMode(cm.cameraIdList[0], on); torch = on; return "تم"
    }

    private fun vol(c: Context, up: Boolean) {
        (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI)
    }
}
