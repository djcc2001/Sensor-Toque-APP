const fs = require('fs');
const path = require('path');

const DATA_FILE = path.join(__dirname, 'toques.json');

// Mock the data file before each test
beforeEach(() => {
  if (fs.existsSync(DATA_FILE)) {
    fs.writeFileSync(DATA_FILE, JSON.stringify({}));
  }
});

afterEach(() => {
  if (fs.existsSync(DATA_FILE)) {
    fs.unlinkSync(DATA_FILE);
  }
});

function cargarDatos() {
  if (!fs.existsSync(DATA_FILE)) fs.writeFileSync(DATA_FILE, JSON.stringify({}));
  try {
    return JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
  } catch (e) {
    console.log('[DATA] Error cargando archivo, usando vacío');
    return {};
  }
}

describe('Lógica de manejo de datos del servidor', () => {
  test('cargarDatos retorna objeto vacío cuando archivo está corrupto', () => {
    fs.writeFileSync(DATA_FILE, 'esto no es json válido');
    const datos = cargarDatos();
    expect(datos).toEqual({});
  });

  test('cargarDatos crea archivo vacío si no existe', () => {
    if (fs.existsSync(DATA_FILE)) fs.unlinkSync(DATA_FILE);
    const datos = cargarDatos();
    expect(datos).toEqual({});
    expect(fs.existsSync(DATA_FILE)).toBe(true);
  });

  test('cargarDatos retorna datos válidos cuando archivo tiene contenido', () => {
    fs.writeFileSync(DATA_FILE, JSON.stringify({ foo: 'bar' }));
    const datos = cargarDatos();
    expect(datos).toEqual({ foo: 'bar' });
  });
});