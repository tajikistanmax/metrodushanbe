package tj.metro.dushanbe.citizen.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Публичная форма обращения (REQ-01/02). */
public record CitizenRequestCreateRequest(
        @NotBlank
        @Pattern(regexp = "complaint|suggestion|incident|question|lost_item")
        String type,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 10_000) String message,
        @Size(max = 160) String contactName,
        @Email @Size(max = 254) String contactEmail,
        @Pattern(regexp = "^[+0-9()\\-\\s]{0,40}$") String contactPhone,
        @Size(max = 64) String lineCode,
        @Size(max = 64) String stationCode,
        @AssertTrue boolean consent) {
}
