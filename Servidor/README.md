# 🖥️ Servidor — Sensor IoT

Backend Node.js y dashboard web para monitoreo y control remoto del sensor TTP223 en tiempo real via WebSocket.

---

## 📁 Estructura

```
Servidor/
├── Servicio/
│   ├── server.js       # Servidor WebSocket + HTTP
│   ├── package.json
│   └── toques.json     # Historial persistente (auto-generado)
└── Frontend/
    ├── index.html
    ├── styles.css
    └── app.js
```

---

## 🚀 Instalación en AWS EC2

### 1. Instala Node.js 20

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
```

### 2. Clona e instala dependencias

```bash
git clone https://github.com/djcc2001/Sensor-Toque_IoT.git
cd Sensor-Toque_IoT/Servidor/Servicio
npm install
```

### 3. Actualiza la IP en el Frontend

En `Frontend/app.js`:
```javascript
const WS_URL   = 'ws://TU_IP_AWS:3001';
const API_BASE = 'http://TU_IP_AWS:3002';
```

### 4. Copia el frontend a Apache

```bash
sudo mkdir /var/www/html/sensor
sudo cp ../Frontend/* /var/www/html/sensor/
```

### 5. Inicia con PM2

```bash
sudo npm install -g pm2
pm2 start server.js --name sensor
pm2 save
pm2 startup
# Ejecuta el comando que muestre pm2 startup
```

### 6. Abre los puertos en AWS Security Groups

| Puerto | Tipo | Uso |
|--------|------|-----|
| 80 | HTTP | Apache / Dashboard |
| 3001 | TCP | WebSocket ESP32 + Dashboard |
| 22 | SSH | Administración |

---

## 🔌 Protocolo WebSocket

El servidor gestiona dos tipos de clientes en el mismo puerto 3001:

### Mensajes del ESP32 → Servidor
```json
{ "tipo": "senal", "valor": 1 }   // toque detectado (HIGH)
{ "tipo": "senal", "valor": 0 }   // dedo suelto (LOW)

### Mensajes Dashboard → Servidor
```json
{ "tipo": "control", "activo": true }   // activar sensor
{ "tipo": "control", "activo": false }  // desactivar sensor
{ "tipo": "limpiar" }                   // limpiar historial
```

### Broadcasts del Servidor → Dashboard
```json
{
  "sensorActivo": true,
  "señalActual": 1,
  "totalToques": 27,
  "ultimoToque": "16:13:47",
  "tipo": "normal"
}
```

### Tipos de broadcast

| Tipo | Cuándo | Acción |
|------|--------|---------------------|
| `normal` | Señal HIGH/LOW del ESP32 | Dashboard dibuja en gráfica |
| `control` | Dashboard cambió ON/OFF | ESP32 prende/apaga LED |
| `estado-inicial` | Cliente recién conectado | Sincroniza estado actual |
| `limpiar` | Se limpió el historial | Resetea gráfica y contadores |

---

## 📊 API REST (historial y estado)

### GET `/api/estado`
Estado actual del sensor.
```json
{ "activo": true, "señalActual": 0, "totalToques": 27, "ultimoToque": "16:13:47" }
```

### GET `/api/reporte?fecha=2026-05-11`
Reporte de toques de una fecha específica.
```json
{
  "fecha": "2026-05-11",
  "total": 27,
  "por_hora": { "15": 10, "16": 17 },
  "toques": [
    { "inicio": "16:13:47", "fin": "16:13:50", "duracion": "3.21s" }
  ]
}
```

### GET `/api/reporte/all`
Resumen de todas las fechas registradas.

---

## 📊 Dashboard

Accede en: `http://TU_IP_AWS/sensor`

Funcionalidades:
- **Gráfica de señal** — Canvas API, dibuja HIGH/LOW en tiempo real (event-driven)
- **Toggle ON/OFF** — activa/desactiva el sensor remotamente
- **LED físico** — responde al toggle del dashboard
- **Gráfica congelada** — se pausa cuando el sensor está OFF
- **Historial** — modal con filtro por fecha, hora pico y duración promedio
- **Estado al cargar** — consulta `/api/estado` antes de conectar WS
- **Reconexión automática** — dashboard se reconecta si pierde la conexión

---

## 🛠️ Comandos PM2

```bash
pm2 status              # Ver estado
pm2 logs sensor         # Ver logs en vivo
pm2 restart sensor      # Reiniciar
pm2 monit               # Monitor CPU/RAM en tiempo real
pm2 flush sensor        # Limpiar logs antiguos
```

---

## 📈 Rendimiento en t2.micro (AWS Free Tier)

| Métrica | Valor |
|---------|-------|
| RAM consumida | ~69 MB |
| Latencia WebSocket | < 50ms |
| Conexión | 1 WebSocket persistente |
| Disponibilidad | 24/7 con PM2 |
| Costo | Free Tier (750h/mes) |
