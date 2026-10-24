package com.autoalert.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.roundToInt

// Message en haut de l'écran, même style que « Code invalide » (vert = succès, rouge = erreur)
object TopBanner {

    private const val TAG = "top_banner"
    private val handler = Handler(Looper.getMainLooper())

    fun show(a: Activity, message: String, success: Boolean) {
        a.runOnUiThread {
            fun dp(v: Int) = (v * a.resources.displayMetrics.density).roundToInt()

            val content = a.findViewById<ViewGroup>(android.R.id.content) ?: return@runOnUiThread

            // Sur Android 15+, le contenu passe sous la barre d'état : on descend la bannière
            var top = dp(12)
            if (Build.VERSION.SDK_INT >= 35) {
                val bars = a.window.decorView.rootWindowInsets
                    ?.getInsets(android.view.WindowInsets.Type.statusBars())
                top += bars?.top ?: 0
            }

            var tv = content.findViewWithTag<TextView>(TAG)
            if (tv == null) {
                tv = TextView(a).apply {
                    tag = TAG
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(18), dp(16), dp(18), dp(16))
                    elevation = dp(16).toFloat()
                }
                content.addView(
                    tv,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP
                    ).apply { setMargins(dp(16), top, dp(16), 0) }
                )
            }

            tv.text = message
            tv.background = GradientDrawable().apply {
                setColor(if (success) Color.rgb(25, 145, 80) else Color.rgb(200, 50, 60))
                cornerRadius = dp(14).toFloat()
            }
            tv.visibility = View.VISIBLE
            tv.bringToFront()

            val view = tv
            handler.removeCallbacksAndMessages(view)
            handler.postAtTime({ view.visibility = View.GONE }, view, SystemClock.uptimeMillis() + 3000L)
        }
    }
}
