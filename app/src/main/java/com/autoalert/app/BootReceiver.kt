package com.autoalert.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Redémarre l'écoute du récepteur après un redémarrage du téléphone
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val p = context.getSharedPreferences("p", Context.MODE_PRIVATE)
        if (p.getString("role", null) == "receiver" && p.getBoolean("r_active", false)) {
            p.edit().putLong("r_since", -1L).apply()
            try {
                context.startForegroundService(Intent(context, ReceiverService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
