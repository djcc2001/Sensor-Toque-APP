package com.embebidos.sensoriot

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.embebidos.sensoriot.service.WebSocketService
import com.embebidos.sensoriot.ui.SensorScreen
import com.embebidos.sensoriot.ui.theme.SensorIoTTheme
import com.embebidos.sensoriot.viewmodel.SensorEstado
import com.embebidos.sensoriot.viewmodel.SensorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SensorViewModel by viewModels()

    private val pedirPermisoNotificacion = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* resultado ignorado, el servicio funciona sin el permiso */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FIX #3 (permiso): solo pedir si no fue concedido previamente
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permiso = android.Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permiso)
                != PackageManager.PERMISSION_GRANTED
            ) {
                pedirPermisoNotificacion.launch(permiso)
            }
        }

        val serviceIntent = Intent(this, WebSocketService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            SensorIoTTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0D1117)
                ) {
                    SensorScreen(viewModel = viewModel)
                }
            }
        }
    }

    // FIX #2: usar onStart/onStop en lugar de onCreate/onDestroy
    // para que el callback no se pierda al rotar la pantalla.
    override fun onStart() {
        super.onStart()
        // FIX #3 (runOnUiThread): onEstadoCambiado ya se invoca desde el main thread
        // via notificarUI — no hace falta runOnUiThread adicional
        WebSocketService.onEstadoCambiado = { nuevoEstado ->
            viewModel.actualizarEstado(nuevoEstado)
            viewModel.actualizarConexion(
                // FIX #3 (enum): comparar con el enum, no con .name
                if (nuevoEstado == SensorEstado.DESCONECTADO) "Reconectando..."
                else "Conectado ✅"
            )
        }
    }

    override fun onStop() {
        super.onStop()
        WebSocketService.onEstadoCambiado = null
    }

    override fun onResume() {
        super.onResume()
        WebSocketService.appEnPrimerPlano = true
    }

    override fun onPause() {
        super.onPause()
        WebSocketService.appEnPrimerPlano = false
    }
}