package tj.metro.dushanbe.ticketing.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Предъявление билета турникету/контролёру (TKT-04).
 *
 * <p>Токен передаётся в теле POST, а не в URL: путь запроса попадает в логи
 * доступа, в историю браузера и в Referer — предъявительский секрет там жить не
 * должен. По той же причине сделано отслеживание обращений (POST /v1/requests/track).
 */
public record TicketValidateRequest(

        @NotBlank
        @Size(max = 128)
        String token) {
}
