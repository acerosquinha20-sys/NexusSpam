package com.nexus.spam

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.*

class OverlayService : Service() {
    companion object { @Volatile var running = false }

    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var floatView: FrameLayout? = null
    private var dot: View? = null
    private var panel: LinearLayout? = null
    private var statusTv: TextView? = null
    private var listBox: LinearLayout? = null
    private var banner: TextView? = null
    private var currentPkg = ""
    private var lastQuery = 0L
    private val tiktok = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    private val poll = object : Runnable {
        override fun run() { checkForeground(); handler.postDelayed(this, 1500) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("nexus", MODE_PRIVATE)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("nx", "Nexus Spam", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "nx")
            .setContentTitle("Nexus Spam ativo").setSmallIcon(android.R.drawable.ic_menu_edit).build())
        buildFloat()
        buildBanner()
        handler.post(poll)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        floatView?.let { runCatching { wm.removeView(it) } }
        panel?.let { runCatching { wm.removeView(it) } }
        banner?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    private fun params(w: Int, h: Int, focusable: Boolean) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    )

    // ---------- Botão flutuante (imagem da coroa) ----------
    private fun buildFloat() {
        val size = Ui.dp(this, 44)
        val lp = params(size, size, false).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 300 }
        val fl = FrameLayout(this)
        val img = ImageView(this).apply { setImageResource(R.drawable.ic_float) }
        fl.addView(img, FrameLayout.LayoutParams(size, size))
        val d = View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.GRAY); setStroke(2, Color.BLACK) } }
        dot = d
        fl.addView(d, FrameLayout.LayoutParams(Ui.dp(this, 12), Ui.dp(this, 12), Gravity.TOP or Gravity.END))

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        fl.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt(); val dy = (e.rawY - ty).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true
                    if (moved) { lp.x = sx + dx; lp.y = sy + dy; wm.updateViewLayout(fl, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel()
            }
            true
        }
        floatView = fl
        wm.addView(fl, lp)
    }

    // ---------- Painel quadrado preto/vermelho ----------
    private fun togglePanel() {
        if (panel != null) { runCatching { wm.removeView(panel) }; panel = null; return }
        val c = this
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; background = Ui.neon(c, 24)
            setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 16))
        }
        box.addView(TextView(c).apply {
            text = "NEXUS SPAM"; setTextColor(Ui.RED); textSize = 20f; setTypeface(null, Typeface.BOLD)
            setShadowLayer(12f, 0f, 0f, Ui.RED); gravity = Gravity.CENTER
        })
        statusTv = TextView(c).apply { textSize = 13f; gravity = Gravity.CENTER; setPadding(0, 8, 0, 8) }
        box.addView(statusTv)
        val input = EditText(c).apply {
            hint = "Digite o texto..."; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            background = Ui.neon(c, 12, Color.parseColor("#1A0000"), Color.BLACK)
            setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10))
        }
        box.addView(input, LinearLayout.LayoutParams(-1, -2))
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val save = Ui.button(c, "Salvar").apply {
            setOnClickListener {
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    val l = loadList(); l.add(t); saveList(l); input.setText(""); renderList()
                }
            }
        }
        val close = Ui.button(c, "Fechar").apply { setOnClickListener { togglePanel() } }
        row.addView(save, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 12, 6, 12) })
        row.addView(close, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(6, 12, 0, 12) })
        box.addView(row)
        val row2 = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val paste = Ui.button(c, "Colar").apply {
            setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(c)?.toString()
                if (t.isNullOrEmpty()) Toast.makeText(c, "Nada para colar", Toast.LENGTH_SHORT).show()
                else input.append(t)
            }
        }
        val openLive = Ui.button(c, "Abrir Live").apply { setOnClickListener { openTikTok() } }
        row2.addView(paste, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, 6, 12) })
        row2.addView(openLive, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(6, 0, 0, 12) })
        box.addView(row2)
        val sv = ScrollView(c)
        listBox = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(listBox)
        box.addView(sv, LinearLayout.LayoutParams(-1, Ui.dp(c, 160)))
        panel = box
        renderList(); updateStatus()
        val lp = params(Ui.dp(c, 300), WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
            gravity = Gravity.CENTER; softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        wm.addView(box, lp)
    }

    private fun openTikTok() {
        val i = tiktok.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (i == null) { Toast.makeText(this, "TikTok não instalado", Toast.LENGTH_SHORT).show(); return }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        panel?.let { runCatching { wm.removeView(it) } }; panel = null
    }

    // ---------- Overlay extra: faixa verde no topo quando o TikTok está aberto ----------
    private fun buildBanner() {
        val tv = TextView(this).apply {
            text = "● NEXUS • TIKTOK ATIVO"; textSize = 11f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply { cornerRadius = Ui.dp(context, 12).toFloat(); setColor(Color.parseColor("#E600E676")) }
            setPadding(Ui.dp(context, 12), Ui.dp(context, 4), Ui.dp(context, 12), Ui.dp(context, 4))
            visibility = View.GONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = Ui.dp(this@OverlayService, 28) }
        banner = tv
        wm.addView(tv, lp)
    }

    private fun loadList(): MutableList<String> =
        (prefs.getString("items", "") ?: "").split("\u0001").filter { it.isNotEmpty() }.toMutableList()
    private fun saveList(l: List<String>) = prefs.edit().putString("items", l.joinToString("\u0001")).apply()

    private fun renderList() {
        val box = listBox ?: return
        box.removeAllViews()
        loadList().forEachIndexed { idx, t ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            r.addView(TextView(this).apply { text = t; setTextColor(Color.WHITE); maxLines = 2 }, LinearLayout.LayoutParams(0, -2, 1f))
            r.addView(Ui.button(this, "Copiar").apply {
                textSize = 11f
                setOnClickListener {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("nexus", t))
                    Toast.makeText(context, "Copiado", Toast.LENGTH_SHORT).show()
                }
            })
            r.addView(Ui.button(this, "X").apply {
                textSize = 11f
                setOnClickListener { val l = loadList(); if (idx < l.size) { l.removeAt(idx); saveList(l); renderList() } }
            })
            box.addView(r, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 4, 0, 4) })
        }
    }

    // ---------- Detecta TikTok em primeiro plano ----------
    private fun checkForeground() {
        if (!MainActivity.hasUsageAccess(this)) { updateStatus(); return }
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val from = if (lastQuery == 0L) now - 3_600_000 else lastQuery
        val ev = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) currentPkg = e.packageName
        }
        lastQuery = now
        updateStatus()
    }

    private fun updateStatus() {
        val noPerm = !MainActivity.hasUsageAccess(this)
        val on = currentPkg in tiktok
        val color = if (on) Color.parseColor("#00E676") else Color.GRAY
        (dot?.background as? GradientDrawable)?.setColor(color)
        banner?.visibility = if (on) View.VISIBLE else View.GONE
        statusTv?.apply {
            text = when { noPerm -> "Permita o acesso de uso no app"; on -> "TikTok detectado ✔"; else -> "TikTok não detectado" }
            setTextColor(if (noPerm) Ui.RED else color)
        }
    }
}
