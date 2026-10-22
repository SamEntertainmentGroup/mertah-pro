package com.autoalert.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import org.json.JSONObject
import kotlin.math.sqrt

class MotionService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID = "antivol_channel"
        private const val NOTIFICATION_ID = 1

        // Armement après 30 secondes
        private const val ARMEMENT = 30_000L

        // Durée minimale pendant laquelle le mouvement doit se maintenir
        private const val DUREE = 0L

        // Lissage des vibrations (secondes)
        private const val TAU_RAPIDE = 0.05f

        // Suivi des dérives lentes de la position de repos (secondes)
        private const val TAU_LENT = 30f
    }

    private var seuil = 0.3f
    private var armedAt = 0L
    private var nextAlertAt = 0L
    private var since = 0L

    private var hasLast = false
    private var lastT = 0L
    private val fast = FloatArray(3)
    private val rest = FloatArray(3)

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sensorManager: SensorManager
    private lateinit var locMgr: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null

    // GPS
    private var lastLoc: Location? = null
    private var tracking = false
    private var trackUntil = 0L

    private val locListener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            val c = lastLoc
            if (c == null ||
                l.provider == LocationManager.GPS_PROVIDER ||
                l.time - c.time > 20_000L ||
                l.accuracy <= c.accuracy
            ) {
                lastLoc = l
            }
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    // Position envoyée toutes les 15 s pendant le suivi
    private val tracker = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() > trackUntil) {
                stopTracking()
                return
            }
            send("pos")
            handler.postDelayed(this, Config.POS_EVERY_MS)
        }
    }

    // Signe de vie
    private val pinger = object : Runnable {
        override fun run() {
            send("ping")
            handler.postDelayed(this, Config.PING_EVERY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locMgr = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "antivol:motion")
        wakeLock?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val n = createNotification()
            when {
                Build.VERSION.SDK_INT >= 34 -> startForeground(
                    NOTIFICATION_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
                Build.VERSION.SDK_INT >= 29 -> startForeground(
                    NOTIFICATION_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
                else -> startForeground(NOTIFICATION_ID, n)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        seuil = getSharedPreferences("p", MODE_PRIVATE).getFloat("seuil", 0.3f)

        val now = System.currentTimeMillis()
        armedAt = now + ARMEMENT
        nextAlertAt = 0L
        since = 0L
        hasLast = false

        val accelerometer =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        sensorManager.unregisterListener(this)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)

        handler.removeCallbacks(pinger)
        handler.postDelayed(pinger, 5_000L)

        return START_STICKY
    }

    // DÉTECTION : écart entre la position actuelle du téléphone et sa position de repos
    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values

        if (!hasLast) {
            for (i in 0..2) {
                fast[i] = v[i]
                rest[i] = v[i]
            }
            lastT = event.timestamp
            hasLast = true
            return
        }

        val dt = ((event.timestamp - lastT) / 1_000_000_000f).coerceAtMost(0.1f)
        lastT = event.timestamp

        val aRapide = dt / (TAU_RAPIDE + dt)
        val aLent = dt / (TAU_LENT + dt)

        for (i in 0..2) {
            fast[i] += aRapide * (v[i] - fast[i])
        }

        val now = System.currentTimeMillis()
        if (now < armedAt || now < nextAlertAt) {
            for (i in 0..2) {
                rest[i] = fast[i]
            }
            since = 0L
            return
        }

        for (i in 0..2) {
            rest[i] += aLent * (fast[i] - rest[i])
        }

        val dx = fast[0] - rest[0]
        val dy = fast[1] - rest[1]
        val dz = fast[2] - rest[2]
        val ecart = sqrt(dx * dx + dy * dy + dz * dz)

        if (ecart <= seuil) {
            since = 0L
            return
        }

        if (since == 0L) since = now

        if (now - since >= DUREE) {
            since = 0L
            nextAlertAt = now + Config.ALERT_COOLDOWN_MS
            send("alert")
            startTracking()
        }
    }

    // ENVOI AU SERVEUR
    private fun send(type: String) {
        val code = getSharedPreferences("p", MODE_PRIVATE).getString("code", "") ?: ""
        if (code.isEmpty()) return

        val o = JSONObject().put("code", code).put("type", type)
        val bat = battery()
        if (bat in 0..100) o.put("battery", bat)

        if (type != "ping") {
            val l = lastLoc ?: lastKnown()
            if (l != null) {
                o.put("lat", l.latitude)
                o.put("lng", l.longitude)
                o.put("acc", l.accuracy.toInt())
            }
        }
        Api.postAsync("event", o)
    }

    private fun battery(): Int = try {
        (getSystemService(BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    } catch (_: Exception) {
        -1
    }

    private fun lastKnown(): Location? = try {
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        ).mapNotNull { locMgr.getLastKnownLocation(it) }.maxByOrNull { it.time }
    } catch (_: Exception) {
        null
    }

    // SUIVI GPS
    private fun startTracking() {
        trackUntil = System.currentTimeMillis() + Config.TRACK_MS
        if (tracking) return
        tracking = true

        try {
            for (prov in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (locMgr.isProviderEnabled(prov)) {
                    locMgr.requestLocationUpdates(prov, 5_000L, 0f, locListener, Looper.getMainLooper())
                }
            }
        } catch (_: Exception) {
        }

        handler.removeCallbacks(tracker)
        handler.postDelayed(tracker, 8_000L)
    }

    private fun stopTracking() {
        tracking = false
        try {
            locMgr.removeUpdates(locListener)
        } catch (_: Exception) {
        }
    }

    // NOTIFICATION
    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopTracking()
        sensorManager.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
}
