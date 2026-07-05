package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.service.AdminNewsService;
import tj.metro.dushanbe.admin.web.dto.NewsCreateRequest;
import tj.metro.dushanbe.admin.web.dto.NewsUpdateRequest;
import tj.metro.dushanbe.content.web.dto.NewsArticleDto;

/**
 * Admin-write эндпоинты новостей (ADM-03/CMS). Пути: /api/v1/admin/news...
 * Защищены dev-фильтром X-Admin-Key. Создание — в статусе draft; публикация —
 * отдельным вызовом с гейтом полноты языков BR-CMS-1.
 */
@RestController
@RequestMapping("/v1/admin/news")
@Tag(name = "Admin: News", description = "Управление новостями (draft → published)")
public class AdminNewsController {

    private final AdminNewsService adminNewsService;

    public AdminNewsController(AdminNewsService adminNewsService) {
        this.adminNewsService = adminNewsService;
    }

    @PostMapping
    @Operation(summary = "Создать новость (draft)",
            description = "Поля: slug, title{tg,ru,en}, body{tg,ru,en}, coverMediaUrl?.")
    public ResponseEntity<NewsArticleDto> create(
            @Valid @RequestBody NewsCreateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminNewsService.create(request, actor));
    }

    @PutMapping("/{slug}")
    @Operation(summary = "Обновить новость", description = "Слаг неизменен. 404 news.not_found.")
    public NewsArticleDto update(
            @PathVariable("slug") String slug,
            @Valid @RequestBody NewsUpdateRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return adminNewsService.update(slug, request, actor);
    }

    @PostMapping("/{slug}/publish")
    @Operation(summary = "Опубликовать новость",
            description = "Гейт полноты языков BR-CMS-1: без tg/ru/en в title/body — 400. 404 news.not_found.")
    public NewsArticleDto publish(
            @PathVariable("slug") String slug,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        return adminNewsService.publish(slug, actor);
    }
}
