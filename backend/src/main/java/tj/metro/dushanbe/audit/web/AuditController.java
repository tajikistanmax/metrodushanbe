package tj.metro.dushanbe.audit.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.audit.service.AuditQueryService;
import tj.metro.dushanbe.audit.web.dto.AuditPageDto;

/**
 * Журнал аудита (ADM-05). Итоговый путь с учётом context-path: /api/v1/admin/audit.
 * Под префиксом /v1/admin/** — защищён dev-фильтром X-Admin-Key
 * (см. AdminKeyAuthFilter; прод-авторизация — Keycloak/OAuth2/RBAC, ТЗ §6.1.7).
 */
@RestController
@RequestMapping("/v1/admin/audit")
@Tag(name = "Admin: Audit", description = "Журнал аудита действий (append-only)")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @Operation(summary = "Лента журнала аудита",
            description = "Постраничный список событий аудита, новые сверху (по времени). "
                    + "Размер страницы ограничен сверху (200).")
    public AuditPageDto list(
            @Parameter(description = "Номер страницы, начиная с 0")
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Размер страницы (1..200)")
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return auditQueryService.page(page, size);
    }
}
