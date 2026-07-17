package tj.metro.dushanbe.integration.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.admin.security.AdminKeyAuthFilter;
import tj.metro.dushanbe.common.error.UnauthorizedException;
import tj.metro.dushanbe.integration.service.TelemetryService;
import tj.metro.dushanbe.integration.web.dto.TrainPositionDto;
import tj.metro.dushanbe.integration.web.dto.TrainPositionReportRequest;

/**
 * Realtime-телеметрия положения поездов (U-INT-04).
 *
 * <p>Контур асимметричен: чтение публичное (пассажир видит поезда на карте),
 * запись — машинная, только от GPS/диспетчерской платформы.
 *
 * <p><b>Авторизация записи — временная, как и весь admin-контур.</b> Внешнего
 * OAuth пока нет (внешний блокер), поэтому {@code POST /positions} проверяет тот
 * же общий секрет {@code X-Admin-Key} ({@code app.admin.dev-key}), что и
 * {@link AdminKeyAuthFilter}. Своей криптографии здесь нет намеренно: изобретать
 * подпись запросов ради временной заглушки — худший из вариантов.
 *
 * <p>Проверка выполняется в контроллере, а не фильтром, потому что
 * {@link AdminKeyAuthFilter} по построению покрывает только префикс
 * {@code /v1/admin}, а этот путь публичный на чтение. Если владелец фильтра
 * предпочтёт единую точку — путь записи надо внести в его {@code shouldNotFilter}
 * и {@code requiredRole()}; этот метод тогда упрощается до вызова сервиса.
 *
 * <p><b>Боевой контур ОБЯЗАН перейти на OAuth 2.0 client credentials</b> (ТЗ
 * §6.1.7, §9.2): у GPS-платформы должен быть собственный клиент со своим
 * идентификатором, ротируемыми учётными данными и правом ровно на публикацию
 * телеметрии. Общий секрет, известный ещё и всем операторам консоли, не даёт ни
 * отзыва доступа для одной платформы, ни ответа на вопрос, кто именно прислал
 * замер. До закрытия этого блокера контур считать доверенным нельзя.
 */
@RestController
@RequestMapping("/v1/telemetry")
@Tag(name = "Telemetry", description = "Положение поездов в реальном времени (U-INT-04)")
public class TelemetryController {

    private final TelemetryService service;
    private final AdminAuthProperties adminAuthProperties;

    public TelemetryController(TelemetryService service, AdminAuthProperties adminAuthProperties) {
        this.service = service;
        this.adminAuthProperties = adminAuthProperties;
    }

    @PostMapping("/positions")
    @Operation(summary = "Публикация замера положения поезда (машинный контур)",
            description = """
                    Требует заголовок X-Admin-Key (временная заглушка вместо OAuth2 client credentials).
                    Коды ошибок: 401 admin.unauthorized; 404 line.not_found;
                    400 telemetry.coordinates_invalid, telemetry.occupancy_invalid, telemetry.line_required.
                    """)
    public ResponseEntity<TrainPositionDto> report(
            @Valid @RequestBody TrainPositionReportRequest request,
            @RequestHeader(name = AdminKeyAuthFilter.HEADER, required = false) String adminKey) {
        requireMachineKey(adminKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.report(request));
    }

    @GetMapping("/positions")
    @Operation(summary = "Текущие положения поездов линии",
            description = "По одному самому свежему замеру на поезд. Коды ошибок: 404 line.not_found.")
    public List<TrainPositionDto> positions(@RequestParam(name = "lineCode") String lineCode) {
        return service.currentPositions(lineCode);
    }

    /** Сравнение в constant-time — как в CitizenRequestService.constantTimeEquals. */
    private void requireMachineKey(String provided) {
        String expected = adminAuthProperties.getDevKey();
        if (expected == null || expected.isBlank() || provided == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                        provided.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("admin.unauthorized",
                    "Требуется корректный X-Admin-Key");
        }
    }
}
