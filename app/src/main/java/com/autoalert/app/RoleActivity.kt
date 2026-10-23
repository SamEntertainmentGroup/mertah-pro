package com.autoalert.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

class RoleActivity : Activity() {

    private val NOIR = Color.rgb(35, 38, 45)
    private val VERT = Color.rgb(25, 145, 80)
    private val GRIS = Color.rgb(220, 223, 229)

    // Une carte : conteneur, pastille d'icône, coche
    private class RoleCard(val root: LinearLayout, val badge: TextView, val check: TextView)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(247, 248, 250))
            addView(root)
        }

        // TITRE + EXPLICATION COURTE
        root.addView(TextView(this).apply {
            text = getString(R.string.role_title)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(NOIR)
            setPadding(dp(2), 0, dp(2), dp(6))
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.role_subtitle)
            textSize = 15f
            setTextColor(Color.rgb(90, 95, 105))
            setPadding(dp(2), 0, dp(2), dp(20))
        })

        // LES DEUX CARTES (même largeur, espace régulier)
        val car = roleCard("🚗", getString(R.string.role_car), getString(R.string.role_car_desc))
        val receiver = roleCard("📱", getString(R.string.role_receiver), getString(R.string.role_receiver_desc))

        car.root.setOnClickListener {
            select(car, true)
            select(receiver, false)
            confirm("car", getString(R.string.role_car_short)) { select(car, false) }
        }
        receiver.root.setOnClickListener {
            select(receiver, true)
            select(car, false)
            confirm("receiver", getString(R.string.role_receiver_short)) { select(receiver, false) }
        }

        val gap = dp(12)
        root.addView(car.root, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(
            receiver.root,
            LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = gap }
        )

        // AVERTISSEMENT DISCRET
        val note = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(238, 242, 250))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(210, 220, 238))
            }
        }
        note.addView(TextView(this).apply {
            text = "ℹ️"
            textSize = 16f
        })
        note.addView(
            TextView(this).apply {
                text = getString(R.string.role_note)
                textSize = 13.5f
                setTextColor(Color.rgb(60, 70, 95))
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
            }
        )
        root.addView(
            note,
            LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) }
        )

        setContentView(scroll)
    }

    // Carte compacte : pastille d'icône à gauche, titre + description, coche à droite
    private fun roleCard(icon: String, title: String, desc: String): RoleCard {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            minimumHeight = dp(84)
            isClickable = true
            isFocusable = true
        }

        val badge = TextView(this).apply {
            text = icon
            textSize = 24f
            gravity = Gravity.CENTER
        }
        card.addView(badge, LinearLayout.LayoutParams(dp(48), dp(48)))

        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(NOIR)
        })
        texts.addView(TextView(this).apply {
            text = desc
            textSize = 14f
            maxLines = 2
            setTextColor(Color.rgb(90, 95, 105))
            setPadding(0, dp(2), 0, 0)
        })
        card.addView(
            texts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
                marginEnd = dp(10)
            }
        )

        val check = TextView(this).apply {
            text = "✓"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(VERT)
            }
            visibility = View.INVISIBLE   // garde la place pour l'alignement
        }
        card.addView(check, LinearLayout.LayoutParams(dp(26), dp(26)))

        val rc = RoleCard(card, badge, check)
        select(rc, false)
        return rc
    }

    // Effet de sélection : bordure verte + coche
    private fun select(c: RoleCard, on: Boolean) {
        c.root.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(18).toFloat()
            setStroke(if (on) dp(2) else dp(1), if (on) VERT else GRIS)
        }
        c.badge.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (on) Color.rgb(222, 244, 231) else Color.rgb(240, 241, 244))
        }
        c.check.visibility = if (on) View.VISIBLE else View.INVISIBLE
    }

    // Choix définitif : confirmation avant d'enregistrer
    private fun confirm(role: String, label: String, onCancel: () -> Unit) {
        AppDialog.show(
            this,
            title = getString(R.string.role_confirm_title),
            message = getString(R.string.role_confirm_choice, label),
            cancel = true,
            onCancel = onCancel
        ) { choose(role) }
    }

    private fun choose(role: String) {
        getSharedPreferences("p", MODE_PRIVATE).edit().putString("role", role).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
