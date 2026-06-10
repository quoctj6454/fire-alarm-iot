package com.example.firealarm.data.model.ui;
import com.example.firealarm.data.model.api.SystemEventResponse;
import com.example.firealarm.data.model.api.SystemSummaryResponse;
import com.example.firealarm.data.model.firebase.SystemStatus;
import java.util.List;


// đây là nơi gom dữ liệu từ api + firebase . activity đọc để vẽ giao diện

public class DashboardState {
    // Trạng thái load dữ liệu API (hiện vòng quay)
    public boolean isLoading;

    // Nếu lỗi mạng, thông báo sẽ nằm ở đây
    public String errorMessage;

    // Dữ liệu Real-time (Firebase)
    public SystemStatus realtimeStatus;

    // Dữ liệu Analytics (PostgreSQL)
    public SystemSummaryResponse summaryData;
    public List<SystemEventResponse> recentEvents;

    public DashboardState(boolean isLoading, String errorMessage, SystemStatus realtimeStatus,
                          SystemSummaryResponse summaryData, List<SystemEventResponse> recentEvents) {
        this.isLoading = isLoading;
        this.errorMessage = errorMessage;
        this.realtimeStatus = realtimeStatus;
        this.summaryData = summaryData;
        this.recentEvents = recentEvents;
    }
}