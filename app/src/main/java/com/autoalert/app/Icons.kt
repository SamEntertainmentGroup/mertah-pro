package com.autoalert.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.math.roundToInt

// Petites icônes dessinées en code (traits fins, style moderne), colorables et nettes à toute taille
object Icons {

    enum class Kind { TARGET, NAV, WAVES, BATTERY, ALERT, PIN, STOP }

    fun drawable(ctx: Context, kind: Kind, color: Int, sizeDp: Int = 22, level: Int = 100): Drawable =
        IconDrawable(kind, color, (sizeDp * ctx.resources.displayMetrics.density).roundToInt(), level)

    private class IconDrawable(
        val kind: Kind,
        color: Int,
        val size: Int,
        val batteryLevel: Int
    ) : Drawable() {

        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size

        override fun draw(c: Canvas) {
            val b = bounds
            val s = min(b.width(), b.height()) / 24f
            c.save()
            c.translate(b.left.toFloat(), b.top.toFloat())
            c.scale(s, s)

            when (kind) {
                Kind.TARGET -> {
                    c.drawCircle(12f, 12f, 7.5f, stroke)
                    c.drawCircle(12f, 12f, 2.5f, fill)
                    c.drawLine(12f, 1.5f, 12f, 5f, stroke)
                    c.drawLine(12f, 19f, 12f, 22.5f, stroke)
                    c.drawLine(1.5f, 12f, 5f, 12f, stroke)
                    c.drawLine(19f, 12f, 22.5f, 12f, stroke)
                }
                Kind.NAV -> {
                    val p = Path().apply {
                        moveTo(12f, 2f)
                        lineTo(20f, 21f)
                        lineTo(12f, 16.5f)
                        lineTo(4f, 21f)
                        close()
                    }
                    c.drawPath(p, fill)
                }
                Kind.WAVES -> {
                    c.drawCircle(12f, 19f, 2f, fill)
                    for (r in floatArrayOf(6f, 10f, 14f)) {
                        c.drawArc(RectF(12f - r, 19f - r, 12f + r, 19f + r), 225f, 90f, false, stroke)
                    }
                }
                Kind.BATTERY -> {
                    c.drawRoundRect(RectF(2f, 7f, 19f, 17f), 2.5f, 2.5f, stroke)
                    c.drawRect(RectF(20.5f, 10f, 22.5f, 14f), fill)
                    val w = 11f * batteryLevel.coerceIn(0, 100) / 100f
                    if (w > 0.5f) c.drawRoundRect(RectF(5f, 9.5f, 5f + w, 14.5f), 1f, 1f, fill)
                }
                Kind.ALERT -> {
                    val p = Path().apply {
                        moveTo(12f, 3f)
                        lineTo(22f, 20f)
                        lineTo(2f, 20f)
                        close()
                    }
                    c.drawPath(p, stroke)
                    c.drawLine(12f, 9f, 12f, 14f, stroke)
                    c.drawCircle(12f, 17f, 1.2f, fill)
                }
                Kind.PIN -> {
                    val p = Path().apply {
                        moveTo(12f, 22f)
                        lineTo(8.27f, 15.32f)
                        arcTo(RectF(5.5f, 3.5f, 18.5f, 16.5f), 125f, 290f)
                        close()
                    }
                    c.drawPath(p, stroke)
                    c.drawCircle(12f, 10f, 2.2f, fill)
                }
                Kind.STOP -> {
                    c.drawRoundRect(RectF(5f, 5f, 19f, 19f), 3f, 3f, fill)
                }
            }
            c.restore()
        }

        override fun setAlpha(alpha: Int) {
            stroke.alpha = alpha
            fill.alpha = alpha
        }

        override fun setColorFilter(cf: ColorFilter?) {
            stroke.colorFilter = cf
            fill.colorFilter = cf
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
