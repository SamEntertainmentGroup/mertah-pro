package com.autoalert.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

class RoleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(247, 248, 250))
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.role_title)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(35, 38, 45))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(28))
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.role_note)
            textSize = 14f
            setTextColor(Color.rgb(90, 95, 105))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        })

        root.addView(
            roleButton(getString(R.string.role_car), getString(R.string.role_car_desc), Color.rgb(35, 38, 45)) {
                confirm("car", getString(R.string.role_car))
            },
            LinearLayout.LayoutParams(-1, dp(110)).apply { bottomMargin = dp(16) }
        )

        root.addView(
            roleButton(getString(R.string.role_receiver), getString(R.string.role_receiver_desc), Color.rgb(25, 145, 80)) {
                confirm("receiver", getString(R.string.role_receiver))
            },
            LinearLayout.LayoutParams(-1, dp(110))
        )

        setContentView(root)
    }

    // Choix définitif : confirmation avant d'enregistrer
    private fun confirm(role: String, label: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.role_confirm_choice, label))
            .setPositiveButton(android.R.string.ok) { _, _ -> choose(role) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun choose(role: String) {
        getSharedPreferences("p", MODE_PRIVATE).edit().putString("role", role).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun roleButton(title: String, desc: String, color: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = "$title\n$desc"
            isAllCaps = false
            textSize = 17f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(16).toFloat()
            }
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
