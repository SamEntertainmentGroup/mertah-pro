package com.autoalert.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin

// Signal d'alerte : double bip net, répété quelques fois (au lieu de la sonnerie de réveil)
object AlertSound {

    private const val RATE = 44100
    private const val CYCLES = 5          // 5 doubles bips, environ 7 secondes
    private const val FREQ = 1800.0       // hauteur du bip (Hz)

    private var track: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())

    // bip 180 ms - silence 100 ms - bip 180 ms - silence 900 ms
    private fun pattern(): ShortArray {
        val beep = RATE * 180 / 1000
        val gap = RATE * 100 / 1000
        val pause = RATE * 900 / 1000
        val cycle = beep + gap + beep + pause
        val fadeLen = RATE * 8 / 1000f
        val data = ShortArray(cycle * CYCLES)

        for (c in 0 until CYCLES) {
            var pos = c * cycle
            repeat(2) {
                for (i in 0 until beep) {
                    val fade = minOf(1f, i / fadeLen, (beep - i) / fadeLen)
                    val v = sin(2.0 * PI * FREQ * i / RATE) * 0.95 * fade
                    data[pos + i] = (v * Short.MAX_VALUE).toInt().toShort()
                }
                pos += beep + gap
            }
        }
        return data
    }

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

                val data = pattern()
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                t.write(data, 0, data.size)
                t.play()
                track = t

                handler.postDelayed({ stop() }, data.size * 1000L / RATE + 300L)
            } catch (_: Exception) {
            }
        }
    }

    fun isPlaying(): Boolean = track != null

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        track?.let {
            try { it.stop() } catch (_: Exception) {}
            try { it.release() } catch (_: Exception) {}
        }
        track = null
    }
}
