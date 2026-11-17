package ai.zero

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

private const val TAG = "ZERO"

class ZeroAccessibility : AccessibilityService() {

    companion object {
        @Volatile
        var inst: ZeroAccessibility? = null
            private set
    }

    /** Nodes as numbered by the last dump(); click(index) refers to THIS list. */
    @Volatile
    private var snapshot: List<AccessibilityNodeInfo> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        inst = this
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        Log.d(TAG, "accessibility connected")
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
    fun lock() = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    fun screenshot() = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    /** Old indexes are meaningless once the screen changes. */
    fun invalidate() { snapshot = emptyList() }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            result.add(node)
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(rootInActiveWindow)
        return result
    }

    fun dump(): String {
        val all = nodes()
        if (all.isEmpty()) return "لا أستطيع قراءة الشاشة"
        snapshot = all
        val pkg = rootInActiveWindow?.packageName

        val lines = all.mapIndexedNotNull { index, n ->
            if (!n.isVisibleToUser) return@mapIndexedNotNull null
            val label = listOf(
                n.text?.toString(),
                n.contentDescription?.toString(),
                n.hintText?.toString()
            ).firstOrNull { !it.isNullOrBlank() }
            val tags = buildString {
                if (n.isEditable) append("[edit]")
                if (n.isClickable) append("[click]")
                if (n.isScrollable) append("[scroll]")
            }
            if (label.isNullOrBlank() && !n.isEditable) null
            else "$index$tags: ${label?.take(80) ?: ""}"
        }.take(100)

        if (lines.isEmpty()) return "لا توجد عناصر نصية ظاهرة"
        return "app: $pkg\n" + lines.joinToString("\n")
    }

    fun click(index: Int): Boolean {
        val node = snapshot.getOrNull(index)
        if (node == null) {
            Log.w(TAG, "click($index): no snapshot / bad index")
            return false
        }
        node.refresh()
        // Labels are usually on non-clickable children: climb to a clickable parent.
        var target: AccessibilityNodeInfo? = node
        var hops = 0
        while (target != null && !target.isClickable && hops < 6) {
            target = target.parent
            hops++
        }
        if (target != null && target.isClickable &&
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        // Fallback: real tap at the element's center.
        return tap(node)
    }

    private fun tap(node: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        node.getBoundsInScreen(r)
        if (r.width() <= 0 || r.height() <= 0) return false
        val path = Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun type(text: String): Boolean {
        val all = nodes()
        val target = all.firstOrNull { it.isEditable && it.isFocused }
            ?: all.firstOrNull { it.isEditable && it.isVisibleToUser }
            ?: return false
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(down: Boolean): Boolean {
        val node = nodes().firstOrNull { it.isScrollable && it.isVisibleToUser } ?: return false
        return node.performAction(
            if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }
}
