package tj.metro.dushanbe.citizen.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Номер обращения и секрет, выданный только один раз при создании. */
public record CitizenRequestTrackingRequest(
        @NotBlank @Size(max = 24) String code,
        @NotBlank @Size(max = 128) String trackingToken) {
}
