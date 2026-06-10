#include <Arduino.h>
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <DHT.h>
#include <Wire.h>               // Thêm thư viện I2C
#include <LiquidCrystal_I2C.h>  // Thêm thư viện LCD

// Helper functions cho Firebase-ESP-Client
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// --- Cấu hình Wi-Fi ---
#define WIFI_SSID "KenBar Xin Chao"
#define WIFI_PASSWORD ""

// --- Cấu hình Firebase ---
#define API_KEY "AIzaSyBwHc76tUflMAl31DV2OAa1gopgssTaIlY"
#define DATABASE_URL "https://firealarm-3bb42-default-rtdb.asia-southeast1.firebasedatabase.app"

// --- Khai báo chân (Pins) ---
#define DHTPIN 4
#define DHTTYPE DHT22
#define MQ2_PIN 34
#define FLAME_PIN 35
#define RELAY_PIN 18

// --- Khởi tạo đối tượng ---
DHT dht(DHTPIN, DHTTYPE);
LiquidCrystal_I2C lcd(0x27, 16, 2); // Khởi tạo LCD địa chỉ 0x27, 16 cột, 2 hàng

FirebaseData fbdo;        
FirebaseData streamData;  
FirebaseAuth auth;
FirebaseConfig config;

// --- Biến thời gian ---
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 2000; 

// --- Callback kích hoạt khi có sự thay đổi trên Firebase ---
void streamCallback(FirebaseStream data) {
  if (data.dataType() == "boolean") {
    bool pumpState = data.boolData();
    digitalWrite(RELAY_PIN, pumpState ? HIGH : LOW);
    Serial.print("Manual pump state changed to: ");
    Serial.println(pumpState ? "ON" : "OFF");
  } else if (data.dataType() == "int") {
    int pumpState = data.intData();
    digitalWrite(RELAY_PIN, pumpState == 1 ? HIGH : LOW);
    Serial.print("Manual pump state changed to: ");
    Serial.println(pumpState == 1 ? "ON" : "OFF");
  }
}

void streamTimeoutCallback(bool timeout) {
  if (timeout) {
    Serial.println("Firebase stream timeout, resuming...");
  }
}

//============== DATA LOG EVENT =============================================///////////////////////
//============================    KHAI BÁO BIẾN  ==========================================
// --- Biến lưu trạng thái cũ để so sánh (Edge Computing) ---
// float lastTemp = 0;
// int lastSmoke = 0;
// bool lastFireState = false;   // false là an toàn, true là có cháy
// bool lastPumpState = false;   // Trạng thái bơm trước đó

// // --- Ngưỡng định nghĩa sự kiện ---
// #define TEMP_DELTA 2.0        // Chênh lệch nhiệt độ để lưu log
// #define SMOKE_DELTA 50        // Chênh lệch khói để lưu log
// #define TEMP_THRESHOLD 50.0   // Ngưỡng báo động nhiệt độ
// #define SMOKE_THRESHOLD 1500   // Ngưỡng báo động khói




//============== DATA LOG EVENT & EDGE COMPUTING ==================================

// --- 1. Biến & Ngưỡng cho Dữ liệu Realtime (Màn hình App) ---
// Yêu cầu: Nhạy bén vừa phải, tránh màn hình App bị "đứng hình" nhưng không spam.
#define TEMP_REALTIME_DELTA 0.5    // Lệch 0.5 độ mới báo lên App
#define SMOKE_REALTIME_DELTA 50   // Lệch 15 ppm mới báo lên App
#define REALTIME_HEARTBEAT 10000   // Bắt buộc cập nhật App mỗi 10s dù không đổi
float lastRealtimeTemp = -100;
int lastRealtimeSmoke = -100;
bool lastRealtimeFire = false;
unsigned long lastRealtimeTime = 0;

// --- 2. Biến & Ngưỡng cho Lịch sử Log (Lưu PostgreSQL) ---
// Yêu cầu: Giãn cách xa hơn, chỉ lưu khi biến động rõ rệt để tiết kiệm Database.
#define TEMP_LOG_DELTA 2.0         // Lệch 2 độ mới lưu Database
#define SMOKE_LOG_DELTA 100        // Lệch 50 ppm mới lưu Database
#define LOG_HEARTBEAT 300000       // Bắt buộc lưu Database mỗi 5 phút (300,000ms)
float lastTemp = -100;
int lastSmoke = -100;
unsigned long lastLogTime = 0;

// --- 3. Biến & Ngưỡng cho Cảnh báo/Sự kiện (Event) ---
#define TEMP_THRESHOLD 50.0   
#define SMOKE_THRESHOLD 1500  
bool lastFireState = false;   
bool lastPumpState = false;

//=======================================================================================================

void setup() {
  Serial.begin(115200);

  // Khởi tạo LCD
  lcd.init();
  lcd.backlight();
  lcd.setCursor(0, 0);
  lcd.print("System Starting");

  // 1. Cấu hình I/O
  pinMode(MQ2_PIN, INPUT);
  pinMode(FLAME_PIN, INPUT);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW); 

  dht.begin();

  // 2. Kết nối Wi-Fi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  WiFi.setAutoReconnect(true); 
  
  
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(300);
  }
  Serial.println("\nWi-Fi connected.");
  
  lcd.clear();
  lcd.print("WiFi Connected");

  // 3. Cấu hình thông tin Firebase
  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;

  // Thực hiện đăng nhập ẩn danh (Anonymous Authentication)
  Serial.print("Sign up as anonymous... ");
  if (Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("OK");
  } else {
    Serial.printf("FAILED: %s\n", config.signer.signupError.message.c_str());
  }

  config.token_status_callback = tokenStatusCallback;

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);

  // 4. Thiết lập lắng nghe (Stream)
  if (!Firebase.RTDB.beginStream(&streamData, "/system_status/manual_pump")) {
    Serial.printf("Stream begin error: %s\n", streamData.errorReason().c_str());
  }
  Firebase.RTDB.setStreamCallback(&streamData, streamCallback, streamTimeoutCallback);
}

void loop() {
  // 2. Tự động bật máy bơm: Tức thời bật khi phát hiện cháy
  if (digitalRead(FLAME_PIN) == LOW) {
    digitalWrite(RELAY_PIN, HIGH);
  }

  if (Firebase.ready() && (millis() - lastSendTime > sendInterval || lastSendTime == 0)) {
    lastSendTime = millis();

    // Đọc cảm biến
    float temp = dht.readTemperature();
    int smoke = analogRead(MQ2_PIN); 
    int fireDetected = digitalRead(FLAME_PIN); 
    
    if (isnan(temp)) {
      Serial.println("Failed to read from DHT sensor!");
      temp = 0.0;
    }

    // --- LOGIC HIỂN THỊ LCD ---
    lcd.clear();
    // Dòng 1: Hiển thị Nhiệt độ và Khói
    lcd.setCursor(0, 0);
    lcd.print("T:"); lcd.print(temp, 1); lcd.print("C");
    lcd.setCursor(9, 0);
    lcd.print("S:"); lcd.print(smoke);

    // Dòng 2: Hiển thị trạng thái Lửa
    lcd.setCursor(0, 1);
    if (fireDetected == LOW) { // Cảm biến lửa thường trả về LOW khi thấy lửa
       lcd.print("FIRE! WARNING ");
    } else {
       lcd.print("Status: Normal ");
    }

  




    bool currentFireState = (fireDetected == LOW);
    bool currentPumpState = (digitalRead(RELAY_PIN) == HIGH);

    // ==============================================================================
    // 🛡️ LỚP LỌC 1: REALTIME (Dành cho UI Mobile)
    // ==============================================================================
    // Chỉ đẩy lên /system_status khi: Lệch ngưỡng nhỏ, HOẶC Trạng thái cháy thay đổi, HOẶC Quá 10 giây.
    bool realtimeChanged = (fabs(temp - lastRealtimeTemp) >= TEMP_REALTIME_DELTA) || 
                           (abs(smoke - lastRealtimeSmoke) >= SMOKE_REALTIME_DELTA) ||
                           (currentFireState != lastRealtimeFire);

    if (realtimeChanged || (millis() - lastRealtimeTime > REALTIME_HEARTBEAT) || lastRealtimeTime == 0) {
      FirebaseJson json;
      json.set("temp", temp);
      json.set("smoke", smoke);
      json.set("fire_detected", currentFireState);

      Serial.print("Edge: Pushing Realtime data... ");
      if (Firebase.RTDB.updateNode(&fbdo, "/system_status", &json)) {
        Serial.println("PASSED");
        // Reset lại mốc so sánh
        lastRealtimeTemp = temp;
        lastRealtimeSmoke = smoke;
        lastRealtimeFire = currentFireState;
        lastRealtimeTime = millis();
      } else {
        Serial.println("FAILED: " + fbdo.errorReason());
      }
    }

    // ==============================================================================
    // 🛡️ LỚP LỌC 2: SENSOR LOGS (Dành cho Database/Biểu đồ)
    // ==============================================================================
    // Chỉ đẩy lên /sensor_logs_buffer khi: Lệch ngưỡng lớn, HOẶC Quá 5 phút (Heartbeat).
    bool logChanged = (fabs(temp - lastTemp) >= TEMP_LOG_DELTA) || 
                      (abs(smoke - lastSmoke) >= SMOKE_LOG_DELTA);

    if (logChanged || (millis() - lastLogTime > LOG_HEARTBEAT) || lastLogTime == 0) {
      FirebaseJson sensorLog;
      sensorLog.set("device_id", "NODE_01");
      sensorLog.set("temperature", temp);
      sensorLog.set("smoke", smoke);
      
      if (Firebase.RTDB.pushJSON(&fbdo, "/sensor_logs_buffer", &sensorLog)) {
        Serial.println("Edge: Pushed Sensor Log");
        lastTemp = temp;
        lastSmoke = smoke;
        lastLogTime = millis();
      }
    }


    // // --- LOGIC REALTIME (Luồng cũ) ---
    // FirebaseJson json;
    // json.set("temp", temp);
    // json.set("smoke", smoke);
    // json.set("fire_detected", (fireDetected == LOW));

    // Serial.print("Pushing sensor data to Firebase... ");
    // if (Firebase.RTDB.updateNode(&fbdo, "/system_status", &json)) {
    //   Serial.println("PASSED");
    // } else {
    //   Serial.println("FAILED");
    //   Serial.println("REASON: " + fbdo.errorReason());
    // }

    // // ==============================================================================
    // // --- LOGIC EDGE COMPUTING ---
    // // ==============================================================================
    // bool currentFireState = (fireDetected == LOW);
    // bool currentPumpState = (digitalRead(RELAY_PIN) == HIGH);

    // // 3. Ghi log cảm biến (Sensor Logs)
    // if (abs(temp - lastTemp) > TEMP_DELTA || abs(smoke - lastSmoke) > SMOKE_DELTA) {
    //   FirebaseJson sensorLog;
    //   sensorLog.set("device_id", "NODE_01");
    //   sensorLog.set("temperature", temp);
    //   sensorLog.set("smoke", smoke);
      
    //   if (Firebase.RTDB.pushJSON(&fbdo, "/sensor_logs_buffer", &sensorLog)) {
    //     Serial.println("Edge: Pushed Sensor Log");
    //     lastTemp = temp;
    //     lastSmoke = smoke;
    //   } else {
    //     Serial.println("Edge: Failed to push Sensor Log: " + fbdo.errorReason());
    //   }
    // }

    // 4. Ghi log sự kiện (System Events)
    
    // a. Sự kiện CHÁY/SAFE: Khi trạng thái lửa thay đổi.
    if (currentFireState != lastFireState) {
      FirebaseJson eventLog;
      eventLog.set("device_id", "NODE_01");
      eventLog.set("event_type", currentFireState ? "FIRE" : "SAFE");
      eventLog.set("description", currentFireState ? "Phát hiện cháy!" : "Hệ thống an toàn.");
      eventLog.set("snapshot_temp", temp);
      eventLog.set("snapshot_smoke", smoke);

      if (Firebase.RTDB.pushJSON(&fbdo, "/system_events_buffer", &eventLog)) {
        Serial.println("Edge: Pushed Event -> " + String(currentFireState ? "FIRE" : "SAFE"));
        lastFireState = currentFireState;
      }
    }

    // b. Sự kiện CẢNH BÁO: Khi chưa cháy nhưng quá nhiệt hoặc quá khói (Cooldown 60s)
    static unsigned long lastWarningTime = 0;
    if (!currentFireState && (temp > TEMP_THRESHOLD || smoke > SMOKE_THRESHOLD)) {
      if (millis() - lastWarningTime > 60000 || lastWarningTime == 0) {
        FirebaseJson eventLog;
        eventLog.set("device_id", "NODE_01");
        eventLog.set("event_type", "WARNING");
        eventLog.set("description", "Vượt ngưỡng an toàn (Nhiệt/Khói).");
        eventLog.set("snapshot_temp", temp);
        eventLog.set("snapshot_smoke", smoke);

        if (Firebase.RTDB.pushJSON(&fbdo, "/system_events_buffer", &eventLog)) {
          Serial.println("Edge: Pushed Event -> WARNING");
          lastWarningTime = millis();
        }
      }
    }

    // c. Sự kiện BƠM: Khi trạng thái RELAY thay đổi.
    if (currentPumpState != lastPumpState) {
      FirebaseJson eventLog;
      eventLog.set("device_id", "NODE_01");
      eventLog.set("event_type", currentPumpState ? "PUMP_ON" : "PUMP_OFF");
      eventLog.set("description", currentPumpState ? "Máy bơm được bật." : "Máy bơm đã tắt.");
      eventLog.set("snapshot_temp", temp);
      eventLog.set("snapshot_smoke", smoke);

      if (Firebase.RTDB.pushJSON(&fbdo, "/system_events_buffer", &eventLog)) {
        Serial.println("Edge: Pushed Event -> " + String(currentPumpState ? "PUMP_ON" : "PUMP_OFF"));
        lastPumpState = currentPumpState;
      }
    }
    // ==============================================================================
  }
}