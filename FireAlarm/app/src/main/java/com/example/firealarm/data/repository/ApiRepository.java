package com.example.firealarm.data.repository;

import com.example.firealarm.data.model.api.SystemEventResponse;
import com.example.firealarm.data.model.api.SystemSummaryResponse;
import com.example.firealarm.data.remote.ApiClient;
import com.example.firealarm.data.remote.ApiService;
import java.util.List;
import retrofit2.Callback;

public class ApiRepository {
    private final ApiService apiService;

    public ApiRepository() {
        apiService = ApiClient.getApiService();
    }

    public void fetchEvents(int limit, int offset, Callback<List<SystemEventResponse>> callback) {
        apiService.getEvents(limit, offset).enqueue(callback);
    }

    public void fetchSummary(Callback<SystemSummaryResponse> callback) {
        apiService.getSummary().enqueue(callback);
    }
}