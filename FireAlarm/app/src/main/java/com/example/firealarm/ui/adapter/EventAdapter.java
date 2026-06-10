package com.example.firealarm.ui.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.firealarm.R;
import com.example.firealarm.data.model.api.SystemEventResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * EventAdapter — Hiển thị danh sách sự kiện trong RecyclerView.
 *
 * Hỗ trợ:
 *  - Append thêm dữ liệu khi người dùng cuộn tới cuối (Pagination)
 *  - Map màu sắc và icon theo event_type
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    // ===== DỮ LIỆU =====
    private final List<SystemEventResponse> events = new ArrayList<>();

    // ===== VIEWHOLDER =====
    public static class EventViewHolder extends RecyclerView.ViewHolder {
        final TextView txtEventIcon;
        final TextView txtEventType;
        final TextView txtEventDesc;
        final TextView txtEventTime;
        final TextView txtEventTemp;
        final TextView txtEventSmoke;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            txtEventIcon  = itemView.findViewById(R.id.txtEventIcon);
            txtEventType  = itemView.findViewById(R.id.txtEventType);
            txtEventDesc  = itemView.findViewById(R.id.txtEventDesc);
            txtEventTime  = itemView.findViewById(R.id.txtEventTime);
            txtEventTemp  = itemView.findViewById(R.id.txtEventTemp);
            txtEventSmoke = itemView.findViewById(R.id.txtEventSmoke);
        }
    }

    // ===== INFLATE LAYOUT =====
    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    // ===== BIND DỮ LIỆU =====
    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        SystemEventResponse event = events.get(position);

        // --- 1. Hiển thị thông tin cơ bản ---
        holder.txtEventDesc.setText(event.description != null ? event.description : "Không có mô tả");

        // --- 2. Format thời gian: Chuyển "2026-06-09T10:30:00" → "10:30:00 · 09/06/2026" ---
        holder.txtEventTime.setText(formatDateTime(event.created_at));

        // --- 3. Thông số cảm biến ---
        holder.txtEventTemp.setText(String.format(Locale.getDefault(), "%.0f°C", event.snapshot_temp));
        holder.txtEventSmoke.setText(event.snapshot_smoke + "ppm");

        // --- 4. Phân loại và tô màu theo event_type ---
        applyEventTypeStyle(holder, event.event_type);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    // ===================================================================
    //  CÁC HÀM PUBLIC ĐỂ MainActivity TƯƠNG TÁC
    // ===================================================================

    /**
     * Đặt lại toàn bộ danh sách (dùng khi load trang đầu tiên / reset).
     *
     * @param newEvents Danh sách sự kiện mới từ API
     */
    public void setEvents(List<SystemEventResponse> newEvents) {
        events.clear();
        if (newEvents != null) {
            events.addAll(newEvents);
        }
        notifyDataSetChanged();
    }

    /**
     * Gắn thêm dữ liệu vào cuối danh sách (dùng khi tải thêm trang - Pagination).
     *
     * @param moreEvents Danh sách sự kiện của trang tiếp theo
     */
    public void appendEvents(List<SystemEventResponse> moreEvents) {
        if (moreEvents == null || moreEvents.isEmpty()) return;
        int startPos = events.size();
        events.addAll(moreEvents);
        notifyItemRangeInserted(startPos, moreEvents.size());
    }

    // ===================================================================
    //  HÀM PRIVATE HỖ TRỢ
    // ===================================================================

    /**
     * Áp dụng icon, màu sắc badge theo loại sự kiện.
     * Thêm case mới tại đây khi backend có thêm event_type.
     */
    private void applyEventTypeStyle(@NonNull EventViewHolder holder, String eventType) {
        if (eventType == null) eventType = "UNKNOWN";

        switch (eventType.toUpperCase()) {
            case "FIRE_ALARM":
            case "FIRE_DETECTED":
                holder.txtEventIcon.setText("🔥");
                holder.txtEventType.setText(eventType.toUpperCase());
                holder.txtEventType.setTextColor(Color.parseColor("#FF5252")); // Đỏ
                holder.txtEventType.setBackgroundColor(Color.parseColor("#22FF5252"));
                break;

            case "HIGH_TEMPERATURE":
            case "TEMP_WARNING":
                holder.txtEventIcon.setText("🌡️");
                holder.txtEventType.setText(eventType.toUpperCase());
                holder.txtEventType.setTextColor(Color.parseColor("#FF9800")); // Cam
                holder.txtEventType.setBackgroundColor(Color.parseColor("#22FF9800"));
                break;

            case "HIGH_SMOKE":
            case "SMOKE_WARNING":
                holder.txtEventIcon.setText("💨");
                holder.txtEventType.setText(eventType.toUpperCase());
                holder.txtEventType.setTextColor(Color.parseColor("#FFEB3B")); // Vàng
                holder.txtEventType.setBackgroundColor(Color.parseColor("#22FFEB3B"));
                break;

            case "PUMP_ON":
                holder.txtEventIcon.setText("✅");
                holder.txtEventType.setText(eventType.toUpperCase());
                holder.txtEventType.setTextColor(Color.parseColor("#4CAF50")); // Xanh lá
                holder.txtEventType.setBackgroundColor(Color.parseColor("#224CAF50"));
                break;

            case "PUMP_OFF":
            case "SYSTEM_NORMAL":
                holder.txtEventIcon.setText("⭕");
                holder.txtEventType.setText(eventType.toUpperCase());
                holder.txtEventType.setTextColor(Color.parseColor("#7EC8E3")); // Xanh dương nhạt
                holder.txtEventType.setBackgroundColor(Color.parseColor("#227EC8E3"));
                break;

            default:
                holder.txtEventIcon.setText("📋");
                holder.txtEventType.setText(eventType.toUpperCase());
                holder.txtEventType.setTextColor(Color.parseColor("#AABBCC")); // Xám
                holder.txtEventType.setBackgroundColor(Color.parseColor("#22AABBCC"));
                break;
        }
    }

    /**
     * Format chuỗi ISO datetime từ API thành định dạng dễ đọc.
     * Input:  "2026-06-09T10:30:00" hoặc "2026-06-09 10:30:00"
     * Output: "10:30:00 · 09/06/2026"
     */
    private String formatDateTime(String rawDateTime) {
        if (rawDateTime == null || rawDateTime.isEmpty()) return "--";
        try {
            // Chuẩn hoá ký tự phân cách
            String normalized = rawDateTime.replace("T", " ");
            // Tách ngày và giờ
            String[] parts = normalized.split(" ");
            if (parts.length < 2) return rawDateTime;

            String datePart = parts[0]; // "2026-06-09"
            String timePart = parts[1].split("\\.")[0]; // "10:30:00" (bỏ milliseconds)

            // Đảo ngày: "2026-06-09" → "09/06/2026"
            String[] dateSplit = datePart.split("-");
            String formattedDate = dateSplit[2] + "/" + dateSplit[1] + "/" + dateSplit[0];

            return timePart + " · " + formattedDate;
        } catch (Exception e) {
            return rawDateTime; // Trả về raw nếu format lỗi
        }
    }
}
