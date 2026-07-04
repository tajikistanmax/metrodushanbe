package tj.metro.dushanbe.common.error;

/**
 * Ресурс не найден (HTTP 404). Код ошибки — машиночитаемый,
 * например {@code line.not_found} или {@code station.not_found}.
 */
public class NotFoundException extends RuntimeException {

    private final String code;

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static NotFoundException line(String lineCode) {
        return new NotFoundException("line.not_found", "Линия с кодом '" + lineCode + "' не найдена");
    }

    public static NotFoundException station(String stationCode) {
        return new NotFoundException("station.not_found", "Станция с кодом '" + stationCode + "' не найдена");
    }
}
