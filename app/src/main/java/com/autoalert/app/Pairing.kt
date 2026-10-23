package com.autoalert.app

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom

object Pairing {

    // Sans caractères ambigus (0/O, 1/l/I)
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newCode(len: Int = 12): String {
        val r = SecureRandom()
        return (1..len).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    fun qrBitmap(text: String, size: Int = 700): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
