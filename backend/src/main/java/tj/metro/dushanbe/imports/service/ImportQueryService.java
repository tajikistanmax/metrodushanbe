package tj.metro.dushanbe.imports.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.imports.domain.ImportError;
import tj.metro.dushanbe.imports.domain.ImportJob;
import tj.metro.dushanbe.imports.repository.ImportErrorRepository;
import tj.metro.dushanbe.imports.repository.ImportJobRepository;
import tj.metro.dushanbe.imports.web.dto.ImportErrorDto;
import tj.metro.dushanbe.imports.web.dto.ImportJobDto;
import tj.metro.dushanbe.imports.web.dto.ImportPageDto;

/**
 * Читающий контур импорта: сводка/лента джобов и построчные ошибки конкретного
 * джоба (IMP-03). Вынесен отдельно от {@link ImportService} (запись/применение),
 * чтобы разделить ответственность и держать чтение в read-only транзакциях.
 */
@Service
@Transactional(readOnly = true)
public class ImportQueryService {

    /** Потолок размера страницы ленты джобов. */
    public static final int MAX_PAGE_SIZE = 200;

    private final ImportJobRepository jobRepository;
    private final ImportErrorRepository errorRepository;

    public ImportQueryService(ImportJobRepository jobRepository, ImportErrorRepository errorRepository) {
        this.jobRepository = jobRepository;
        this.errorRepository = errorRepository;
    }

    /** Сводка джоба по id; неизвестный id → 404 import.not_found. */
    public ImportJobDto job(UUID id) {
        return jobRepository.findById(id).map(ImportQueryService::toDto)
                .orElseThrow(() -> notFound(id));
    }

    /** Построчные ошибки джоба; неизвестный id → 404 import.not_found. */
    public List<ImportErrorDto> errors(UUID id) {
        if (!jobRepository.existsById(id)) {
            throw notFound(id);
        }
        return errorRepository.findByJob_IdOrderByCreatedAtAsc(id).stream()
                .map(ImportQueryService::toDto).toList();
    }

    /** Страница ленты джобов, новые сверху. */
    public ImportPageDto page(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ImportJob> result = jobRepository.findAllBy(pageable);
        List<ImportJobDto> items = result.getContent().stream().map(ImportQueryService::toDto).toList();
        return new ImportPageDto(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private static NotFoundException notFound(UUID id) {
        return new NotFoundException("import.not_found", "Задание импорта с id '" + id + "' не найдено");
    }

    public static ImportJobDto toDto(ImportJob j) {
        return new ImportJobDto(j.getId(), j.getType(), j.getFormat(), j.getStatus(),
                j.getSourceName(), j.getSourceHash(),
                j.getFeatureCount(), j.getCreatedCount(), j.getUpdatedCount(), j.getFailedCount(),
                instant(j.getStartedAt()), instant(j.getFinishedAt()), instant(j.getCreatedAt()));
    }

    private static ImportErrorDto toDto(ImportError e) {
        return new ImportErrorDto(e.getId(), e.getFeatureRef(), e.getMessage(), e.getSeverity(),
                instant(e.getCreatedAt()));
    }

    private static java.time.Instant instant(OffsetDateTime value) {
        return value != null ? value.toInstant() : null;
    }
}
