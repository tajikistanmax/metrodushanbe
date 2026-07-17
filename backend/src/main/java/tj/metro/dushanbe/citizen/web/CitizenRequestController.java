package tj.metro.dushanbe.citizen.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.citizen.service.CitizenRequestService;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestCreateRequest;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestCreateResponse;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestPublicDto;
import tj.metro.dushanbe.citizen.web.dto.CitizenRequestTrackingRequest;

/** Публичная подача и защищённое отслеживание обращений (REQ-01/03). */
@RestController
@RequestMapping("/v1/requests")
@Tag(name = "Citizen requests", description = "Обращения граждан и отслеживание статуса")
public class CitizenRequestController {

    private final CitizenRequestService service;

    public CitizenRequestController(CitizenRequestService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Подать обращение",
            description = "Возвращает публичный номер и одноразово показываемый секрет отслеживания.")
    public ResponseEntity<CitizenRequestCreateResponse> create(
            @Valid @RequestBody CitizenRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/track")
    @Operation(summary = "Проверить статус обращения",
            description = "Требует номер и секрет. Не возвращает контактные данные заявителя.")
    public CitizenRequestPublicDto track(
            @Valid @RequestBody CitizenRequestTrackingRequest request) {
        return service.track(request.code(), request.trackingToken());
    }
}
