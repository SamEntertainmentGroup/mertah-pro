package com.autoalert.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Bouton « Arrêter l'alarme » de la notification : coupe le son sans ouvrir l'app
class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlertSound.stop()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(ReceiverService.ID_ALERT)
        nm.cancel(ReceiverService.ID_LOST)
    }
}
