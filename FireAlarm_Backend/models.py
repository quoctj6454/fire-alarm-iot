from pydantic import BaseModel
from datetime import datetime
from typing import Optional

# Cấu trúc cho 1 sự kiênj ( Event)
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


# Summary ( Dùng cho DashBoard Analytics)
class SystemSummary(BaseModel):
    total_warnings: int
    fires_today: int
    last_event_time: Optional[datetime] = None

class WarningCount(BaseModel):
    total: int

class FireDailyStat(BaseModel):
    date: datetime
    count: int