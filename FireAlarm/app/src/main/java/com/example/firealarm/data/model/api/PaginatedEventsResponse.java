package com.example.firealarm.data.model.api;

import java.util.List;

public class PaginatedEventsResponse {
    public List<SystemEventResponse> items;
    public int total;
    public int page;
    public int total_pages;
    public int limit;
    public int offset;
}
