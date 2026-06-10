package com.example.firealarm.data.remote;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // TODO: Thay bằng địa chỉ IPv4 máy tính của bạn (VD: 192.168.1.5)
    // Phải có dấu "/" ở cuối
    private static final String BASE_URL = "http://192.168.11.82:8000/";

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