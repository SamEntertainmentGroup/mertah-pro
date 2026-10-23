package com.autoalert.app

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

// Fenêtre pop-up au style de l'application : coins très arrondis, texte lisible,
// bouton principal « Compris » (foncé) et bouton secondaire « Annuler » (gris)
object AppDialog {

    private val NOIR = Color.rgb(35, 38, 45)

    fun show(
        a: Activity,
        title: String,
        message: String? = null,
        content: View? = null,
        cancel: Boolean = false,
        onConfirm: (() -> Unit)? = null
    ) {
        fun dp(v: Int) = (v * a.resources.displayMetrics.density).roundToInt()

        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(26), dp(28), dp(26), dp(22))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(28).toFloat()
            }
        }

        box.addView(TextView(a).apply {
            text = title
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(NOIR)
            setPadding(0, 0, 0, dp(14))
        })

        if (message != null) {
            box.addView(TextView(a).apply {
                text = message
                textSize = 18f
                setTextColor(Color.rgb(70, 75, 85))
                setLineSpacing(0f, 1.2f)
                setPadding(0, 0, 0, dp(22))
            })
        }

        if (content != null) {
            box.addView(
                content,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = dp(22)
                }
            )
        }

        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }

        if (cancel) {
            row.addView(
                Button(a).apply {
                    text = a.getString(R.string.dialog_cancel)
                    isAllCaps = false
                    textSize = 18f
                    setTextColor(NOIR)
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(235, 236, 239))
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(1), Color.rgb(220, 223, 229))
                    }
                    stateListAnimator = null
                    setOnClickListener { d.dismiss() }
                },
                LinearLayout.LayoutParams(0, dp(58), 1f).apply { marginEnd = dp(12) }
            )
        }

        row.addView(
            Button(a).apply {
                text = a.getString(R.string.dialog_ok)
                isAllCaps = false
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(NOIR)
                    cornerRadius = dp(16).toFloat()
                }
                stateListAnimator = null
                setOnClickListener {
                    d.dismiss()
                    onConfirm?.invoke()
                }
            },
            LinearLayout.LayoutParams(0, dp(58), 1f)
        )
        box.addView(row)

        d.setContentView(box)
        d.show()
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((a.resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}
