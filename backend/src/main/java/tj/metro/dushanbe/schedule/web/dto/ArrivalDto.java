package tj.metro.dushanbe.schedule.web.dto;

/**
 * Одно оценочное прибытие на станцию (SCH-03, headway-based).
 * {@code time} — местное время суток «HH:mm»; {@code etaMinutes} — сколько минут
 * до прибытия от «сейчас» (0 = прибывает в текущую минуту).
 */
public record ArrivalDto(String time, long etaMinutes) {
}
