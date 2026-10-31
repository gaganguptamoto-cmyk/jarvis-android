package com.jarvis.assistant.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun go(action: Int): Boolean = performGlobalAction(action)

    // Topmost app window that is not JARVIS itself (overlay panel can become "active").
    fun targetRoot(): AccessibilityNodeInfo? {
        val own = packageName
        for (w in windows) {
            if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val r = w.root ?: continue
            if (r.packageName?.toString() == own) continue
            return r
        }
        return rootInActiveWindow?.takeIf { it.packageName?.toString() != own }
    }

    fun hasTarget(): Boolean = targetRoot() != null

    fun readScreen(maxChars: Int = 500): String {
        val root = targetRoot() ?: return ""
        val out = StringBuilder()
        collect(root, out, maxChars)
        return out.toString().trim()
    }

    private fun collect(node: AccessibilityNodeInfo, out: StringBuilder, max: Int) {
        if (out.length >= max) return
        if (node.isVisibleToUser && !node.isPassword) {
            val text = node.text?.toString()?.trim().orEmpty()
            val t = text.ifEmpty { node.contentDescription?.toString()?.trim().orEmpty() }
            if (t.isNotEmpty()) out.append(t).append(". ")
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            collect(c, out, max)
        }
    }

    fun findByText(label: String): AccessibilityNodeInfo? {
        val root = targetRoot() ?: return null
        return root.findAccessibilityNodeInfosByText(label)
            .firstOrNull { it.isVisibleToUser && !it.isPassword }
    }

    fun labelOf(node: AccessibilityNodeInfo): String =
        node.text?.toString()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString().orEmpty()

    fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 6) {
            if (n.isClickable && n.isEnabled) {
                return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            n = n.parent
            depth++
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val root = targetRoot() ?: return false
        val field = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (field.isPassword) return false
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text,
        )
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun hasInputFocus(): Boolean {
        val root = targetRoot() ?: return false
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) != null
    }

    fun scroll(forward: Boolean): Boolean {
        val root = targetRoot() ?: return false
        val n = findScrollable(root) ?: return false
        return n.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
        )
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findScrollable(c)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile
        var instance: JarvisAccessibilityService? = null
    }
}
