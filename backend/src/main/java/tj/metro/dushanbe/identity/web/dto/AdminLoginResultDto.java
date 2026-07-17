package tj.metro.dushanbe.identity.web.dto;

/**
 * Результат проверки учётных данных оператора.
 *
 * <p>Токена сессии здесь нет: сессию выпускает и подписывает Next-контур консоли
 * (admin/src/lib/auth.ts), backend отвечает только за проверку пароля и выдачу
 * профиля с ролью.
 */
public record AdminLoginResultDto(AdminUserDto user) {
}
