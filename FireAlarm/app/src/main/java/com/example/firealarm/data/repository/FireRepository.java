package com.example.firealarm.data.repository;

import androidx.lifecycle.LiveData;
import com.example.firealarm.data.model.api.PaginatedEventsResponse;
import com.example.firealarm.data.model.api.SystemSummaryResponse;
import com.example.firealarm.data.model.firebase.SystemStatus;
import retrofit2.Callback;

public class FireRepository {
    private final FirebaseRepository firebaseRepository;
    private final ApiRepository apiRepository;

    public FireRepository() {
        firebaseRepository = new FirebaseRepository();
        apiRepository = new ApiRepository();
    }

    // --- FIREBASE ROUTING ---
    public void startFirebaseListening() {
        firebaseRepository.initializeAndListen();
    }

    public LiveData<SystemStatus> getRealtimeStatus() {
        return firebaseRepository.getSystemStatusLiveData();
    }

    public void setPumpState(boolean state) {
        firebaseRepository.togglePump(state);
    }

    // --- API ROUTING ---
    public void getEvents(int limit, int offset, String eventType, Boolean todayOnly,
                          Callback<PaginatedEventsResponse> callback) {
        apiRepository.fetchEvents(limit, offset, eventType, todayOnly, callback);
    }

    public void getSystemSummary(Callback<SystemSummaryResponse> callback) {
        apiRepository.fetchSummary(callback);
    }
}