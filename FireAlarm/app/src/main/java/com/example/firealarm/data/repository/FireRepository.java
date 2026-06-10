package com.example.firealarm.data.repository;

import androidx.lifecycle.LiveData;
import com.example.firealarm.data.model.api.SystemEventResponse;
import com.example.firealarm.data.model.api.SystemSummaryResponse;
import com.example.firealarm.data.model.firebase.SystemStatus;
import java.util.List;
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
    public void getRecentEvents(Callback<List<SystemEventResponse>> callback) {
        apiRepository.fetchEvents(20, 0, callback);
    }

    public void getSystemSummary(Callback<SystemSummaryResponse> callback) {
        apiRepository.fetchSummary(callback);
    }

    public void getEventsPaged(int offset, int limit, Callback<List<SystemEventResponse>> callback) {
        apiRepository.fetchEvents(limit, offset, callback);
    }
}