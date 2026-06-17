from fastapi import FastAPI, HTTPException, Query
from database import get_db_connection, release_db_connection
from fastapi.middleware.cors import CORSMiddleware
import psycopg2.extras
import math
from models import (
    SystemEvent, SensorLog, SystemSummary, FireDailyStat,
    PaginatedEventsResponse, DashboardResponse,
)
from typing import List, Optional, Tuple

app = FastAPI(title="Fire Alarm API System")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# So sánh ngày lịch theo giờ Việt Nam (UTC+7) — dùng cho filter "hôm nay"
_VN_TODAY_DATE = "(NOW() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date"
_VN_EVENT_DATE = "DATE(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')"
_VN_IS_TODAY = f"{_VN_EVENT_DATE} = {_VN_TODAY_DATE}"


def _event_filter_clause(event_type: Optional[str], today_only: bool) -> Tuple[str, list]:
    conditions = []
    params = []
    if event_type:
        conditions.append("event_type = %s")
        params.append(event_type)
    if today_only:
        conditions.append(_VN_IS_TODAY)
    where_clause = ("WHERE " + " AND ".join(conditions)) if conditions else ""
    return where_clause, params


def _fetch_summary(cur) -> dict:
    cur.execute(f"""
        SELECT
            COUNT(*) FILTER (WHERE event_type = 'WARNING') AS total_warnings,
            COUNT(*) FILTER (WHERE event_type = 'WARNING'
                AND {_VN_IS_TODAY}) AS warnings_today,
            COUNT(*) FILTER (WHERE event_type = 'FIRE') AS total_fires,
            COUNT(*) FILTER (WHERE event_type = 'FIRE'
                AND {_VN_IS_TODAY}) AS fires_today,
            MAX(created_at) AS last_event_time
        FROM system_events
    """)
    return cur.fetchone()


def _fetch_events_page(
        cur,
        limit: int,
        offset: int,
        event_type: Optional[str],
        today_only: bool,
        include_total: bool,
) -> dict:
    where_clause, params = _event_filter_clause(event_type, today_only)

    if include_total:
        cur.execute(
            f"SELECT COUNT(*) AS total FROM system_events {where_clause}",
            params,
        )
        total = cur.fetchone()["total"]
    else:
        total = -1

    cur.execute(
        f"""SELECT created_at, event_type, description, snapshot_temp, snapshot_smoke
            FROM system_events
            {where_clause}
            ORDER BY created_at DESC
            LIMIT %s OFFSET %s""",
        params + [limit, offset],
    )
    items = cur.fetchall()

    page = (offset // limit) + 1
    if include_total:
        total_pages = max(1, math.ceil(total / limit))
    else:
        total_pages = -1

    return {
        "items": items,
        "total": total,
        "page": page,
        "total_pages": total_pages,
        "limit": limit,
        "offset": offset,
    }


@app.get("/api/events", response_model=PaginatedEventsResponse)
def get_events(
        limit: int = Query(20, gt=0, le=100),
        offset: int = Query(0, ge=0),
        event_type: Optional[str] = Query(None),
        today_only: bool = Query(False),
        include_total: bool = Query(True),
):
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        return _fetch_events_page(
            cur, limit, offset, event_type, today_only, include_total
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail={"error": str(e)})
    finally:
        cur.close()
        release_db_connection(conn)


@app.get("/api/summary", response_model=SystemSummary)
def get_system_summary():
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        return _fetch_summary(cur)
    except Exception as e:
        raise HTTPException(status_code=500, detail={"error": str(e)})
    finally:
        cur.close()
        release_db_connection(conn)


@app.get("/api/dashboard", response_model=DashboardResponse)
def get_dashboard(
        page: int = Query(1, ge=1),
        limit: int = Query(20, gt=0, le=100),
        event_type: Optional[str] = Query(None),
        today_only: bool = Query(False),
        include_total: bool = Query(True),
):
    """Summary + 1 trang events trong 1 request (tab Thống kê)."""
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        offset = (page - 1) * limit
        summary = _fetch_summary(cur)
        events = _fetch_events_page(
            cur, limit, offset, event_type, today_only, include_total
        )
        return {"summary": summary, "events": events}
    except Exception as e:
        raise HTTPException(status_code=500, detail={"error": str(e)})
    finally:
        cur.close()
        release_db_connection(conn)


@app.get("/api/sensors/history", response_model=List[SensorLog])
def get_sensor_history(limit: int = Query(50, gt=0, le=100)):
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        cur.execute("""
            SELECT created_at AS time, temperature, smoke
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


@app.get("/api/stats/fire-daily", response_model=List[FireDailyStat])
def get_fire_daily_stats():
    conn = get_db_connection()
    cur = conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    try:
        cur.execute("""
            SELECT DATE(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') AS date,
                   COUNT(*) AS count
            FROM system_events
            WHERE event_type = 'FIRE'
            GROUP BY DATE(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')
            ORDER BY date ASC;
        """)
        return cur.fetchall()
    except Exception as e:
        raise HTTPException(status_code=500, detail={"error": str(e)})
    finally:
        cur.close()
        release_db_connection(conn)
