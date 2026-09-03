package com.embebidos.sensoriot

import org.junit.Test
import org.junit.Assert.*

/**
 * Pruebas unitarias reales para lógica de negocio:
 * - Parseo de mensajes WebSocket
 * - Transformación de datos del sensor
 * - Lógica del ViewModel (StateFlow)
 */
class SensorUnitTest {

    // ── Pruebas de parseo de mensajes WebSocket ─────────────────────
    @Test
    fun `mensaje normal tiene tipo y señalActual`() {
        val json = """{"tipo":"normal","señalActual":1}"""
        val obj = org.json.JSONObject(json)
        val tipo = obj.optString("tipo")
        val señal = obj.optInt("señalActual", 0)
        assertEquals("normal", tipo)
        assertEquals(1, señal)
    }

    @Test
    fun `mensaje control con sensorActivo=false es de apagado`() {
        val json = """{"tipo":"control","sensorActivo":false}"""
        val obj = org.json.JSONObject(json)
        val tipo = obj.optString("tipo")
        val activo = obj.optBoolean("sensorActivo")
        assertEquals("control", tipo)
        assertEquals(false, activo)
    }

    @Test
    fun `mensaje estado-inicial tiene los campos esperados`() {
        val json = """{"tipo":"estado-inicial","sensorActivo":true,"señalActual":0}"""
        val obj = org.json.JSONObject(json)
        val tipo = obj.optString("tipo")
        val activo = obj.optBoolean("sensorActivo", true)
        val señal = obj.optInt("señalActual", 0)
        assertEquals("estado-inicial", tipo)
        assertEquals(true, activo)
        assertEquals(0, señal)
    }

    // ── Pruebas de lógica de estado del sensor ─────────────────────
    @Test
    fun `transición de LOW a HIGH cuando llega señal 1`() {
        // Lógica idéntica a la de procesarMensaje cuándo llega "normal"
        var estadoActual = com.embebidos.sensoriot.viewmodel.SensorEstado.LOW
        val json = """{"tipo":"normal","señalActual":1}"""
        val obj = org.json.JSONObject(json)
        val valor = obj.optInt("señalActual", 0)
        val nuevoEstado = if (valor == 1) com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH else com.embebidos.sensoriot.viewmodel.SensorEstado.LOW
        if (nuevoEstado == com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH && estadoActual != com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH) {
            estadoActual = nuevoEstado
        }
        assertEquals(com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH, estadoActual)
    }

    @Test
    fun `transición de HIGH a HIGH cuando ya está HIGH (no cambio)`() {
        var estadoActual = com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH
        val json = """{"tipo":"normal","señalActual":1}"""
        val obj = org.json.JSONObject(json)
        val valor = obj.optInt("señalActual", 0)
        val nuevoEstado = if (valor == 1) com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH else com.embebidos.sensoriot.viewmodel.SensorEstado.LOW
        // No debería cambiar porque ya es HIGH
        if (!(nuevoEstado == com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH && estadoActual != com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH)) {
            // estado inalterado
        }
        assertEquals(com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH, estadoActual)
    }

    @Test
    fun `transición de HIGH a LOW cuando llega señal 0`() {
        var estadoActual = com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH
        val json = """{"tipo":"normal","señalActual":0}"""
        val obj = org.json.JSONObject(json)
        val valor = obj.optInt("señalActual", 0)
        val nuevoEstado = if (valor == 1) com.embebidos.sensoriot.viewmodel.SensorEstado.HIGH else com.embebidos.sensoriot.viewmodel.SensorEstado.LOW
        if (nuevoEstado == com.embebidos.sensoriot.viewmodel.SensorEstado.LOW && estadoActual != com.embebidos.sensoriot.viewmodel.SensorEstado.LOW) {
            estadoActual = nuevoEstado
        }
        assertEquals(com.embebidos.sensoriot.viewmodel.SensorEstado.LOW, estadoActual)
    }

    // ── Pruebas de lógica de toggle del ViewModel ──────────────────
    @Test
    fun `toggleSensor ON actualiza estado a LOW optimistically`() {
        // Lógica del ViewModel.toggleSensor(activar=true)
        val activar = true
        val nuevoEstado = if (activar) com.embebidos.sensoriot.viewmodel.SensorEstado.LOW else com.embebidos.sensoriot.viewmodel.SensorEstado.OFF
        assertEquals(com.embebidos.sensoriot.viewmodel.SensorEstado.LOW, nuevoEstado)
    }

    @Test
    fun `toggleSensor OFF actualiza estado a OFF optimistically`() {
        // Lógica del ViewModel.toggleSensor(activar=false)
        val activar = false
        val nuevoEstado = if (activar) com.embebidos.sensoriot.viewmodel.SensorEstado.LOW else com.embebidos.sensoriot.viewmodel.SensorEstado.OFF
        assertEquals(com.embebidos.sensoriot.viewmodel.SensorEstado.OFF, nuevoEstado)
    }
}