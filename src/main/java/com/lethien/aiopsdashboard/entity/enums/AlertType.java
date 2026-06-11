package com.lethien.aiopsdashboard.entity.enums;

public enum AlertType {
    DAILY_COST,      // cost_usd > threshold trong 1 ngày
    MONTHLY_COST,    // tổng cost tháng > threshold
    DAILY_REQUESTS,  // số request > threshold trong 1 ngày
    ERROR_RATE,      // tỉ lệ lỗi % > threshold
    LATENCY          // avg latency_ms > threshold
}
