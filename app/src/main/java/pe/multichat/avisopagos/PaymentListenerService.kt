package pe.multichat.avisopagos

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android llama a este servicio cada vez que llega una notificación
 * (requiere activar "Acceso a notificaciones" para Aviso Pagos).
 */
class PaymentListenerService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val recent = LinkedHashMap<String, Long>()   // evita duplicados

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val r = tts?.setLanguage(Locale("es", "PE")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (r < 0) tts?.setLanguage(Locale("es"))
            ttsReady = true
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        val hora = SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date())

        val allowed = Config.packages(this).contains(pkg)

        // Registro para calibrar: toda notificación que mencione soles
        if ("S/" in title || "S/" in text) {
            Config.addLog(this, "$hora  [$pkg]${if (allowed) "" else " (no escuchado)"}\n   $title | $text")
        }
        if (!allowed) return

        // Duplicados: la misma notificación puede llegar 2+ veces
        val key = "$pkg|$title|$text"
        val now = System.currentTimeMillis()
        recent.entries.removeAll { now - it.value > 120_000 }
        if (recent.containsKey(key)) return
        recent[key] = now

        val appName = Config.APP_NAMES[pkg] ?: appLabel(pkg)
        val payment = PaymentParser.parse(appName, title, text, sbn.postTime)

        // Siempre se reenvía la notificación tal cual llegó; el Multichat la interpreta.
        // Si la app logró leer monto/pagador, se agregan como ayuda.
        val json = org.json.JSONObject()
            .put("app", appName)
            .put("paquete", pkg)
            .put("titulo", title)
            .put("texto", text)
            .put("fecha_ms", sbn.postTime)
            .put("dispositivo", Config.deviceName(this))
            .put("monto", payment?.amount ?: org.json.JSONObject.NULL)
            .put("pagador", payment?.payer ?: org.json.JSONObject.NULL)
            .put("es_pago_recibido", payment != null)
        Sender.send(this, json.toString())

        if (payment != null) {
            Config.addLog(this, "$hora  ✅ ${payment.app}  S/ ${payment.amount}  ${payment.payer ?: ""}")
            if (Config.ttsEnabled(this)) speak(payment.spoken())
        }
    }

    private fun speak(msg: String) {
        if (ttsReady) tts?.speak(msg, TextToSpeech.QUEUE_ADD, null, "pago_${System.nanoTime()}")
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
