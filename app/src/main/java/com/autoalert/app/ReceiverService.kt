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

        var alert = false
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
                }
                if (e.optString("type") == "alert") {
                    ed.putLong("r_alert_ts", ts)
                    alert = true
                }
                ed.apply()
            }
        }

        p.edit().putLong("r_since", res.optLong("last_id", since)).apply()

        if (alert) fireAlert()
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

        getSystemService(NotificationManager::class.java).notify(ID_ALERT, b.build())
    }

    // NOTIFICATIONS
    private fun createChannels() {
        val m = getSystemService(NotificationManager::class.java)

        m.createNotificationChannel(
            NotificationChannel(CH_FG, getString(R.string.ch_recv), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
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
