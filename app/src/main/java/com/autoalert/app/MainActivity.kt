package com.autoalert.app

import android.Manifest
import android.app.Activity
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
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var root: LinearLayout
    private lateinit var banner: TextView
    private lateinit var phoneInput: EditText
    private lateinit var linkText: TextView
    private lateinit var sensitivityBar: SeekBar
    private val sensitivityLabels = mutableListOf<TextView>()
    private lateinit var toggleBtn: Button
    private lateinit var configCard: LinearLayout
    private lateinit var callBtn: Button
    private lateinit var notifBtn: Button
    private lateinit var batBtn: Button

    private val handler = Handler(Looper.getMainLooper())
    private val hideBanner = Runnable { banner.visibility = View.GONE }

    private val choiceNames = listOf(
        R.string.sens_normal,
        R.string.sens_sensible,
        R.string.sens_extreme
    )
    private val choiceValues = listOf(0.40f, 0.30f, 0.20f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("p", MODE_PRIVATE)

        // Choix du mode au premier lancement
        when (prefs.getString("role", null)) {
            null -> {
                startActivity(Intent(this, RoleActivity::class.java))
                finish()
                return
            }
            "receiver" -> {
                startActivity(Intent(this, ReceiverActivity::class.java))
                finish()
                return
            }
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 248, 250))
            setPadding(dp(20), dp(28), dp(20), dp(20))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        }

        // BANDEAU DE MESSAGE
        banner = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(16), dp(18), dp(16))
            elevation = dp(8).toFloat()
            visibility = View.GONE
        }

        // CONFIGURATION
        configCard = createCard().apply {
            background = roundedBackground(
                Color.rgb(255, 243, 224),
                Color.rgb(255, 204, 128),
                16
            )
        }

        configCard.addView(TextView(this).apply {
            text = getString(R.string.config_title)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(160, 90, 0))
            setPadding(0, 0, 0, dp(6))
        })

        callBtn = addRow(configCard, getString(R.string.perm_location)) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1
            )
        }

        notifBtn = addRow(configCard, getString(R.string.perm_notif)) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                2
            )
        }

        batBtn = addRow(configCard, getString(R.string.perm_battery)) {
            askBattery()
        }

        root.addView(configCard)

        // NUMÉRO
        root.addView(createSectionTitle(getString(R.string.section_code)))

        val phoneCard = createCard()

        phoneInput = EditText(this).apply {
            hint = getString(R.string.code_hint)
            setHintTextColor(Color.rgb(160, 165, 175))
            textSize = 17f
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(32))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setText(prefs.getString("code", ""))
            background = roundedBackground(
                Color.WHITE,
                Color.rgb(220, 223, 229),
                12
            )
        }

        phoneCard.addView(phoneInput)

        linkText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(90, 95, 105))
            setPadding(dp(4), dp(10), dp(4), 0)
        }
        phoneCard.addView(linkText)
        root.addView(phoneCard)

        // SENSIBILITÉ : barre horizontale à 3 positions
        root.addView(createSectionTitle(getString(R.string.section_sensitivity)))

        val sensitivityCard = createCard()

        val currentMode =
            if (License.isUnlocked(this)) prefs.getInt("mode", 0).coerceIn(0, 2) else 0

        sensitivityBar = SeekBar(this).apply {
            max = 2
            progress = currentMode
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(35, 38, 45))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(35, 38, 45))
            tickMark = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(dp(6), dp(6))
                setColor(Color.rgb(150, 155, 165))
            }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser && value > 0 && !License.isUnlocked(this@MainActivity)) {
                        bar?.progress = 0
                        highlightSensitivity(0)
                        showBanner(getString(R.string.msg_locked_option), false)
                        return
                    }
                    highlightSensitivity(value)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }

        sensitivityCard.addView(
            sensitivityBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val labelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), 0)
        }

        val gravities = listOf(Gravity.START, Gravity.CENTER_HORIZONTAL, Gravity.END)

        choiceNames.forEachIndexed { index, nameRes ->
            val label = TextView(this).apply {
                text = getString(nameRes)
                textSize = 14f
                gravity = gravities[index]
            }
            sensitivityLabels.add(label)
            labelsRow.addView(
                label,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }

        sensitivityCard.addView(labelsRow)
        root.addView(sensitivityCard)
        highlightSensitivity(currentMode)
        refreshSensitivity()

        // GRAND BOUTON ROND : cadenas fermé = actif, ouvert = inactif
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

        val toggleBox = FrameLayout(this).apply {
            setPadding(0, dp(40), 0, dp(25))
        }

        toggleBox.addView(
            toggleBtn,
            FrameLayout.LayoutParams(
                dp(250),
                dp(250),
                Gravity.CENTER_HORIZONTAL
            )
        )
        
        root.addView(toggleBox)

        // BOUTON PLUS
        val plusButton = Button(this).apply {
            text = getString(R.string.btn_plus)
            isAllCaps = false
            textSize = 16f
            setTextColor(Color.rgb(35, 38, 45))
            background = roundedBackground(
                Color.WHITE,
                Color.rgb(35, 38, 45),
                12
            )
            stateListAnimator = null
            setOnClickListener {
                startActivity(Intent(this@MainActivity, PlusActivity::class.java))
            }
        }

        root.addView(
            plusButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56)
            ).apply {
                topMargin = dp(24)
                bottomMargin = dp(20)
            }
        )

        updateStatus(prefs.getBoolean("active", false))
        refreshConfig()

        // BASCULE : un toucher active ou désactive immédiatement
        toggleBtn.setOnClickListener {

            hideKeyboard()
            try {
                val v = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                v.vibrate(android.os.VibrationEffect.createOneShot(150, 255))
            } catch (_: Exception) {
            }

            if (prefs.getBoolean("active", false)) {

                stopService(
                    Intent(this, MotionService::class.java)
                )

                prefs.edit()
                    .putBoolean("active", false)
                    .apply()

                updateStatus(false)

                showBanner(getString(R.string.msg_stopped), false)

                return@setOnClickListener
            }

            val num = phoneInput.text.toString().trim()

            if (!num.matches(Regex("^[A-Za-z0-9]{8,32}$"))) {
                showBanner(getString(R.string.msg_invalid_code), false)
                return@setOnClickListener
            }

            if (!allOk()) {
                refreshConfig()
                showBanner(getString(R.string.msg_finish_config), false)
                scroll.smoothScrollTo(0, 0)
                return@setOnClickListener
            }

            val index =
                if (License.isUnlocked(this)) sensitivityBar.progress.coerceIn(0, 2) else 0

            prefs.edit()
                .putString("code", num)
                .putInt("mode", index)
                .putFloat("seuil", choiceValues[index])
                .putBoolean("active", true)
                .putLong("c_ok_ms", 0L)
                .putBoolean("c_fail", false)
                .apply()

            stopService(
                Intent(this, MotionService::class.java)
            )

            startForegroundService(
                Intent(this, MotionService::class.java)
            )

            updateStatus(true)

            showBanner(getString(R.string.msg_activated), true)
        }

        // ÉCRAN = contenu + bandeau par-dessus
        val frame = FrameLayout(this)

        frame.addView(
            scroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        frame.addView(
            banner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                setMargins(
                    dp(16),
                    dp(12),
                    dp(16),
                    0
                )
            }
        )

        setContentView(frame)
    }

    override fun onResume() {
        super.onResume()
        if (!::callBtn.isInitialized) return
        refreshConfig()
        refreshSensitivity()
        handler.removeCallbacks(linkTicker)
        handler.post(linkTicker)
    }

    override fun onPause() {
        handler.removeCallbacks(linkTicker)
        super.onPause()
    }

    private val linkTicker = object : Runnable {
        override fun run() {
            refreshLink()
            handler.postDelayed(this, 3000)
        }
    }

    // Indicateur de liaison avec le serveur
    private fun refreshLink() {
        if (!::linkText.isInitialized) return
        val p = getSharedPreferences("p", MODE_PRIVATE)
        if (!p.getBoolean("active", false)) {
            linkText.text = ""
            return
        }
        val ok = p.getLong("c_ok_ms", 0L)
        val fresh = ok > 0 && System.currentTimeMillis() - ok < 12 * 60_000L
        linkText.text = when {
            fresh -> getString(
                R.string.car_link_ok,
                java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(ok))
            )
            p.getBoolean("c_fail", false) || ok > 0 -> getString(R.string.car_link_fail)
            else -> getString(R.string.car_link_wait)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(hideBanner)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        refreshConfig()

        if (
            grantResults.isNotEmpty() &&
            grantResults[0] != PackageManager.PERMISSION_GRANTED
        ) {
            showBanner(getString(R.string.msg_allow_settings), false)

            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    // BANDEAU
    private fun showBanner(
        message: String,
        success: Boolean
    ) {
        banner.text = message

        banner.background = roundedBackground(
            if (success) {
                Color.rgb(25, 145, 80)
            } else {
                Color.rgb(200, 50, 60)
            },
            Color.TRANSPARENT,
            14
        )

        banner.visibility = View.VISIBLE

        handler.removeCallbacks(hideBanner)

        handler.postDelayed(
            hideBanner,
            3000
        )
    }

    // CLAVIER
    private fun hideKeyboard() {

        (
            getSystemService(INPUT_METHOD_SERVICE)
                as InputMethodManager
        ).hideSoftInputFromWindow(
            phoneInput.windowToken,
            0
        )

        phoneInput.clearFocus()

        root.isFocusableInTouchMode = true
        root.requestFocus()
    }

    // ÉTAT DES AUTORISATIONS
    private fun callOk() =
        checkSelfPermission(
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun notifOk() =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun batteryOk(): Boolean {

        val pm =
            getSystemService(POWER_SERVICE)
                as PowerManager

        return pm.isIgnoringBatteryOptimizations(
            packageName
        )
    }

    private fun allOk() =
        callOk() &&
        notifOk() &&
        batteryOk()

    private fun refreshConfig() {

        setBtn(callBtn, callOk())
        setBtn(notifBtn, notifOk())
        setBtn(batBtn, batteryOk())

        configCard.visibility =
            if (allOk()) {
                View.GONE
            } else {
                View.VISIBLE
            }
    }

    private fun askBattery() {

        try {

            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )

        } catch (e: Exception) {

            try {
                startActivity(
                    Intent(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    // OUTILS D'INTERFACE
    private fun addRow(
        card: LinearLayout,
        label: String,
        onClick: () -> Unit
    ): Button {

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }

        row.addView(
            TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(Color.rgb(35, 38, 45))
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val btn = Button(this).apply {
            isAllCaps = false
            textSize = 13f
            setOnClickListener {
                onClick()
            }
        }

        row.addView(
            btn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(44)
            )
        )

        card.addView(row)

        return btn
    }

    private fun setBtn(
        btn: Button,
        ok: Boolean
    ) {

        btn.isEnabled = !ok

        btn.text = if (ok) {
            getString(R.string.btn_ok)
        } else {
            getString(R.string.btn_authorize)
        }

        btn.setTextColor(Color.WHITE)

        btn.background = roundedBackground(
            if (ok) {
                Color.rgb(25, 145, 80)
            } else {
                Color.rgb(230, 126, 0)
            },
            Color.TRANSPARENT,
            12
        )
    }

    private fun createCard(): LinearLayout {

        return LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(18),
                dp(16),
                dp(18),
                dp(16)
            )

            background = roundedBackground(
                Color.WHITE,
                Color.rgb(232, 234, 238),
                16
            )

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(
                        0,
                        dp(6),
                        0,
                        dp(8)
                    )
                }
        }
    }

    private fun createSectionTitle(
        text: String
    ): TextView {

        return TextView(this).apply {

            this.text = text

            textSize = 13f

            setTypeface(
                null,
                Typeface.BOLD
            )

            setTextColor(
                Color.rgb(90, 95, 105)
            )

            setPadding(
                dp(4),
                dp(18),
                dp(4),
                dp(5)
            )
        }
    }

    private fun roundedBackground(
        color: Int,
        strokeColor: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {

            setColor(color)

            cornerRadius =
                dp(radius).toFloat()

            if (strokeColor != Color.TRANSPARENT) {
                setStroke(
                    dp(1),
                    strokeColor
                )
            }
        }
    }

    
    // ÉTAT : bouton rond plein, vert ou rouge
    private fun updateStatus(
        active: Boolean
    ) {

        val color = if (active) {
            Color.rgb(8, 120, 61)    // Vert foncé
        } else {
            Color.rgb(197, 22, 37)   // Rouge foncé
        }

        toggleBtn.text = if (active) "🔒" else "🔓"
        toggleBtn.setTextColor(Color.WHITE)

        toggleBtn.contentDescription =
            if (active) {
                getString(R.string.status_active)
            } else {
                getString(R.string.status_inactive)
            }

        toggleBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

        // Sensibilité grisée et verrouillée tant que la protection est active
        val barTint = android.content.res.ColorStateList.valueOf(
            if (active) Color.rgb(190, 195, 205) else Color.rgb(35, 38, 45)
        )
        sensitivityBar.progressTintList = barTint
        sensitivityBar.thumbTintList = barTint
        sensitivityBar.setOnTouchListener { _, _ -> active }

        // Code verrouillé tant que la protection est active
        phoneInput.isEnabled = !active
        phoneInput.alpha = if (active) 0.5f else 1f
        refreshLink()
    }

    // Cadenas sur Sensible et Extrême tant qu'aucun code valide n'est saisi
    private fun refreshSensitivity() {
        val unlocked = License.isUnlocked(this)

        choiceNames.forEachIndexed { index, nameRes ->
            val locked = index > 0 && !unlocked
            sensitivityLabels.getOrNull(index)?.text =
                if (locked) "🔒 " + getString(nameRes) else getString(nameRes)
        }

        if (!unlocked && sensitivityBar.progress != 0) {
            sensitivityBar.progress = 0
        }
    }

    private fun highlightSensitivity(selected: Int) {
        sensitivityLabels.forEachIndexed { index, label ->
            val on = index == selected
            label.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            label.setTextColor(
                if (on) Color.rgb(35, 38, 45) else Color.rgb(140, 145, 155)
            )
        }
    }

    private fun dp(value: Int): Int =
        (
            value *
                resources.displayMetrics.density
        ).roundToInt()
}
