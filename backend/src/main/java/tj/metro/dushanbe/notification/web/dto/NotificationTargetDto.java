package tj.metro.dushanbe.notification.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Таргет рассылки (NTF-02): {@code type} — line|station|segment|role,
 * {@code code} — metro_line.code / metro_station.code / код сегмента / код роли.
 *
 * <p>Одна и та же запись используется и в ответах, и в admin-запросах создания
 * рассылки: форма таргета в обе стороны одинакова, а второй тип-близнец только
 * разошёлся бы с этим по мере правок.
 */
public record NotificationTargetDto(
        @NotBlank @Pattern(regexp = "line|station|segment|role") String type,
        @NotBlank @Size(max = 64) String code) {
}
