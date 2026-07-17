package tj.metro.dushanbe.admin.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Перевод рассылки в новое состояние. Допустимость перехода проверяется по карте
 * NotificationStatus.TRANSITIONS — иначе 400 {@code notification.transition_invalid}.
 *
 * <p>{@code sending} через этот эндпоинт принимать можно, но осмысленный путь к
 * отправке — POST /{code}/send: он же создаёт доставки и доводит до {@code sent}.
 */
public record NotificationStatusRequest(
        @NotBlank @Pattern(regexp = "draft|scheduled|sending|sent|cancelled") String status) {
}
