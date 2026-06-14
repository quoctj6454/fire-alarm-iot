package com.example.firealarm.ui.main;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.firealarm.R;
import com.example.firealarm.ui.adapter.EventAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import androidx.lifecycle.ViewModelProvider;

import com.example.firealarm.viewmodel.FireViewModel;


public class MainActivity extends AppCompatActivity {

    // ===== HẰNG SỐ =====
    private static final String TAG = "FireAlarmApp";
    private static final String NOTIF_CHANNEL_ID = "fire_alarm_channel";
    private static final int NOTIF_ID = 1001;
    private static final int NOTIF_PERMISSION_REQUEST_CODE = 101;

    // ─────────────────────────────────────────────────────────────────
    //  ĐỔI URL NÀY THÀNH ĐỊA CHỈ SERVER CAMERA CỦA BẠN
    //  Ví dụ MJPEG (ESP32-CAM): "http://192.168.1.100:81/stream"
    //  Ví dụ trang web HLS   : "http://192.168.1.100:8080/"
    // ─────────────────────────────────────────────────────────────────
    private static final String CAMERA_STREAM_URL = "http://192.168.11.41:5000/api/stream";

    // Ngưỡng cảnh báo
    private static final long TEMP_THRESHOLD  = 50;  // °C
    private static final long SMOKE_THRESHOLD = 400; // ppm

//    // ===== FIREBASE (KHÔNG THAY ĐỔI KHỞI TẠO) =====
//    private DatabaseReference mDatabase;
//    private FirebaseAuth mAuth;

    // ===== VIEWMODEL (Thay thế cho Firebase Auth & Database) =====
    private FireViewModel viewModel;


    // ===== BIẾN TRẠNG THÁI =====
    private boolean isFireActive = false;       // Trạng thái lửa hiện tại
    private boolean manualPumpOn = false;       // Trạng thái bơm thủ công hiện tại
    private android.view.animation.Animation blinkAnim; // Animation nhấp nháy

    // ===== VIEW REFERENCES =====
    private TextView txtTempValue;
    private TextView txtSmokeValue;
    private TextView txtEnvWarning;
    private TextView imgWarningIcon;
    private FrameLayout layoutSafetyBg;
    private TextView txtSafetyIcon;
    private TextView txtSafetyStatus;
    private TextView txtPumpIcon;
    private TextView txtPumpStatus;
    private TextView txtPumpBadge;
    private MaterialButton btnTogglePump;

    // ===== VIEW REFERENCES - ANALYTICS (Thêm mới) =====
    private View layoutRealtime;
    private View layoutAnalytics;
    private BottomNavigationView bottomNavigation;
    private TextView txtTotalWarnings;
    private TextView txtWarningsToday;
    private TextView txtTotalFires;
    private TextView txtFiresToday;
    private TextView txtLastEventTime;
    private RecyclerView recyclerEvents;
    private ProgressBar progressEvents;

    // ===== PHÂN TRANG =====
    private EventAdapter eventAdapter;
    private MaterialButton btnPagePrev;
    private MaterialButton btnPageNext;
    private TextView txtPageInfo;

    // ===== NÚT FILTER ANALYTICS =====
    private MaterialButton btnTabAllEvents;
    private MaterialButton btnTabFireOnly;
    private MaterialButton btnTabWarningOnly;

    // ===== CAMERA STREAM =====
    private ImageView imgCameraStream;
    private View layoutStreamPlaceholder;
    private TextView txtStreamStatus;
    private Thread mjpegThread;
    private volatile boolean streamRunning = false;
    // Frame dropping: decoder ghi vào đây; UI chỉ đọc frame MỚI NHẤT
    private final AtomicReference<Bitmap> latestFrame = new AtomicReference<>(null);
    // true = đã có 1 runnable đang chờ trên UI thread; tránh xếp hàng vô hạn
    private final AtomicBoolean framePending = new AtomicBoolean(false);

    // ===================================================================
    //  VÒNG ĐỜI ACTIVITY - KHÔNG THAY ĐỔI PHẦN KHỞI TẠO FIREBASE/AUTH
    // ===================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


////
////        // =========== TEST API ==========
//        com.example.firealarm.data.remote.ApiService apiService =
//                com.example.firealarm.data.remote.ApiClient.getApiService();
//
//        apiService.getSummary().enqueue(new retrofit2.Callback<com.example.firealarm.data.model.api.SystemSummaryResponse>() {
//            @Override
//            public void onResponse(retrofit2.Call<com.example.firealarm.data.model.api.SystemSummaryResponse> call,
//                                   retrofit2.Response<com.example.firealarm.data.model.api.SystemSummaryResponse> response) {
//                if (response.isSuccessful() && response.body() != null) {
//                    // NẾU VÀO ĐÂY: API GỌI THÀNH CÔNG VÀ JSON ĐÃ PARSE ĐÚNG!
//                    int totalWarnings = response.body().total_warnings;
//                    int fires = response.body().fires_today;
//                    Log.d("TEST_API_SE", "✅ THÀNH CÔNG! Tổng cảnh báo: " + totalWarnings + ", Số vụ cháy: " + fires);
//                } else {
//                    Log.e("TEST_API_SE", "❌ LỖI API: Mã lỗi " + response.code());
//                }
//            }
//
//            @Override
//            public void onFailure(retrofit2.Call<com.example.firealarm.data.model.api.SystemSummaryResponse> call, Throwable t) {
//                // NẾU VÀO ĐÂY: Lỗi không gọi được máy chủ (Sai IP, chưa mở port, thiếu quyền Internet)
//                Log.e("TEST_API_SE", "❌ THẤT BẠI KẾT NỐI: " + t.getMessage());
//            }
//        });

        // 1. Ánh xạ View
        bindViews();

        // 1.5. Khởi động camera stream ngay sau khi bind views
        setupCameraStream(CAMERA_STREAM_URL);

        // 2. Tạo kênh thông báo (Android 8+)
        createNotificationChannel();

        // 2.5 Yêu cầu quyền hiển thị thông báo (Android 13+)
        requestNotificationPermission();

        // 3. Tải animation nhấp nháy
        blinkAnim = AnimationUtils.loadAnimation(this, R.anim.blink_animation);


        // 2. KHỞI TẠO VIEWMODEL (Mới)
        viewModel = new ViewModelProvider(this).get(FireViewModel.class);

        // 3. LẮNG NGHE DỮ LIỆU TỪ VIEWMODEL (Mới)
        observeViewModel();

        // 4. Kích hoạt lấy dữ liệu Analytics (Mới)
        viewModel.loadMiniAnalytics();

        // 6. Setup Bottom Navigation và RecyclerView (Thêm mới)
        setupRecyclerView();
        setupBottomNavigation();

        // 7. Setup 3 nút filter + nút phân trang
        setupFilterButtons();
        setupPaginationButtons();

        // 5. Xử lý nút bấm qua ViewModel (Thay đổi)
        btnTogglePump.setOnClickListener(v -> {
            boolean newStatus = !manualPumpOn;
            // Gọi ViewModel thay vì ghi trực tiếp lên Firebase
            viewModel.togglePump(newStatus);
        });

//
//        // 4. Khởi tạo Firebase Auth (GIỮ NGUYÊN)
//        mAuth = FirebaseAuth.getInstance();
//
//        // 5. Đăng nhập ẩn danh trước khi kết nối (GIỮ NGUYÊN)
//        mAuth.signInAnonymously()
//                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
//                    @Override
//                    public void onComplete(@NonNull Task<AuthResult> task) {
//                        if (task.isSuccessful()) {
//                            Log.d("FirebaseAuth", "Đăng nhập ẩn danh thành công");
//                            startListeningToDatabase();
//                        } else {
//                            Log.e("FirebaseAuth", "Đăng nhập thất bại", task.getException());
//                        }
//                    }
//                });
//
//        // 6. Xử lý nút bơm thủ công
//        btnTogglePump.setOnClickListener(v -> {
//            boolean newStatus = !manualPumpOn;
//            controlPump(newStatus);
//        });



    }

    // ===== Ánh xạ tất cả View =====
    private void bindViews() {
        txtTempValue    = findViewById(R.id.txtTempValue);
        txtSmokeValue   = findViewById(R.id.txtSmokeValue);
        txtEnvWarning   = findViewById(R.id.txtEnvWarning);
        imgWarningIcon  = findViewById(R.id.imgWarningIcon);
        layoutSafetyBg  = findViewById(R.id.layoutSafetyBg);
        txtSafetyIcon   = findViewById(R.id.txtSafetyIcon);
        txtSafetyStatus = findViewById(R.id.txtSafetyStatus);
        txtPumpIcon     = findViewById(R.id.txtPumpIcon);
        txtPumpStatus   = findViewById(R.id.txtPumpStatus);
        txtPumpBadge    = findViewById(R.id.txtPumpBadge);
        btnTogglePump   = findViewById(R.id.btnTogglePump);

        // --- Analytics Views (Thêm mới) ---
        layoutRealtime   = findViewById(R.id.layout_realtime);
        layoutAnalytics  = findViewById(R.id.layout_analytics);
        bottomNavigation = findViewById(R.id.bottom_navigation);
        txtTotalWarnings = findViewById(R.id.txtTotalWarnings);
        txtWarningsToday = findViewById(R.id.txtWarningsToday);
        txtTotalFires    = findViewById(R.id.txtTotalFires);
        txtFiresToday    = findViewById(R.id.txtFiresToday);
        txtLastEventTime = findViewById(R.id.txtLastEventTime);
        recyclerEvents   = findViewById(R.id.recyclerEvents);
        progressEvents   = findViewById(R.id.progressEvents);

        // --- Nút phân trang ---
        btnPagePrev = findViewById(R.id.btnPagePrev);
        btnPageNext = findViewById(R.id.btnPageNext);
        txtPageInfo = findViewById(R.id.txtPageInfo);

        // --- Nút filter ---
        btnTabAllEvents    = findViewById(R.id.btnTabAllEvents);
        btnTabFireOnly     = findViewById(R.id.btnTabFireOnly);
        btnTabWarningOnly  = findViewById(R.id.btnTabWarningOnly);

        // --- Camera stream ---
        imgCameraStream         = findViewById(R.id.img_camera_stream);
        layoutStreamPlaceholder = findViewById(R.id.layout_stream_placeholder);
        txtStreamStatus         = findViewById(R.id.txt_stream_status);
    }

    // ===================================================================
    //  CAMERA STREAM — MJPEG DECODER (OkHttp + ImageView)
    // ===================================================================

    /**
     * Khởi động MJPEG decoder trong background thread.
     * Tự động retry mỗi 5 giây nếu mất kết nối.
     * URL được lấy từ hằng số CAMERA_STREAM_URL.
     */
    private void setupCameraStream(String streamUrl) {
        streamRunning = true;

        mjpegThread = new Thread(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.SECONDS)   // Không timeout khi đang stream
                    .build();

            while (streamRunning) {
                Request request = new Request.Builder().url(streamUrl).build();
                try (Response response = client.newCall(request).execute()) {

                    if (!response.isSuccessful() || response.body() == null) {
                        runOnUiThread(this::showStreamOffline);
                        sleepBeforeRetry();
                        continue;
                    }

                    InputStream inputStream = response.body().byteStream();
                    ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream(32768);
                    // Chunk lớn hơn = ít syscall hơn = throughput tốt hơn
                    byte[] chunk = new byte[16384];
                    boolean inFrame = false;
                    int prevByte = -1;
                    boolean firstFrameReceived = false;

                    while (streamRunning) {
                        int n = inputStream.read(chunk);
                        if (n == -1) break;

                        for (int i = 0; i < n; i++) {
                            int b = chunk[i] & 0xFF;

                            if (!inFrame) {
                                // Tìm JPEG SOI marker: 0xFF 0xD8
                                if (prevByte == 0xFF && b == 0xD8) {
                                    inFrame = true;
                                    frameBuffer.reset();
                                    frameBuffer.write(0xFF);
                                    frameBuffer.write(0xD8);
                                }
                            } else {
                                frameBuffer.write(b);
                                // Tìm JPEG EOI marker: 0xFF 0xD9
                                if (prevByte == 0xFF && b == 0xD9) {
                                    byte[] frameBytes = frameBuffer.toByteArray();
                                    Bitmap bitmap = BitmapFactory.decodeByteArray(
                                            frameBytes, 0, frameBytes.length);

                                    if (bitmap != null) {
                                        final boolean isFirst = !firstFrameReceived;
                                        if (!firstFrameReceived) firstFrameReceived = true;

                                        // Ghi frame mới nhất vào AtomicReference
                                        latestFrame.set(bitmap);

                                        // Chỉ post lên UI nếu KHÔNG có frame nào đang chờ
                                        // → tránh xếp hàng, luôn hiển thị frame mới nhất
                                        if (framePending.compareAndSet(false, true)) {
                                            runOnUiThread(() -> {
                                                if (imgCameraStream == null) return;
                                                Bitmap toRender = latestFrame.getAndSet(null);
                                                if (toRender != null) {
                                                    imgCameraStream.setImageBitmap(toRender);
                                                }
                                                if (isFirst) {
                                                    imgCameraStream.setVisibility(View.VISIBLE);
                                                    layoutStreamPlaceholder.setVisibility(View.GONE);
                                                    txtStreamStatus.setText("● LIVE");
                                                    txtStreamStatus.setTextColor(
                                                            Color.parseColor("#4CAF50"));
                                                }
                                                // Mở khoá: cho phép post frame tiếp theo
                                                framePending.set(false);
                                            });
                                        }
                                        // Nếu framePending=true: frame này bị drop,
                                        // latestFrame đã được ghi đè = frame cũ bị loại bỏ
                                    }
                                    inFrame = false;
                                    frameBuffer.reset();
                                }
                            }
                            prevByte = b;
                        }
                    }

                } catch (IOException e) {
                    Log.w(TAG, "Camera stream ngắt kết nối: " + e.getMessage());
                }

                if (streamRunning) {
                    runOnUiThread(this::showStreamOffline);
                    sleepBeforeRetry();
                }
            }
        });

        mjpegThread.setName("mjpeg-stream-thread");
        mjpegThread.setDaemon(true);
        mjpegThread.start();
    }

    /** Ngủ 5 giây trước khi thử kết nối lại */
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Hiển thị placeholder + badge OFFLINE khi mất kết nối stream */
    private void showStreamOffline() {
        if (imgCameraStream == null) return;
        imgCameraStream.setVisibility(View.GONE);
        layoutStreamPlaceholder.setVisibility(View.VISIBLE);
        txtStreamStatus.setText("● OFFLINE");
        txtStreamStatus.setTextColor(Color.parseColor("#EF5350"));
    }

    // ===================================================================
    //  VÒNG ĐỜI ACTIVITY — QUẢN LÝ MJPEG THREAD
    // ===================================================================

    @Override
    protected void onResume() {
        super.onResume();
        // Khởi động lại stream nếu đã bị dừng khi pause
        if (!streamRunning && imgCameraStream != null) {
            setupCameraStream(CAMERA_STREAM_URL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Dừng stream để tiết kiệm tài nguyên khi app chạy nền
        streamRunning = false;
        if (mjpegThread != null) {
            mjpegThread.interrupt();
            mjpegThread = null;
        }
    }

    @Override
    protected void onDestroy() {
        streamRunning = false;
        if (mjpegThread != null) {
            mjpegThread.interrupt();
            mjpegThread = null;
        }
        latestFrame.set(null);
        framePending.set(false);
        imgCameraStream = null;
        super.onDestroy();
    }

    // ===================================================================
    //  QUAN SÁT DỮ LIỆU TỔNG HỢP (THAY THẾ CHO addValueEventListener)
    // ===================================================================
    private void observeViewModel() {
        viewModel.getDashboardState().observe(this, state -> {
            if (state.isLoading) {
                progressEvents.setVisibility(View.VISIBLE);
            } else {
                progressEvents.setVisibility(View.GONE);
            }

            if (state.errorMessage != null) {
                Log.e(TAG, "ViewModel error: " + state.errorMessage);
                android.widget.Toast.makeText(this, state.errorMessage, android.widget.Toast.LENGTH_SHORT).show();
            }

            // Dữ liệu Real-time Firebase
            if (state.realtimeStatus != null) {
                this.manualPumpOn = state.realtimeStatus.manual_pump;
                updateUI(
                        state.realtimeStatus.temp,
                        state.realtimeStatus.smoke,
                        state.realtimeStatus.fire_detected,
                        state.realtimeStatus.manual_pump
                );
            }

            // 4 chỉ số card từ /api/summary — nguồn sự thật là PostgreSQL
            if (state.summaryData != null) {
                txtTotalWarnings.setText(String.valueOf(state.summaryData.total_warnings));
                txtWarningsToday.setText(String.valueOf(state.summaryData.warnings_today));
                txtTotalFires.setText(String.valueOf(state.summaryData.total_fires));
                txtFiresToday.setText(String.valueOf(state.summaryData.fires_today));
                String lastTime = state.summaryData.last_event_time != null
                        ? state.summaryData.last_event_time : "--";
                txtLastEventTime.setText(lastTime);
            }
        });

        // Danh sách sự kiện — thay thế toàn bộ list mỗi khi đổi trang/tab
        viewModel.getFilteredEvents().observe(this, events -> {
            if (events != null) {
                eventAdapter.updateData(events);
            }
        });

        // Trạng thái phân trang — cập nhật nút + label
        viewModel.getPaginationState().observe(this, state -> {
            int page       = state[0];
            int totalPages = state[1];
            int totalItems = state[2];

            txtPageInfo.setText("Trang " + page + "/" + totalPages
                    + "  (" + totalItems + " sự kiện)");

            btnPagePrev.setEnabled(page > 1);
            btnPageNext.setEnabled(page < totalPages);

            btnPagePrev.setAlpha(page > 1 ? 1f : 0.35f);
            btnPageNext.setAlpha(page < totalPages ? 1f : 0.35f);
        });
    }

    // ===================================================================
    //  LẮNG NGHE DỮ LIỆU FIREBASE - CHỈ NHẬN VÀ CHUYỂN TIẾP
    // ===================================================================
//    private void startListeningToDatabase() {
//        // Kết nối đến node "system_status" (GIỮ NGUYÊN)
//        mDatabase = FirebaseDatabase.getInstance().getReference("system_status");
//
//        mDatabase.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (!snapshot.exists()) {
//                    Log.w(TAG, "Node 'system_status' chưa có dữ liệu.");
//                    return;
//                }
//
//                try {
//                    // --- Đọc dữ liệu an toàn, xử lý ép kiểu đúng chuẩn ---
//                    Long tempRaw   = snapshot.child("temp").getValue(Long.class);
//                    Long smokeRaw  = snapshot.child("smoke").getValue(Long.class);
//                    Boolean fireRaw = snapshot.child("fire_detected").getValue(Boolean.class);
//                    Boolean pumpRaw = snapshot.child("manual_pump").getValue(Boolean.class);
//
//                    // Gán giá trị mặc định nếu null
//                    long temp  = (tempRaw  != null) ? tempRaw  : 0L;
//                    long smoke = (smokeRaw != null) ? smokeRaw : 0L;
//                    boolean fireDetected = (fireRaw != null) && fireRaw;
//                    boolean pumpStatus   = (pumpRaw != null) && pumpRaw;
//
//                    // Cập nhật trạng thái bơm nội bộ theo Firebase
//                    manualPumpOn = pumpStatus;
//
//                    // Chuyển tiếp tới hàm điều phối chính
//                    updateUI(temp, smoke, fireDetected, pumpStatus);
//
//                } catch (Exception e) {
//                    Log.e(TAG, "Lỗi định dạng dữ liệu: " + e.getMessage());
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e(TAG, "Không thể đọc dữ liệu: " + error.getMessage());
//            }
//        });
//    }

    // ===================================================================
    //  HÀM ĐIỀU PHỐI CHÍNH
    // ===================================================================
    /**
     * Hàm điều phối: Gọi các hàm chuyên biệt với đúng tham số.
     * Không chứa logic xử lý trực tiếp.
     */
    void updateUI(long temp, long smoke, boolean fireDetected, boolean pumpStatus) {
        // Cập nhật số liệu cảm biến lên màn hình
        txtTempValue.setText(String.valueOf(temp));
        txtSmokeValue.setText(String.valueOf(smoke));

        // Gọi xử lý cảnh báo môi trường
        handleEnvironmentWarnings(temp, smoke);

        // Gọi xử lý trạng thái lửa (chỉ dùng cảm biến vật lý)
        handleFireLogic(fireDetected);

        // Cập nhật giao diện trạng thái bơm
        updatePumpStatusUI(pumpStatus);

        // Cập nhật text nút điều khiển
        if (pumpStatus) {
            btnTogglePump.setText("⭕  TẮT BƠM THỦ CÔNG");
        } else {
            btnTogglePump.setText("💧  BẬT BƠM THỦ CÔNG");
        }
    }

    // ===================================================================
    //  XỬ LÝ CẢNH BÁO MÔI TRƯỜNG
    // ===================================================================
    /**
     * Phân tích nhiệt độ và khói để hiển thị cảnh báo môi trường phù hợp.
     */
    void handleEnvironmentWarnings(long temp, long smoke) {
        boolean highTemp  = temp  > TEMP_THRESHOLD;
        boolean highSmoke = smoke > SMOKE_THRESHOLD;

        if (highTemp && highSmoke) {
            // Cả hai cùng cao → Nguy cơ hỏa hoạn cao
            imgWarningIcon.setText("🔴");
            txtEnvWarning.setText("Cảnh báo: Nguy cơ hỏa hoạn cao!");
            txtEnvWarning.setTextColor(0xFFFF5252);
        } else if (highTemp) {
            imgWarningIcon.setText("🌡️");
            txtEnvWarning.setText("Nhiệt độ tăng cao! (" + temp + "°C)");
            txtEnvWarning.setTextColor(0xFFFF9800);
        } else if (highSmoke) {
            imgWarningIcon.setText("💨");
            txtEnvWarning.setText("Mức khói bất thường! (" + smoke + " ppm)");
            txtEnvWarning.setTextColor(0xFFFFEB3B);
        } else {
            // Tất cả ổn định
            imgWarningIcon.setText("✅");
            txtEnvWarning.setText("Chỉ số ổn định");
            txtEnvWarning.setTextColor(0xFF81C784);
        }
    }

    // ===================================================================
    //  XỬ LÝ LOGIC LỬA (CHỈ DÙNG CẢM BIẾN VẬT LÝ)
    // ===================================================================
    /**
     * Xác định trạng thái hỏa hoạn dựa trên cảm biến lửa vật lý.
     * Placeholder Camera AI được định nghĩa riêng bên dưới.
     *
     * @param flameSensorValue giá trị từ cảm biến lửa vật lý (fire_detected)
     */
    void handleFireLogic(boolean flameSensorValue) {
        // Ghi chú: checkAICameraStatus() là placeholder — luôn false hiện tại.
        // Trong tương lai: boolean aiResult = checkAICameraStatus();
        // Và có thể kết hợp: if (flameSensorValue || aiResult) {...}

        if (flameSensorValue) {
            // === CÓ LỬA ===
            if (!isFireActive) {
                isFireActive = true;
                // Gửi thông báo đẩy lên thanh trạng thái
                sendPushNotification("🔥 PHÁT HIỆN HỎA HOẠN! Hệ thống bơm đã tự kích hoạt.");
                // Tự động bật bơm
//                controlPump(true);
                viewModel.togglePump(true); // Đã sửa để gọi qua ViewModel
            }
            showFireAlert();
        } else {
            // === AN TOÀN ===
            if (isFireActive) {
                isFireActive = false;
                // Tự động tắt bơm khi hết lửa
//                controlPump(false);
                viewModel.togglePump(false); // Đã sửa để gọi qua ViewModel
            }
            showSafeStatus();
        }
    }

    // ===================================================================
    //  PLACEHOLDER: CAMERA AI (TÍCH HỢP SAU)
    // ===================================================================
    /**
     * Placeholder cho kết quả nhận diện từ Camera AI.
     * Hiện tại luôn trả về false.
     * TODO: Thay thế bằng logic xử lý kết quả nhận diện thực tế.
     *
     * @return true nếu Camera AI phát hiện lửa, false nếu không.
     */
    boolean checkAICameraStatus() {
        return false;
    }

    // ===================================================================
    //  ĐIỀU KHIỂN BƠM (GỬI LÊN FIREBASE)
    // ===================================================================
    /**
     * Hàm DUY NHẤT chịu trách nhiệm ghi trạng thái bơm lên Firebase.
     *
     * @param status true = bật bơm, false = tắt bơm
     */
//    void controlPump(boolean status) {
//        if (mDatabase == null) {
//            Log.w(TAG, "controlPump: Database chưa được khởi tạo.");
//            return;
//        }
//        mDatabase.child("manual_pump").setValue(status)
//                .addOnCompleteListener(task -> {
//                    if (task.isSuccessful()) {
//                        Log.d(TAG, "controlPump: Đã ghi manual_pump = " + status);
//                    } else {
//                        Log.e(TAG, "controlPump: Ghi Firebase thất bại", task.getException());
//                    }
//                });
//    }

    // ===================================================================
    //  GỬI THÔNG BÁO ĐẨY (PUSH NOTIFICATION)
    // ===================================================================
    /**
     * Tạo và đẩy thông báo lên thanh trạng thái Android khi phát hiện hỏa hoạn.
     *
     * @param msg Nội dung thông báo
     */
    void sendPushNotification(String msg) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🔥 CẢNH BÁO HỎA HOẠN!")
                .setContentText(msg)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true);

        NotificationManagerCompat notifManager = NotificationManagerCompat.from(this);
        try {
            notifManager.notify(NOTIF_ID, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Chưa được cấp quyền POST_NOTIFICATIONS: " + e.getMessage());
        }
    }

    // ===================================================================
    //  HÀM HỖ TRỢ CẬP NHẬT GIAO DIỆN (KHÔNG PHẢI LOGIC CHÍNH)
    // ===================================================================

    /** Hiển thị trạng thái CÓ LỬA với animation nhấp nháy */
    private void showFireAlert() {
        layoutSafetyBg.setBackgroundResource(R.drawable.bg_fire_card);
        layoutSafetyBg.startAnimation(blinkAnim);
        txtSafetyIcon.setText("🔥");
        txtSafetyStatus.setText("CÓ CHÁY");
        txtSafetyStatus.setTextColor(0xFFFFFFFF);
    }

    /** Hiển thị trạng thái AN TOÀN và dừng nhấp nháy */
    private void showSafeStatus() {
        layoutSafetyBg.clearAnimation();
        layoutSafetyBg.setBackgroundResource(R.drawable.bg_safe_card);
        txtSafetyIcon.setText("✅");
        txtSafetyStatus.setText("AN TOÀN");
        txtSafetyStatus.setTextColor(0xFFFFFFFF);
    }

    /** Cập nhật giao diện card trạng thái bơm */
    private void updatePumpStatusUI(boolean isOn) {
        if (isOn) {
            txtPumpIcon.setText("✅");
            txtPumpStatus.setText("ĐANG BẬT");
            txtPumpStatus.setTextColor(0xFF4CAF50);
            txtPumpBadge.setText(" BẬT ");
            txtPumpBadge.setBackgroundColor(0x3381C784);
            txtPumpBadge.setTextColor(0xFF81C784);
        } else {
            txtPumpIcon.setText("⭕");
            txtPumpStatus.setText("ĐANG TẮT");
            txtPumpStatus.setTextColor(0xFFEF9A9A);
            txtPumpBadge.setText(" TẮT ");
            txtPumpBadge.setBackgroundColor(0x33EF9A9A);
            txtPumpBadge.setTextColor(0xFFEF9A9A);
        }
    }

    /** Yêu cầu quyền hiển thị thông báo (bắt buộc cho Android 13.0+) */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_REQUEST_CODE);
            }
        }
    }

    /** Tạo kênh thông báo (bắt buộc cho Android 8.0+) */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Kênh Cảnh Báo Hỏa Hoạn";
            String description = "Nhận thông báo khi hệ thống phát hiện lửa";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(NOTIF_CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    // ===================================================================
    //  SETUP RECYCLERVIEW VÀ SCROLL LISTENER (PAGINATION)
    // ===================================================================
    private void setupRecyclerView() {
        eventAdapter = new EventAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerEvents.setLayoutManager(layoutManager);
        recyclerEvents.setAdapter(eventAdapter);
        // Scroll listener đã bỏ — phân trang dùng nút ◀ ▶ thay cho infinite scroll
    }

    // ===================================================================
    //  SETUP BOTTOM NAVIGATION
    // ===================================================================
    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_realtime) {
                layoutRealtime.setVisibility(View.VISIBLE);
                layoutAnalytics.setVisibility(View.GONE);
                return true;
            } else if (id == R.id.nav_analytics) {
                layoutRealtime.setVisibility(View.GONE);
                layoutAnalytics.setVisibility(View.VISIBLE);
                return true;
            }
            return false;
        });
    }

    // ===================================================================
    //  SETUP 3 NÚT FILTER (Thêm mới)
    // ===================================================================
    private void setupFilterButtons() {
        btnTabAllEvents.setOnClickListener(v -> {
            viewModel.loadEventPage(FireViewModel.FILTER_ALL, 1);
            setActiveFilterButton(btnTabAllEvents);
        });

        btnTabFireOnly.setOnClickListener(v -> {
            viewModel.loadEventPage(FireViewModel.FILTER_FIRE, 1);
            setActiveFilterButton(btnTabFireOnly);
        });

        btnTabWarningOnly.setOnClickListener(v -> {
            viewModel.loadEventPage(FireViewModel.FILTER_WARNING, 1);
            setActiveFilterButton(btnTabWarningOnly);
        });

        setActiveFilterButton(btnTabAllEvents);
    }

    private void setupPaginationButtons() {
        btnPagePrev.setOnClickListener(v -> viewModel.loadPrevPage());
        btnPageNext.setOnClickListener(v -> viewModel.loadNextPage());
    }

    /**
     * Cập nhật style nút active/inactive để người dùng biết đang ở filter nào.
     * Nút active: màu cam (#FF6B35), chữ trắng.
     * Nút inactive: nền tối (#1A1A2E), chữ xám, có viền.
     */
    private void setActiveFilterButton(MaterialButton activeBtn) {
        MaterialButton[] allBtns = {btnTabAllEvents, btnTabFireOnly, btnTabWarningOnly};
        for (MaterialButton btn : allBtns) {
            if (btn == activeBtn) {
                // Đang active
                btn.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF6B35")));
                btn.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
                btn.setStrokeWidth(0);
            } else {
                // Không active
                btn.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#1A1A2E")));
                btn.setTextColor(android.graphics.Color.parseColor("#AABBCC"));
                btn.setStrokeWidth(1);
                btn.setStrokeColor(
                        android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#33FFFFFF")));
            }
        }
    }
}