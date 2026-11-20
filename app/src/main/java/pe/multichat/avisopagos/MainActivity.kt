package pe.multichat.avisopagos

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.util.Locale

/** Pantalla de configuración (hecha por código, sin XML). */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var logView: TextView
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("es", "PE") }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        fun label(t: String) = root.addView(TextView(this).apply { text = t; setPadding(0, 24, 0, 4) })
        fun field(value: String, hint: String, multi: Boolean = false) = EditText(this).apply {
            setText(value); this.hint = hint
            if (multi) { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 3 }
            root.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        fun button(t: String, onClick: () -> Unit) = root.addView(Button(this).apply {
            text = t; setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        status = TextView(this).apply { textSize = 16f }
        root.addView(status)

        label("URL de destino (Multichat o hosting)")
        val url = field(Config.url(this), "http://192.168.1.50:8080/pago")
        label("Token (opcional)")
        val token = field(Config.token(this), "clave-secreta")
        label("Nombre de este celular")
        val device = field(Config.deviceName(this), "Celular 1")
        label("Apps a escuchar (un paquete por línea)")
        val pkgs = field(Config.packages(this).joinToString("\n"), "com.ejemplo.app", multi = true)
        val ttsBox = CheckBox(this).apply { text = "Anunciar pagos en voz alta"; isChecked = Config.ttsEnabled(this@MainActivity) }
        root.addView(ttsBox)

        button("Guardar") {
            Config.save(this, url.text.toString(), token.text.toString(), device.text.toString(),
                ttsBox.isChecked, pkgs.text.toString())
            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
        }
        button("1. Dar acceso a notificaciones") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        button("2. Excluir del ahorro de batería") {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Ya está excluida", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")))
            }
        }
        button("Probar (pago simulado)") {
            val p = Payment("prueba", "25.50", "Cliente de Prueba", "PRUEBA", System.currentTimeMillis())
            if (ttsBox.isChecked) tts?.speak(p.spoken(), TextToSpeech.QUEUE_ADD, null, "test")
            Sender.send(this, p.toJson(Config.deviceName(this)))
            Toast.makeText(this, "Prueba enviada a ${Config.url(this)}", Toast.LENGTH_LONG).show()
        }
        button("Limpiar registro") { Config.clearLog(this); refresh() }

        label("Registro (notificaciones con monto)")
        logView = TextView(this).apply { textSize = 12f; setTextIsSelectable(true) }
        root.addView(logView)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        Sender.flush(this)
        refresh()
    }

    private fun refresh() {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        status.text = if (enabled) "● Escuchando notificaciones" else "● SIN acceso a notificaciones (paso 1)"
        status.setTextColor(if (enabled) Color.rgb(0, 140, 60) else Color.RED)
        val pend = Config.pending(this).size
        val lines = Config.log(this)
        logView.text = (if (pend > 0) "⚠ $pend pago(s) pendientes de envío\n\n" else "") +
            (if (lines.isEmpty()) "(vacío)" else lines.joinToString("\n\n"))
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
