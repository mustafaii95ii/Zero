package ai.zero

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ZeroAccessibility : AccessibilityService() {

    companion object {
        @Volatile
        var inst: ZeroAccessibility? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        inst = this
        serviceInfo = serviceInfo.apply {
            flags = flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (inst === this) inst = null
        super.onDestroy()
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun quickSettings() = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun lock(): Boolean {
        return if (Build.VERSION.SDK_INT >= 28)
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        else false
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            result.add(node)
            for (i in 0 until node.childCount) {
                walk(node.getChild(i))
            }
        }
        walk(rootInActiveWindow)
        return result
    }

    fun dump(): String {
        val all = nodes()
        if (all.isEmpty()) return "لا أستطيع قراءة الشاشة"

        return all.mapIndexedNotNull { index, node ->
            val label = listOf(
                node.text?.toString(),
                node.contentDescription?.toString()
            ).firstOrNull { !it.isNullOrBlank() }

            if (label.isNullOrBlank()) null
            else "$index: $label"
        }.take(100).joinToString("\n")
            .ifBlank { "لا توجد عناصر نصية ظاهرة" }
    }

    fun click(index: Int): Boolean {
        val node = nodes().getOrNull(index) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun type(text: String): Boolean {
        val node = rootInActiveWindow ?: return false
        val target = findFocused(node) ?: return false

        return target.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
        )
    }

    private fun findFocused(
        node: AccessibilityNodeInfo
    ): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocused(child)
            if (found != null) return found
        }
        return null
    }

    fun scroll(down: Boolean): Boolean {
        val node = rootInActiveWindow ?: return false
        val action = if (down)
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD

        return node.performAction(action)
    }

    fun screenshot(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false

        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    result.hardwareBuffer.close()
                }

                override fun onFailure(errorCode: Int) {}
            }
        )
        return true
    }
}
