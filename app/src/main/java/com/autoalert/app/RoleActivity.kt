package com.autoalert.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

class RoleActivity : Activity() {

    private val NOIR = Color.rgb(35, 38, 45)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(32), dp(22), dp(28))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(247, 248, 250))
            addView(root)
        }

        // TITRE
        root.addView(TextView(this).apply {
            text = getString(R.string.role_title)
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
            setTextColor(NOIR)
            setPadding(dp(4), 0, dp(4), dp(10))
        })

        // EXPLICATION
        root.addView(TextView(this).apply {
            text = getString(R.string.role_subtitle)
            textSize = 18f
            setTextColor(Color.rgb(90, 95, 105))
            setLineSpacing(0f, 1.15f)
            setPadding(dp(4), 0, dp(4), dp(30))
        })

        // OPTION 1 : voiture
        root.addView(
            roleCard(
                "🚗",
                getString(R.string.role_car),
                getString(R.string.role_car_desc),
                NOIR
            ) { confirm("car", getString(R.string.role_car_short)) },
            LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        // OPTION 2 : mon téléphone
        root.addView(
            roleCard(
                "📱",
                getString(R.string.role_receiver),
                getString(R.string.role_receiver_desc),
                Color.rgb(25, 145, 80)
            ) { confirm("receiver", getString(R.string.role_receiver_short)) },
            LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(28)
            }
        )

        // AVERTISSEMENT
        root.addView(
            TextView(this).apply {
                text = "⚠️  " + getString(R.string.role_note)
                textSize = 16f
                setTextColor(Color.rgb(140, 80, 0))
                setLineSpacing(0f, 1.15f)
                setPadding(dp(18), dp(16), dp(18), dp(16))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(255, 243, 224))
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.rgb(255, 204, 128))
                }
            },
            LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(30)
            }
        )

        setContentView(scroll)
    }

    // Grande carte tactile : icône, titre, explication
    private fun roleCard(
        icon: String,
        title: String,
        desc: String,
        color: Int,
        onClick: () -> Unit
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(26), dp(24), dp(26))
            minimumHeight = dp(170)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(24).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        card.addView(TextView(this).apply {
            text = icon
            textSize = 44f
            setPadding(0, 0, 0, dp(8))
        })
        card.addView(TextView(this).apply {
            text = title
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        })
        card.addView(TextView(this).apply {
            text = desc
            textSize = 17f
            setTextColor(Color.argb(235, 255, 255, 255))
            setLineSpacing(0f, 1.15f)
        })
        return card
    }

    // Choix définitif : confirmation avant d'enregistrer
    private fun confirm(role: String, label: String) {
        AppDialog.show(
            this,
            title = getString(R.string.role_confirm_title),
            message = getString(R.string.role_confirm_choice, label),
            cancel = true
        ) { choose(role) }
    }

    private fun choose(role: String) {
        getSharedPreferences("p", MODE_PRIVATE).edit().putString("role", role).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
