const WS_URL   = 'ws://' + (process.env.WS_HOST || '3.131.82.32') + ':' + (process.env.WS_PORT || '3001');
const API_BASE = 'http://' + (process.env.WS_HOST || '3.131.82.32') + ':' + (process.env.WS_PORT || '3002');

// ── GRÁFICA ──────────────────────────────────────────
const MAX_PUNTOS = 300;
const MAX_MINUTOS = 60;     // datos mayores a N minutos se descartan
let datos             = new Array(MAX_PUNTOS).fill(0);
let señalActualLocal  = 0;
let sensorActivoLocal = true;
let pausado           = false;
let tiempoInicioSesal = Date.now(); // marca de tiempo para límite de sesión

const canvas = document.getElementById('grafica');
const ctx    = canvas.getContext('2d');

function resizeCanvas() {
  canvas.width  = canvas.offsetWidth  * window.devicePixelRatio;
  canvas.height = canvas.offsetHeight * window.devicePixelRatio;
  ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
}
window.addEventListener('resize', () => { resizeCanvas(); dibujarGrafica(); });
resizeCanvas();

function dibujarGrafica() {
  const W = canvas.offsetWidth;
  const H = canvas.offsetHeight;
  ctx.clearRect(0, 0, W, H);

  ctx.fillStyle = '#0a0a0a';
  ctx.fillRect(0, 0, W, H);

  ctx.strokeStyle = 'rgba(255,255,255,0.03)';
  ctx.lineWidth = 1;
  [0.25, 0.5, 0.75].forEach(y => {
    ctx.beginPath(); ctx.moveTo(0, H*y); ctx.lineTo(W, H*y); ctx.stroke();
  });
  [0.25, 0.5, 0.75].forEach(x => {
    ctx.beginPath(); ctx.moveTo(W*x, 0); ctx.lineTo(W*x, H); ctx.stroke();
  });

  ctx.fillStyle = 'rgba(255,255,255,0.15)';
  ctx.font = '10px monospace';
  ctx.fillText('HIGH', 4, H*0.22);
  ctx.fillText('LOW',  4, H*0.85);

  const paso  = W / (MAX_PUNTOS - 1);
  const yAlto = H * 0.18;
  const yBajo = H * 0.82;

  ctx.strokeStyle = '#22c55e';
  ctx.lineWidth   = 2;
  ctx.beginPath();

  for (let i = 0; i < datos.length; i++) {
    const x = i * paso;
    const y = datos[i] === 1 ? yAlto : yBajo;
    if (i === 0) {
      ctx.moveTo(x, y);
    } else {
      if (datos[i] !== datos[i-1]) {
        ctx.lineTo(x, datos[i-1] === 1 ? yAlto : yBajo);
        ctx.lineTo(x, y);
      } else {
        ctx.lineTo(x, y);
      }
    }
  }
  ctx.stroke();
}

setInterval(() => {
  if (pausado) return;
  datos.push(señalActualLocal);
  if (datos.length > MAX_PUNTOS) datos.shift();

  // Límite de tiempo: descartar datos más viejos que MAX_MINUTOS
  const ahora = Date.now();
  if (ahora - tiempoInicioSesal > MAX_MINUTOS * 60 * 1000) {
    datos = new Array(MAX_PUNTOS).fill(0);
    señalActualLocal = 0;
    tiempoInicioSesal = ahora;
  }
  dibujarGrafica();
}, 200);

// ── UI ────────────────────────────────────────────────
function actualizarToggle(activo) {
  const toggle = document.getElementById('toggle-sensor');
  const t      = document.getElementById('toggle-text');
  toggle.checked = activo;
  t.textContent  = activo ? 'ON' : 'OFF';
  t.className    = 'toggle-status ' + (activo ? 'on' : 'off');
}

function actualizarEstadoVisual(señal, sensorOn) {
  const eVal = document.getElementById('estado-val');
  const dot  = document.getElementById('live-dot');
  const ltxt = document.getElementById('live-text');

  if (señal === 1 && sensorOn) {
    eVal.textContent = 'HIGH';
    eVal.className   = 'stat-val high';
    dot.className    = 'live-dot activo';
    ltxt.textContent = '● tocando';
    ltxt.className   = 'live-text activo';
  } else if (sensorOn) {
    eVal.textContent = 'LOW';
    eVal.className   = 'stat-val low';
    dot.className    = 'live-dot';
    ltxt.textContent = 'sin toque';
    ltxt.className   = 'live-text';
  } else {
    eVal.textContent = 'OFF';
    eVal.className   = 'stat-val low';
    dot.className    = 'live-dot';
    ltxt.textContent = 'sensor apagado';
    ltxt.className   = 'live-text';
  }
}

// ── WEBSOCKET ─────────────────────────────────────────
let ws;

function procesarEvento(data) {
  document.getElementById('total-val').textContent  = data.totalToques || 0;
  document.getElementById('ultimo-val').textContent = data.ultimoToque || '--';

  // Control OFF → pausar
  if (data.tipo === 'control' && !data.sensorActivo) {
    pausado           = true;
    sensorActivoLocal = false;
    actualizarToggle(false);
    actualizarEstadoVisual(0, false);
    return;
  }

  // Control ON → pausar hasta reactivación
  if (data.tipo === 'control' && data.sensorActivo) {
    pausado = true;
    actualizarToggle(true);
    return;
  }

  // Reactivación → reanudar inmediatamente
  if (data.tipo === 'reactivar') {
    pausado           = false;
    sensorActivoLocal = true;
    señalActualLocal  = data.señalActual || 0;
    actualizarToggle(true);
    actualizarEstadoVisual(señalActualLocal, true);
    datos.push(señalActualLocal);
    if (datos.length > MAX_PUNTOS) datos.shift();
    dibujarGrafica();
    return;
  }

  // Señal normal
  if (data.tipo === 'normal') {
    pausado           = false;
    sensorActivoLocal = true;
    señalActualLocal  = data.señalActual || 0;
    actualizarEstadoVisual(señalActualLocal, true);
    datos.push(señalActualLocal);
    if (datos.length > MAX_PUNTOS) datos.shift();
    dibujarGrafica();
    
    if (document.getElementById('modal-overlay').classList.contains('abierto')) {
      setTimeout(buscarReporteModal, 300);
    }
  }

  // Estado inicial
  if (data.tipo === 'estado-inicial') {
    sensorActivoLocal = data.sensorActivo;
    señalActualLocal  = data.señalActual || 0;
    pausado           = !data.sensorActivo;
    actualizarToggle(data.sensorActivo);
    actualizarEstadoVisual(data.señalActual || 0, data.sensorActivo);
    dibujarGrafica();
  }

  // Limpiar
  if (data.tipo === 'limpiar') {
    datos = new Array(MAX_PUNTOS).fill(0);
    señalActualLocal = 0;
    dibujarGrafica();
  }
}

function conectarWS() {
  if (ws) {
    ws.onclose = null;
    ws.close();
  }
  ws = new WebSocket(WS_URL);
  ws.onmessage = (e) => {
    try { procesarEvento(JSON.parse(e.data)); } catch(err) {}
  };
  ws.onclose = () => setTimeout(conectarWS, 2000);
}

// ── CONTROLES ─────────────────────────────────────────
async function toggleSensor() {
  const activo = document.getElementById('toggle-sensor').checked;

  // Enviar por WebSocket
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ tipo: 'control', activo }));
  }

  // También por HTTP como respaldo
  try {
    await fetch(`${API_BASE}/api/control`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ activo })
    });
  } catch(err) {}
}

function limpiarGrafica() {
  datos = new Array(MAX_PUNTOS).fill(0);
  señalActualLocal = 0;
  dibujarGrafica();
  
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ tipo: 'limpiar' }));
  }
  fetch(`${API_BASE}/api/limpiar`, { method: 'POST' }).catch(()=>{});
}

// ── MODAL ─────────────────────────────────────────────
function abrirModal() {
  document.getElementById('modal-fecha').value = new Date().toLocaleDateString('en-CA', {timeZone:'America/Lima'});
  document.getElementById('modal-overlay').classList.add('abierto');
  buscarReporteModal();
}

function cerrarModal() {
  document.getElementById('modal-overlay').classList.remove('abierto');
}

document.getElementById('modal-overlay').addEventListener('click', e => {
  if (e.target === document.getElementById('modal-overlay')) cerrarModal();
});

async function buscarReporteModal() {
  const fecha = document.getElementById('modal-fecha').value;
  if (!fecha) return;
  try {
    const r = await fetch(`${API_BASE}/api/reporte?fecha=${fecha}`);
    renderModal(await r.json());
  } catch(e) {
    document.getElementById('modal-contenido').innerHTML = '<div class="no-datos">Error</div>';
  }
}

function renderModal(data) {
  document.getElementById('modal-total').textContent = data.total || 0;
  if (!data.total) {
    document.getElementById('modal-duracion-avg').textContent = '--';
    document.getElementById('modal-contenido').innerHTML = `<div class="no-datos">Sin toques el ${data.fecha}</div>`;
    return;
  }
  const toques = data.toques || [];
  const avg = (toques.reduce((s,t)=>s+parseFloat(t.duracion),0) / toques.length).toFixed(2) + 's';
  document.getElementById('modal-duracion-avg').textContent = avg;

  const horas = Object.entries(data.por_hora||{}).sort((a,b)=>b[1]-a[1]);
  const horasHTML = horas.map(([k,v])=>`<div class="hora-item${k===horas[0][0]?' pico':''}"><div class="hora-num">${v}</div><div class="hora-label">${k}:00${k===horas[0][0]?' 🔥':''}</div></div>`).join('');

  const filasHTML = toques.map(t=>`<tr><td>${t.inicio}</td><td>${t.fin}</td><td style="color:#60a5fa">${t.duracion}</td></tr>`).join('');

  document.getElementById('modal-contenido').innerHTML = `
    <div class="seccion-titulo">POR HORA</div><div class="horas-grid">${horasHTML}</div>
    <div class="seccion-titulo">DETALLE</div>
    <table class="tabla"><thead><tr><th>INICIO</th><th>FIN</th><th>DURACIÓN</th></tr></thead><tbody>${filasHTML}</tbody></table>`;
}

// ── INICIO ────────────────────────────────────────────
conectarWS();