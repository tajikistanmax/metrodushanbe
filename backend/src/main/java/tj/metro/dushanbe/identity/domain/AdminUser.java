package tj.metro.dushanbe.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Именованный оператор консоли (ADM-01).
 *
 * <p>{@code passwordHash} — BCrypt-хеш; сущность никогда не хранит и не возвращает
 * открытый пароль. Геттер хеша существует только ради проверки на входе и не
 * попадает в DTO (см. AdminUserDto).
 */
@Entity
@Table(name = "admin_user")
public class AdminUser {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Convert(converter = AdminRoleConverter.class)
    @Column(name = "role", nullable = false, length = 24)
    private AdminRole role;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "session_version", nullable = false)
    private long sessionVersion;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AdminUser() {
    }

    public AdminUser(UUID id, String username, String displayName, String passwordHash,
                     AdminRole role, boolean active) {
        this.id = id;
        this.username = normalizeUsername(username);
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    /** Логин нечувствителен к регистру; БД дополнительно требует lower(username). */
    public static String normalizeUsername(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Обновляет профиль и роль; логин стабилен, пароль меняется отдельно. */
    public void update(String newDisplayName, AdminRole newRole, boolean enabled) {
        boolean securityChanged = role != newRole || active != enabled;
        displayName = newDisplayName;
        role = newRole;
        active = enabled;
        if (securityChanged) {
            sessionVersion++;
        }
    }

    /** Смена пароля: принимает уже вычисленный BCrypt-хеш. */
    public void changePasswordHash(String newHash) {
        passwordHash = newHash;
        sessionVersion++;
    }

    public void markLoggedIn(OffsetDateTime at) {
        lastLoginAt = at;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public AdminRole getRole() { return role; }
    public boolean isActive() { return active; }
    public long getSessionVersion() { return sessionVersion; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
