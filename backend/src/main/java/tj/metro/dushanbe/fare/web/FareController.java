package tj.metro.dushanbe.fare.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.fare.service.FareService;
import tj.metro.dushanbe.fare.web.dto.FareProductDto;

/** Публичные утверждённые/активные тарифы и правила действия продуктов. */
@RestController
@RequestMapping("/v1/fares")
@Tag(name = "Fares", description = "Публичный справочник тарифов")
public class FareController {

    private final FareService service;

    public FareController(FareService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Список активных тарифных продуктов")
    public List<FareProductDto> list() {
        return service.activeFares();
    }
}
