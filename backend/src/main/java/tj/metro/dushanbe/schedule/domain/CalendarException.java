package tj.metro.dushanbe.schedule.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "calendar_exception")
public class CalendarException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "exception_date", nullable = false, unique = true)
    private LocalDate exceptionDate;

    @Column(name = "day_type", nullable = false, length = 20)
    private String dayType;

    @Column(name = "description_tg")
    private String descriptionTg;

    @Column(name = "description_ru")
    private String descriptionRu;

    @Column(name = "description_en")
    private String descriptionEn;

    @Column(name = "is_recurring", nullable = false)
    private boolean isRecurring;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected CalendarException() {
    }

    public CalendarException(LocalDate exceptionDate, String dayType,
                             String descriptionTg, String descriptionRu, String descriptionEn,
                             boolean isRecurring) {
        this.exceptionDate = exceptionDate;
        this.dayType = dayType;
        this.descriptionTg = descriptionTg;
        this.descriptionRu = descriptionRu;
        this.descriptionEn = descriptionEn;
        this.isRecurring = isRecurring;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDate getExceptionDate() {
        return exceptionDate;
    }

    public String getDayType() {
        return dayType;
    }

    public String getDescriptionTg() {
        return descriptionTg;
    }

    public String getDescriptionRu() {
        return descriptionRu;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public boolean isRecurring() {
        return isRecurring;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setExceptionDate(LocalDate exceptionDate) {
        this.exceptionDate = exceptionDate;
    }

    public void setDayType(String dayType) {
        this.dayType = dayType;
    }

    public void setDescriptionTg(String descriptionTg) {
        this.descriptionTg = descriptionTg;
    }

    public void setDescriptionRu(String descriptionRu) {
        this.descriptionRu = descriptionRu;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public void setRecurring(boolean recurring) {
        isRecurring = recurring;
    }
}
