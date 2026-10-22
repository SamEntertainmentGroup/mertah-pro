package com.autoalert.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper

object AlertSound {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun play(ctx: Context) {
        handler.post {
            stop()
            try {
                val app = ctx.applicationContext

                // Volume d'alarme relevé s'il est trop bas
                val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max * 7 / 10) {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, max * 8 / 10, 0)
                }

                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(app, uri)
                    isLooping = true
                    prepare()
                    start()
                }
                handler.postDelayed({ stop() }, 20_000L)
            } catch (_: Exception) {
            }
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        player = null
    }
}
