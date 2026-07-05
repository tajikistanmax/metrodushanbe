package tj.metro.dushanbe.alert.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * Таргет сервисного уведомления (строка таблицы {@code service_alert_target}):
 * ссылка на линию или станцию по стабильному коду.
 * {@code type} — line|station; {@code code} — metro_line.code / metro_station.code.
 */
@Embeddable
public class AlertTarget {

    @Column(name = "target_type", nullable = false, length = 16)
    private String type;

    @Column(name = "target_code", nullable = false, length = 64)
    private String code;

    /** Конструктор для JPA. */
    protected AlertTarget() {
    }

    public AlertTarget(String type, String code) {
        this.type = type;
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AlertTarget other)) {
            return false;
        }
        return Objects.equals(type, other.type) && Objects.equals(code, other.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, code);
    }
}
