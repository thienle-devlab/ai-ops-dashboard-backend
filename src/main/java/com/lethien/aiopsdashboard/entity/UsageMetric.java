package com.lethien.aiopsdashboard.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "usage_metrics",
uniqueConstraints = @UniqueConstraint(
        name = "uq_metrics_provider_model_date",
        columnNames = {"provider_id", "model", "metric_date"}
),
indexes = {
        @Index(name = "idx_usage_metrics_provider_date",
                columnList = "provider_id, metric_date DESC"),
        @Index(name = "idx_usage_metrics_user_date",
                columnList = "user_id, metric_date DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private AiProvider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "total_requests", nullable = false)
    @Builder.Default
    private Integer totalRequests = 0;

    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private Integer successCount = 0;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private Integer errorCount = 0;

    @Column(name = "total_tokens", nullable = false)
    @Builder.Default
    private Long totalTokens = 0L;

    @Column(name = "prompt_tokens", nullable = false)
    @Builder.Default
    private Long promptTokens = 0L;

    @Column(name = "completion_tokens", nullable = false)
    @Builder.Default
    private Long completionTokens = 0L;

    @Column(name = "total_cost_usd", nullable = false, precision = 14, scale = 6)
    @Builder.Default
    private BigDecimal totalCostUsd = BigDecimal.ZERO;

    @Column(name = "avg_latency_ms")
    private Integer avgLatencyMs;

    @Column(name = "p95_latency_ms")
    private Integer p95LatencyMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false,
            insertable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    // ── Business methods ───────────────────────────────────────────────────

    /** Tính tỉ lệ lỗi (%) */
    public double errorRate() {
        if (totalRequests == null || totalRequests == 0) return 0.0;
        return (double) errorCount / totalRequests * 100;
    }

    /** Tính tỉ lệ thành công (%) */
    public double successRate() {
        return 100.0 - errorRate();
    }

    /** Cộng dồn data từ 1 RequestLog vào metric này (dùng trong scheduler) */
    public void accumulate(RequestLog log) {
        this.totalRequests++;
        int tokens = log.computedTotalTokens();
        this.totalTokens += tokens;
        this.promptTokens += log.getPromptTokens() != null ? log.getPromptTokens() : 0;
        this.completionTokens += log.getCompletionTokens() != null ? log.getCompletionTokens() : 0;
        this.totalCostUsd = this.totalCostUsd.add(log.getCostUsd() != null ? log.getCostUsd() : BigDecimal.ZERO);

        if (log.isSuccess()) {
            this.successCount++;
        } else {
            this.errorCount++;
        }
    }
}
