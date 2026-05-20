package com.embebidos.sensoriot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.embebidos.sensoriot.R
import com.embebidos.sensoriot.service.WebSocketService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SensorEstado { HIGH, LOW, OFF, DESCONECTADO }

data class Toque(
    val inicio: String,
    val fin: String,
    val duracion: String
)

class SensorViewModel : ViewModel() {

    private val _estado = MutableStateFlow(SensorEstado.DESCONECTADO)
    val estado: StateFlow<SensorEstado> = _estado

    private val _conexion = MutableStateFlow("Conectando...")
    val conexion: StateFlow<String> = _conexion

    private val _toques = MutableStateFlow<List<Toque>>(emptyList())
    val toques: StateFlow<List<Toque>> = _toques

    private val _cargandoHistorial = MutableStateFlow(false)
    val cargandoHistorial: StateFlow<Boolean> = _cargandoHistorial

    private val _errorHistorial = MutableStateFlow("")
    val errorHistorial: StateFlow<String> = _errorHistorial

    fun actualizarEstado(nuevoEstado: SensorEstado) {
        _estado.value = nuevoEstado
    }

    fun actualizarConexion(mensaje: String) {
        _conexion.value = mensaje
    }

    fun toggleSensor(activar: Boolean) {
        // Actualizar estado optimistamente para que la UI responda de inmediato
        _estado.value = if (activar) SensorEstado.LOW else SensorEstado.OFF

        // FIX #2: Sincronizar estadoActual del service para que la lógica
        // de detección de HIGH en procesarMensaje esté alineada
        WebSocketService.estadoActual = if (activar) SensorEstado.LOW else SensorEstado.OFF

        // Reproducir warning localmente al apagar desde la app
        if (!activar) {
            WebSocketService.instancia?.reproducirSonido(R.raw.warning)
        }

        WebSocketService.enviarComando(activar)
    }

    fun cargarHistorial(fecha: String? = null) {
        val fechaConsulta = fecha ?: SimpleDateFormat(
            "yyyy-MM-dd", Locale.getDefault()
        ).format(Date())

        viewModelScope.launch {
            _cargandoHistorial.value = true
            _errorHistorial.value = ""
            try {
                val resultado = withContext(Dispatchers.IO) {
                    val url = URL("http://3.131.82.32:3002/api/reporte?fecha=$fechaConsulta")
                    // FIX #5: usar finally para garantizar disconnect() aunque falle readText()
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    try {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        conn.disconnect()
                    }
                }

                val json = JSONObject(resultado)
                val toquesJson = json.optJSONArray("toques")
                val lista = mutableListOf<Toque>()

                if (toquesJson != null) {
                    for (i in 0 until toquesJson.length()) {
                        val t = toquesJson.getJSONObject(i)
                        lista.add(
                            Toque(
                                inicio   = t.optString("inicio", "--"),
                                fin      = t.optString("fin", "--"),
                                duracion = t.optString("duracion", "--")
                            )
                        )
                    }
                }
                _toques.value = lista

            } catch (e: Exception) {
                _errorHistorial.value = "No se pudo cargar el historial"
            } finally {
                _cargandoHistorial.value = false
            }
        }
    }
}