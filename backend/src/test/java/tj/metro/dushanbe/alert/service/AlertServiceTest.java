package tj.metro.dushanbe.alert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.alert.domain.AlertTarget;
import tj.metro.dushanbe.alert.domain.ServiceAlert;
import tj.metro.dushanbe.alert.repository.ServiceAlertRepository;
import tj.metro.dushanbe.alert.web.dto.AlertDto;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.network.repository.MetroStationLineRepository;

/**
 * Юнит-тесты AlertService без БД: репозитории — mock, «сейчас» — Clock.fixed.
 * Окно действия вычисляется в БД (JPQL {@code findActivePublished}) и здесь
 * не проверяется — покрыто интеграционным тестом; юнит-тесты проверяют,
 * что в репозиторий передаётся «сейчас» из Clock, а также сортировку,
 * таргет-фильтры (включая связь станция-линия) и валидацию severity.
 */
class AlertServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-04T12:00:00Z");
    private static final OffsetDateTime NOW_ODT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private final ServiceAlertRepository repository = mock(ServiceAlertRepository.class);
    private final MetroStationLineRepository stationLineRepository = mock(MetroStationLineRepository.class);
    private final AlertService service =
            new AlertService(repository, stationLineRepository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void passesClockNowToRepositoryWindowQuery() {
        when(repository.findActivePublished(NOW_ODT)).thenReturn(List.of());

        assertEquals(List.of(), service.activeAlerts(null, null, null));

        verify(repository).findActivePublished(NOW_ODT);
    }

    @Test
    void sortsBySeverityThenStartsAtDescending() {
        ServiceAlert infoOld = alert("A-INFO-OLD", "info", NOW.minus(Duration.ofDays(9)), null);
        ServiceAlert infoNew = alert("A-INFO-NEW", "info", NOW.minus(Duration.ofDays(1)), null);
        ServiceAlert warning = alert("A-WARN", "warning", NOW.minus(Duration.ofDays(5)), null);
        ServiceAlert critical = alert("A-CRIT", "critical", NOW.minus(Duration.ofDays(7)), null);
        when(repository.findActivePublished(NOW_ODT)).thenReturn(List.of(infoOld, infoNew, warning, critical));

        List<String> codes = service.activeAlerts(null, null, null).stream().map(AlertDto::code).toList();

        assertEquals(List.of("A-CRIT", "A-WARN", "A-INFO-NEW", "A-INFO-OLD"), codes);
    }

    @Test
    void lineFilterIncludesTargetedAndNetworkWideOnly() {
        ServiceAlert targetedL2 = alert("A-L2", "warning", NOW.minus(Duration.ofDays(1)), null,
                new AlertTarget("line", "L2"), new AlertTarget("station", "ST-L2-02"));
        ServiceAlert targetedL1 = alert("A-L1", "warning", NOW.minus(Duration.ofDays(1)), null,
                new AlertTarget("line", "L1"));
        ServiceAlert networkWide = alert("A-NET", "info", NOW.minus(Duration.ofDays(2)), null);
        when(repository.findActivePublished(NOW_ODT)).thenReturn(List.of(targetedL2, targetedL1, networkWide));

        List<String> codes = service.activeAlerts("L2", null, null).stream().map(AlertDto::code).toList();

        assertEquals(List.of("A-L2", "A-NET"), codes);
    }

    @Test
    void lineFilterIsNotExpandedByStationTargets() {
        // станционный таргет станции линии L2 не включает alert в выдачу по ?lineCode=L2
        ServiceAlert stationOnly = alert("A-STATION-ONLY", "warning", NOW.minus(Duration.ofDays(1)), null,
                new AlertTarget("station", "ST-L2-02"));
        when(repository.findActivePublished(NOW_ODT)).thenReturn(List.of(stationOnly));

        assertEquals(List.of(), service.activeAlerts("L2", null, null));
    }

    @Test
    void stationFilterIncludesAlertsTargetedAtStationsLine() {
        // ST-L2-02 принадлежит линии L2 => line-targeted alert (только line L2) попадает в выдачу
        when(stationLineRepository.findLineCodesByStationCode("ST-L2-02")).thenReturn(List.of("L2"));
        ServiceAlert lineTargeted = alert("A-LINE-L2", "warning", NOW.minus(Duration.ofDays(1)), null,
                new AlertTarget("line", "L2"));
        ServiceAlert otherLine = alert("A-LINE-L1", "warning", NOW.minus(Duration.ofDays(1)), null,
                new AlertTarget("line", "L1"));
        ServiceAlert networkWide = alert("A-NET", "info", NOW.minus(Duration.ofDays(2)), null);
        when(repository.findActivePublished(NOW_ODT)).thenReturn(List.of(lineTargeted, otherLine, networkWide));

        List<String> codes = service.activeAlerts(null, "ST-L2-02", null).stream().map(AlertDto::code).toList();

        assertEquals(List.of("A-LINE-L2", "A-NET"), codes);
    }

    @Test
    void combinedLineAndStationFilterIsUnion() {
        // объединение: попадает прошедший хотя бы один фильтр («не потерять уведомление»)
        when(stationLineRepository.findLineCodesByStationCode("ST-L2-02")).thenReturn(List.of("L2"));
        ServiceAlert byLine = alert("A-BY-LINE", "warning", NOW.minus(Duration.ofDays(1)), null,
                new AlertTarget("line", "L1"));
        ServiceAlert byStation = alert("A-BY-STATION", "warning", NOW.minus(Duration.ofDays(2)), null,
                new AlertTarget("station", "ST-L2-02"));
        ServiceAlert neither = alert("A-NEITHER", "warning", NOW.minus(Duration.ofDays(3)), null,
                new AlertTarget("station", "ST-L1-03"));
        when(repository.findActivePublished(NOW_ODT)).thenReturn(List.of(byLine, byStation, neither));

        List<String> codes = service.activeAlerts("L1", "ST-L2-02", null).stream().map(AlertDto::code).toList();

        assertEquals(List.of("A-BY-LINE", "A-BY-STATION"), codes);
    }

    @Test
    void stationFilterDoesNotMatchLineTargetWithSameCode() {
        // таргет типа line не должен совпадать со stationCode даже при равенстве кодов
        when(stationLineRepository.findLineCodesByStationCode("ST-L2-02")).thenReturn(List.of("L2"));
        ServiceAlert lineTargeted = alert("A-LINE", "warning", NOW.minus(Duration.ofDays(1)), null,
                new AlertTarget("line", "ST-L2-02"));
        ServiceAlert stationTargeted = alert("A-STATION", "warning", NOW.minus(Duration.ofDays(2)), null,
                new AlertTarget("station", "ST-L2-02"));
        when(repository.findActivePublished(NOW_ODT)).thenReturn(List.of(lineTargeted, stationTargeted));

        List<String> codes = service.activeAlerts(null, "ST-L2-02", null).stream().map(AlertDto::code).toList();

        assertEquals(List.of("A-STATION"), codes);
    }

    @Test
    void severityFilterIsStrict() {
        ServiceAlert info = alert("A-INFO", "info", NOW.minus(Duration.ofDays(1)), null);
        ServiceAlert warning = alert("A-WARN", "warning", NOW.minus(Duration.ofDays(1)), null);
        when(repository.findActivePublished(NOW_ODT)).thenReturn(List.of(info, warning));

        List<String> codes = service.activeAlerts(null, null, "warning").stream().map(AlertDto::code).toList();

        assertEquals(List.of("A-WARN"), codes);
    }

    @Test
    void invalidSeverityThrowsBadRequestWithDomainCode() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.activeAlerts(null, null, "bogus"));

        assertEquals("alert.severity_invalid", ex.getCode());
    }

    @Test
    void dtoCarriesFullI18nAndNullEndsAt() {
        ServiceAlert endless = alert("A-ENDLESS", "info", NOW.minus(Duration.ofDays(1)), null);
        when(repository.findActivePublished(NOW_ODT)).thenReturn(List.of(endless));

        AlertDto dto = service.activeAlerts(null, null, null).getFirst();

        assertEquals(Map.of("tg", "Сарлавҳа", "ru", "Заголовок", "en", "Title"), dto.title());
        assertNull(dto.endsAt());
        assertEquals(NOW.minus(Duration.ofDays(1)), dto.startsAt());
        assertEquals(List.of(), dto.targets());
    }

    /** Фабрика published-уведомления с фиксированными i18n-текстами. */
    private static ServiceAlert alert(String code, String severity, Instant startsAt, Instant endsAt,
                                      AlertTarget... targets) {
        return new ServiceAlert(
                UUID.randomUUID(), code, severity, "published",
                Map.of("tg", "Сарлавҳа", "ru", "Заголовок", "en", "Title"),
                Map.of("tg", "Матн", "ru", "Текст", "en", "Body"),
                OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC),
                endsAt != null ? OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC) : null,
                List.of(targets));
    }
}
