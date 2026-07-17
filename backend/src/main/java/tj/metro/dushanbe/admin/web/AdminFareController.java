package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.service.AdminFareService;
import tj.metro.dushanbe.admin.web.dto.FareCreateRequest;
import tj.metro.dushanbe.admin.web.dto.FareUpdateRequest;
import tj.metro.dushanbe.fare.web.dto.FareProductDto;

/** Операторское управление тарифным справочником. */
@RestController
@RequestMapping("/v1/admin/fares")
@Tag(name = "Admin: Fares", description = "CRUD тарифных продуктов")
public class AdminFareController {

    private final AdminFareService service;

    public AdminFareController(AdminFareService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Все тарифы, включая неактивные")
    public List<FareProductDto> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<FareProductDto> create(
            @Valid @RequestBody FareCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, actor));
    }

    @PutMapping("/{code}")
    public FareProductDto update(
            @PathVariable("code") String code,
            @Valid @RequestBody FareUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.update(code, request, actor);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(
            @PathVariable("code") String code,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        service.delete(code, actor);
        return ResponseEntity.noContent().build();
    }
}
