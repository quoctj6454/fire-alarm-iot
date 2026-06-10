package com.example.firealarm.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.firealarm.data.firebase.FirebaseDataSource;
import com.example.firealarm.data.model.firebase.SystemStatus;


// làm nhiệm vụ trung chuyển dữ liệu từ sourse ( firebase/api ) leen viewmodel bằng cơ chế LiveData
public class FirebaseRepository {
    private final FirebaseDataSource dataSource;
    private final MutableLiveData<SystemStatus> systemStatusLiveData;

    public FirebaseRepository() {
        dataSource = new FirebaseDataSource();
        systemStatusLiveData = new MutableLiveData<>();
    }

    public LiveData<SystemStatus> getSystemStatusLiveData() {
        return systemStatusLiveData;
    }

    public void initializeAndListen() {
        dataSource.authenticate(new FirebaseDataSource.AuthCallback() {
            @Override
            public void onSuccess() {
                dataSource.listenToSystemStatus(new FirebaseDataSource.StatusCallback() {
                    @Override
                    public void onDataChange(SystemStatus status) {
                        systemStatusLiveData.postValue(status); // Bắn dữ liệu mới lên
                    }
                    @Override
                    public void onError(Exception e) { }
                });
            }
            @Override
            public void onError(Exception e) { }
        });
    }

    public void togglePump(boolean status) {
        dataSource.setManualPump(status);
    }
}