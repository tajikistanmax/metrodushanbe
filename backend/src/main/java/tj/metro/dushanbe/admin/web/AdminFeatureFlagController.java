package tj.metro.dushanbe.admin.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tj.metro.dushanbe.admin.web.dto.FeatureFlagToggleRequest;
import tj.metro.dushanbe.featureflag.domain.FeatureFlag;
import tj.metro.dushanbe.featureflag.service.FeatureFlagService;

@RestController
@RequestMapping("/v1/admin/feature-flags")
@Tag(name = "Admin: Feature Flags", description = "Управление feature flags (ADM-07)")
public class AdminFeatureFlagController {

    private final FeatureFlagService featureFlagService;

    public AdminFeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    @Operation(summary = "Список всех feature flags")
    public List<FeatureFlag> list() {
        return featureFlagService.findAll();
    }

    @PutMapping("/{flagKey}")
    @Operation(summary = "Переключить feature flag",
            description = "Меняет значение enabled для указанного флага.")
    public void toggle(
            @PathVariable("flagKey") String flagKey,
            @RequestBody FeatureFlagToggleRequest request,
            @RequestHeader(name = "X-Admin-Actor", defaultValue = "dev-admin") String actor) {
        featureFlagService.setEnabled(flagKey, request.enabled(), actor);
    }
}
