package tj.metro.dushanbe.incident.web.dto;

import java.util.Map;

/**
 * Счётчики для плиток дашборда (макет photo/…13_06_19 «Инциденты и безопасность»).
 *
 * @param today     зарегистрировано за текущие сутки
 * @param open      не устранённые: open + acknowledged + in_progress
 * @param byCategory разрез суток по категориям; ключи — коды категорий, нули включены
 */
public record IncidentStatsDto(
        long today,
        long open,
        Map<String, Long> byCategory) {
}
