package com.embebidos.sensoriot.service

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.embebidos.sensoriot.MainActivity
import com.embebidos.sensoriot.R
import com.embebidos.sensoriot.viewmodel.SensorEstado
import okhttp3.*
import org.json.JSONObject

class WebSocketService : Service() {

    private lateinit var client: OkHttpClient
    private var webSocket: WebSocket? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile private var isConnecting = false
    private var reintentos = 0

    companion object {
        const val CHANNEL_ID          = "sensor_channel"
        const val CHANNEL_TOQUE_ID    = "sensor_toque_channel"
        const val CHANNEL_APAGADO_ID  = "sensor_apagado_channel"
        const val NOTIF_FOREGROUND_ID = 1
        const val NOTIF_ALERTA_ID     = 2
        const val WS_URL              = "ws://3.131.82.32:3001"

        @Volatile var estadoActual: SensorEstado = SensorEstado.DESCONECTADO

        // Solo invocado desde el main thread (ver notificarUI)
        var onEstadoCambiado: ((SensorEstado) -> Unit)? = null

        var appEnPrimerPlano: Boolean = false

        @Volatile var instancia: WebSocketService? = null

        fun enviarComando(activar: Boolean) {
            val ws = instancia?.webSocket
            if (ws == null) {
                android.util.Log.w("WebSocketService", "enviarComando: WebSocket no disponible")
                return
            }
            ws.send("""{"tipo":"control","activo":$activar}""")
        }
    }

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instancia = this
        crearCanalesNotificacion()
        client = OkHttpClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifPersistente = crearNotificacionPersistente("Conectando al sensor...")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_FOREGROUND_ID,
                notifPersistente,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIF_FOREGROUND_ID, notifPersistente)
        }
        conectarWebSocket()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instancia = null
        webSocket?.cancel()
        webSocket = null
        client.dispatcher.executorService.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    // ─── Conexión WebSocket ───────────────────────────────────────────────────

    private fun conectarWebSocket() {
        if (isConnecting) return
        isConnecting = true

        webSocket?.close(1000, "Reconectando...")
        webSocket = null

        val request = Request.Builder().url(WS_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting = false
                reintentos = 0
                estadoActual = SensorEstado.LOW
                notificarUI(SensorEstado.LOW)
                actualizarNotificacionPersistente("✅ Conectado — Sensor activo")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                procesarMensaje(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnecting = false
                estadoActual = SensorEstado.DESCONECTADO
                notificarUI(SensorEstado.DESCONECTADO)
                actualizarNotificacionPersistente("❌ Sin conexión — Reintentando...")
                programarReconexion()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnecting = false
                estadoActual = SensorEstado.DESCONECTADO
                notificarUI(SensorEstado.DESCONECTADO)
                if (code != 1000) {
                    programarReconexion()
                }
            }
        })
    }

    private fun programarReconexion() {
        val delay = minOf(3000L * (1L shl reintentos), 60_000L)
        reintentos++
        android.util.Log.d("WebSocketService", "Reconectando en ${delay}ms (intento $reintentos)")
        mainHandler.postDelayed({ conectarWebSocket() }, delay)
    }

    // ─── Procesamiento de mensajes ────────────────────────────────────────────

    private fun procesarMensaje(texto: String) {
        try {
            val json = JSONObject(texto)
            val tipo = json.optString("tipo")

            when (tipo) {
                "normal" -> {
                    val valor = json.optInt("señalActual", 0)
                    val nuevoEstado = if (valor == 1) SensorEstado.HIGH else SensorEstado.LOW

                    if (nuevoEstado == SensorEstado.HIGH && estadoActual != SensorEstado.HIGH) {
                        estadoActual = SensorEstado.HIGH
                        mainHandler.post {
                            if (appEnPrimerPlano) {
                                reproducirSonido(R.raw.door)
                            } else {
                                abrirAppEnPrimerPlano()
                            }
                        }
                    } else {
                        estadoActual = nuevoEstado
                    }
                    notificarUI(nuevoEstado)
                }

                "control" -> {
                    val activo = json.optBoolean("sensorActivo", true)
                    if (!activo) {
                        estadoActual = SensorEstado.OFF
                        notificarUI(SensorEstado.OFF)
                        mainHandler.post {
                            if (appEnPrimerPlano) {
                                reproducirSonido(R.raw.warning)
                            } else {
                                // Usar el nuevo método que garantiza sonido con pantalla apagada
                                enviarNotificacionCritica(
                                    canal   = CHANNEL_APAGADO_ID,
                                    titulo  = "⚠️ Sensor apagado",
                                    mensaje = "El sensor fue desactivado remotamente",
                                    sonido  = R.raw.warning,
                                    notifId = 11
                                )
                            }
                        }
                    } else {
                        estadoActual = SensorEstado.LOW
                        notificarUI(SensorEstado.LOW)
                    }
                }

                "estado-inicial" -> {
                    val activo = json.optBoolean("sensorActivo", true)
                    val señal  = json.optInt("señalActual", 0)
                    val nuevoEstado = when {
                        !activo    -> SensorEstado.OFF
                        señal == 1 -> SensorEstado.HIGH
                        else       -> SensorEstado.LOW
                    }
                    estadoActual = nuevoEstado
                    notificarUI(nuevoEstado)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun notificarUI(estado: SensorEstado) {
        mainHandler.post {
            onEstadoCambiado?.invoke(estado)
        }
    }

    // ─── Sonido ───────────────────────────────────────────────────────────────

    fun reproducirSonido(recurso: Int) {
        try {
            val mediaPlayer = MediaPlayer.create(applicationContext, recurso)
            mediaPlayer?.setOnCompletionListener { it.release() }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ─── Notificaciones ───────────────────────────────────────────────────────

    private fun crearCanalesNotificacion() {
        val manager = getSystemService(NotificationManager::class.java)
        val atributos = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val canalPersistente = NotificationChannel(
            CHANNEL_ID, "Estado del Sensor IoT", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notificación persistente del servicio"
            setSound(null, null)
        }

        val canalToque = NotificationChannel(
            CHANNEL_TOQUE_ID, "Toque de puerta", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Suena cuando alguien toca el sensor"
            setSound(Uri.parse("android.resource://${packageName}/raw/door"), atributos)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 100, 300)
            setBypassDnd(true)
        }

        val canalApagado = NotificationChannel(
            CHANNEL_APAGADO_ID, "Sensor apagado", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Suena cuando el sensor se apaga remotamente"
            setSound(Uri.parse("android.resource://${packageName}/raw/warning"), atributos)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500)
            setBypassDnd(true)
        }

        manager.createNotificationChannel(canalPersistente)
        manager.createNotificationChannel(canalToque)
        manager.createNotificationChannel(canalApagado)
    }

    private fun crearNotificacionPersistente(texto: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sensor IoT")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun actualizarNotificacionPersistente(texto: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_FOREGROUND_ID, crearNotificacionPersistente(texto))
    }

    private fun enviarNotificacion(canal: String, titulo: String, mensaje: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, canal)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)  // Cambiado a false
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        val id = when (canal) {
            CHANNEL_TOQUE_ID   -> 10
            CHANNEL_APAGADO_ID -> 11
            else               -> NOTIF_ALERTA_ID
        }
        manager.notify(id, notif)
    }

    /**
     * Notificación crítica que suena incluso con pantalla apagada
     * Usa WakeLock para asegurar que Android procese el sonido
     */
    private fun enviarNotificacionCritica(
        canal: String,
        titulo: String,
        mensaje: String,
        sonido: Int,
        notifId: Int
    ) {
        // Adquirir WakeLock para asegurar que la notificación se procese
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager

        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SensorIoT::CriticalWakeLock"
        )
        wakeLock.acquire(5000L)

        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val sonidoUri = Uri.parse("android.resource://${packageName}/${sonido}")

            val notif = NotificationCompat.Builder(this, canal)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(sonidoUri)
                .setVibrate(longArrayOf(0, 500))
                .setOnlyAlertOnce(false)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(notifId, notif)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // Liberar el WakeLock después de un tiempo prudencial
            mainHandler.postDelayed({
                if (wakeLock.isHeld) wakeLock.release()
            }, 4000)
        }
    }

    private fun abrirAppEnPrimerPlano() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager

        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
            "SensorIoT::WakeLock"
        )

        wakeLock.acquire(8000L)

        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("desde_sensor", true)
            }

            val pendingIntent = PendingIntent.getActivity(
                this, 99, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(this, CHANNEL_TOQUE_ID)
                .setContentTitle("🚨 ¡Alguien tocó la puerta!")
                .setContentText("El sensor detectó contacto")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setTimeoutAfter(15000)
                .setOngoing(false)
                .build()

            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(99, notif)

            mainHandler.postDelayed({
                try {
                    if (!appEnPrimerPlano) {
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    if (wakeLock.isHeld) wakeLock.release()
                }
            }, 500)

        } catch (e: Exception) {
            if (wakeLock.isHeld) wakeLock.release()
            e.printStackTrace()
        }
    }
}