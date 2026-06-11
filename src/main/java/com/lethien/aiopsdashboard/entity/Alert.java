package com.lethien.aiopsdashboard.entity;

import com.lethien.aiopsdashboard.entity.enums.AlertType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alerts", indexes = {
        // Partial index WHERE is_active = TRUE — chỉ index alert đang bật
        // JPA không hỗ trợ partial index nên định nghĩa trong SQL migration
        @Index(name = "idx_alerts_user_id",     columnList = "user_id"),
        @Index(name = "idx_alerts_provider_id", columnList = "provider_id"),
        @Index(name = "idx_alerts_active",      columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // NULL = áp dụng cho tất cả providers của user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private AiProvider provider;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "alert_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    @Column(name = "threshold_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal thresholdValue;

    @Column(name = "threshold_unit", nullable = false, length = 20)
    @Builder.Default
    private String thresholdUnit = "USD";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // [FIX-4] Tránh spam notification — số giờ tối thiểu giữa 2 lần notify
    @Column(name = "notification_cooldown_hours", nullable = false)
    @Builder.Default
    private Integer notificationCooldownHours = 24;

    @Column(name = "last_triggered_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime lastTriggeredAt;

    // [FIX-4] Lần cuối notification thực sự được gửi (khác triggered vì có cooldown)
    @Column(name = "last_notified_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime lastNotifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    // Quản lý bởi DB trigger [FIX-2]
    @Column(name = "updated_at", nullable = false,
            insertable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    // ── Business methods ───────────────────────────────────────────────────

    /** Alert áp dụng cho tất cả provider (không gắn với provider cụ thể) */
    public boolean isGlobal() {
        return this.provider == null;
    }

    /**
     * Kiểm tra có thể gửi notification không dựa vào cooldown.
     * Tránh spam khi alert triggered nhiều ngày liên tiếp.
     */
    public boolean canNotify() {
        if (!Boolean.TRUE.equals(this.isActive)) return false;
        if (this.lastNotifiedAt == null) return true;

        OffsetDateTime nextAllowedAt = this.lastNotifiedAt.plusHours(this.notificationCooldownHours);

        return OffsetDateTime.now().isAfter(nextAllowedAt);

    }

    /**
     * Đánh dấu alert đã triggered — gọi mỗi khi phát hiện vượt threshold.
     * Chỉ cập nhật lastNotifiedAt nếu qua cooldown.
     */
    public void markTriggered() {
        this.lastTriggeredAt = OffsetDateTime.now();
        if (canNotify()) {
            this.lastNotifiedAt = OffsetDateTime.now();
        }
    }

    /** Soft disable alert */
    public void deactivate() {
        this.isActive = false;
    }

}
