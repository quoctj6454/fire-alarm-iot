import os

import firebase_admin
from firebase_admin import credentials, db
import psycopg2
from psycopg2 import extras
from dotenv import load_dotenv

load_dotenv()

# --- 1. CẤU HÌNH KẾT NỐI (đọc từ file .env) ---
cred_path = os.environ.get("FIREBASE_CREDENTIALS", "serviceAccountKey.json")
cred = credentials.Certificate(cred_path)
firebase_admin.initialize_app(cred, {
    "databaseURL": os.environ["FIREBASE_DATABASE_URL"],
})

db_params = {
    "host": os.environ["PGHOST"],
    "database": os.environ["PGDATABASE"],
    "user": os.environ["PGUSER"],
    "password": os.environ["PGPASSWORD"],
    "port": int(os.environ.get("PGPORT", 5432)),
    "sslmode": os.environ.get("PGSSLMODE", "require"),
}

def get_db_connection():
    return psycopg2.connect(**db_params)

# --- 2. LOGIC XỬ LÝ SỰ KIỆN (SYSTEM EVENTS) ---
def handle_system_event(event):
    if event.data is None: return
    
    # Firebase listen() trả về cả mảng dữ liệu nếu là lần đầu chạy
    # data_items = event.data if isinstance(event.data, dict) else {event.path.strip('/'): event.data}

    if event.path == '/' or event.path == '':
        data_items = event.data # Lần đầu load toàn bộ
    else:
        # Có 1 dòng dữ liệu mới đẩy lên
        data_items = {event.path.strip('/'): event.data}
    
    conn = get_db_connection()
    cur = conn.cursor()
    
    try:
        for key, value in data_items.items():
            print(f"📦 Đang xử lý sự kiện: {key}")
            
            # Extract & Transform
            query = """
                INSERT INTO system_events (device_id, event_type, description, snapshot_temp, snapshot_smoke)
                VALUES (%s, %s, %s, %s, %s)
            """
            params = (
                value.get('device_id', 'NODE_01'),
                value.get('event_type'),
                value.get('description'),
                value.get('snapshot_temp'),
                value.get('snapshot_smoke')
            )
            
            # Load
            cur.execute(query, params)
            
            # Clean: Xóa sau khi đã lưu vào SQL thành công
            db.reference(f'/system_events_buffer/{key}').delete()
            
        conn.commit()
        print("✅ Đã đồng bộ sự kiện sang PostgreSQL.")
    except Exception as e:
        print(f"❌ Lỗi: {e}")
        conn.rollback()
    finally:
        cur.close()
        conn.close()

# --- 3. LOGIC XỬ LÝ CẢM BIẾN (SENSOR LOGS) ---
def handle_sensor_log(event):
    if event.data is None: return
    # data_items = event.data if isinstance(event.data, dict) else {event.path.strip('/'): event.data}

    if event.path == '/' or event.path == '':
        data_items = event.data
    else:
        data_items = {event.path.strip('/'): event.data}
    
    conn = get_db_connection()
    cur = conn.cursor()
    try:
        for key, value in data_items.items():
            query = """
                INSERT INTO sensor_logs (device_id, temperature, smoke)
                VALUES (%s, %s, %s)
            """
            cur.execute(query, (value.get('device_id', 'NODE_01'), value.get('temperature'), value.get('smoke')))
            db.reference(f'/sensor_logs_buffer/{key}').delete()
        conn.commit()
        print("📈 Đã đồng bộ log cảm biến sang PostgreSQL.")
    except Exception as e:
        print(f"❌ Lỗi: {e}")
        conn.rollback()
    finally:
        cur.close()
        conn.close()





# --- 4. BẮT ĐẦU LẮNG NGHE ---
print("🚀 ETL Worker đang chạy... Đang lắng nghe Firebase.")
db.reference('/system_events_buffer').listen(handle_system_event)
db.reference('/sensor_logs_buffer').listen(handle_sensor_log)