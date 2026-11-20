package pe.multichat.avisopagos

import android.content.Context
import org.json.JSONArray

/** Configuración y almacenamiento simple (SharedPreferences). */
object Config {
    private const val PREFS = "aviso_pagos"

    /**
     * Paquetes de apps que se escuchan. Plin llega desde la app de cada banco.
     * VERIFICA estos nombres con el "Registro" de la app: muestra el paquete
     * de cada notificación con monto que llega al celular.
     */
    val DEFAULT_PACKAGES = listOf(
        "com.bcp.innovacxion.yapeapp",     // Yape
        "pe.com.interbank.mobilebanking",  // Interbank (incluye Plin)
        "com.bbva.nxt_peru",               // BBVA (incluye Plin)
    )

    val APP_NAMES = mapOf(
        "com.bcp.innovacxion.yapeapp" to "Yape",
        "pe.com.interbank.mobilebanking" to "Interbank",
        "com.bbva.nxt_peru" to "BBVA",
    )

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun url(c: Context) = prefs(c).getString("url", "") ?: ""
    fun token(c: Context) = prefs(c).getString("token", "") ?: ""
    fun deviceName(c: Context) = prefs(c).getString("device", "Celular 1") ?: "Celular 1"
    fun ttsEnabled(c: Context) = prefs(c).getBoolean("tts", true)
    fun packages(c: Context): List<String> =
        (prefs(c).getString("packages", null) ?: DEFAULT_PACKAGES.joinToString("\n"))
            .lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun save(c: Context, url: String, token: String, device: String, tts: Boolean, packages: String) {
        prefs(c).edit()
            .putString("url", url.trim())
            .putString("token", token.trim())
            .putString("device", device.trim())
            .putBoolean("tts", tts)
            .putString("packages", packages)
            .apply()
    }

    // ---- Registro (últimas 40 líneas) ----
    @Synchronized
    fun addLog(c: Context, line: String) {
        val arr = JSONArray(prefs(c).getString("log", "[]"))
        val out = JSONArray().put(line)
        for (i in 0 until minOf(arr.length(), 39)) out.put(arr.getString(i))
        prefs(c).edit().putString("log", out.toString()).apply()
    }

    fun log(c: Context): List<String> {
        val arr = JSONArray(prefs(c).getString("log", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun clearLog(c: Context) = prefs(c).edit().putString("log", "[]").apply()

    // ---- Cola de envíos pendientes (si no hubo internet) ----
    @Synchronized
    fun pending(c: Context): MutableList<String> {
        val arr = JSONArray(prefs(c).getString("pending", "[]"))
        return (0 until arr.length()).map { arr.getString(it) }.toMutableList()
    }

    @Synchronized
    fun setPending(c: Context, items: List<String>) {
        val arr = JSONArray()
        items.takeLast(200).forEach { arr.put(it) }
        prefs(c).edit().putString("pending", arr.toString()).apply()
    }
}
