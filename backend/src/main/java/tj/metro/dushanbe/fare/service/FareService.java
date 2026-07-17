package tj.metro.dushanbe.fare.service;

import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.fare.domain.FareProduct;
import tj.metro.dushanbe.fare.repository.FareProductRepository;
import tj.metro.dushanbe.fare.web.dto.FareProductDto;

/** Публичный справочник только активных тарифов. */
@Service
@Transactional(readOnly = true)
public class FareService {

    /**
     * Допустимые категории пассажира ({@code fare_product.rider_category}). Значения
     * обязаны совпадать с {@code chk_fare_category} (V018) и с {@code @Pattern} в
     * {@code FareCreateRequest}/{@code FareUpdateRequest}. Порядок — от общего к частному:
     * список попадает в сообщения об ошибках импорта, и оператору его читать.
     */
    public static final List<String> RIDER_CATEGORIES =
            List.of("all", "adult", "child", "student", "senior");

    private final FareProductRepository repository;

    public FareService(FareProductRepository repository) {
        this.repository = repository;
    }

    @Cacheable("fares")
    public List<FareProductDto> activeFares() {
        return repository.findByActiveTrueOrderByAmountAscCodeAsc().stream()
                .map(FareService::toDto)
                .toList();
    }

    public static FareProductDto toDto(FareProduct product) {
        return new FareProductDto(product.getCode(), product.getNameI18n(),
                product.getDescriptionI18n(), product.getAmount(), product.getCurrency(),
                product.getRiderCategory(), product.getValidityMinutes(), product.isActive(),
                product.getUpdatedAt() == null ? null : product.getUpdatedAt().toInstant());
    }
}
