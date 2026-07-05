package tj.metro.dushanbe.admin.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.web.dto.LineCreateRequest;
import tj.metro.dushanbe.admin.web.dto.LineUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.network.domain.MetroLine;
import tj.metro.dushanbe.network.repository.MetroLineRepository;
import tj.metro.dushanbe.network.service.NetworkService;
import tj.metro.dushanbe.network.web.dto.LineDto;

/**
 * Admin-write контур линий (ADM-02). Создание/обновление/soft-delete с валидацией
 * входа (цвет, статус, полнота языков) и обязательной записью в аудит (BR-ADM-1).
 * Каждая мутация — в одной транзакции с записью аудита (атомарность).
 *
 * <p>Публичные read-эндпоинты не затрагиваются; soft-delete (BR-NET-2) помечает
 * строку {@code deleted_at}, но сокрытие удалённых линий из публичной выдачи
 * (read-side temporal-фильтр, BR-NET-3) в этой фазе не реализовано — см. отчёт.
 */
@Service
public class AdminLineService {

    private final MetroLineRepository lineRepository;
    private final AuditService auditService;
    private final Clock clock;

    public AdminLineService(MetroLineRepository lineRepository, AuditService auditService, Clock clock) {
        this.lineRepository = lineRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** Создать линию (статус/цвет/языки валидируются; аудит line.create). */
    @Transactional
    public LineDto create(LineCreateRequest request, String actor) {
        AdminSupport.requireUnique(lineRepository.existsByCode(request.code()),
                "line.code_exists", "code", request.code());
        AdminSupport.requireIn(request.status(), NetworkService.LINE_STATUSES, "line.status_invalid", "status");
        AdminSupport.requireLanguages(request.name(), "name");

        int sortOrder = request.sortOrder() != null ? request.sortOrder() : 0;
        MetroLine line = new MetroLine(UUID.randomUUID(), request.code(), request.colorHex(),
                request.status(), request.name(), sortOrder, AdminSupport.multiLine(request.path()));
        MetroLine saved = lineRepository.save(line);

        auditService.record(actor, "line.create", "line", saved.getCode(), null, snapshot(saved));
        return toDto(saved);
    }

    /** Обновить линию по коду (аудит line.update со снимками до/после). */
    @Transactional
    public LineDto update(String code, LineUpdateRequest request, String actor) {
        MetroLine line = lineRepository.findByCode(code).orElseThrow(() -> NotFoundException.line(code));
        AdminSupport.requireIn(request.status(), NetworkService.LINE_STATUSES, "line.status_invalid", "status");
        AdminSupport.requireLanguages(request.name(), "name");

        Map<String, Object> before = snapshot(line);
        int sortOrder = request.sortOrder() != null ? request.sortOrder() : line.getSortOrder();
        line.updateDetails(request.colorHex(), request.status(), request.name(), sortOrder);
        if (request.path() != null) {
            line.setGeom(AdminSupport.multiLine(request.path()));
        }
        MetroLine saved = lineRepository.save(line);

        auditService.record(actor, "line.update", "line", code, before, snapshot(saved));
        return toDto(saved);
    }

    /** Soft-delete линии по коду (BR-NET-2; аудит line.delete). */
    @Transactional
    public void softDelete(String code, String actor) {
        MetroLine line = lineRepository.findByCode(code).orElseThrow(() -> NotFoundException.line(code));
        Map<String, Object> before = snapshot(line);
        line.softDelete(OffsetDateTime.now(clock));
        MetroLine saved = lineRepository.save(line);
        auditService.record(actor, "line.delete", "line", code, before, snapshot(saved));
    }

    private static LineDto toDto(MetroLine line) {
        return new LineDto(line.getCode(), line.getNameI18n(), line.getColorHex(),
                line.getStatus(), line.getSortOrder());
    }

    private static Map<String, Object> snapshot(MetroLine line) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", line.getCode());
        snapshot.put("colorHex", line.getColorHex());
        snapshot.put("status", line.getStatus());
        snapshot.put("name", line.getNameI18n());
        snapshot.put("sortOrder", line.getSortOrder());
        snapshot.put("deletedAt", line.getDeletedAt() != null ? line.getDeletedAt().toInstant().toString() : null);
        return snapshot;
    }
}
