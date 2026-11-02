package com.nexus.spam

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Só cola o texto no campo de digitação focado do TikTok, uma vez por toque. Não envia nada. */
class NexusAccess : AccessibilityService() {
    companion object { @Volatile var inst: NexusAccess? = null }

    override fun onServiceConnected() { inst = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean { inst = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun findInput(): AccessibilityNodeInfo? {
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if (it.isEditable) return it }
        for (w in windows) {
            val n = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (n != null && n.isEditable) return n
        }
        return null
    }

    fun pasteText(text: String): Boolean {
        val node = findInput() ?: return false
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("nexus", text))
        if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return true
        val old = if (node.isShowingHintText) "" else node.text?.toString() ?: ""
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, old + text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
