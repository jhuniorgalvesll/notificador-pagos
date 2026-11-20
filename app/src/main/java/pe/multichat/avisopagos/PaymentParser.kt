package pe.multichat.avisopagos

import org.json.JSONObject

data class Payment(
    val app: String,
    val amount: String,      // normalizado, ej. "1250.50"
    val payer: String?,      // quién pagó, si se pudo leer
    val raw: String,         // texto original de la notificación
    val time: Long,
) {
    fun toJson(device: String): String = JSONObject()
        .put("app", app)
        .put("monto", amount)
        .put("moneda", "PEN")
        .put("pagador", payer ?: JSONObject.NULL)
        .put("texto_original", raw)
        .put("fecha_ms", time)
        .put("dispositivo", device)
        .toString()

    /** Frase que se dice en voz alta. */
    fun spoken(): String {
        val parts = amount.split(".")
        val soles = parts[0]
        val cent = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toIntOrNull() ?: 0
        val monto = if (cent > 0) "$soles soles con $cent céntimos" else "$soles soles"
        val de = if (payer.isNullOrBlank()) "" else ", de $payer"
        return "Pago recibido por $app: $monto$de"
    }
}

object PaymentParser {

    // S/ 50, S/50.00, S/. 1,250.50, S/ 12,5
    private val AMOUNT = Regex("""S/\.?\s*([0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)""")

    // Palabras que indican dinero ENTRANTE
    private val INCOMING = listOf(
        "recib", "te yape", "yapeó", "yapeo", "te envi", "te envió", "te pag",
        "abono", "abonó", "deposit", "transferencia recibida", "te plin", "plineó",
    )

    // Palabras que indican dinero SALIENTE (se ignoran)
    private val OUTGOING = listOf("enviaste", "pagaste", "yapeaste", "realizaste", "transferiste", "plineaste")

    private val PAYER_PATTERNS = listOf(
        Regex("""^(.+?)\s+te\s+(?:yape[oó]|envi[oó]|pag[oó]|transfiri[oó]|deposit[oó]|abon[oó]|pline[oó])""", RegexOption.IGNORE_CASE),
        Regex("""\bde\s+([A-ZÁÉÍÓÚÑ][\wÁÉÍÓÚÑáéíóúñ.* ]{2,40}?)(?=\s+(?:por|con|a\s+tu|en)\b|[.,\n]|$)"""),
    )

    fun parse(app: String, title: String, text: String, time: Long): Payment? {
        val full = "$title. $text".trim()
        val lower = full.lowercase()

        if (OUTGOING.any { lower.contains(it) }) return null
        if (INCOMING.none { lower.contains(it) }) return null

        val m = AMOUNT.find(full) ?: return null
        val amount = normalize(m.groupValues[1]) ?: return null

        val payer = findPayer(text) ?: findPayer(title) ?: findPayer(full)
        return Payment(app, amount, payer, full, time)
    }

    private fun findPayer(s: String): String? {
        for (p in PAYER_PATTERNS) {
            val g = p.find(s.trim())?.groupValues?.getOrNull(1)?.substringAfterLast('!')?.trim()?.trimEnd('.', ',')
            if (!g.isNullOrBlank() && !g.contains("S/") && g.length in 3..45) return g
        }
        return null
    }

    /** "1,250.50" -> "1250.50"   "12,5" -> "12.50"   "50" -> "50.00" */
    fun normalize(s: String): String? {
        val clean = when {
            s.contains(",") && s.contains(".") -> s.replace(",", "")
            Regex("""^\d+,\d{1,2}$""").matches(s) -> s.replace(",", ".")
            else -> s.replace(",", "")
        }
        val v = clean.toBigDecimalOrNull() ?: return null
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    }
}
