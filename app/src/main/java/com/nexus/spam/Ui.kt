package com.nexus.spam

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.Button

object Ui {
    val RED = Color.parseColor("#FF1A1A")
    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    /** Caixa quadrada 3D: brilho neon externo + degradê preto/vinho + borda vermelha, pontas arredondadas */
    fun neon(c: Context, radiusDp: Int, top: Int = Color.parseColor("#3A0000"), bottom: Int = Color.BLACK): LayerDrawable {
        val r = dp(c, radiusDp).toFloat()
        val glow = GradientDrawable().apply {
            cornerRadius = r + dp(c, 3); setColor(Color.TRANSPARENT)
            setStroke(dp(c, 5), Color.parseColor("#55FF1A1A"))
        }
        val body = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = r; setStroke(dp(c, 2), RED)
        }
        return LayerDrawable(arrayOf(glow, body)).apply {
            val i = dp(c, 3); setLayerInset(1, i, i, i, i)
        }
    }

    fun button(c: Context, text: String): Button = Button(c).apply {
        this.text = text; setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        background = neon(c, 14, Color.parseColor("#B00000"), Color.parseColor("#2A0000"))
        isAllCaps = true; gravity = Gravity.CENTER; setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6))
    }
}
