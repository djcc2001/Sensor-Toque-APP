#include <Arduino.h>
#include <WiFi.h>
#include <WiFiManager.h>
#include <WebSocketsClient.h>
#include <ArduinoJson.h>
#include <time.h>

#define PIN_SENSOR  14
#define PIN_LED     23
#define PIN_BOOT    0
#define DEBOUNCE_MS 80

#include "config.h"

bool controlActivo = true;
bool wsConectado = false;
int estadoSensor = LOW;
int ultimoEnviado = -1;
unsigned long ultimoEnvio = 0;
unsigned long ultimoCambioSensor = 0;
int lecturaAnterior = LOW;
int estadoEstable = LOW;
unsigned long ultimoIntentoRecon = 0;
unsigned long ultimaEstabilidad = 0;
int reconexiones = 0;

WiFiManager wm;
WebSocketsClient wsClient;
StaticJsonDocument<100> doc;
char buffer[100];

// ── Obtener hora actual formateada ────────────────────
String getHoraActual() {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    return "--:--:--";
  }
  char hora[9];
  strftime(hora, sizeof(hora), "%H:%M:%S", &timeinfo);
  return String(hora);
}

// ── Log con hora real ─────────────────────────────────
void logConHora(const char* mensaje) {
  Serial.printf("[%s] %s\n", getHoraActual().c_str(), mensaje);
}

// ── Parpadear LED ─────────────────────────────────────
void parpadearLED(int veces, int retardo) {
  for (int i = 0; i < veces; i++) {
    digitalWrite(PIN_LED, HIGH);
    delay(retardo);
    digitalWrite(PIN_LED, LOW);
    delay(retardo);
  }
  digitalWrite(PIN_LED, controlActivo ? HIGH : LOW);
}

void actualizarLED() {
  digitalWrite(PIN_LED, controlActivo ? HIGH : LOW);
}

void enviarSensor(int valor) {
  if (!controlActivo || !wsConectado) return;
  if (valor == ultimoEnviado) return;
  unsigned long ahora = millis();
  if (ahora - ultimoEnvio < 150) return;
  
  doc.clear();
  doc["tipo"] = "senal";
  doc["valor"] = valor;
  serializeJson(doc, buffer);
  
  if (wsClient.sendTXT(buffer)) {
    ultimoEnvio = ahora;
    ultimoEnviado = valor;
    logConHora(valor ? ">>> HIGH" : ">>> LOW");
  }
}

void manejarMensaje(uint8_t* payload, size_t length) {
  char msg[200];
  memcpy(msg, payload, min(length, (size_t)199));
  msg[min(length, (size_t)199)] = '\0';
  
  if (!strstr(msg, "\"tipo\":\"control\"")) return;
  Serial.printf("[%s] <<< %s\n", getHoraActual().c_str(), msg);
  
  bool activar = strstr(msg, "\"activo\":true") || strstr(msg, "\"sensorActivo\":true");
  bool desactivar = strstr(msg, "\"activo\":false") || strstr(msg, "\"sensorActivo\":false");
  
  if (desactivar && controlActivo) {
    controlActivo = false;
    actualizarLED();
    ultimoEnviado = -1;
    logConHora("LED OFF");
  }
  else if (activar && !controlActivo) {
    controlActivo = true;
    actualizarLED();
    logConHora("LED ON");
    
    delay(50);
    int lecturaFresca = digitalRead(PIN_SENSOR);
    delay(DEBOUNCE_MS);
    if (digitalRead(PIN_SENSOR) == lecturaFresca) {
      estadoSensor = lecturaFresca;
      estadoEstable = lecturaFresca;
    }
    
    Serial.printf("[%s] Estado real al prender: %s\n", 
                  getHoraActual().c_str(),
                  estadoSensor ? "HIGH" : "LOW");
    enviarSensor(estadoSensor);
  }
}

void eventoWS(WStype_t type, uint8_t* payload, size_t length) {
  switch(type) {
    case WStype_CONNECTED:
      logConHora("WS CONECTADO!");
      wsConectado = true;
      reconexiones++;
      
      parpadearLED(3, 150);
      
      delay(50);
      enviarSensor(estadoSensor);
      
      Serial.printf("[%s] Conexión #%d | Sensor: %s | Control: %s\n",
                    getHoraActual().c_str(),
                    reconexiones,
                    estadoSensor ? "HIGH" : "LOW",
                    controlActivo ? "ON" : "OFF");
      break;
      
    case WStype_DISCONNECTED:
      logConHora("WS DESCONECTADO");
      wsConectado = false;
      digitalWrite(PIN_LED, LOW); 
      break;
      
    case WStype_TEXT:
      manejarMensaje(payload, length);
      break;
      
    case WStype_ERROR:
      logConHora("WS ERROR");
      wsConectado = false;
      digitalWrite(PIN_LED, LOW);
      break;
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n╔════════════════════════════════════╗");
  Serial.println("║   SENSOR IOT vFINAL                ║");
  Serial.println("╚════════════════════════════════════╝");
  
  pinMode(PIN_SENSOR, INPUT_PULLDOWN);
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_BOOT, INPUT_PULLUP);
  
  digitalWrite(PIN_LED, LOW);
  
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.persistent(false);
  
  wm.setDebugOutput(false);
  wm.setConfigPortalTimeout(120);
  wm.setConnectTimeout(10);
  
  Serial.printf("[%s] Conectando WiFi...\n", getHoraActual().c_str());
  
  if (!wm.autoConnect("Sensor-IoT", "sensor123")) {
    Serial.printf("[%s] WiFi FALLÓ\n", getHoraActual().c_str());
    delay(3000);
    ESP.restart();
  }
  
  // Configurar NTP para obtener hora real
  configTime(-18000, 0, "pool.ntp.org", "time.nist.gov");  // UTC-5 (Perú)
  
  Serial.printf("[%s] WiFi: %s | RSSI: %d dBm\n", 
                getHoraActual().c_str(),
                WiFi.localIP().toString().c_str(), 
                WiFi.RSSI());
  
  // Esperar a que NTP sincronice (máximo 3 segundos)
  int intentos = 0;
  struct tm timeinfo;
  while (!getLocalTime(&timeinfo) && intentos < 15) {
    delay(200);
    intentos++;
  }
  
  if (getLocalTime(&timeinfo)) {
    Serial.printf("[%s] Hora sincronizada\n", getHoraActual().c_str());
  } else {
    Serial.println("[--:--:--] No se pudo sincronizar NTP");
  }
  
  wsClient.begin(WS_HOST, WS_PORT, WS_PATH);
  wsClient.onEvent(eventoWS);
  wsClient.setReconnectInterval(3000);
  wsClient.enableHeartbeat(10000, 3000, 3);
  
  Serial.printf("[%s] Sistema listo!\n", getHoraActual().c_str());
}

void loop() {
  unsigned long ahora = millis();
  
  wsClient.loop();
  
  int lectura = digitalRead(PIN_SENSOR);
  
  if (lectura != lecturaAnterior) {
    ultimoCambioSensor = ahora;
    lecturaAnterior = lectura;
  }
  
  if ((ahora - ultimoCambioSensor) >= DEBOUNCE_MS) {
    if (lectura != estadoEstable) {
      estadoEstable = lectura;
      estadoSensor = lectura;
      Serial.printf("[%s] SENSOR: %s\n", 
                    getHoraActual().c_str(),
                    lectura ? "HIGH" : "LOW");
      enviarSensor(lectura);
    }
  }
  
  if (!wsConectado && (ahora - ultimoIntentoRecon > 3000)) {
    ultimoIntentoRecon = ahora;
    Serial.printf("[%s] Intentando reconectar WS...\n", getHoraActual().c_str());
    wsClient.disconnect();
    delay(100);
    wsClient.begin(WS_HOST, WS_PORT, WS_PATH);
    wsClient.onEvent(eventoWS);
    wsClient.setReconnectInterval(3000);
    wsClient.enableHeartbeat(10000, 3000, 3);
  }
  
  static unsigned long bootInicio = 0;
  if (digitalRead(PIN_BOOT) == LOW) {
    if (bootInicio == 0) bootInicio = ahora;
    if (ahora - bootInicio > 3000) {
      Serial.printf("[%s] Reset WiFi\n", getHoraActual().c_str());
      wm.resetSettings();
      delay(500);
      ESP.restart();
    }
  } else {
    bootInicio = 0;
  }
  
  if (ahora - ultimaEstabilidad > 30000) {
    ultimaEstabilidad = ahora;
    Serial.printf("[%s] ESTADO | WiFi: %s | WS: %s | Control: %s | Sensor: %s | Mem: %d\n",
                  getHoraActual().c_str(),
                  WiFi.isConnected() ? "OK" : "NO",
                  wsConectado ? "OK" : "NO",
                  controlActivo ? "ON" : "OFF",
                  estadoSensor ? "HIGH" : "LOW",
                  ESP.getFreeHeap());
  }
  
  delay(5);
}