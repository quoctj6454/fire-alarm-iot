package com.example.firealarm.data.remote;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // Deploy lên server đám mây Render (Chạy 24/24)
    private static final String BASE_URL = "https://firealarm-backend-kccv.onrender.com/";

    // Dùng khi test local — thay bằng IPv4 máy tính của bạn
    // private static final String BASE_URL = "http://192.168.1.14:8000/";
    private static Retrofit retrofit = null;

    public static ApiService getApiService() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create()) // Tự động Parse JSON
                    .build();
        }
        return retrofit.create(ApiService.class);
    }
}