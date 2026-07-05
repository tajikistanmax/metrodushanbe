package tj.metro.dushanbe.audit.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.audit.domain.AuditEvent;
import tj.metro.dushanbe.audit.repository.AuditEventRepository;
import tj.metro.dushanbe.audit.web.dto.AuditEventDto;
import tj.metro.dushanbe.audit.web.dto.AuditPageDto;

/**
 * Читающий контур журнала аудита (ADM-05): постраничная лента событий, новые
 * сверху. Вынесен отдельно от {@link AuditService} (запись), чтобы сохранить
 * append-only-семантику записи и разделить ответственность.
 */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    /** Потолок размера страницы — защита от чрезмерных выборок. */
    public static final int MAX_PAGE_SIZE = 200;

    private final AuditEventRepository repository;

    public AuditQueryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /** Страница журнала аудита, отсортированная по времени по убыванию. */
    public AuditPageDto page(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "at"));
        Page<AuditEvent> result = repository.findAllBy(pageable);
        List<AuditEventDto> items = result.getContent().stream().map(AuditQueryService::toDto).toList();
        return new AuditPageDto(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private static AuditEventDto toDto(AuditEvent e) {
        return new AuditEventDto(e.getId(), e.getActor(), e.getAction(),
                e.getEntityType(), e.getEntityId(), e.getBefore(), e.getAfter(),
                e.getAt().toInstant());
    }
}
