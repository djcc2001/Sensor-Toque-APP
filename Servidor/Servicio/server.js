const express = require('express');
const WebSocket = require('ws');
const cors = require('cors');
const fs = require('fs');
const path = require('path');

const app = express();
app.use(cors());
app.use(express.json());

const DATA_FILE = path.join(__dirname, 'toques.json');

function cargarDatos() {
  if (!fs.existsSync(DATA_FILE)) fs.writeFileSync(DATA_FILE, JSON.stringify({}));
  return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
}

function guardarDatos(datos) {
  fs.writeFileSync(DATA_FILE, JSON.stringify(datos, null, 2));
}

function getFechaHoy() {
  return new Date().toLocaleDateString('en-CA', { timeZone: 'America/Lima' });
}

function getHoraActual() {
  return new Date().toLocaleTimeString('es-PE', { timeZone: 'America/Lima', hour12: false });
}

function registrarToque(inicio, fin, duracion) {
  const datos   = cargarDatos();
  const fecha   = getFechaHoy();
  const horaKey = inicio.slice(0, 2);
  if (!datos[fecha]) datos[fecha] = { total: 0, por_hora: {}, toques: [] };
  datos[fecha].total++;
  datos[fecha].por_hora[horaKey] = (datos[fecha].por_hora[horaKey] || 0) + 1;
  datos[fecha].toques.unshift({ inicio, fin, duracion });
  if (datos[fecha].toques.length > 200) datos[fecha].toques.pop();
  guardarDatos(datos);
  return datos[fecha].total;
}

function getToquesHoy() {
  const datos = cargarDatos();
  const fecha = getFechaHoy();
  return datos[fecha] ? datos[fecha].total : 0;
}

let estado = {
  sensorActivo: true,
  señalActual:  0,
  totalToques:  getToquesHoy(),
  ultimoToque:  '--',
  historial:    [],
  tipo:         'normal'
};

let toqueInicio    = null;
let toqueInicioStr = null;

const wss = new WebSocket.Server({ port: 3001 });

// ── BROADCAST A TODOS LOS CLIENTES ────────────────────
function broadcast(extra = {}) {
  // No incluir historial en broadcasts normales (muy pesado)
  const { historial, ...estadoLigero } = estado;
  const msg = JSON.stringify({ ...estadoLigero, ...extra });
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) client.send(msg);
  });
}

// ── BROADCAST A TODOS EXCEPTO UNO ─────────────────────
function broadcastExcept(exceptClient, extra = {}) {
  const msg = JSON.stringify({ ...estado, ...extra });
  wss.clients.forEach(client => {
    if (client !== exceptClient && client.readyState === WebSocket.OPEN) {
      client.send(msg);
    }
  });
}

// ── PROCESAR SEÑAL TÁCTIL ─────────────────────────────
function procesarSenal(valor, origen) {
  const ahora = getHoraActual();

  if (!estado.sensorActivo) {
    console.log(`[${origen}] Ignorado (OFF): ${valor ? 'HIGH' : 'LOW'}`);
    return false;
  }

  if (valor === 1 && estado.señalActual === 0) {
    estado.señalActual = 1;
    toqueInicio        = Date.now();
    toqueInicioStr     = ahora;
    estado.ultimoToque = ahora;
    estado.totalToques++;
    console.log(`[${origen}] HIGH: ${ahora}`);
    broadcastExcept(null, { tipo: 'normal' });
    return true;
  }

  if (valor === 0 && estado.señalActual === 1) {
    estado.señalActual = 0;
    const duracionMs   = Date.now() - toqueInicio;
    const duracion     = (duracionMs / 1000).toFixed(2) + 's';
    registrarToque(toqueInicioStr, ahora, duracion);
    estado.historial.unshift({ inicio: toqueInicioStr, fin: ahora, duracion });
    if (estado.historial.length > 50) estado.historial.pop();
    toqueInicio    = null;
    toqueInicioStr = null;
    console.log(`[${origen}] LOW: ${ahora} (${duracion})`);
    broadcastExcept(null, { tipo: 'normal' });
    return true;
  }

  return false;
}

// ── PROCESAR REACTIVACIÓN ─────────────────────────────
function procesarReactivar(valor, origen) {
  const ahora = getHoraActual();

  estado.sensorActivo = true;

  if (valor === 1) {
    estado.señalActual = 1;
    if (!toqueInicio) {
      toqueInicio        = Date.now();
      toqueInicioStr     = ahora;
      estado.ultimoToque = ahora;
      estado.totalToques++;
    }
    console.log(`[${origen}] Reactivado HIGH: ${ahora}`);
  } else {
    estado.señalActual = 0;
    if (toqueInicio) {
      const duracionMs = Date.now() - toqueInicio;
      const duracion   = (duracionMs / 1000).toFixed(2) + 's';
      registrarToque(toqueInicioStr, ahora, duracion);
      estado.historial.unshift({ inicio: toqueInicioStr, fin: ahora, duracion });
      if (estado.historial.length > 50) estado.historial.pop();
      toqueInicio    = null;
      toqueInicioStr = null;
      console.log(`[${origen}] Reactivado LOW (cerrando toque): ${ahora} (${duracion})`);
    } else {
      console.log(`[${origen}] Reactivado LOW: ${ahora}`);
    }
  }

  broadcastExcept(null, { tipo: 'reactivar' });
}

// ── WEBSOCKET: MANEJAR MENSAJES ───────────────────────
wss.on('connection', (ws) => {
  console.log('[WS] Cliente conectado');

  // Enviar estado actual al recién conectado
  ws.send(JSON.stringify({ ...estado, tipo: 'estado-inicial' }));

  ws.on('message', (data) => {
    try {
      const msg = JSON.parse(data);
      console.log(`[WS] Mensaje: ${JSON.stringify(msg)}`);

      switch (msg.tipo) {
        case 'senal':
          // ESP32 envía señal táctil
          procesarSenal(msg.valor, 'WS-ESP32');
          break;

        case 'reactivar':
          // ESP32 envía reactivación
          procesarReactivar(msg.valor, 'WS-ESP32');
          break;

        case 'control':
          // Dashboard envía ON/OFF
          if (!msg.activo && estado.sensorActivo) {
            // APAGAR
            if (estado.señalActual === 1 && toqueInicio) {
              const ahora = getHoraActual();
              const duracionMs = Date.now() - toqueInicio;
              const duracion   = (duracionMs / 1000).toFixed(2) + 's';
              registrarToque(toqueInicioStr, ahora, duracion);
              estado.historial.unshift({ inicio: toqueInicioStr, fin: ahora, duracion });
              if (estado.historial.length > 50) estado.historial.pop();
              toqueInicio    = null;
              toqueInicioStr = null;
            }
            estado.sensorActivo = false;
            estado.señalActual = 0;
            console.log('[CONTROL] Sensor OFF');
            broadcast({ tipo: 'control' });
          }
          else if (msg.activo && !estado.sensorActivo) {
            // PRENDER
            estado.sensorActivo = true;
            console.log('[CONTROL] Sensor ON — avisando al ESP32');
            broadcast({ tipo: 'control' });
          }
          break;

        case 'limpiar':
          estado.historial   = [];
          estado.señalActual = 0;
          estado.totalToques = 0;
          estado.ultimoToque = '--';
          toqueInicio        = null;
          toqueInicioStr     = null;
          broadcast({ tipo: 'limpiar' });
          break;

        default:
          console.log(`[WS] Tipo desconocido: ${msg.tipo}`);
      }
    } catch (e) {
      console.log(`[WS] Error parseando mensaje: ${data}`);
    }
  });

  ws.on('close', () => {
    console.log('[WS] Cliente desconectado');
  });
});

// ── ENDPOINTS HTTP (dashboard y compatibilidad) ────────

// Estado para dashboard
app.get('/api/estado', (req, res) => {
  res.json({
    activo:      estado.sensorActivo,
    señalActual: estado.señalActual,
    totalToques: estado.totalToques,
    ultimoToque: estado.ultimoToque
  });
});

// Control desde dashboard (alternativa HTTP)
app.post('/api/control', (req, res) => {
  const { activo } = req.body;
  const ahora = getHoraActual();

  if (!activo && estado.sensorActivo) {
    if (estado.señalActual === 1 && toqueInicio) {
      const duracionMs = Date.now() - toqueInicio;
      const duracion   = (duracionMs / 1000).toFixed(2) + 's';
      registrarToque(toqueInicioStr, ahora, duracion);
      estado.historial.unshift({ inicio: toqueInicioStr, fin: ahora, duracion });
      if (estado.historial.length > 50) estado.historial.pop();
      toqueInicio    = null;
      toqueInicioStr = null;
    }
    estado.sensorActivo = false;
    estado.señalActual = 0;
    console.log('[CONTROL] Sensor OFF (HTTP)');
    broadcast({ tipo: 'control' });
  }

  if (activo && !estado.sensorActivo) {
    estado.sensorActivo = true;
    console.log('[CONTROL] Sensor ON (HTTP)');
    broadcast({ tipo: 'control' });
  }

  res.json({ ok: true, sensorActivo: estado.sensorActivo });
});

// Limpiar
app.post('/api/limpiar', (req, res) => {
  estado.historial   = [];
  estado.señalActual = 0;
  estado.totalToques = 0;
  estado.ultimoToque = '--';
  toqueInicio        = null;
  toqueInicioStr     = null;
  broadcast({ tipo: 'limpiar' });
  res.json({ ok: true });
});

// Reporte por fecha
app.get('/api/reporte', (req, res) => {
  const datos = cargarDatos();
  const fecha = req.query.fecha || getFechaHoy();
  if (!datos[fecha]) return res.json({ fecha, total: 0, por_hora: {}, toques: [] });
  res.json({ fecha, ...datos[fecha] });
});

// Todas las fechas
app.get('/api/reporte/all', (req, res) => {
  const datos = cargarDatos();
  const resumen = Object.entries(datos)
    .map(([fecha, d]) => ({ fecha, total: d.total }))
    .sort((a, b) => b.fecha.localeCompare(a.fecha));
  res.json(resumen);
});

// Iniciar servidores
app.listen(3002, () => {
  console.log('Servidor HTTP en puerto 3002');
  estado.totalToques = getToquesHoy();
  console.log(`Toques hoy: ${estado.totalToques}`);
  console.log(`Sensor: ${estado.sensorActivo ? 'ON' : 'OFF'}`);
});

wss.on('listening', () => console.log('WebSocket en puerto 3001'));