package com.lethien.aiopsdashboard.entity;

import com.lethien.aiopsdashboard.entity.enums.RequestStatus;
import com.lethien.aiopsdashboard.entity.enums.RequestType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "request_logs", indexes = {
        @Index(name = "idx_request_logs_provider_time",
                columnList = "provider_id, created_at DESC"),
        @Index(name = "idx_request_logs_user_time",
                columnList = "user_id, created_at DESC"),
        @Index(name = "idx_request_logs_provider_status_time",
                columnList = "provider_id, status, created_at DESC"),
        @Index(name = "idx_request_logs_provider_model_time",
                columnList = "provider_id, model, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    // Denormalized field — tránh JOIN qua ai_providers khi query
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    @Builder.Default
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens", nullable = false)
    @Builder.Default
    private Integer completionTokens = 0;

    // GENERATED ALWAYS AS (prompt_tokens + completion_tokens) STORED
    // insertable=false, updatable=false: JPA không ghi vào cột này
    @Column(name = "total_tokens", insertable = false, updatable = false)
    private Integer totalTokens;

    @Column(name = "cost_usd", nullable = false, precision = 12, scale = 8)
    @Builder.Default
    private BigDecimal costUsd = BigDecimal.ZERO;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RequestStatus status = RequestStatus.SUCCESS;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "request_type", length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RequestType requestType = RequestType.CHAT;

    // request_logs không có updated_at — immutable sau khi insert
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    // ── Business methods ───────────────────────────────────────────────────

    /** Request có thành công không */
    public boolean isSuccess() {
        return RequestStatus.SUCCESS.equals(this.status);
    }

    /** Request có bị lỗi không (bao gồm cả TIMEOUT và RATE_LIMITED) */
    public boolean isError() {
        return !isSuccess();
    }

    /** Tổng token thực tế (fallback nếu DB chưa tính) */
    public int computedTotalTokens() {
        if (this.totalTokens != null) return this.totalTokens;
        return (this.promptTokens != null ? this.promptTokens : 0)
                + (this.completionTokens != null ? this.completionTokens : 0);
    }

    /** Factory method — tạo log khi request thành công */
    public static RequestLog success(AiProvider provider, User user, String model,
                                     int promptTokens, int completionTokens,
                                     BigDecimal costUsd, int latencyMs,
                                     RequestType requestType) {
        return RequestLog.builder()
                .provider(provider)
                .user(user)
                .model(model)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .costUsd(costUsd)
                .latencyMs(latencyMs)
                .status(RequestStatus.SUCCESS)
                .requestType(requestType)
                .build();
    }

    /** Factory method — tạo log khi request thất bại */
    public static RequestLog failure(AiProvider provider, User user, String model,
                                     RequestStatus status, String errorMessage,
                                     int latencyMs, RequestType requestType) {
        return RequestLog.builder()
                .provider(provider)
                .user(user)
                .model(model)
                .latencyMs(latencyMs)
                .status(status)
                .errorMessage(errorMessage)
                .requestType(requestType)
                .build();
    }
}
