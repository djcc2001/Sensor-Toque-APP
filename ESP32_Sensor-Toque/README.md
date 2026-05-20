# 🔌 ESP32 — Firmware Sensor IoT

Firmware para ESP32 DevKit V1 que controla un sensor táctil TTP223 y se comunica con el servidor AWS via **WebSocket full-duplex**, permitiendo monitoreo y control remoto en tiempo real.

---

## 🧰 Hardware requerido

| Componente | Cantidad |
|-----------|----------|
| ESP32 DevKit V1 (CP2102) | 1 |
| Sensor táctil TTP223 | 1 |
| LED verde (indicador) | 1 |
| Resistencia 220Ω | 1 |
| Breadboard | 1 |
| Cables dupont | varios |

---

## 🔧 Conexiones

```
TTP223          ESP32
─────────────────────────
GND     →       GND
VCC     →       3.3V  (alimentación permanente)
IO      →       GPIO14 (señal táctil)

LED indicador
─────────────────────────
(+) → resistencia 220Ω → GPIO23
(-) → GND
```

> ⚠️ El VCC del TTP223 va al pin **3.3V** directamente — NO a un GPIO.
> Esto garantiza alimentación estable y evita rebotes por cortes de energía.
> El GPIO23 controla únicamente el **LED indicador** ON/OFF.

---

## ⚙️ Configuración

### 1. Instala PlatformIO en VSCode
Extensiones → buscar **PlatformIO IDE** → Instalar

### 2. Actualiza la IP del servidor en `src/main.cpp`
```cpp
const char* WS_HOST = "http://3.131.82.32/";
const int   WS_PORT = 3001;
```

### 3. Permisos de puerto en Linux
```bash
sudo usermod -a -G dialout $USER
sudo usermod -a -G tty $USER
# Cierra sesión y vuelve a entrar
```

### 4. Sube el firmware
```
PlatformIO → Upload  (Ctrl+Alt+U)
```

---

## 📶 Configuración WiFi

Primera vez o sin red guardada:
1. El ESP32 crea el WiFi **`Sensor-IoT`** (contraseña: `sensor123`)
2. Conéctate desde tu celular o PC
3. Abre `192.168.4.1` en el navegador
4. Selecciona tu red y escribe la contraseña
5. El ESP32 se reconecta automáticamente

### Cambiar WiFi sin tocar el código
Mantén presionado **BOOT 3 segundos** — el LED parpadeará 3 veces como confirmación y el portal WiFi se activará.

---

## 🧠 Arquitectura del firmware

### Dual Core FreeRTOS

| Núcleo | Tarea | Stack | Responsabilidad |
|--------|-------|-------|----------------|
| Núcleo 0 | `tareaSensor` | 4KB | Lectura del sensor, debounce 80ms |
| Núcleo 1 | `tareaWebSocket` | 8KB | Conexión WS, heartbeat, reconexión |
| Núcleo 0 | `tareaBoton` | 2KB | Detección de pulsación larga (3s) |

### Comunicación WebSocket

El ESP32 actúa como **cliente WebSocket** con conexión persistente al servidor:

```
Toca sensor → ESP32 envía {"tipo":"senal","valor":1}
Suelta → ESP32 envía {"tipo":"senal","valor":0}
Dashboard ON → ESP32 recibe {"tipo":"control","activo":true} → LED ON
Dashboard OFF→ ESP32 recibe {"tipo":"control","activo":false} → LED OFF
```

### Debounce adaptativo

```cpp
#define DEBOUNCE_MS 80  // ms para filtrar rebotes al reactivar

// Solo se aplica debounce al reactivar el sensor
// En operación normal: respuesta inmediata
```

El TTP223 genera rebotes al reactivarse. El debounce de 150ms con `INPUT_PULLDOWN` filtra efectivamente las señales espurias, determinado experimentalmente.

---

## 📦 Dependencias

```ini
lib_deps =
    tzapu/WiFiManager @ ^2.0.17
    links2004/WebSockets @ ^2.4.0
    bblanchon/ArduinoJson @ ^6.21.3
```

PlatformIO las descarga automáticamente al compilar.

---

## 🔍 Monitor serial

Mensajes clave en el monitor (115200 baud):

```
[20:29:34] WiFi: 192.168.x.x | RSSI: -60 dBm
[20:29:39] WS CONECTADO!
[20:29:49] SENSOR: HIGH
[20:29:49] >>> HIGH
[20:30:14] SENSOR: LOW
[20:30:14] >>> LOW
[20:29:45] LED OFF
[20:29:50] LED ON
[20:29:35] Intentando reconectar WS...
```

Los timestamps muestran hora real (HH:MM:SS) sincronizada via NTP.
