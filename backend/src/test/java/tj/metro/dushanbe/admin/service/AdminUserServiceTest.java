package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tj.metro.dushanbe.admin.web.dto.AdminLoginRequest;
import tj.metro.dushanbe.admin.web.dto.AdminUserCreateRequest;
import tj.metro.dushanbe.admin.web.dto.AdminUserUpdateRequest;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.BadRequestException;
import tj.metro.dushanbe.common.error.ForbiddenException;
import tj.metro.dushanbe.common.error.NotFoundException;
import tj.metro.dushanbe.common.error.UnauthorizedException;
import tj.metro.dushanbe.identity.domain.AdminRole;
import tj.metro.dushanbe.identity.domain.AdminUser;
import tj.metro.dushanbe.identity.repository.AdminUserRepository;

class AdminUserServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T10:15:30Z");
    private static final String PASSWORD = "correct-horse-battery";

    private final AdminUserRepository repository = mock(AdminUserRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    /** Настоящий BCrypt со стоимостью 4: проверяем реальное хеширование, но быстро. */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private final AdminUserService service = new AdminUserService(repository, passwordEncoder,
            auditService, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void authenticateAcceptsValidCredentialsAndRecordsLogin() {
        AdminUser user = user("operator1", AdminRole.OPERATOR, true);
        when(repository.findByUsername("operator1")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        var result = service.authenticate(new AdminLoginRequest("operator1", PASSWORD));

        assertEquals("operator1", result.username());
        assertEquals("operator", result.role());
        assertEquals(NOW.atOffset(ZoneOffset.UTC), user.getLastLoginAt());
        verify(auditService).record(eq("operator1"), eq("auth.login"), eq("admin_user"),
                eq("operator1"), isNull(), any());
    }

    @Test
    void authenticateNormalizesUsernameCase() {
        AdminUser user = user("operator1", AdminRole.OPERATOR, true);
        when(repository.findByUsername("operator1")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        var result = service.authenticate(new AdminLoginRequest("  OPERATOR1  ", PASSWORD));

        assertEquals("operator1", result.username());
    }

    @Test
    void authenticateRejectsWrongPassword() {
        AdminUser user = user("operator1", AdminRole.OPERATOR, true);
        when(repository.findByUsername("operator1")).thenReturn(Optional.of(user));

        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> service.authenticate(new AdminLoginRequest("operator1", "wrong-password")));

        assertEquals("auth.invalid_credentials", error.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void authenticateRejectsUnknownUserWithSameErrorAsWrongPassword() {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> service.authenticate(new AdminLoginRequest("ghost", PASSWORD)));

        // Тот же код, что и при неверном пароле — иначе логины перебираются по ответу.
        assertEquals("auth.invalid_credentials", error.getCode());
    }

    @Test
    void authenticateRejectsDeactivatedUser() {
        AdminUser user = user("retired", AdminRole.EDITOR, false);
        when(repository.findByUsername("retired")).thenReturn(Optional.of(user));

        UnauthorizedException error = assertThrows(UnauthorizedException.class,
                () -> service.authenticate(new AdminLoginRequest("retired", PASSWORD)));

        assertEquals("auth.invalid_credentials", error.getCode());
        // Независимая транзакция — иначе запись откатится вместе с 401.
        verify(auditService).recordIndependently(eq("retired"), eq("auth.login_failed"),
                eq("admin_user"), eq("retired"), isNull(),
                eq(java.util.Map.of("reason", "inactive")));
    }

    @Test
    void createHashesPasswordAndNeverStoresPlaintext() {
        when(repository.existsByUsername("editor2")).thenReturn(false);
        when(repository.save(any(AdminUser.class))).thenAnswer(i -> i.getArgument(0));
        var request = new AdminUserCreateRequest("Editor2", "Редактор", PASSWORD, "editor", true);

        var result = service.create(request, "admin");

        assertEquals("editor2", result.username());
        assertEquals("editor", result.role());
        verify(repository).save(any(AdminUser.class));
        verify(auditService).record(eq("admin"), eq("admin_user.create"), eq("admin_user"),
                eq("editor2"), isNull(), any());
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(repository.existsByUsername("taken")).thenReturn(true);
        var request = new AdminUserCreateRequest("taken", "Дубль", PASSWORD, "viewer", true);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.create(request, "admin"));

        assertEquals("admin_user.username_exists", error.getCode());
        verify(repository, never()).save(any());
    }

    @Test
    void updateWithoutPasswordKeepsExistingHash() {
        AdminUser user = user("editor2", AdminRole.EDITOR, true);
        String hashBefore = user.getPasswordHash();
        when(repository.findByUsername("editor2")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        service.update("editor2", new AdminUserUpdateRequest("Новое имя", null, "operator", true), "admin");

        assertEquals(hashBefore, user.getPasswordHash());
        assertEquals(AdminRole.OPERATOR, user.getRole());
    }

    @Test
    void updateWithPasswordReplacesHash() {
        AdminUser user = user("editor2", AdminRole.EDITOR, true);
        String hashBefore = user.getPasswordHash();
        when(repository.findByUsername("editor2")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        service.update("editor2",
                new AdminUserUpdateRequest("Редактор", "brand-new-password", "editor", true), "admin");

        assertFalse(hashBefore.equals(user.getPasswordHash()));
        assertNotNull(user.getPasswordHash());
    }

    @Test
    void updateRejectsDemotingLastActiveSuperadmin() {
        AdminUser user = user("root", AdminRole.SUPERADMIN, true);
        when(repository.findByUsername("root")).thenReturn(Optional.of(user));
        when(repository.countByRoleAndActiveIsTrue(AdminRole.SUPERADMIN)).thenReturn(1L);

        BadRequestException error = assertThrows(BadRequestException.class, () ->
                service.update("root", new AdminUserUpdateRequest("Root", null, "viewer", true), "root"));

        assertEquals("admin_user.last_superadmin", error.getCode());
        assertEquals(AdminRole.SUPERADMIN, user.getRole());
    }

    @Test
    void updateAllowsDemotingSuperadminWhenAnotherExists() {
        AdminUser user = user("root", AdminRole.SUPERADMIN, true);
        when(repository.findByUsername("root")).thenReturn(Optional.of(user));
        when(repository.countByRoleAndActiveIsTrue(AdminRole.SUPERADMIN)).thenReturn(2L);
        when(repository.save(user)).thenReturn(user);

        var result = service.update("root",
                new AdminUserUpdateRequest("Root", null, "editor", true), "other-root");

        assertEquals("editor", result.role());
    }

    @Test
    void deleteRejectsSelfDeletion() {
        AdminUser user = user("admin", AdminRole.SUPERADMIN, true);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(user));

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.delete("admin", "ADMIN"));

        assertEquals("admin_user.self_delete", error.getCode());
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteRejectsLastActiveSuperadmin() {
        AdminUser user = user("root", AdminRole.SUPERADMIN, true);
        when(repository.findByUsername("root")).thenReturn(Optional.of(user));
        when(repository.countByRoleAndActiveIsTrue(AdminRole.SUPERADMIN)).thenReturn(1L);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.delete("root", "someone-else"));

        assertEquals("admin_user.last_superadmin", error.getCode());
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteUnknownUserReturnsDomainNotFound() {
        when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class,
                () -> service.delete("ghost", "admin"));

        assertEquals("admin_user.not_found", error.getCode());
    }

    @Test
    void listReturnsUsersWithoutPasswordHash() {
        when(repository.findAllByOrderByRoleAscUsernameAsc())
                .thenReturn(List.of(user("root", AdminRole.SUPERADMIN, true)));

        var result = service.list();

        assertEquals(1, result.size());
        assertEquals("root", result.get(0).username());
        // AdminUserDto — record без поля хеша; проверяем его отсутствие в компонентах.
        boolean exposesHash = java.util.Arrays.stream(result.get(0).getClass().getRecordComponents())
                .anyMatch(c -> c.getName().toLowerCase(java.util.Locale.ROOT).contains("password"));
        assertFalse(exposesHash);
    }

    @Test
    void requireRoleRejectsInsufficientBackendRole() {
        AdminUser user = user("viewer", AdminRole.VIEWER, true);
        when(repository.findByUsername("viewer")).thenReturn(Optional.of(user));

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> service.requireRole("viewer", AdminRole.SUPERADMIN));

        assertEquals("admin.forbidden", error.getCode());
    }

    @Test
    void changingPasswordIncrementsSessionVersion() {
        AdminUser user = user("editor2", AdminRole.EDITOR, true);
        long before = user.getSessionVersion();
        when(repository.findByUsername("editor2")).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);

        var result = service.update("editor2",
                new AdminUserUpdateRequest("Editor", "another-valid-password", "editor", true),
                "root");

        assertEquals(before + 1, result.sessionVersion());
    }

    private AdminUser user(String username, AdminRole role, boolean active) {
        return new AdminUser(UUID.randomUUID(), username, "Оператор",
                passwordEncoder.encode(PASSWORD), role, active);
    }
}
