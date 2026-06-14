from fastapi import FastAPI, HTTPException, Query
from database import get_db_connection, release_db_connection
from fastapi.middleware.cors import CORSMiddleware
import psycopg2.extras
import math
from models import SystemEvent, SensorLog, SystemSummary, FireDailyStat, PaginatedEventsResponse
from typing import List, Optional

app = FastAPI(title="Fire Alarm API System")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 1. Lấy log sự kiện - server-side filter + phân trang có metadata
@app.get("/api/events", response_model=PaginatedEventsResponse)
def get_events(
        limit: int = Query(20, gt=0, le=100),
        offset: int = Query(0, ge=0),
        event_type: Optional[str] = Query(None),
        today_only: bool = Query(False)   # dùng CURRENT_DATE của server, tránh lệch timezone
):
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        conditions = []
        params = []
        if event_type:
            conditions.append("event_type = %s")
            params.append(event_type)
        if today_only:
            conditions.append("created_at >= CURRENT_DATE")  # cùng timezone với /api/summary

        where_clause = ("WHERE " + " AND ".join(conditions)) if conditions else ""

        # COUNT để tính total_pages (index đã có → nhanh)
        cur.execute(
            f"SELECT COUNT(*) as total FROM system_events {where_clause}",
            params
        )
        total = cur.fetchone()["total"]

        # DATA: chỉ 1 trang
        cur.execute(
            f"""SELECT created_at, event_type, description, snapshot_temp, snapshot_smoke
                FROM system_events
                {where_clause}
                ORDER BY created_at DESC
                LIMIT %s OFFSET %s""",
            params + [limit, offset]
        )
        items = cur.fetchall()

        page = (offset // limit) + 1
        total_pages = max(1, math.ceil(total / limit))

        return {
            "items": items,
            "total": total,
            "page": page,
            "total_pages": total_pages,
            "limit": limit,
            "offset": offset,
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail={"error": str(e)})
    finally:
        cur.close()
        release_db_connection(conn)

# 2. Endpoint Summary - tổng quan 4 chỉ số
@app.get("/api/summary", response_model=SystemSummary)
def get_system_summary():
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        cur.execute("""
            SELECT
                (SELECT COUNT(*) FROM system_events WHERE event_type = 'WARNING')                          AS total_warnings,
                (SELECT COUNT(*) FROM system_events WHERE event_type = 'WARNING' AND created_at >= CURRENT_DATE) AS warnings_today,
                (SELECT COUNT(*) FROM system_events WHERE event_type = 'FIRE')                             AS total_fires,
                (SELECT COUNT(*) FROM system_events WHERE event_type = 'FIRE'    AND created_at >= CURRENT_DATE) AS fires_today,
                (SELECT MAX(created_at) FROM system_events)                                                AS last_event_time
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