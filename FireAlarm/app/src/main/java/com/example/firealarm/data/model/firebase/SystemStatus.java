package com.example.firealarm.data.model.firebase;

public class SystemStatus {
    public long temp;
    public long smoke;
    public boolean fire_detected;
    public boolean manual_pump;

    // Constructor mặc định cần thiết cho Firebase ép kiểu
    public SystemStatus() {
    }

    public SystemStatus(long temp, long smoke, boolean fire_detected, boolean manual_pump) {
        this.temp = temp;
        this.smoke = smoke;
        this.fire_detected = fire_detected;
        this.manual_pump = manual_pump;
    }
}