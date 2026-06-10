package com.example.firealarm.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.firealarm.data.model.api.SystemEventResponse;
import com.example.firealarm.data.model.api.SystemSummaryResponse;
import com.example.firealarm.data.model.firebase.SystemStatus;
import com.example.firealarm.data.model.ui.DashboardState;
import com.example.firealarm.data.repository.FireRepository;

import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FireViewModel extends ViewModel {

    private final FireRepository repository;

    // LiveData DUY NHẤT mà UI sẽ observe
    private final MutableLiveData<DashboardState> dashboardState = new MutableLiveData<>();

    // Các biến lưu trữ trạng thái hiện tại để "gom" dữ liệu
    private boolean currentIsLoading = false;
    private String currentError = null;
    private SystemStatus currentRealtime = null;
    private SystemSummaryResponse currentSummary = null;
    private List<SystemEventResponse> currentEvents = null;

    public FireViewModel() {
        repository = new FireRepository();

        // Cập nhật State rỗng ban đầu
        updateState();

        // 1. Kích hoạt Firebase ngay lập tức
        repository.startFirebaseListening();

        // Lắng nghe dữ liệu Firebase thay đổi và cập nhật vào DashboardState
        repository.getRealtimeStatus().observeForever(status -> {
            currentRealtime = status;
            currentError = null; // Reset lỗi nếu kết nối lại thành công
            updateState();
        });
    }

    public LiveData<DashboardState> getDashboardState() {
        return dashboardState;
    }

    // Hàm nội bộ để đóng gói 3 nguồn dữ liệu vào 1 object cho UI
    private void updateState() {
        dashboardState.setValue(new DashboardState(
                currentIsLoading,
                currentError,
                currentRealtime,
                currentSummary,
                currentEvents
        ));
    }

    // --- 2. Logic gọi API (Analytics) ---
    public void loadMiniAnalytics() {
        currentIsLoading = true;
        currentError = null;
        updateState();

        // Bước 1: Lấy Summary (Số cảnh báo, số vụ cháy)
        repository.getSystemSummary(new Callback<SystemSummaryResponse>() {
            @Override
            public void onResponse(Call<SystemSummaryResponse> call, Response<SystemSummaryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentSummary = response.body();
                    // Lấy Summary xong thì lấy tiếp Danh sách Events
                    fetchRecentEvents();
                } else {
                    currentError = "Lỗi tải Summary: " + response.code();
                    currentIsLoading = false;
                    updateState();
                }
            }

            @Override
            public void onFailure(Call<SystemSummaryResponse> call, Throwable t) {
                currentError = "Lỗi kết nối API: " + t.getMessage();
                currentIsLoading = false;
                updateState();
            }
        });
    }

    private void fetchRecentEvents() {
        repository.getRecentEvents(new Callback<List<SystemEventResponse>>() {
            @Override
            public void onResponse(Call<List<SystemEventResponse>> call, Response<List<SystemEventResponse>> response) {
                if (response.isSuccessful()) {
                    currentEvents = response.body();
                } else {
                    currentError = "Lỗi tải Danh sách Sự kiện: " + response.code();
                }
                currentIsLoading = false;
                updateState(); // Hoàn tất toàn bộ chu trình load
            }

            @Override
            public void onFailure(Call<List<SystemEventResponse>> call, Throwable t) {
                currentError = "Lỗi kết nối API (Events): " + t.getMessage();
                currentIsLoading = false;
                updateState();
            }
        });
    }

    // --- 3. Logic tương tác (Nút bật/tắt bơm) ---
    public void togglePump(boolean status) {
        repository.setPumpState(status);
    }

    // --- 4. Tải thêm trang sự kiện (Pagination) ---
    public void loadMoreEvents(int offset, int limit) {
        repository.getEventsPaged(offset, limit, new Callback<List<SystemEventResponse>>() {
            @Override
            public void onResponse(Call<List<SystemEventResponse>> call,
                                   Response<List<SystemEventResponse>> response) {
                if (response.isSuccessful()) {
                    currentEvents = response.body();
                } else {
                    currentError = "Lỗi tải trang: " + response.code();
                }
                currentIsLoading = false;
                updateState();
            }

            @Override
            public void onFailure(Call<List<SystemEventResponse>> call, Throwable t) {
                currentError = "Lỗi kết nối phân trang: " + t.getMessage();
                currentIsLoading = false;
                updateState();
            }
        });
    }
}