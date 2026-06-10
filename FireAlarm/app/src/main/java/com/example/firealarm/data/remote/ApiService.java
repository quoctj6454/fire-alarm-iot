package com.example.firealarm.data.remote;

import com.example.firealarm.data.model.api.FireDailyStatResponse;
import com.example.firealarm.data.model.api.SensorLogResponse;
import com.example.firealarm.data.model.api.SystemEventResponse;
import com.example.firealarm.data.model.api.SystemSummaryResponse;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ApiService {
    // 1. Lấy danh sách sự kiện (Có phân trang)
    @GET("/api/events")
    Call<List<SystemEventResponse>> getEvents(
            @Query("limit") int limit,
            @Query("offset") int offset
    );

    // 2. Lấy thông số tổng hợp (Mini-analytics)
    @GET("/api/summary")
    Call<SystemSummaryResponse> getSummary();

    // 3. Lấy lịch sử cảm biến cho biểu đồ
    @GET("/api/sensors/history")
    Call<List<SensorLogResponse>> getSensorHistory(@Query("limit") int limit);

    // 4. Lấy thống kê cháy theo ngày
    @GET("/api/stats/fire-daily")
    Call<List<FireDailyStatResponse>> getFireDailyStats();
}