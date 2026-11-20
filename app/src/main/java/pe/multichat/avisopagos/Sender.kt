package pe.multichat.avisopagos

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Envía cada pago (JSON por POST) al Multichat o al hosting. Si falla, lo guarda y reintenta. */
object Sender {

    fun send(c: Context, json: String) {
        val ctx = c.applicationContext
        thread {
            val queue = Config.pending(ctx)
            queue.add(json)
            val failed = mutableListOf<String>()
            for (item in queue) {
                if (!post(ctx, item)) failed.add(item)
            }
            Config.setPending(ctx, failed)
            if (failed.isNotEmpty()) Config.addLog(ctx, "⚠ ${failed.size} pago(s) pendientes de envío")
        }
    }

    /** Reintenta lo pendiente (se llama también desde la pantalla principal). */
    fun flush(c: Context) {
        val ctx = c.applicationContext
        thread {
            val failed = Config.pending(ctx).filterNot { post(ctx, it) }
            Config.setPending(ctx, failed)
        }
    }

    private fun post(c: Context, json: String): Boolean {
        val url = Config.url(c)
        if (url.isBlank()) return false
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val token = Config.token(c)
            if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
