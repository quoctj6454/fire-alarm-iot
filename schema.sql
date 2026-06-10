-- ============================================================
-- Fire Alarm IoT Data Platform - PostgreSQL Schema (Neon.tech)
-- Chạy script này trên Neon SQL Editor hoặc psql trước khi
-- khởi động ETL worker (data_ingestion.py) và FastAPI backend.
-- ============================================================

-- Bảng 1: Lưu lịch sử thông số cảm biến định kỳ
CREATE TABLE IF NOT EXISTS sensor_logs (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(50) DEFAULT 'NODE_01',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    temperature NUMERIC(5, 2),
    smoke INTEGER
);

-- Index tối ưu truy vấn theo thời gian (biểu đồ xu hướng)
CREATE INDEX IF NOT EXISTS idx_sensor_logs_time ON sensor_logs (created_at DESC);

-- Bảng 2: Lưu lịch sử sự kiện hệ thống
CREATE TABLE IF NOT EXISTS system_events (
    id SERIAL PRIMARY KEY,
    device_id VARCHAR(50) DEFAULT 'NODE_01',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    event_type VARCHAR(20) CHECK (
        event_type IN ('WARNING', 'FIRE', 'SAFE', 'PUMP_ON', 'PUMP_OFF')
    ),
    description TEXT,
    snapshot_temp NUMERIC(5, 2),
    snapshot_smoke INTEGER
);

-- Index tối ưu lọc sự kiện theo thời gian và loại sự kiện
CREATE INDEX IF NOT EXISTS idx_system_events_time ON system_events (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_system_events_type ON system_events (event_type);
