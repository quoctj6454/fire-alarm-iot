from fastapi import FastAPI, HTTPException, Query
from database import get_db_connection, release_db_connection
from fastapi.middleware.cors import CORSMiddleware
import psycopg2.extras
from models import SystemEvent, SensorLog, SystemSummary, FireDailyStat
from typing import List

app = FastAPI(title="Fire Alarm API System")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 1. Lấy log sự kiện - thêm Pagination & Input Validation
@app.get("/api/events", response_model=List[SystemEvent])
def get_events(
        limit: int = Query(20, gt=0, le=100), # Validate: 1-100 bản ghi
        offset: int = Query(0, ge=0)          # Không cho phép số âm
):
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        # CHỈ SELECT FIELD CẦN THIẾT - KHÔNG DÙNG SELECT *
        query = """
                SELECT created_at, event_type, description, snapshot_temp, snapshot_smoke
                FROM system_events
                ORDER BY created_at DESC
                    LIMIT %s OFFSET %s; \
                """
        cur.execute(query, (limit, offset))
        return cur.fetchall()
    except Exception as e:
        raise HTTPException(status_code=500, detail={"error": str(e)})
    finally:
        cur.close()
        release_db_connection(conn)

# 2. Endpoint Summary - Gộp dữ liệu phân tích
@app.get("/api/summary", response_model=SystemSummary)
def get_system_summary():
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        # Truy vấn gộp để tối ưu hiệu năng
        cur.execute("""
                    SELECT
                            (SELECT COUNT(*) FROM system_events WHERE event_type = 'WARNING') as total_warnings,
                            (SELECT COUNT(*) FROM system_events WHERE event_type = 'FIRE' AND created_at >= CURRENT_DATE) as fires_today,
                            (SELECT MAX(created_at) FROM system_events) as last_event_time;
                    """)
        return cur.fetchone()
    except Exception as e:
        raise HTTPException(status_code=500, detail={"error": str(e)})
    finally:
        cur.close()
        release_db_connection(conn)

# 3. Lấy dữ liệu cảm biến - giới hạn cho Mobile (50 điểm gần nhất)
@app.get("/api/sensors/history", response_model=List[SensorLog])
def get_sensor_history(limit: int = Query(50, gt=0, le=100)):
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        cur.execute("""
                    SELECT created_at as time, temperature, smoke
                    FROM sensor_logs
                    ORDER BY created_at DESC
                        LIMIT %s;
                    """, (limit,))
        return cur.fetchall()
    except Exception as e:
        raise HTTPException(status_code=500, detail={"error": str(e)})
    finally:
        cur.close()
        release_db_connection(conn)

# 4. Thống kê theo ngày (Dành cho Bar Chart)
@app.get("/api/stats/fire-daily", response_model=List[FireDailyStat])
def get_fire_daily_stats():
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        query = """
                SELECT DATE(created_at) as date, COUNT(*) as count
                FROM system_events
                WHERE event_type = 'FIRE'
                GROUP BY DATE(created_at)
                ORDER BY date ASC; \
                """
        cur.execute(query)
        return cur.fetchall()
    except Exception as e:
        raise HTTPException(status_code=500, detail={"error": str(e)})
    finally:
        cur.close()
        release_db_connection(conn)