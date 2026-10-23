package com.autoalert.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Après un redémarrage : le récepteur repart seul, la voiture demande de rouvrir l'app
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val p = context.getSharedPreferences("p", Context.MODE_PRIVATE)
        val role = p.getString("role", null)

        if (role == "receiver" && p.getBoolean("r_active", false)) {
            p.edit().putLong("r_since", -1L).apply()
            try {
                context.startForegroundService(Intent(context, ReceiverService::class.java))
            } catch (_: Exception) {
            }
        }

        if (role == "car" && p.getBoolean("active", false)) {
            try {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.createNotificationChannel(
                    NotificationChannel("boot_channel", context.getString(R.string.ch_boot), NotificationManager.IMPORTANCE_HIGH)
                )
                val open = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                nm.notify(
                    5,
                    Notification.Builder(context, "boot_channel")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle(context.getString(R.string.boot_title))
                        .setContentText(context.getString(R.string.boot_text))
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build()
                )
            } catch (_: Exception) {
            }
        }
    }
}
