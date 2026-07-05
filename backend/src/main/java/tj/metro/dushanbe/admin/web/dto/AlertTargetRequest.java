package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Таргет уведомления во входящем запросе (ADM-02/BR-ALT-3).
 * {@code type} — line|station; {@code code} — стабильный код линии/станции.
 */
public record AlertTargetRequest(
        @NotBlank String type,
        @NotBlank String code) {
}
