from pydantic import BaseModel
from datetime import datetime
from typing import Optional, List

# Cấu trúc cho 1 sự kiện (Event)
class SystemEvent(BaseModel):
    created_at: datetime
    event_type: str
    description: Optional[str] = None
    snapshot_temp: Optional[float] = None
    snapshot_smoke: Optional[int] = None


# Cấu trúc cho log cảm biến
class SensorLog(BaseModel):
    time: datetime
    temperature: float
    smoke: int


# Summary (Dùng cho DashBoard Analytics)
class SystemSummary(BaseModel):
    total_warnings: int
    warnings_today: int
    total_fires: int
    fires_today: int
    last_event_time: Optional[datetime] = None


# Response phân trang cho danh sách sự kiện
class PaginatedEventsResponse(BaseModel):
    items: List[SystemEvent]
    total: int
    page: int
    total_pages: int
    limit: int
    offset: int


class WarningCount(BaseModel):
    total: int

class FireDailyStat(BaseModel):
    date: datetime
    count: int