package tj.metro.dushanbe.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Счётчик неудачных входов и текущая блокировка (аудит-пункт 9).
 *
 * <p><b>Почему в БД, а не в памяти.</b> Счётчик в {@code ConcurrentHashMap} обнуляется
 * перезапуском процесса: атакующему достаточно дождаться деплоя или вызвать OOM, и
 * подбор продолжается с нуля. Блокировка обязана переживать рестарт, поэтому
 * состояние живёт в таблице и общее для всех инстансов за балансировщиком.
 *
 * <p><b>Счётчик заводится и для несуществующего логина.</b> Это не расточительство,
 * а требование неразличимости: если бы блокировка появлялась только у реальных
 * учётных записей, ответ «слишком много попыток» сам по себе доказывал бы, что
 * логин существует, — и перебор учёток шёл бы прямо через lockout.
 */
@Entity
@Table(name = "admin_login_attempt")
public class LoginAttempt {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Convert(converter = LoginAttemptScope.Persistence.class)
    @Column(name = "scope", nullable = false, length = 16)
    private LoginAttemptScope scope;

    /** Ключ в пределах области: логин либо «ip|логин». */
    @Column(name = "subject", nullable = false, length = 160)
    private String subject;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "first_failure_at")
    private OffsetDateTime firstFailureAt;

    @Column(name = "last_failure_at")
    private OffsetDateTime lastFailureAt;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected LoginAttempt() {
    }

    public LoginAttempt(UUID id, LoginAttemptScope scope, String subject) {
        this.id = id;
        this.scope = scope;
        this.subject = subject;
        this.failureCount = 0;
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

    /** Ключ пары IP+логин. Порядок фиксирован, чтобы ключ был устойчив. */
    public static String ipUserSubject(String ip, String username) {
        return (ip == null || ip.isBlank() ? "unknown" : ip) + "|" + username;
    }

    /** Действует ли блокировка на момент {@code now}. */
    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Сколько секунд осталось до снятия блокировки; 0 — блокировки нет. */
    public long retryAfterSeconds(OffsetDateTime now) {
        if (!isLocked(now)) {
            return 0L;
        }
        long seconds = Duration.between(now, lockedUntil).toSeconds();
        // Округление вверх: «повторите через 0 секунд» при остатке в 400 мс —
        // не подсказка, а насмешка над оператором.
        return Math.max(1L, seconds);
    }

    /**
     * Учитывает неудачную попытку и, если порог достигнут, включает блокировку.
     *
     * <p>Окно скользит целиком: если с первой неудачи прошло больше {@code window},
     * серия считается прерванной и счёт начинается заново. Иначе редкие опечатки
     * за месяц накопились бы в блокировку живого оператора.
     *
     * <p>При срабатывании порога счётчик сбрасывается: после снятия блокировки
     * оператор получает полный набор попыток, а не блокируется каждой следующей.
     *
     * @return true, если этой попыткой блокировка включилась
     */
    public boolean registerFailure(OffsetDateTime now, int threshold, Duration window,
                                   Duration lockDuration) {
        if (firstFailureAt == null || firstFailureAt.plus(window).isBefore(now)) {
            firstFailureAt = now;
            failureCount = 0;
        }
        failureCount++;
        lastFailureAt = now;
        if (failureCount >= threshold) {
            lockedUntil = now.plus(lockDuration);
            failureCount = 0;
            firstFailureAt = null;
            return true;
        }
        return false;
    }

    /** Успешный вход обнуляет историю: серия прервана, блокировать больше нечего. */
    public void reset() {
        failureCount = 0;
        firstFailureAt = null;
        lastFailureAt = null;
        lockedUntil = null;
    }

    public UUID getId() { return id; }
    public LoginAttemptScope getScope() { return scope; }
    public String getSubject() { return subject; }
    public int getFailureCount() { return failureCount; }
    public OffsetDateTime getFirstFailureAt() { return firstFailureAt; }
    public OffsetDateTime getLastFailureAt() { return lastFailureAt; }
    public OffsetDateTime getLockedUntil() { return lockedUntil; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
