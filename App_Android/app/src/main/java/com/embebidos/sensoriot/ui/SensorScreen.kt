package com.embebidos.sensoriot.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.embebidos.sensoriot.viewmodel.SensorEstado
import com.embebidos.sensoriot.viewmodel.SensorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(viewModel: SensorViewModel) {
    val estado by viewModel.estado.collectAsState()
    val conexion by viewModel.conexion.collectAsState()
    val toques by viewModel.toques.collectAsState()
    val cargando by viewModel.cargandoHistorial.collectAsState()
    val errorHistorial by viewModel.errorHistorial.collectAsState()

    var mostrarHistorial by remember { mutableStateOf(false) }

    val colorPrimario by animateColorAsState(
        targetValue = when (estado) {
            SensorEstado.HIGH         -> Color(0xFFFFD600)
            SensorEstado.LOW          -> Color(0xFF00E676)
            SensorEstado.OFF          -> Color(0xFFFF5722)
            SensorEstado.DESCONECTADO -> Color(0xFF9E9E9E)
        },
        animationSpec = tween(400),
        label = "colorPrimario"
    )

    val icono = when (estado) {
        SensorEstado.HIGH         -> "🚨"
        SensorEstado.LOW          -> "✅"
        SensorEstado.OFF          -> "⚠️"
        SensorEstado.DESCONECTADO -> "📡"
    }

    val titulo = when (estado) {
        SensorEstado.HIGH         -> "¡Alguien tocó\nla puerta!"
        SensorEstado.LOW          -> "Todo tranquilo"
        SensorEstado.OFF          -> "Sensor apagado"
        SensorEstado.DESCONECTADO -> "Sin conexión"
    }

    val subtitulo = when (estado) {
        SensorEstado.HIGH         -> "El sensor detectó contacto"
        SensorEstado.LOW          -> "El sensor está libre y activo"
        // FIX menor: texto genérico para no confundir apagado local vs remoto
        SensorEstado.OFF          -> "El sensor está desactivado"
        SensorEstado.DESCONECTADO -> "Intentando reconectar al servidor..."
    }

    val estaActivo = estado != SensorEstado.OFF && estado != SensorEstado.DESCONECTADO

    // Animación de pulso solo cuando HIGH
    val pulso = remember { Animatable(1f) }
    LaunchedEffect(estado) {
        if (estado == SensorEstado.HIGH) {
            pulso.animateTo(
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = EaseInOut),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            pulso.animateTo(1f, animationSpec = tween(300))
        }
    }

    // Modal historial
    if (mostrarHistorial) {
        var mostrarDatePicker by remember { mutableStateOf(false) }
        var fechaSeleccionada by remember {
            mutableStateOf(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            )
        }
        val fechaMostrar = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .parse(fechaSeleccionada)?.let {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
            } ?: fechaSeleccionada

        if (mostrarDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = System.currentTimeMillis()
            )
            DatePickerDialog(
                onDismissRequest = { mostrarDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            fechaSeleccionada = SimpleDateFormat(
                                "yyyy-MM-dd", Locale.getDefault()
                            ).format(Date(millis))
                            viewModel.cargarHistorial(fechaSeleccionada)
                        }
                        mostrarDatePicker = false
                    }) { Text("Confirmar", color = Color(0xFF00E676)) }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDatePicker = false }) {
                        Text("Cancelar", color = Color(0xFF8B949E))
                    }
                },
                colors = DatePickerDefaults.colors(
                    containerColor = Color(0xFF161B22)
                )
            ) {
                DatePicker(
                    state = datePickerState,
                    colors = DatePickerDefaults.colors(
                        containerColor = Color(0xFF161B22),
                        titleContentColor = Color.White,
                        headlineContentColor = Color.White,
                        weekdayContentColor = Color(0xFF8B949E),
                        subheadContentColor = Color(0xFF8B949E),
                        dayContentColor = Color.White,
                        selectedDayContainerColor = Color(0xFF00E676),
                        selectedDayContentColor = Color.Black,
                        todayContentColor = Color(0xFF00E676),
                        todayDateBorderColor = Color(0xFF00E676)
                    )
                )
            }
        }

        Dialog(onDismissRequest = { mostrarHistorial = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📋 Historial de toques",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        TextButton(onClick = { mostrarHistorial = false }) {
                            Text("Cerrar", color = Color(0xFF8B949E))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "📅 $fechaMostrar",
                            fontSize = 13.sp,
                            color = Color(0xFF8B949E)
                        )
                        TextButton(onClick = { mostrarDatePicker = true }) {
                            Text("Cambiar fecha", fontSize = 13.sp, color = Color(0xFF00E676))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total de toques", fontSize = 14.sp, color = Color(0xFFCDD9E5))
                            Text(
                                "${toques.size}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when {
                        cargando -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF00E676))
                                    Text("Cargando...", color = Color(0xFF8B949E), fontSize = 14.sp)
                                }
                            }
                        }
                        errorHistorial.isNotEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("❌ $errorHistorial", color = Color(0xFFFF5722), fontSize = 14.sp)
                            }
                        }
                        toques.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("📭 Sin toques registrados", color = Color(0xFF8B949E), fontSize = 14.sp)
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("#", color = Color(0xFF8B949E), fontSize = 12.sp, modifier = Modifier.width(24.dp))
                                Text("Inicio", color = Color(0xFF8B949E), fontSize = 12.sp, modifier = Modifier.width(70.dp))
                                Text("Fin", color = Color(0xFF8B949E), fontSize = 12.sp, modifier = Modifier.width(70.dp))
                                Text("Duración", color = Color(0xFF8B949E), fontSize = 12.sp, modifier = Modifier.width(60.dp))
                            }
                            HorizontalDivider(color = Color(0xFF30363D))
                            Spacer(modifier = Modifier.height(4.dp))
                            // FIX #4: itemsIndexed en lugar de items + indexOf
                            // evita O(n²) y el bug con toques duplicados
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                itemsIndexed(toques) { index, toque ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${index + 1}", color = Color(0xFF8B949E), fontSize = 13.sp, modifier = Modifier.width(24.dp))
                                            Text(toque.inicio, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(70.dp))
                                            Text(toque.fin, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(70.dp))
                                            Text(toque.duracion, color = Color(0xFF00E676), fontSize = 13.sp, modifier = Modifier.width(60.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Pantalla principal
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Sensor IoT",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "TTP223 — ESP32 — AWS",
                            fontSize = 11.sp,
                            color = Color(0xFF8B949E)
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = if (estaActivo) "ON" else "OFF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (estaActivo) Color(0xFF00E676) else Color(0xFFFF5722),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Switch(
                            checked = estaActivo,
                            onCheckedChange = { viewModel.toggleSensor(it) },
                            enabled = estado != SensorEstado.DESCONECTADO,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color(0xFF00E676),
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFFF5722),
                                disabledCheckedTrackColor = Color(0xFF30363D),
                                disabledUncheckedTrackColor = Color(0xFF30363D)
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF161B22)
                )
            )
        },
        containerColor = Color(0xFF0D1117)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {

            // Círculo indicador con pulso
            Box(
                modifier = Modifier
                    .scale(pulso.value)
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(colorPrimario.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(colorPrimario.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(colorPrimario),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icono, fontSize = 44.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            // Texto estado
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = titulo,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitulo,
                    fontSize = 15.sp,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center
                )
            }

            // Botón historial
            Button(
                onClick = {
                    mostrarHistorial = true
                    viewModel.cargarHistorial()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF161B22)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, colorPrimario.copy(alpha = 0.4f)
                )
            ) {
                Text("📋  Ver historial de toques", fontSize = 15.sp, color = Color.White)
            }

            // Info servidor y conexión
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22))
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔌 WebSocket", fontSize = 13.sp, color = Color(0xFF8B949E))
                        Text(
                            text = conexion,
                            fontSize = 13.sp,
                            color = if (conexion.contains("Conectado"))
                                Color(0xFF00E676) else Color(0xFFFF5722)
                        )
                    }
                    HorizontalDivider(
                        color = Color(0xFF30363D),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("☁️ Servidor", fontSize = 13.sp, color = Color(0xFF8B949E))
                        Text(Constants.API_BASE, fontSize = 13.sp, color = Color(0xFF8B949E))
                    }
                }
            }
        }
    }
}