package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.service.AdminIncidentService;
import tj.metro.dushanbe.admin.web.dto.IncidentCreateRequest;
import tj.metro.dushanbe.admin.web.dto.IncidentTransitionRequest;
import tj.metro.dushanbe.admin.web.dto.IncidentUpdateRequest;
import tj.metro.dushanbe.incident.web.dto.IncidentDto;
import tj.metro.dushanbe.incident.web.dto.IncidentStatsDto;

/**
 * Операционный учёт инцидентов. Раздел внутренний — публичного аналога нет:
 * пассажир видит только то, что оператор осознанно опубликует как service_alert.
 */
@RestController
@RequestMapping("/v1/admin/incidents")
@Tag(name = "Admin: Incidents", description = "Регистрация и разбор инцидентов")
public class AdminIncidentController {

    private final AdminIncidentService service;

    public AdminIncidentController(AdminIncidentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Список инцидентов", description = "Фильтр ?status=open|acknowledged|in_progress|resolved|closed")
    public List<IncidentDto> list(@RequestParam(name = "status", required = false) String status) {
        return service.list(status);
    }

    @GetMapping("/stats")
    @Operation(summary = "Счётчики за сутки для плиток дашборда")
    public IncidentStatsDto stats() {
        return service.stats();
    }

    @GetMapping("/{code}")
    public IncidentDto get(@PathVariable("code") String code) {
        return service.get(code);
    }

    @PostMapping
    public ResponseEntity<IncidentDto> create(
            @Valid @RequestBody IncidentCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, actor));
    }

    @PutMapping("/{code}")
    public IncidentDto update(
            @PathVariable("code") String code,
            @Valid @RequestBody IncidentUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.update(code, request, actor);
    }

    @PostMapping("/{code}/transition")
    @Operation(summary = "Перевести инцидент в новое состояние",
            description = "400 incident.transition_invalid, если переход не разрешён текущим статусом")
    public IncidentDto transition(
            @PathVariable("code") String code,
            @Valid @RequestBody IncidentTransitionRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.transition(code, request, actor);
    }
}
