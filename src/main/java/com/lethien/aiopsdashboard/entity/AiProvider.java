package com.lethien.aiopsdashboard.entity;

import com.lethien.aiopsdashboard.entity.enums.ProviderType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_providers",
uniqueConstraints = @UniqueConstraint(
        name = "uq_provider_name_per_user",
        columnNames = {"user_id", "name"}
))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    // LAZY: không load User khi query AiProvider (tránh N+1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "provider_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ProviderType providerType;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "api_key_encrypted", columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(name = "default_model", length = 100)
    private String defaultModel;

    @Column(length = 500)
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createAt;

    @Column(name = "updated_at", nullable = false,
            insertable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    // ── Relationships ──────────────────────────────────────────────────────

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RequestLog> requestLogs = new ArrayList<>();

    @OneToMany(mappedBy = "provider",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<UsageMetric> usageMetrics = new ArrayList<>();

    @OneToMany(mappedBy = "provider",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Alert> alerts = new ArrayList<>();

    // ── Business methods ───────────────────────────────────────────────────

    /** Provider có cần API key không (Ollama chạy local thì không cần) */
    public boolean requiresApiKey() {
        return !ProviderType.OLLAMA.equals(this.providerType);
    }

    /** Kiểm tra provider đã được cấu hình đầy đủ để gọi API chưa */
    public boolean isConfigured() {
        if (!Boolean.TRUE.equals(this.isActive)) return false;
        if (requiresApiKey()) {
            return this.apiKeyEncrypted != null && !this.apiKeyEncrypted.isBlank();
        }
        return this.apiKeyEncrypted != null && !this.baseUrl.isBlank();
    }

    /** Soft delete — không xoá khỏi DB, chỉ đánh dấu inactive */
    public void deactivate() {
        this.isActive = false;
    }

    /** Thêm request log và đảm bảo quan hệ 2 chiều */
    public void addRequestLog(RequestLog log) {
        requestLogs.add(log);
        log.setProvider(this);
    }
}
