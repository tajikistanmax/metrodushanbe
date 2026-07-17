package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.admin.web.dto.FareCreateRequest;
import tj.metro.dushanbe.admin.web.dto.FareUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.fare.domain.FareProduct;
import tj.metro.dushanbe.fare.repository.FareProductRepository;

class AdminFareServiceTest {

    private static final Map<String, String> NAME =
            Map.of("tg", "Тарофа", "ru", "Тариф", "en", "Fare");
    private static final Map<String, String> DESCRIPTION =
            Map.of("tg", "Тавсиф", "ru", "Описание", "en", "Description");

    private final FareProductRepository repository = mock(FareProductRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminFareService service = new AdminFareService(repository, auditService);

    @Test
    void createValidatesPersistsAndAudits() {
        when(repository.existsByCode("SINGLE")).thenReturn(false);
        when(repository.save(any(FareProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FareCreateRequest request = new FareCreateRequest("SINGLE", NAME, DESCRIPTION,
                new BigDecimal("3.00"), "TJS", "all", 90, true);

        var result = service.create(request, "fare-editor");

        assertEquals("SINGLE", result.code());
        assertEquals(new BigDecimal("3.00"), result.amount());
        verify(auditService).record(eq("fare-editor"), eq("fare.create"),
                eq("fare_product"), eq("SINGLE"), isNull(), any());
    }

    @Test
    void createRejectsIncompleteLanguages() {
        when(repository.existsByCode("BAD")).thenReturn(false);
        FareCreateRequest request = new FareCreateRequest("BAD",
                Map.of("tg", "Тарофа", "ru", "Тариф"), DESCRIPTION,
                BigDecimal.ONE, "TJS", "all", null, false);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.create(request, "editor"));

        assertEquals("validation.i18n_incomplete", error.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void updateChangesEditableFields() {
        FareProduct product = product("SINGLE");
        when(repository.findByCode("SINGLE")).thenReturn(Optional.of(product));
        when(repository.save(product)).thenReturn(product);
        FareUpdateRequest request = new FareUpdateRequest(NAME, DESCRIPTION,
                new BigDecimal("4.50"), "TJS", "adult", 120, true);

        var result = service.update("SINGLE", request, "editor");

        assertEquals(new BigDecimal("4.50"), result.amount());
        assertEquals("adult", result.riderCategory());
        assertEquals(120, result.validityMinutes());
    }

    @Test
    void deleteUnknownFareReturnsDomainNotFound() {
        when(repository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> service.delete("UNKNOWN", "editor"));

        assertEquals("fare.not_found", error.getCode());
    }

    private static FareProduct product(String code) {
        return new FareProduct(UUID.randomUUID(), code, NAME, DESCRIPTION,
                new BigDecimal("3.00"), "TJS", "all", 90, true);
    }
}
