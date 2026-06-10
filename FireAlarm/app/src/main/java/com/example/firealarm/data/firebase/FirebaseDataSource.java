package com.example.firealarm.data.firebase;

import android.util.Log;
import androidx.annotation.NonNull;
import com.example.firealarm.data.model.firebase.SystemStatus;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class FirebaseDataSource {
    private static final String TAG = "FirebaseDataSource";
    private final FirebaseAuth mAuth;
    private final DatabaseReference mDatabase;

    public FirebaseDataSource() {
        mAuth = FirebaseAuth.getInstance();
        // Giữ nguyên node "system_status" của Quốc
        mDatabase = FirebaseDatabase.getInstance().getReference("system_status");
    }

    // --- 1. Logic đăng nhập ẩn danh (Bê từ MainActivity) ---
    public void authenticate(AuthCallback callback) {
        mAuth.signInAnonymously().addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Đăng nhập Firebase ẩn danh thành công");
                    callback.onSuccess();
                } else {
                    Log.e(TAG, "Đăng nhập thất bại", task.getException());
                    callback.onError(task.getException());
                }
            }
        });
    }

    // --- 2. Logic lắng nghe Real-time (Bê từ MainActivity) ---
    public void listenToSystemStatus(StatusCallback callback) {
        mDatabase.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.w(TAG, "Node 'system_status' chưa có dữ liệu.");
                    return;
                }
                try {
                    // Giữ nguyên 100% logic ép kiểu và giá trị mặc định của Quốc
                    Long tempRaw   = snapshot.child("temp").getValue(Long.class);
                    Long smokeRaw  = snapshot.child("smoke").getValue(Long.class);
                    Boolean fireRaw = snapshot.child("fire_detected").getValue(Boolean.class);
                    Boolean pumpRaw = snapshot.child("manual_pump").getValue(Boolean.class);

                    long temp  = (tempRaw  != null) ? tempRaw  : 0L;
                    long smoke = (smokeRaw != null) ? smokeRaw : 0L;
                    boolean fireDetected = (fireRaw != null) && fireRaw;
                    boolean pumpStatus   = (pumpRaw != null) && pumpRaw;

                    // Đóng gói vào Model
                    SystemStatus status = new SystemStatus(temp, smoke, fireDetected, pumpStatus);
                    callback.onDataChange(status);

                } catch (Exception e) {
                    Log.e(TAG, "Lỗi định dạng dữ liệu: " + e.getMessage());
                    callback.onError(e);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Không thể đọc dữ liệu: " + error.getMessage());
                callback.onError(error.toException());
            }
        });
    }

    // --- 3. Logic điều khiển bơm (Bê từ MainActivity) ---
    public void setManualPump(boolean status) {
        mDatabase.child("manual_pump").setValue(status).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "controlPump: Đã ghi manual_pump = " + status);
            } else {
                Log.e(TAG, "controlPump: Ghi Firebase thất bại", task.getException());
            }
        });
    }

    // --- Interfaces nội bộ ---
    public interface AuthCallback {
        void onSuccess();
        void onError(Exception e);
    }

    public interface StatusCallback {
        void onDataChange(SystemStatus status);
        void onError(Exception e);
    }
}