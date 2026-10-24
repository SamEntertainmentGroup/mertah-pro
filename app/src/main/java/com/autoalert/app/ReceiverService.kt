package com.autoalert.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONObject

class ReceiverService : Service() {

    companion object {
        const val CH_FG = "recv_channel"
        const val CH_ALERT = "alert_channel"
        const val ID_FG = 1
        const val ID_ALERT = 2
        const val ID_LOST = 3
        const val ID_INFO = 4
        const val CH_INFO = "info_channel"
    }

    @Volatile private var running = false
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var fails = 0

    override fun onCreate() {
        super.onCreate()
        createChannels()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "antivol:receiver")
        wakeLock?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val n = foregroundNotification(getString(R.string.recv_listening))
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID_FG, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID_FG, n)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!running) {
            running = true
            worker = Thread {
                while (running) {
                    try {
                        poll()
                    } catch (_: Exception) {
                    }
                    try {
                        Thread.sleep(Config.POLL_MS)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }.also { it.start() }
        }
        return START_STICKY
    }

    // INTERROGATION DU SERVEUR
    private fun poll() {
        val p = getSharedPreferences("p", MODE_PRIVATE)
        val code = p.getString("code", "") ?: ""
        if (code.isEmpty()) return

        val since = p.getLong("r_since", -1L)
        val res = Api.post("poll", JSONObject().put("code", code).put("since", since))

        if (res == null) {
            fails++
            if (fails == 6) updateForeground(getString(R.string.recv_offline))
            return
        }
        if (fails >= 6) updateForeground(getString(R.string.recv_listening))
        fails = 0

        // Dernier signe de vie de la voiture (âge en secondes, calculé par le serveur)
        val alive = res.optJSONObject("alive")
        p.edit()
            .putLong("r_poll_ms", System.currentTimeMillis())
            .putLong("r_alive_age", if (alive != null) alive.optLong("age", -1L) else -1L)
            .apply()
        if (alive != null && alive.has("battery") && !alive.isNull("battery")) {
            p.edit().putInt("r_battery", alive.optInt("battery")).apply()
        }

        // Alarme si la voiture ne répond plus (téléphone éteint, sans internet...)
        val off = alive?.optBoolean("off", false) ?: false
        p.edit().putBoolean("r_off", off).apply()
        val ageSec = alive?.optLong("age", 0L) ?: 0L
        if (alive != null && !off && ageSec > Config.LOST_AFTER_SEC) {
            if (!p.getBoolean("r_lost", false)) {
                p.edit().putBoolean("r_lost", true).apply()
                fireLost((ageSec / 60).toInt())
            }
        } else {
            p.edit().putBoolean("r_lost", false).apply()
        }

        var alert = false
        var stopped = false
        var trace = p.getString("r_trace", "") ?: ""
        val events = res.optJSONArray("events")
        if (events != null) {
            for (i in 0 until events.length()) {
                val e = events.getJSONObject(i)
                val ts = e.optLong("ts")
                val ed = p.edit()

                ed.putLong("r_alive_ts", ts)
                if (e.has("battery") && !e.isNull("battery")) {
                    ed.putInt("r_battery", e.optInt("battery"))
                }
                if (e.has("lat") && !e.isNull("lat") && e.has("lng") && !e.isNull("lng")) {
                    ed.putString("r_lat", e.getDouble("lat").toString())
                    ed.putString("r_lng", e.getDouble("lng").toString())
                    ed.putLong("r_pos_ts", ts)
                    if (e.has("acc") && !e.isNull("acc")) ed.putInt("r_acc", e.optInt("acc"))
                    trace = appendTrace(trace, e.getDouble("lat"), e.getDouble("lng"))
                }
                if (e.optString("type") == "off") stopped = true
                if (e.optString("type") == "alert") {
                    ed.putLong("r_alert_ts", ts)
                    alert = true
                }
                ed.apply()
            }
        }

        p.edit()
            .putLong("r_since", res.optLong("last_id", since))
            .putString("r_trace", trace)
            .apply()

        if (alert) fireAlert()
        if (stopped) notifyInfo(getString(R.string.recv_off_title), getString(R.string.recv_off_text))
    }

    // Les 60 dernières positions, pour tracer le trajet sur la carte
    private fun appendTrace(t: String, lat: Double, lng: Double): String {
        val pt = "$lat,$lng"
        val list = if (t.isEmpty()) mutableListOf() else t.split(";").toMutableList()
        if (list.lastOrNull() == pt) return t
        list.add(pt)
        return list.takeLast(60).joinToString(";")
    }

    // Action « Arrêter l'alarme » dans la notification
    private fun stopAction(): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, StopAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_lock_silent_mode),
            getString(R.string.recv_stop_alarm),
            pi
        ).build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, ReceiverActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // ALARME : la voiture ne répond plus
    private fun fireLost(min: Int) {
        AlertSound.play(this)
        val n = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.recv_lost_title))
            .setContentText(getString(R.string.recv_lost_text, min))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(stopAction())
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_LOST, n)
    }

    // Information simple, sans alarme
    private fun notifyInfo(title: String, text: String) {
        val n = Notification.Builder(this, CH_INFO)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_INFO, n)
    }

    // ALARME : bip + notification
    private fun fireAlert() {
        AlertSound.play(this)

        val p = getSharedPreferences("p", MODE_PRIVATE)
        val lat = p.getString("r_lat", null)
        val lng = p.getString("r_lng", null)

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, ReceiverActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val b = Notification.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.recv_alert_title))
            .setContentText(getString(R.string.recv_alert_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (lat != null && lng != null) {
            val map = PendingIntent.getActivity(
                this, 1,
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(Voiture)"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_dialog_map),
                    getString(R.string.recv_action_map),
                    map
                ).build()
            )
        }

        b.addAction(stopAction())
        getSystemService(NotificationManager::class.java).notify(ID_ALERT, b.build())
    }

    // NOTIFICATIONS
    private fun createChannels() {
        val m = getSystemService(NotificationManager::class.java)

        m.createNotificationChannel(
            NotificationChannel(CH_FG, getString(R.string.ch_recv), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )

        m.createNotificationChannel(
            NotificationChannel(CH_INFO, getString(R.string.ch_info), NotificationManager.IMPORTANCE_DEFAULT)
        )

        // Le bip est joué par AlertSound (volume d'alarme)
        m.createNotificationChannel(
            NotificationChannel(CH_ALERT, getString(R.string.ch_alert), NotificationManager.IMPORTANCE_HIGH)
                .apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
        )
    }

    private fun foregroundNotification(text: String): Notification =
        Notification.Builder(this, CH_FG)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(getString(R.string.recv_title))
            .setContentText(text)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

    private fun updateForeground(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(ID_FG, foregroundNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }
}
