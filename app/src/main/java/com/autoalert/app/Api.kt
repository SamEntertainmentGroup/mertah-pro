package com.autoalert.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object Api {

    private val io = Executors.newSingleThreadExecutor()

    // Appel synchrone : renvoie la réponse JSON ou null en cas d'échec
    fun post(path: String, body: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(Config.SERVER + "/?rest_route=/autoalert/v1/" + path)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            if (conn.responseCode != 200) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(text)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    // Envoi en arrière-plan avec 3 tentatives
    fun postAsync(path: String, body: JSONObject, onResult: ((Boolean) -> Unit)? = null) {
        io.execute {
            for (i in 0 until 3) {
                if (post(path, body) != null) {
                    onResult?.invoke(true)
                    return@execute
                }
                try { Thread.sleep(3000) } catch (_: InterruptedException) { return@execute }
            }
            onResult?.invoke(false)
        }
    }
}
