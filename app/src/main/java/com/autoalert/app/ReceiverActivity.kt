package com.autoalert.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class ReceiverActivity : Activity() {

    private enum class Link { STOPPED, WAIT, SERVER, NONE, CAR_OFF, OK, OLD }

    private lateinit var codeInput: EditText
    private lateinit var toggleBtn: Button
    private lateinit var configCard: LinearLayout
    private lateinit var notifBtn: Button
    private lateinit var batBtn: Button
    private lateinit var linkText: TextView
    private lateinit var web: WebView
    private var mapReady = false
    private var shownTs = -1L
    private var shownTrace = ""

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

        // CODE DE LA VOITURE
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
        codeCard.addView(
            grayButton(getString(R.string.scan_btn)) { scanCode() },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) }
        )
        root.addView(codeCard)

        // VOITURE : état, carte, boutons
        root.addView(sectionTitle(getString(R.string.recv_section_car)))
        val carCard = card()
        linkText = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(NOIR)
            setPadding(dp(2), 0, 0, dp(8))
        }
        carCard.addView(linkText)

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
        carCard.addView(web, LinearLayout.LayoutParams(-1, dp(240)))

        carCard.addView(
            grayButton(getString(R.string.ask_btn)) { askCar() },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) }
        )
        carCard.addView(
            grayButton(getString(R.string.details_btn)) { showDetails() },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) }
        )
        root.addView(carCard)

        // BOUTON D'ACTIVATION RECTANGULAIRE
        toggleBtn = Button(this).apply {
            isAllCaps = false
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            stateListAnimator = null
        }
        root.addView(toggleBtn, LinearLayout.LayoutParams(-1, dp(68)).apply { topMargin = dp(16) })

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
                .putLong("r_start_ms", System.currentTimeMillis())
                .putBoolean("r_lost", false)
                .putBoolean("r_off", false)
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
        getSystemService(NotificationManager::class.java).apply {
            cancel(ReceiverService.ID_ALERT)
            cancel(ReceiverService.ID_LOST)
        }
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
            Toast.makeText(this, R.string.msg_allow_settings, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
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
        val (state, min) = linkState()
        linkText.text = when (state) {
            Link.STOPPED -> getString(R.string.link_off)
            Link.WAIT -> getString(R.string.car_link_wait)
            Link.SERVER -> getString(R.string.link_server)
            Link.NONE -> getString(R.string.link_none)
            Link.CAR_OFF -> getString(R.string.link_car_off)
            Link.OK -> getString(R.string.link_ok, min)
            Link.OLD -> getString(R.string.link_old, min)
        }
        paintToggle(state == Link.OK)
        updateMap()
    }

    // Détails dans une fenêtre séparée
    private fun showDetails() {
        val p = getSharedPreferences("p", MODE_PRIVATE)
        val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        fun t(ms: Long) = if (ms > 0) fmt.format(Date(ms)) else getString(R.string.info_none)

        val pollMs = p.getLong("r_poll_ms", 0L)
        val age = p.getLong("r_alive_age", -1L)
        val aliveMs = if (age >= 0 && pollMs > 0) pollMs - age * 1000 else 0L

        val sb = StringBuilder()
        sb.append("🚨 ${getString(R.string.info_alert)} :\n${t(p.getLong("r_alert_ts", 0) * 1000)}\n\n")
        sb.append("📍 ${getString(R.string.info_pos)} :\n${t(p.getLong("r_pos_ts", 0) * 1000)}")
        val lat = p.getString("r_lat", null)?.toDoubleOrNull()
        val lng = p.getString("r_lng", null)?.toDoubleOrNull()
        if (lat != null && lng != null) sb.append("\n" + String.format("%.5f, %.5f", lat, lng))
        sb.append("\n\n💚 ${getString(R.string.info_alive)} :\n${t(aliveMs)}")
        if (p.contains("r_battery")) sb.append("\n\n🔋 ${p.getInt("r_battery", 0)}%")

        AlertDialog.Builder(this)
            .setTitle(R.string.details_title)
            .setMessage(sb.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
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

    private fun scanCode() {
        if (!codeInput.isEnabled) {
            Toast.makeText(this, R.string.scan_locked, Toast.LENGTH_LONG).show()
            return
        }
        try {
            GmsBarcodeScanning.getClient(this).startScan()
                .addOnSuccessListener { b ->
                    val code = (b.rawValue ?: "").removePrefix("autoalert:").trim()
                    if (code.matches(Regex("^[A-Za-z0-9]{8,32}$"))) {
                        codeInput.setText(code)
                        Toast.makeText(this, R.string.scan_ok, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, R.string.scan_bad, Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, R.string.scan_fail, Toast.LENGTH_LONG).show()
                }
        } catch (_: Exception) {
            Toast.makeText(this, R.string.scan_fail, Toast.LENGTH_LONG).show()
        }
    }

    // Demande à la voiture d'envoyer sa position maintenant
    private fun askCar() {
        val p = getSharedPreferences("p", MODE_PRIVATE)
        if (!p.getBoolean("r_active", false)) {
            Toast.makeText(this, R.string.ask_start_first, Toast.LENGTH_LONG).show()
            return
        }
        val code = p.getString("code", "") ?: ""
        Toast.makeText(this, R.string.ask_sent, Toast.LENGTH_LONG).show()

        // La carte se recentre sur la voiture, même si elle a été déplacée
        if (mapReady) web.evaluateJavascript("recenter()", null)

        Api.postAsync("ask", org.json.JSONObject().put("code", code)) { ok ->
            if (!ok) runOnUiThread { Toast.makeText(this, R.string.ask_fail, Toast.LENGTH_LONG).show() }
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
        toggleBtn.text = getString(if (active) R.string.recv_btn_on else R.string.recv_btn_off)
        refreshInfo()
    }

    private fun paintToggle(connected: Boolean) {
        toggleBtn.background = GradientDrawable().apply {
            setColor(if (connected) Color.rgb(8, 120, 61) else Color.rgb(197, 22, 37))
            cornerRadius = dp(16).toFloat()
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
