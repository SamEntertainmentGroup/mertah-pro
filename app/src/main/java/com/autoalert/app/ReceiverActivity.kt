package com.autoalert.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class ReceiverActivity : Activity() {

    private lateinit var codeInput: EditText
    private lateinit var toggleBtn: Button
    private lateinit var configCard: LinearLayout
    private lateinit var notifBtn: Button
    private lateinit var batBtn: Button
    private lateinit var linkText: TextView
    private lateinit var infoAlert: TextView
    private lateinit var infoPos: TextView
    private lateinit var infoAlive: TextView
    private lateinit var mapBtn: Button

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshInfo()
            handler.postDelayed(this, 3000)
        }
    }

    private val NOIR = Color.rgb(35, 38, 45)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("p", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 248, 250))
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        // CONFIGURATION
        configCard = card().apply {
            background = rounded(Color.rgb(255, 243, 224), Color.rgb(255, 204, 128), 16)
        }
        configCard.addView(TextView(this).apply {
            text = getString(R.string.config_title)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(160, 90, 0))
            setPadding(0, 0, 0, dp(6))
        })
        notifBtn = addRow(configCard, getString(R.string.perm_notif)) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        }
        batBtn = addRow(configCard, getString(R.string.perm_battery)) { askBattery() }
        root.addView(configCard)

        // CODE
        root.addView(sectionTitle(getString(R.string.section_code)))
        val codeCard = card()
        codeInput = EditText(this).apply {
            hint = getString(R.string.code_hint)
            setHintTextColor(Color.rgb(160, 165, 175))
            textSize = 17f
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(32))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setText(prefs.getString("code", ""))
            background = rounded(Color.WHITE, Color.rgb(220, 223, 229), 12)
        }
        codeCard.addView(codeInput)
        root.addView(codeCard)

        // ÉTAT DE LA VOITURE
        root.addView(sectionTitle(getString(R.string.recv_section_car)))
        val infoCard = card()
        linkText = infoLine().apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        infoCard.addView(linkText)
        infoAlert = infoLine()
        infoPos = infoLine()
        infoAlive = infoLine()
        infoCard.addView(infoAlert)
        infoCard.addView(infoPos)
        infoCard.addView(infoAlive)
        mapBtn = Button(this).apply {
            text = getString(R.string.btn_open_map)
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(25, 145, 80), Color.TRANSPARENT, 12)
            stateListAnimator = null
            setOnClickListener { openMap() }
        }
        infoCard.addView(mapBtn, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        root.addView(infoCard)

        // GRAND BOUTON ROND
        toggleBtn = Button(this).apply {
            isAllCaps = false
            textSize = 80f
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            stateListAnimator = null
        }
        val toggleBox = FrameLayout(this).apply { setPadding(0, dp(30), 0, dp(20)) }
        toggleBox.addView(toggleBtn, FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER_HORIZONTAL))
        root.addView(toggleBox)

        // PLUS
        root.addView(
            Button(this).apply {
                text = getString(R.string.btn_plus)
                isAllCaps = false
                textSize = 16f
                setTextColor(NOIR)
                background = rounded(Color.WHITE, NOIR, 12)
                stateListAnimator = null
                setOnClickListener { startActivity(Intent(this@ReceiverActivity, PlusActivity::class.java)) }
            },
            LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(16); bottomMargin = dp(20) }
        )

        updateStatus(prefs.getBoolean("r_active", false))

        toggleBtn.setOnClickListener {
            if (prefs.getBoolean("r_active", false)) {
                stopService(Intent(this, ReceiverService::class.java))
                prefs.edit().putBoolean("r_active", false).apply()
                updateStatus(false)
                Toast.makeText(this, R.string.msg_stopped, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val code = codeInput.text.toString().trim()
            if (!code.matches(Regex("^[A-Za-z0-9]{8,32}$"))) {
                Toast.makeText(this, R.string.msg_invalid_code, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!allOk()) {
                refreshConfig()
                Toast.makeText(this, R.string.msg_finish_config, Toast.LENGTH_LONG).show()
                scroll.smoothScrollTo(0, 0)
                return@setOnClickListener
            }

            // since = -1 : on ignore l'historique, seules les nouvelles alertes comptent
            prefs.edit()
                .putString("code", code)
                .putLong("r_since", -1L)
                .putLong("r_poll_ms", 0L)
                .putBoolean("r_active", true)
                .apply()

            stopService(Intent(this, ReceiverService::class.java))
            startForegroundService(Intent(this, ReceiverService::class.java))
            updateStatus(true)
            Toast.makeText(this, R.string.msg_activated, Toast.LENGTH_SHORT).show()
        }

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        AlertSound.stop()
        getSystemService(NotificationManager::class.java).cancel(ReceiverService.ID_ALERT)
        refreshConfig()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshConfig()
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.msg_allow_settings, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    // INFOS SUR LA VOITURE
    private fun refreshInfo() {
        val p = getSharedPreferences("p", MODE_PRIVATE)
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        fun t(ts: Long) = if (ts > 0) fmt.format(Date(ts * 1000)) else getString(R.string.info_none)

        val lat = p.getString("r_lat", null)
        val lng = p.getString("r_lng", null)
        val bat = if (p.contains("r_battery")) "  ·  🔋 ${p.getInt("r_battery", 0)}%" else ""

        infoAlert.text = "🚨 ${getString(R.string.info_alert)} : ${t(p.getLong("r_alert_ts", 0))}"
        infoPos.text = "📍 ${getString(R.string.info_pos)} : ${t(p.getLong("r_pos_ts", 0))}"
        infoAlive.text = "💚 ${getString(R.string.info_alive)} : ${t(p.getLong("r_alive_ts", 0))}$bat"

        // Indicateur de liaison avec la voiture
        val now = System.currentTimeMillis()
        val pollMs = p.getLong("r_poll_ms", 0L)
        val age = p.getLong("r_alive_age", -1L)
        linkText.text = when {
            !p.getBoolean("r_active", false) -> getString(R.string.link_off)
            pollMs == 0L || now - pollMs > 60_000L -> getString(R.string.link_server)
            age < 0 -> getString(R.string.link_none)
            else -> {
                val min = ((age + (now - pollMs) / 1000) / 60).toInt().coerceAtLeast(1)
                if (min <= 12) getString(R.string.link_ok, min) else getString(R.string.link_old, min)
            }
        }

        mapBtn.alpha = if (lat != null && lng != null) 1f else 0.45f
    }

    private fun openMap() {
        val p = getSharedPreferences("p", MODE_PRIVATE)
        val lat = p.getString("r_lat", null)
        val lng = p.getString("r_lng", null)
        if (lat == null || lng == null) {
            Toast.makeText(this, R.string.msg_no_pos, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(Voiture)")))
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps?q=$lat,$lng"))
                )
            } catch (_: Exception) {
            }
        }
    }

    // AUTORISATIONS
    private fun notifOk() =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun batteryOk() =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun allOk() = notifOk() && batteryOk()

    private fun refreshConfig() {
        setBtn(notifBtn, notifOk())
        setBtn(batBtn, batteryOk())
        configCard.visibility = if (allOk()) View.GONE else View.VISIBLE
    }

    private fun askBattery() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
            }
        }
    }

    // ÉTAT : bouton rond vert ou rouge
    private fun updateStatus(active: Boolean) {
        codeInput.isEnabled = !active
        codeInput.alpha = if (active) 0.5f else 1f
        toggleBtn.text = if (active) "🔒" else "🔓"
        toggleBtn.setTextColor(Color.WHITE)
        toggleBtn.contentDescription =
            getString(if (active) R.string.status_active else R.string.status_inactive)
        toggleBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (active) Color.rgb(8, 120, 61) else Color.rgb(197, 22, 37))
        }
    }

    // OUTILS D'INTERFACE
    private fun addRow(card: LinearLayout, label: String, onClick: () -> Unit): Button {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(
            TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(NOIR)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        val btn = Button(this).apply {
            isAllCaps = false
            textSize = 13f
            setOnClickListener { onClick() }
        }
        row.addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)))
        card.addView(row)
        return btn
    }

    private fun setBtn(btn: Button, ok: Boolean) {
        btn.isEnabled = !ok
        btn.text = getString(if (ok) R.string.btn_ok else R.string.btn_authorize)
        btn.setTextColor(Color.WHITE)
        btn.background = rounded(
            if (ok) Color.rgb(25, 145, 80) else Color.rgb(230, 126, 0),
            Color.TRANSPARENT,
            12
        )
    }

    private fun infoLine() = TextView(this).apply {
        textSize = 15f
        setTextColor(NOIR)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(16), dp(18), dp(16))
        background = rounded(Color.WHITE, Color.rgb(232, 234, 238), 16)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(6), 0, dp(8)) }
    }

    private fun sectionTitle(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.rgb(90, 95, 105))
        setPadding(dp(4), dp(18), dp(4), dp(5))
    }

    private fun rounded(color: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}
