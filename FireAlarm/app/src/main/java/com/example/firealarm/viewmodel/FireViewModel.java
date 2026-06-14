package com.example.firealarm.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.firealarm.data.model.api.PaginatedEventsResponse;
import com.example.firealarm.data.model.api.SystemEventResponse;
import com.example.firealarm.data.model.api.SystemSummaryResponse;
import com.example.firealarm.data.model.firebase.SystemStatus;
import com.example.firealarm.data.model.ui.DashboardState;
import com.example.firealarm.data.repository.FireRepository;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FireViewModel extends ViewModel {

    private final FireRepository repository;

    public static final String FILTER_ALL     = "ALL";
    public static final String FILTER_FIRE    = "FIRE";
    public static final String FILTER_WARNING = "WARNING";

    private static final int PAGE_SIZE = 20;

    // LiveData DUY NHẤT mà UI observe cho dữ liệu tổng hợp
    private final MutableLiveData<DashboardState> dashboardState = new MutableLiveData<>();

    // LiveData cho danh sách sự kiện đã load từ API
    private final MutableLiveData<List<SystemEventResponse>> filteredEvents = new MutableLiveData<>();

    // LiveData cho trạng thái phân trang: int[]{currentPage, totalPages, totalItems}
    private final MutableLiveData<int[]> paginationState = new MutableLiveData<>();

    // State nội bộ
    private boolean currentIsLoading = false;
    private String currentError = null;
    private SystemStatus currentRealtime = null;
    private SystemSummaryResponse currentSummary = null;

    // Pagination state
    private int currentPage = 1;
    private int totalPages  = 1;
    private int totalItems  = 0;
    private String currentFilter = FILTER_ALL;

    public FireViewModel() {
        repository = new FireRepository();
        updateState();
        paginationState.setValue(new int[]{1, 1, 0});

        // Bắt đầu lắng nghe Firebase realtime
        repository.startFirebaseListening();
        repository.getRealtimeStatus().observeForever(status -> {
            currentRealtime = status;
            currentError = null;
            updateState();
        });
    }

    public LiveData<DashboardState> getDashboardState() { return dashboardState; }
    public LiveData<List<SystemEventResponse>> getFilteredEvents() { return filteredEvents; }
    public LiveData<int[]> getPaginationState() { return paginationState; }
    public String getCurrentFilter() { return currentFilter; }

    private void updateState() {
        dashboardState.setValue(new DashboardState(
                currentIsLoading, currentError, currentRealtime, currentSummary, null));
    }

    // --- Load tổng quan + trang đầu tiên ---
    public void loadMiniAnalytics() {
        currentIsLoading = true;
        currentError = null;
        updateState();

        // Load summary cho card — độc lập với danh sách events
        repository.getSystemSummary(new Callback<SystemSummaryResponse>() {
            @Override
            public void onResponse(Call<SystemSummaryResponse> call,
                                   Response<SystemSummaryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentSummary = response.body();
                } else {
                    currentError = "Lỗi tải Summary: " + response.code();
                }
                currentIsLoading = false;
                updateState();
            }

            @Override
            public void onFailure(Call<SystemSummaryResponse> call, Throwable t) {
                currentError = "Lỗi kết nối API (Summary): " + t.getMessage();
                currentIsLoading = false;
                updateState();
            }
        });

        // Load trang đầu danh sách — độc lập, không phụ thuộc vào summary
        loadEventPage(FILTER_ALL, 1);
    }

    // --- Tải 1 trang sự kiện từ server (nguồn sự thật là PostgreSQL) ---
    public void loadEventPage(String filter, int page) {
        currentFilter = filter;
        currentPage   = page;

        int offset = (page - 1) * PAGE_SIZE;

        // Tab Cháy/Báo lọc theo event_type + today_only (server dùng CURRENT_DATE, tránh lệch timezone)
        // Tab Tất cả không lọc gì
        String eventType = null;
        Boolean todayOnly = null;
        if (FILTER_FIRE.equals(filter)) {
            eventType = "FIRE";
            todayOnly = true;
        } else if (FILTER_WARNING.equals(filter)) {
            eventType = "WARNING";
            todayOnly = true;
        }
        // FILTER_ALL: cả hai null → không gửi param → lấy tất cả

        repository.getEvents(PAGE_SIZE, offset, eventType, todayOnly,
                new Callback<PaginatedEventsResponse>() {
                    @Override
                    public void onResponse(Call<PaginatedEventsResponse> call,
                                           Response<PaginatedEventsResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            PaginatedEventsResponse data = response.body();
                            currentPage = data.page;
                            totalPages  = data.total_pages;
                            totalItems  = data.total;

                            List<SystemEventResponse> items =
                                    data.items != null ? data.items : new ArrayList<>();
                            filteredEvents.setValue(items);
                            paginationState.setValue(
                                    new int[]{currentPage, totalPages, totalItems});
                        } else {
                            currentError = "Lỗi tải danh sách: " + response.code();
                            updateState();
                        }
                    }

                    @Override
                    public void onFailure(Call<PaginatedEventsResponse> call, Throwable t) {
                        currentError = "Lỗi kết nối (Events): " + t.getMessage();
                        updateState();
                    }
                });
    }

    // --- Điều hướng trang ---
    public void loadNextPage() {
        if (currentPage < totalPages) {
            loadEventPage(currentFilter, currentPage + 1);
        }
    }

    public void loadPrevPage() {
        if (currentPage > 1) {
            loadEventPage(currentFilter, currentPage - 1);
        }
    }

    // --- Điều khiển bơm ---
    public void togglePump(boolean status) {
        repository.setPumpState(status);
    }
}
