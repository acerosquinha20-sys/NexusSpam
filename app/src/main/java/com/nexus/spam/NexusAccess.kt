package com.nexus.spam

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Cola o texto no chat do TikTok. O ply3 também abre o chat e envia — uma mensagem por toque. */
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


    private val ui = Handler(Looper.getMainLooper())
    @Volatile private var busy = false

    private val chatHints = listOf("diga algo", "adicionar comentário", "comentar", "say something", "add comment", "comment", "chat")
    private val sendHints = listOf("enviar", "send", "publicar", "post")

    private fun roots(): List<AccessibilityNodeInfo> {
        val l = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { l.add(it) }
        for (w in windows) w.root?.let { l.add(it) }
        return l
    }

    private fun walk(n: AccessibilityNodeInfo?, pred: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (n == null) return null
        if (pred(n)) return n
        for (i in 0 until n.childCount) walk(n.getChild(i), pred)?.let { return it }
        return null
    }

    private fun label(n: AccessibilityNodeInfo) =
        ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "") + " " + (n.hintText?.toString() ?: "")).lowercase()

    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        while (c != null) {
            if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            c = c.parent
        }
        return false
    }

    /** ply3: abre o chat (se fechado), cola o texto salvo e envia. Retorna false se não achou o chat. */
    fun pasteAndSend(text: String, done: (Boolean) -> Unit) {
        if (busy) { done(true); return }
        busy = true
        fun finish(ok: Boolean) { busy = false; done(ok) }

        fun sendStep() {
            // 1) botão de enviar
            var btn: AccessibilityNodeInfo? = null
            for (r in roots()) {
                btn = walk(r) { n -> n.isVisibleToUser && (n.isClickable || n.parent?.isClickable == true) &&
                    (n.viewIdResourceName?.lowercase()?.contains("send") == true ||
                     sendHints.any { h -> label(n).trim() == h || label(n).trim().startsWith("$h ") }) }
                if (btn != null) break
            }
            val sent = btn?.let { clickNode(it) } ?: false
            if (sent) { finish(true); return }
            // 2) fallback: Enter do teclado (Android 11+)
            val inp = findInput()
            if (inp != null && Build.VERSION.SDK_INT >= 30 &&
                inp.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) { finish(true); return }
            finish(false)
        }

        fun pasteStep() {
            if (!pasteText(text)) { finish(false); return }
            ui.postDelayed({ sendStep() }, 350)
        }

        if (findInput() != null) { pasteStep(); return }
        // chat fechado: tenta abrir tocando na caixa "Diga algo..."
        var opener: AccessibilityNodeInfo? = null
        for (r in roots()) {
            opener = walk(r) { n -> n.isVisibleToUser && (n.isEditable || chatHints.any { h -> label(n).contains(h) }) }
            if (opener != null) break
        }
        if (opener == null) { finish(false); return }
        opener.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        clickNode(opener)
        ui.postDelayed({
            if (findInput() != null) pasteStep() else finish(false)
        }, 600)
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
