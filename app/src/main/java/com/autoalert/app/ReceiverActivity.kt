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
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class ReceiverActivity : Activity() {

    private enum class Link { STOPPED, WAIT, SERVER, NONE, CAR_OFF, OK, OLD }

    private lateinit var codeInput: EditText
    private lateinit var codeTitle: TextView
    private lateinit var codeCard: LinearLayout
    private lateinit var toggleBtn: LinearLayout
    private lateinit var toggleIcon: ImageView
    private lateinit var configCard: LinearLayout
    private lateinit var notifBtn: Button
    private lateinit var batBtn: Button
    private lateinit var stopBar: LinearLayout
    private lateinit var stopTitle: TextView
    private lateinit var dot: View
    private lateinit var linkText: TextView
    private lateinit var batBox: LinearLayout
    private lateinit var batIcon: ImageView
    private lateinit var batText: TextView
    private lateinit var alertLine: TextView
    private lateinit var posLine: TextView
    private lateinit var web: WebView
    private var mapReady = false
    private var shownTs = -1L
    private var shownTrace = ""

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refreshInfo()
            handler.postDelayed(this, 1000)
        }
    }

    private val NOIR = Color.rgb(35, 38, 45)
    private val GRIS_TEXTE = Color.rgb(90, 95, 105)

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

        // POP-UP « ARRÊTER L'ALARME » : flotte en haut, comme une notification (ne décale rien)
        stopBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = rounded(Color.rgb(197, 22, 37), Color.TRANSPARENT, 16)
            elevation = dp(16).toFloat()
            visibility = View.GONE
            isClickable = true
            setOnClickListener { stopAlarm() }
        }
        stopBar.addView(
            ImageView(this).apply { setImageDrawable(icon(Icons.Kind.ALERT, Color.WHITE, 28)) },
            LinearLayout.LayoutParams(dp(28), dp(28))
        )
        val stopTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        stopTitle = TextView(this).apply {
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        stopTexts.addView(stopTitle)
        stopTexts.addView(TextView(this).apply {
            text = getString(R.string.recv_stop_alarm)
            textSize = 14f
            setTextColor(Color.argb(230, 255, 255, 255))
        })
        stopBar.addView(
            stopTexts,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) }
        )

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

        // CODE DE LA VOITURE (caché pendant l'écoute)
        codeTitle = sectionTitle(getString(R.string.section_code))
        root.addView(codeTitle)
        codeCard = card()
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
        codeCard.addView(
            grayButton(getString(R.string.scan_btn)) { scanCode() },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) }
        )
        root.addView(codeCard)

        // BOUTON D'ACTIVATION (en haut)
        // GRAND BOUTON D'ACTIVATION : cadenas seul, il remplit l'espace libre (créé ici, placé sous la carte)
        toggleIcon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        toggleBtn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = dp(110)
            isClickable = true
            addView(toggleIcon, LinearLayout.LayoutParams(dp(72), dp(72)))
            // le cadenas grandit avec le bouton, donc avec la taille de l'écran
            addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
                val size = (minOf(r - l, b - t) * 0.45f).toInt().coerceAtLeast(dp(48))
                if (toggleIcon.layoutParams.width != size) {
                    toggleIcon.post {
                        val lp = toggleIcon.layoutParams
                        lp.width = size
                        lp.height = size
                        toggleIcon.layoutParams = lp
                    }
                }
            }
        }

        // CARTE VOITURE : état, carte, boutons, infos
        val carCard = card().apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16)
        }

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), dp(12))
        }
        dot = View(this)
        statusRow.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)))
        linkText = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(NOIR)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        statusRow.addView(
            linkText,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(10)
                marginEnd = dp(8)
            }
        )
        batBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        batIcon = ImageView(this)
        batText = TextView(this).apply {
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(NOIR)
        }
        batBox.addView(batIcon, LinearLayout.LayoutParams(dp(24), dp(24)))
        batBox.addView(batText, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        statusRow.addView(batBox)
        carCard.addView(statusRow)

        // Carte (coins arrondis) + bouton de recentrage
        val mapBox = FrameLayout(this).apply {
            background = rounded(Color.rgb(235, 236, 239), Color.TRANSPARENT, 14)
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            setOnTouchListener { v, _ ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                false
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    mapReady = true
                    updateMap(true)
                }
            }
            loadDataWithBaseURL(Config.SERVER + "/", MAP_HTML, "text/html", "UTF-8", null)
        }
        mapBox.addView(web, FrameLayout.LayoutParams(-1, -1))
        val recenter = ImageView(this).apply {
            setImageDrawable(icon(Icons.Kind.TARGET, NOIR, 24))
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(220, 223, 229))
            }
            elevation = dp(4).toFloat()
            isClickable = true
            setOnClickListener { if (mapReady) web.evaluateJavascript("recenter()", null) }
        }
        mapBox.addView(
            recenter,
            FrameLayout.LayoutParams(dp(46), dp(46), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(12), dp(12))
            }
        )
        carCard.addView(mapBox, LinearLayout.LayoutParams(-1, dp(240)))

        // Boutons côte à côte : Interroger / Itinéraire
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(
            iconButton(Icons.Kind.WAVES, getString(R.string.ask_short)) { askCar() },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(10) }
        )
        btnRow.addView(
            iconButton(Icons.Kind.NAV, getString(R.string.route_btn)) { openRoute() },
            LinearLayout.LayoutParams(0, dp(52), 1f)
        )
        carCard.addView(btnRow, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        // Dernière alerte et dernière position
        alertLine = infoLine()
        posLine = infoLine()
        carCard.addView(
            infoRow(Icons.Kind.ALERT, Color.rgb(200, 110, 0), alertLine),
            LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
        )
        carCard.addView(
            infoRow(Icons.Kind.PIN, GRIS_TEXTE, posLine),
            LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        )
        root.addView(carCard)

        // GRAND BOUTON D'ACTIVATION sous la carte : prend tout l'espace jusqu'au bouton Plus
        root.addView(toggleBtn, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })

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
            LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(24); bottomMargin = dp(20) }
        )

        updateStatus(prefs.getBoolean("r_active", false))

        toggleBtn.setOnClickListener {
            if (prefs.getBoolean("r_active", false)) {
                stopService(Intent(this, ReceiverService::class.java))
                prefs.edit().putBoolean("r_active", false).apply()
                updateStatus(false)
                TopBanner.show(this, getString(R.string.msg_stopped), false)
                return@setOnClickListener
            }

            val code = codeInput.text.toString().trim()
            if (!code.matches(Regex("^[A-Za-z0-9]{8,32}$"))) {
                TopBanner.show(this, getString(R.string.msg_invalid_code), false)
                return@setOnClickListener
            }
            if (!allOk()) {
                refreshConfig()
                TopBanner.show(this, getString(R.string.msg_finish_config), false)
                scroll.smoothScrollTo(0, 0)
                return@setOnClickListener
            }

            // since = -1 : on ignore l'historique, seules les nouvelles alertes comptent
            prefs.edit()
                .putString("code", code)
                .putLong("r_since", -1L)
                .putLong("r_poll_ms", 0L)
                .putLong("r_start_ms", System.currentTimeMillis())
                .putBoolean("r_lost", false)
                .putBoolean("r_off", false)
                .putBoolean("r_active", true)
                .apply()

            stopService(Intent(this, ReceiverService::class.java))
            startForegroundService(Intent(this, ReceiverService::class.java))
            updateStatus(true)
            TopBanner.show(this, getString(R.string.msg_activated), true)
        }

        setContentView(scroll)

        // Ajouté par-dessus le contenu : n'ajoute aucun espace en haut
        var top = dp(12)
        if (Build.VERSION.SDK_INT >= 35) {
            top += window.decorView.rootWindowInsets
                ?.getInsets(android.view.WindowInsets.Type.statusBars())?.top ?: 0
        }
        addContentView(
            stopBar,
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply { setMargins(dp(16), top, dp(16), 0) }
        )
    }

    override fun onResume() {
        super.onResume()
        stopAlarm()
        refreshConfig()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshConfig()
        if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            TopBanner.show(this, getString(R.string.msg_allow_settings), false)
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    // ALARME
    private fun stopAlarm() {
        AlertSound.stop()
        getSystemService(NotificationManager::class.java).apply {
            cancel(ReceiverService.ID_ALERT)
            cancel(ReceiverService.ID_LOST)
        }
        if (::stopBar.isInitialized) stopBar.visibility = View.GONE
    }

    // ÉTAT DE LA LIAISON AVEC LA VOITURE
    private fun linkState(): Pair<Link, Int> {
        val p = getSharedPreferences("p", MODE_PRIVATE)
        if (!p.getBoolean("r_active", false)) return Link.STOPPED to 0

        val now = System.currentTimeMillis()
        val pollMs = p.getLong("r_poll_ms", 0L)
        if (pollMs == 0L) {
            val start = p.getLong("r_start_ms", now)
            return (if (now - start > 30_000L) Link.SERVER else Link.WAIT) to 0
        }
        if (now - pollMs > 60_000L) return Link.SERVER to 0

        val age = p.getLong("r_alive_age", -1L)
        if (age < 0) return Link.NONE to 0
        if (p.getBoolean("r_off", false)) return Link.CAR_OFF to 0

        val min = ((age + (now - pollMs) / 1000) / 60).toInt().coerceAtLeast(1)
        return (if (min <= 3) Link.OK else Link.OLD) to min
    }

    private fun refreshInfo() {
        val p = getSharedPreferences("p", MODE_PRIVATE)
        val (state, min) = linkState()

        linkText.text = when (state) {
            Link.STOPPED -> getString(R.string.link_off)
            Link.WAIT -> getString(R.string.link_wait)
            Link.SERVER -> getString(R.string.link_server)
            Link.NONE -> getString(R.string.link_none)
            Link.CAR_OFF -> getString(R.string.link_car_off)
            Link.OK -> getString(R.string.link_ok, min)
            Link.OLD -> getString(R.string.link_old, min)
        }
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                when (state) {
                    Link.OK -> Color.rgb(25, 145, 80)
                    Link.OLD, Link.WAIT -> Color.rgb(230, 126, 0)
                    Link.SERVER, Link.NONE -> Color.rgb(197, 22, 37)
                    Link.STOPPED, Link.CAR_OFF -> Color.rgb(150, 155, 165)
                }
            )
        }

        // Batterie du téléphone de la voiture
        val bat = p.getInt("r_battery", -1)
        if (bat >= 0 && (state == Link.OK || state == Link.OLD)) {
            batIcon.setImageDrawable(
                Icons.drawable(this, Icons.Kind.BATTERY, if (bat <= 20) Color.rgb(197, 22, 37) else NOIR, 24, bat)
            )
            batText.text = "$bat%"
            batBox.visibility = View.VISIBLE
        } else {
            batBox.visibility = View.GONE
        }

        // Dernière alerte et dernière position
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        fun t(ts: Long) = if (ts > 0) fmt.format(Date(ts * 1000)) else getString(R.string.info_none)
        alertLine.text = "${getString(R.string.info_alert)} : ${t(p.getLong("r_alert_ts", 0))}"
        posLine.text = "${getString(R.string.info_pos)} : ${t(p.getLong("r_pos_ts", 0))}"

        val playing = AlertSound.isPlaying()
        if (playing) {
            stopTitle.text = getString(
                if (p.getString("r_alarm_kind", "alert") == "lost") R.string.bar_lost else R.string.bar_alert
            )
        }
        stopBar.visibility = if (playing) View.VISIBLE else View.GONE
        paintToggle(state == Link.OK)
        updateMap()
    }

    // CARTE
    private fun updateMap(force: Boolean = false) {
        if (!mapReady) return
        val p = getSharedPreferences("p", MODE_PRIVATE)
        val lat = p.getString("r_lat", null) ?: return
        val lng = p.getString("r_lng", null) ?: return
        val ts = p.getLong("r_pos_ts", 0L)
        val trace = p.getString("r_trace", "") ?: ""
        if (!force && ts == shownTs && trace == shownTrace) return
        shownTs = ts
        shownTrace = trace
        val acc = p.getInt("r_acc", 0)
        val pts = trace.split(";").filter { it.contains(",") }
            .joinToString(",", "[", "]") { "[$it]" }
        web.evaluateJavascript("setCar($lat,$lng,$acc,$pts)", null)
    }

    // Itinéraire vers la voiture (Google Maps, sinon une autre appli de cartes)
    private fun openRoute() {
        val p = getSharedPreferences("p", MODE_PRIVATE)
        val lat = p.getString("r_lat", null)
        val lng = p.getString("r_lng", null)
        if (lat == null || lng == null) {
            TopBanner.show(this, getString(R.string.msg_no_pos), false)
            return
        }
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
                    .setPackage("com.google.android.apps.maps")
            )
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(Voiture)")))
            } catch (_: Exception) {
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"))
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun scanCode() {
        if (!codeInput.isEnabled) {
            TopBanner.show(this, getString(R.string.scan_locked), false)
            return
        }
        try {
            GmsBarcodeScanning.getClient(this).startScan()
                .addOnSuccessListener { b ->
                    val code = (b.rawValue ?: "").removePrefix("autoalert:").trim()
                    if (code.matches(Regex("^[A-Za-z0-9]{8,32}$"))) {
                        codeInput.setText(code)
                        TopBanner.show(this, getString(R.string.scan_ok), true)
                    } else {
                        TopBanner.show(this, getString(R.string.scan_bad), false)
                    }
                }
                .addOnFailureListener {
                    TopBanner.show(this, getString(R.string.scan_fail), false)
                }
        } catch (_: Exception) {
            TopBanner.show(this, getString(R.string.scan_fail), false)
        }
    }

    // Demande à la voiture d'envoyer sa position maintenant
    private fun askCar() {
        val p = getSharedPreferences("p", MODE_PRIVATE)
        if (!p.getBoolean("r_active", false)) {
            TopBanner.show(this, getString(R.string.ask_start_first), false)
            return
        }
        val code = p.getString("code", "") ?: ""
        TopBanner.show(this, getString(R.string.ask_sent), true)

        // La carte se recentre sur la voiture, même si elle a été déplacée
        if (mapReady) web.evaluateJavascript("recenter()", null)

        Api.postAsync("ask", org.json.JSONObject().put("code", code)) { ok ->
            if (!ok) runOnUiThread { TopBanner.show(this, getString(R.string.ask_fail), false) }
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

    // BOUTON D'ACTIVATION : vert seulement si la voiture est réellement jointe
    private fun updateStatus(active: Boolean) {
        codeInput.isEnabled = !active
        codeInput.alpha = if (active) 0.5f else 1f
        val vis = if (active) View.GONE else View.VISIBLE
        codeTitle.visibility = vis
        codeCard.visibility = vis
        toggleIcon.setImageDrawable(
            Icons.drawable(this, if (active) Icons.Kind.LOCK_CLOSED else Icons.Kind.LOCK_OPEN, Color.WHITE, 24)
        )
        toggleBtn.contentDescription = getString(if (active) R.string.recv_btn_on else R.string.recv_btn_off)
        refreshInfo()
    }

    private fun paintToggle(connected: Boolean) {
        toggleBtn.background = GradientDrawable().apply {
            setColor(if (connected) Color.rgb(8, 120, 61) else Color.rgb(197, 22, 37))
            cornerRadius = dp(16).toFloat()
        }
    }

    // OUTILS D'INTERFACE
    private fun icon(kind: Icons.Kind, color: Int, sizeDp: Int) = Icons.drawable(this, kind, color, sizeDp)

    // Bouton gris (style de la page Plus) avec icône et texte centrés ensemble
    private fun iconButton(kind: Icons.Kind, label: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(235, 236, 239), Color.rgb(220, 223, 229), 12)
            isClickable = true
            setOnClickListener { onClick() }
            addView(
                ImageView(this@ReceiverActivity).apply { setImageDrawable(icon(kind, NOIR, 22)) },
                LinearLayout.LayoutParams(dp(22), dp(22))
            )
            addView(
                TextView(this@ReceiverActivity).apply {
                    text = label
                    textSize = 16f
                    setTextColor(NOIR)
                },
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) }
            )
        }
    }

    private fun infoRow(kind: Icons.Kind, color: Int, text: TextView): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                ImageView(this@ReceiverActivity).apply { setImageDrawable(icon(kind, color, 18)) },
                LinearLayout.LayoutParams(dp(18), dp(18))
            )
            addView(
                text,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(10)
                }
            )
        }
    }

    private fun infoLine() = TextView(this).apply {
        textSize = 14f
        setTextColor(GRIS_TEXTE)
    }

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

    // Même style gris que les boutons de la page Plus
    private fun grayButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(NOIR)
        background = rounded(Color.rgb(235, 236, 239), Color.rgb(220, 223, 229), 12)
        stateListAnimator = null
        setOnClickListener { onClick() }
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
        setTextColor(GRIS_TEXTE)
        setPadding(dp(4), dp(18), dp(4), dp(5))
    }

    private fun rounded(color: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
}

private const val MAP_HTML = """<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<style>html,body,#m{height:100%;margin:0}.car{font-size:28px;line-height:28px;text-align:center}</style>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
</head><body><div id="m"></div>
<script>
var map=L.map('m').setView([28.0,2.5],5);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'OpenStreetMap'}).addTo(map);
var mk=null,ci=null,first=true,follow=true;
var tr=L.polyline([],{color:'#c51625',weight:4}).addTo(map);
map.on('dragstart',function(){follow=false;});
function setCar(lat,lng,acc,pts){
  var p=[lat,lng];
  if(!mk){
    mk=L.marker(p,{icon:L.divIcon({className:'car',html:'&#128663;',iconSize:[28,28],iconAnchor:[14,14]})}).addTo(map);
    ci=L.circle(p,{radius:acc||0,color:'#1a73e8',weight:1,fillOpacity:0.1}).addTo(map);
  }else{
    mk.setLatLng(p);ci.setLatLng(p);ci.setRadius(acc||0);
  }
  tr.setLatLngs(pts);
  if(first){map.setView(p,16);first=false;}
  else if(follow){map.panTo(p);}
}
function recenter(){
  follow=true;
  if(mk){map.setView(mk.getLatLng(),Math.max(map.getZoom(),16));}
}
</script></body></html>"""
