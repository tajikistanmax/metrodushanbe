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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.web.dto.CalendarExceptionCreateRequest;
import tj.metro.dushanbe.admin.web.dto.CalendarExceptionUpdateRequest;
import tj.metro.dushanbe.schedule.domain.CalendarException;
import tj.metro.dushanbe.schedule.repository.CalendarExceptionRepository;
import tj.metro.dushanbe.schedule.web.dto.CalendarExceptionDto;
import tj.metro.dushanbe.common.error.NotFoundException;

@RestController
@RequestMapping("/v1/admin/calendar-exceptions")
@Tag(name = "Admin: Calendar Exceptions", description = "Manage calendar exceptions for schedule day-type overrides")
public class AdminCalendarExceptionController {

    private final CalendarExceptionRepository repository;

    public AdminCalendarExceptionController(CalendarExceptionRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "List all calendar exceptions")
    public List<CalendarExceptionDto> list() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @PostMapping
    @Operation(summary = "Create a calendar exception")
    public ResponseEntity<CalendarExceptionDto> create(@Valid @RequestBody CalendarExceptionCreateRequest request) {
        CalendarException entity = new CalendarException(
                request.exceptionDate(),
                request.dayType(),
                request.descriptionTg(),
                request.descriptionRu(),
                request.descriptionEn(),
                request.isRecurring());
        CalendarException saved = repository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a calendar exception")
    public CalendarExceptionDto update(@PathVariable Long id,
                                       @Valid @RequestBody CalendarExceptionUpdateRequest request) {
        CalendarException entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("calendar_exception.not_found",
                        "Calendar exception with id " + id + " not found"));
        entity.setExceptionDate(request.exceptionDate());
        entity.setDayType(request.dayType());
        entity.setDescriptionTg(request.descriptionTg());
        entity.setDescriptionRu(request.descriptionRu());
        entity.setDescriptionEn(request.descriptionEn());
        entity.setRecurring(request.isRecurring());
        return toDto(repository.save(entity));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a calendar exception")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("calendar_exception.not_found",
                    "Calendar exception with id " + id + " not found");
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private CalendarExceptionDto toDto(CalendarException e) {
        return new CalendarExceptionDto(
                e.getId(),
                e.getExceptionDate(),
                e.getDayType(),
                e.getDescriptionTg(),
                e.getDescriptionRu(),
                e.getDescriptionEn(),
                e.isRecurring(),
                e.getCreatedAt());
    }
}
