package tj.metro.dushanbe.admin.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.admin.web.dto.FareCreateRequest;
import tj.metro.dushanbe.admin.web.dto.FareUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.common.i18n.I18nValidator;
import tj.metro.dushanbe.fare.domain.FareProduct;
import tj.metro.dushanbe.fare.repository.FareProductRepository;
import tj.metro.dushanbe.fare.service.FareService;
import tj.metro.dushanbe.fare.web.dto.FareProductDto;

/** CRUD тарифного справочника с i18n-валидацией, кэш-инвалидацией и аудитом. */
@Service
public class AdminFareService {

    private final FareProductRepository repository;
    private final AuditService auditService;

    public AdminFareService(FareProductRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FareProductDto> list() {
        return repository.findAllByOrderByAmountAscCodeAsc().stream()
                .map(FareService::toDto)
                .toList();
    }

    @Transactional
    @CacheEvict(value = "fares", allEntries = true)
    public FareProductDto create(FareCreateRequest request, String actor) {
        AdminSupport.requireUnique(repository.existsByCode(request.code()),
                "fare.code_exists", "code", request.code());
        validateI18n(request.name(), request.description());
        FareProduct product = new FareProduct(UUID.randomUUID(), request.code(), request.name(),
                request.description(), request.amount(), request.currency(), request.riderCategory(),
                request.validityMinutes(), request.active());
        FareProduct saved = repository.save(product);
        auditService.record(actor, "fare.create", "fare_product", request.code(),
                null, snapshot(saved));
        return FareService.toDto(saved);
    }

    @Transactional
    @CacheEvict(value = "fares", allEntries = true)
    public FareProductDto update(String code, FareUpdateRequest request, String actor) {
        FareProduct product = find(code);
        validateI18n(request.name(), request.description());
        Map<String, Object> before = snapshot(product);
        product.update(request.name(), request.description(), request.amount(), request.currency(),
                request.riderCategory(), request.validityMinutes(), request.active());
        FareProduct saved = repository.save(product);
        auditService.record(actor, "fare.update", "fare_product", code, before, snapshot(saved));
        return FareService.toDto(saved);
    }

    @Transactional
    @CacheEvict(value = "fares", allEntries = true)
    public void delete(String code, String actor) {
        FareProduct product = find(code);
        Map<String, Object> before = snapshot(product);
        repository.delete(product);
        auditService.record(actor, "fare.delete", "fare_product", code, before, null);
    }

    private FareProduct find(String code) {
        return repository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("fare.not_found", "Тариф не найден"));
    }

    private static void validateI18n(Map<String, String> name, Map<String, String> description) {
        I18nValidator.requireAll(name, "name");
        I18nValidator.requireAll(description, "description");
    }

    private static Map<String, Object> snapshot(FareProduct product) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", product.getCode());
        snapshot.put("amount", product.getAmount());
        snapshot.put("currency", product.getCurrency());
        snapshot.put("riderCategory", product.getRiderCategory());
        snapshot.put("validityMinutes", product.getValidityMinutes());
        snapshot.put("active", product.isActive());
        return snapshot;
    }
}
