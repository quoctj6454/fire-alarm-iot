package com.example.firealarm.data.remote;

import com.example.firealarm.data.model.api.FireDailyStatResponse;
import com.example.firealarm.data.model.api.PaginatedEventsResponse;
import com.example.firealarm.data.model.api.SensorLogResponse;
import com.example.firealarm.data.model.api.SystemSummaryResponse;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ApiService {
    // 1. Lấy danh sách sự kiện - server-side filter + phân trang có metadata
    @GET("/api/events")
    Call<PaginatedEventsResponse> getEvents(
            @Query("limit") int limit,
            @Query("offset") int offset,
            @Query("event_type") String eventType,
            @Query("today_only") Boolean todayOnly
    );

    // 2. Lấy thông số tổng hợp (4 chỉ số: tổng + hôm nay cho báo và cháy)
    @GET("/api/summary")
    Call<SystemSummaryResponse> getSummary();

    // 3. Lấy lịch sử cảm biến cho biểu đồ
    @GET("/api/sensors/history")
    Call<List<SensorLogResponse>> getSensorHistory(@Query("limit") int limit);

    // 4. Lấy thống kê cháy theo ngày
    @GET("/api/stats/fire-daily")
    Call<List<FireDailyStatResponse>> getFireDailyStats();
}