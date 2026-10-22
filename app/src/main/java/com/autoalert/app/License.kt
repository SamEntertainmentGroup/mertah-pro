
package com.autoalert.app

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object License {

    // Page Facebook professionnelle AutoAlert
    const val MESSENGER = "https://m.me/61595770670414"

    // Même clé que dans codes.php : à remplacer par une clé secrète personnelle
    private const val SECRET = "CHANGE-THIS-LONG-RANDOM-SECRET"

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val EPOCH_DAYS = 20089L
    private const val MAC_BITS = 34
    private const val DAY_MS = 86_400_000L

    private fun today(): Long =
        System.currentTimeMillis() / DAY_MS - EPOCH_DAYS

    // Code appareil : 8 caractères
    fun deviceCode(ctx: Context): String {
        val id = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        val hash = MessageDigest.getInstance("SHA-256")
            .digest("antivol:$id".toByteArray())

        var v = 0L
        for (i in 0 until 5) {
            v = (v shl 8) or (hash[i].toLong() and 0xFF)
        }

        return encode(v, 8)
    }

    fun deviceCodeDisplay(ctx: Context): String {
        val c = deviceCode(ctx)
        return c.substring(0, 4) + "-" + c.substring(4)
    }

    // 0 = valide, 1 = invalide, 2 = expiré
    fun activate(ctx: Context, raw: String): Int {
        val code = normalize(raw)
        val expiry = expiryOf(ctx, code) ?: return 1

        if (expiry < today()) return 2

        ctx.getSharedPreferences("p", Context.MODE_PRIVATE)
            .edit()
            .putString("unlock_code", code)
            .apply()

        return 0
    }

    // Date d'expiration si le déblocage est valide
    fun unlockedUntil(ctx: Context): Long? {
        val code = ctx.getSharedPreferences("p", Context.MODE_PRIVATE)
            .getString("unlock_code", null) ?: return null

        val expiry = expiryOf(ctx, code) ?: return null

        return if (expiry >= today()) expiry else null
    }

    fun isUnlocked(ctx: Context): Boolean =
        unlockedUntil(ctx) != null

    fun formatDate(day: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM)
            .format(Date((day + EPOCH_DAYS) * DAY_MS))

    // OUTILS INTERNES
    private fun normalize(raw: String): String =
        raw.uppercase().filter { ALPHABET.indexOf(it) >= 0 }

    private fun expiryOf(ctx: Context, code: String): Long? {
        if (code.length != 10) return null

        val v = decode(code) ?: return null
        val expiry = v ushr MAC_BITS
        val mac = v and ((1L shl MAC_BITS) - 1)

        return if (mac(deviceCode(ctx), expiry) == mac) {
            expiry
        } else {
            null
        }
    }

    private fun mac(device: String, expiry: Long): Long {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(SECRET.toByteArray(), "HmacSHA256"))

        val h = m.doFinal("$device:$expiry".toByteArray())

        var v = 0L
        for (i in 0 until 5) {
            v = (v shl 8) or (h[i].toLong() and 0xFF)
        }

        return v and ((1L shl MAC_BITS) - 1)
    }

    private fun encode(value: Long, len: Int): String {
        val sb = StringBuilder()

        for (i in len - 1 downTo 0) {
            sb.append(
                ALPHABET[((value shr (5 * i)) and 31L).toInt()]
            )
        }

        return sb.toString()
    }

    private fun decode(s: String): Long? {
        var v = 0L

        for (c in s) {
            val idx = ALPHABET.indexOf(c)
            if (idx < 0) return null

            v = (v shl 5) or idx.toLong()
        }

        return v
    }
}
