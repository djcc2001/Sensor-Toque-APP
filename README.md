# :satellite: Sensor IoT — Control Remoto desde AWS

Sistema IoT de monitoreo y control remoto en tiempo real de un sensor táctil capacitivo TTP223 con ESP32, comunicación WebSocket full-duplex, dashboard web desplegado en AWS EC2 y **aplicación Android nativa**.

![Dashboard](imgs/dashboard.png)

---

## :dart: Descripción

El sistema permite comunicación **bidireccional completa** desde tres interfaces:

- **Hardware**: El usuario toca el sensor físico → la señal aparece instantáneamente en el dashboard y en la app Android
- **Dashboard web**: El usuario activa/desactiva el sensor → el LED físico y la app responden de inmediato
- **App Android**: El usuario controla el sensor desde el celular → recibe notificaciones con sonido incluso con la pantalla apagada

Todo por una **única conexión WebSocket persistente** — sin HTTP polling, sin colas, sin latencia.

---

## :camera: Capturas

| Dashboard Web | Circuito | App Android |
|:---:|:---:|:---:|
| <img src="imgs/dashboard.png" width="250"/> | <img src="imgs/circuito.png" width="250"/> | <img src="imgs/app.jpeg" width="140"/> |

---

## :triangular_ruler: Arquitectura

```
TTP223 → ESP32 ──WebSocket──► Node.js AWS ──WebSocket──► Dashboard Web
                ◄─────────────────────────────────────────
                    Comandos ON/OFF en tiempo real

                              Node.js AWS ──WebSocket──► App Android
                          ◄─────────────────────────────────────────
                                  Control remoto móvil
```

### Flujo de comunicación

| Origen | Destino | Mensaje | Protocolo |
|--------|---------|---------|-----------|
| ESP32 | Servidor | `{"tipo":"senal","valor":1}` | WebSocket |
| ESP32 | Servidor | `{"tipo":"senal","valor":0}` | WebSocket |
| Dashboard / App | Servidor | `{"tipo":"control","activo":true\|false}` | WebSocket |
| Servidor | Todos | `{"tipo":"normal","señalActual":1}` | WebSocket |
| Servidor | Todos | `{"tipo":"control","sensorActivo":false}` | WebSocket |
| Servidor | Nuevo cliente | `{"tipo":"estado-inicial",...}` | WebSocket |

---

## :file_folder: Estructura del repositorio

```
Sensor_IoT/
├── ESP32_Sensor-Toque/         # Firmware ESP32
│   ├── src/
│   │   └── main.cpp
│   └── platformio.ini
├── Servidor/
│   ├── Servicio/               # Backend Node.js
│   │   ├── server.js
│   │   └── package.json
│   └── Frontend/               # Dashboard web
│       ├── index.html
│       ├── styles.css
│       └── app.js
├── App_Android/                    # Aplicación Android
│   ├── app/src/main/
│   │   ├── java/com/embebidos/sensoriot/
│   │   │   ├── MainActivity.kt
│   │   │   ├── service/WebSocketService.kt
│   │   │   ├── ui/SensorScreen.kt
│   │   │   └── viewmodel/SensorViewModel.kt
│   │   └── res/raw/
│   │       ├── door.wav
│   │       └── warning.mp3
│   └── README.md
├── imgs/
│   ├── dashboard.png
│   ├── circuito.png
│   ├── foto.jpeg
│   └── app_android.png
└── README.md
```

---

## ⚡ Características

### Sistema completo
- **WebSocket full-duplex** — una sola conexión persistente para todo
- **Control ON/OFF remoto** — LED físico responde instantáneamente
- **Gráfica event-driven** — dibuja solo cuando llega un evento real
- **Congelar/descongelar** — gráfica se pausa en gris al apagar
- **Debounce 80 ms** — filtra rebotes del TTP223
- **Historial persistente** — toques guardados en disco por fecha
- **Reporte por fecha** — hora de inicio, fin, duración y hora pico
- **Reconexión automática** — ESP32 cada 3 s, dashboard y app cada 3 s
- **WiFi configurable** — portal web al primer arranque (WiFiManager)
- **Timestamps NTP** — logs con hora real sincronizada
- **LED indicador** — parpadea 3 veces al conectar WebSocket

### App Android
- **Notificaciones con sonido** — `door.wav` al detectar toque, `warning.mp3` al apagarse
- **Segundo plano garantizado** — Foreground Service mantiene la conexión activa
- **Sin notificaciones redundantes** — se suprimen si la app está en primer plano
- **Control ON/OFF** — switch en la barra superior de la app
- **Historial por fecha** — selector de calendario integrado
- **Interfaz reactiva** — Jetpack Compose actualiza la UI en tiempo real

---

## :electric_plug: Conexiones hardware

| TTP223 | ESP32 |
|--------|-------|
| GND | GND |
| VCC | 3.3 V |
| IO | GPIO14 |

| LED indicador | ESP32 |
|---------------|-------|
| (+) Ánodo | GPIO23 via 220 Ω |
| (-) Cátodo | GND |

![Circuito](imgs/circuito.png)

---

## 🛠️ Tecnologías

| Capa | Tecnología |
|------|-----------|
| Hardware | ESP32 DevKit V1, TTP223, LED |
| Firmware | C++ / Arduino / FreeRTOS / PlatformIO |
| Protocolo | WebSocket (full-duplex, RFC 6455) |
| Backend | Node.js, Express, ws, PM2 |
| Frontend web | HTML5, CSS3, JavaScript, Canvas API |
| App móvil | Kotlin, Jetpack Compose, MVVM, OkHttp |
| Infraestructura | AWS EC2 Ubuntu, Apache |
| Sincronización | NTP (pool.ntp.org) |

---

## :rocket: Inicio rápido

### 1. Firmware ESP32
```bash
# Abre ESP32_Sensor-Toque/ en PlatformIO
# Configura WiFi con el portal web al primer arranque
pio run --target upload
```

### 2. Servidor Node.js en AWS
```bash
cd Servidor/Servicio
npm install
pm2 start server.js --name sensor-iot
pm2 save && pm2 startup
```

### 3. Dashboard web
```bash
# Copia Frontend/ a /var/www/html/sensor en el servidor Apache
```

### 4. App Android
```bash
# Abre Android/SensorIoT/ en Android Studio
# Agrega door.wav y warning.mp3 en res/raw/
# Conecta tu dispositivo y presiona ▶️
```

Consulta el [README de la app Android](Android/SensorIoT/README.md) para instrucciones detalladas.

---

## 📊 API REST

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/estado` | Estado actual del sensor |
| GET | `/api/reporte?fecha=YYYY-MM-DD` | Historial de toques por fecha |
| GET | `/api/reporte/all` | Resumen de todas las fechas |

**WebSocket**: `ws://3.131.82.32:3001`

---

## 📈 Rendimiento

| Métrica | Valor |
|---------|-------|
| Latencia de señal táctil | < 80 ms |
| Latencia de comando remoto | < 50 ms |
| Reconexión automática | 3 segundos |
| RAM del servidor (AWS t2.micro) | ~69 MB |
| RAM libre ESP32 | ~240 KB |
