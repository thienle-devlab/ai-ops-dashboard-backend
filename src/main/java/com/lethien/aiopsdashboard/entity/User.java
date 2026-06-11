package com.lethien.aiopsdashboard.entity;

import com.lethien.aiopsdashboard.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createAt;

    // updated_at được quản lý bởi DB trigger set_updated_at() [FIX-2]
    // insertable=false: không ghi khi INSERT (DB tự set DEFAULT NOW())
    // updatable=false : không ghi khi UPDATE (DB trigger tự cập nhật)
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime updatedAt;

    // ── Relationships ──────────────────────────────────────────────────────
    // mappedBy: JPA không tạo FK ở phía này (FK nằm ở ai_providers)
    // cascade PERSIST, MERGE: lưu/update providers khi lưu user
    // orphanRemoval: xoá provider nếu bị remove khỏi list

    @OneToMany(mappedBy = "user",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<AiProvider> providers = new ArrayList<>();

    @OneToMany(mappedBy = "user",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Alert> alerts = new ArrayList<>();

    // ── Business methods ───────────────────────────────────────────────────

    /** Kiểm tra user có quyền ADMIN không */
    public boolean isAdmin() {
        return Role.ADMIN.equals(this.role);
    }

    /** Kiểm tra user đang active và có thể đăng nhập */
    public boolean canLogin() {
        return Boolean.TRUE.equals(this.isActive);
    }

    /** Thêm provider và đảm bảo quan hệ 2 chiều */
    public void addProvider(AiProvider provider) {
        providers.add(provider);
        provider.setUser(this);
    }

    /** Xoá provider và đảm bảo quan hệ 2 chiều */
    public void removeProvider(AiProvider provider) {
        providers.remove(provider);
        provider.setUser(null);
    }

    /** Thêm alert và đảm bảo quan hệ 2 chiều */
    public void addAlert(Alert alert) {
        alerts.add(alert);
        alert.setUser(this);
    }
}
