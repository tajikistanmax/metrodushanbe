package tj.metro.dushanbe.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Статический график движения по линии (таблица {@code line_schedule}, ТЗ §6.2.4 SCH-01).
 * Одна запись описывает интервальный график линии для типа дня: первое/последнее
 * отправление и интервал движения (headway) внутри окна действия
 * [{@code effectiveFrom}, {@code effectiveTo}] ({@code effectiveTo} NULL = бессрочно).
 *
 * <p>Линия задаётся стабильным кодом {@code lineCode} (FK на {@code metro_line.code},
 * см. V011) — так же, как таргеты alert ссылаются на коды сети. Прогнозные прибытия
 * (SCH-03) на основе headway — оценочные, до появления realtime-фида (SCH-05/08).
 */
@Entity
@Table(name = "line_schedule")
public class LineSchedule {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Стабильный код линии ({@code metro_line.code}), например "L1". */
    @Column(name = "line_code", nullable = false, length = 32)
    private String lineCode;

    /** Тип дня: weekday|weekend|holiday (CHECK в V011). */
    @Column(name = "day_type", nullable = false, length = 16)
    private String dayType;

    @Column(name = "first_departure", nullable = false)
    private LocalTime firstDeparture;

    @Column(name = "last_departure", nullable = false)
    private LocalTime lastDeparture;

    /** Интервал движения в минутах (headway); > 0 (CHECK в V011). */
    @Column(name = "headway_minutes", nullable = false)
    private int headwayMinutes;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** NULL = бессрочно. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Конструктор для JPA. */
    protected LineSchedule() {
    }

    public LineSchedule(UUID id, String lineCode, String dayType,
                        LocalTime firstDeparture, LocalTime lastDeparture, int headwayMinutes,
                        LocalDate effectiveFrom, LocalDate effectiveTo) {
        this.id = id;
        this.lineCode = lineCode;
        this.dayType = dayType;
        this.firstDeparture = firstDeparture;
        this.lastDeparture = lastDeparture;
        this.headwayMinutes = headwayMinutes;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
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

    /**
     * Действует ли график в операционном смысле в момент {@code time} суток:
     * {@code firstDeparture <= time <= lastDeparture}. Вне окна — сервис не работает,
     * прибытия не оцениваются (BR-SCH-2: устаревшее/невалидное не отдаём).
     */
    public boolean isWithinServiceHours(LocalTime time) {
        return !time.isBefore(firstDeparture) && !time.isAfter(lastDeparture);
    }

    public UUID getId() {
        return id;
    }

    public String getLineCode() {
        return lineCode;
    }

    public String getDayType() {
        return dayType;
    }

    public LocalTime getFirstDeparture() {
        return firstDeparture;
    }

    public LocalTime getLastDeparture() {
        return lastDeparture;
    }

    public int getHeadwayMinutes() {
        return headwayMinutes;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
