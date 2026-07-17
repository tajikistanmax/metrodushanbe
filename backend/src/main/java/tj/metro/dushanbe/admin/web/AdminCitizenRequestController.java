package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.citizen.service.CitizenRequestService;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestAdminDto;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestUpdateRequest;

/** Операторская очередь обращений и управление workflow (REQ-04). */
@RestController
@RequestMapping("/v1/admin/requests")
@Tag(name = "Admin: Citizen requests", description = "Очередь и статусы обращений граждан")
public class AdminCitizenRequestController {

    private final CitizenRequestService service;

    public AdminCitizenRequestController(CitizenRequestService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Список обращений", description = "Новые сверху; status — опциональный фильтр.")
    public List<CitizenRequestAdminDto> list(
            @RequestParam(name = "status", required = false) String status) {
        return service.list(status);
    }

    @PutMapping("/{code}")
    @Operation(summary = "Изменить статус, ответ и исполнителя обращения")
    public CitizenRequestAdminDto update(
            @PathVariable("code") String code,
            @Valid @RequestBody CitizenRequestUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return service.update(code, request, actor);
    }
}
