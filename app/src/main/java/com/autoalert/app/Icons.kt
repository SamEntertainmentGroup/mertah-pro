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

    enum class Kind { TARGET, NAV, WAVES, BATTERY, ALERT, PIN, STOP, CAR, PHONE, LOCK_CLOSED, LOCK_OPEN, SHARE, CHAT }

    fun drawable(ctx: Context, kind: Kind, color: Int, sizeDp: Int = 22, level: Int = 100): Drawable =
        IconDrawable(kind, color, (sizeDp * ctx.resources.displayMetrics.density).roundToInt(), level)

    // Texte avec une icône dessinée juste devant (un seul espace, pas de trou)
    fun withIcon(ctx: Context, label: String, kind: Kind, color: Int, sizeDp: Int): CharSequence {
        val d = drawable(ctx, kind, color, sizeDp)
        d.setBounds(0, 0, d.intrinsicWidth, d.intrinsicHeight)
        val s = android.text.SpannableString("  $label")
        s.setSpan(
            CenteredImageSpan(d),
            0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return s
    }

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
                Kind.CAR -> {
                    // Toit avec deux vitres évidées
                    val roof = Path().apply {
                        fillType = Path.FillType.EVEN_ODD
                        moveTo(5f, 11f); lineTo(7.5f, 6f); lineTo(16.5f, 6f); lineTo(19f, 11f); close()
                        moveTo(7.6f, 10f); lineTo(9f, 7.4f); lineTo(11.4f, 7.4f); lineTo(11.4f, 10f); close()
                        moveTo(12.6f, 10f); lineTo(12.6f, 7.4f); lineTo(15f, 7.4f); lineTo(16.4f, 10f); close()
                    }
                    c.drawPath(roof, fill)
                    c.drawRoundRect(RectF(2f, 11f, 22f, 18f), 2.5f, 2.5f, fill)
                    c.drawCircle(7.2f, 18.2f, 2.6f, fill)
                    c.drawCircle(16.8f, 18.2f, 2.6f, fill)
                }
                Kind.PHONE -> {
                    val p = Path().apply {
                        fillType = Path.FillType.EVEN_ODD
                        addRoundRect(RectF(6.5f, 2f, 17.5f, 22f), 2.8f, 2.8f, Path.Direction.CW)
                        addRect(RectF(8.4f, 5f, 15.6f, 17.8f), Path.Direction.CW)
                        addCircle(12f, 20f, 0.9f, Path.Direction.CW)
                    }
                    c.drawPath(p, fill)
                }
                Kind.SHARE -> {
                    val sw = stroke.strokeWidth
                    stroke.strokeWidth = 1.8f
                    c.drawLine(8.4f, 10.7f, 15.6f, 6.3f, stroke)
                    c.drawLine(8.4f, 13.3f, 15.6f, 17.7f, stroke)
                    stroke.strokeWidth = sw
                    c.drawCircle(18f, 5.5f, 3f, fill)
                    c.drawCircle(6f, 12f, 3f, fill)
                    c.drawCircle(18f, 18.5f, 3f, fill)
                }
                Kind.CHAT -> {
                    val bubble = Path().apply {
                        fillType = Path.FillType.EVEN_ODD
                        addRoundRect(RectF(3f, 4f, 21f, 17f), 3.5f, 3.5f, Path.Direction.CW)
                        addCircle(8f, 10.5f, 1.3f, Path.Direction.CW)
                        addCircle(12f, 10.5f, 1.3f, Path.Direction.CW)
                        addCircle(16f, 10.5f, 1.3f, Path.Direction.CW)
                    }
                    c.drawPath(bubble, fill)
                    val tail = Path().apply {
                        moveTo(7f, 16f); lineTo(7f, 21f); lineTo(12.5f, 16f); close()
                    }
                    c.drawPath(tail, fill)
                }
                Kind.LOCK_CLOSED, Kind.LOCK_OPEN -> {
                    val shackle = Path().apply {
                        moveTo(8f, 10f)
                        lineTo(8f, 7f)
                        arcTo(RectF(8f, 3f, 16f, 11f), 180f, 180f)
                        if (kind == Kind.LOCK_CLOSED) lineTo(16f, 10f)
                    }
                    val sw = stroke.strokeWidth
                    stroke.strokeWidth = 2.4f
                    c.drawPath(shackle, stroke)
                    stroke.strokeWidth = sw
                    val body = Path().apply {
                        fillType = Path.FillType.EVEN_ODD
                        addRoundRect(RectF(4.5f, 10f, 19.5f, 21.5f), 2.8f, 2.8f, Path.Direction.CW)
                        addCircle(12f, 15f, 1.8f, Path.Direction.CW)
                    }
                    c.drawPath(body, fill)
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

// Icône alignée verticalement sur le milieu des lettres (et non posée sur la ligne de base)
private class CenteredImageSpan(d: Drawable) : android.text.style.ImageSpan(d) {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int = drawable.bounds.right

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val centre = y - paint.textSize * 0.3f
        val transY = centre - drawable.bounds.height() / 2f
        canvas.save()
        canvas.translate(x, transY)
        drawable.draw(canvas)
        canvas.restore()
    }
}
