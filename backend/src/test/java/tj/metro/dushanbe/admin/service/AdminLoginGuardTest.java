package tj.metro.dushanbe.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.admin.security.AdminAuthProperties;
import tj.metro.dushanbe.audit.service.AuditService;
import tj.metro.dushanbe.common.error.TooManyRequestsException;
import tj.metro.dushanbe.identity.domain.LoginAttempt;
import tj.metro.dushanbe.identity.domain.LoginAttemptScope;
import tj.metro.dushanbe.identity.repository.LoginAttemptRepository;

/** Блокировка входа (аудит-пункт 9): порог, аудит блокировки, сброс, выключение. */
class AdminLoginGuardTest {

    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
    private static final OffsetDateTime NOW_ODT = NOW.atOffset(ZoneOffset.UTC);
    private static final String IP = "203.0.113.9";

    private final LoginAttemptRepository repository = mock(LoginAttemptRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final AdminAuthProperties properties = properties();
    private final AdminLoginGuard guard = new AdminLoginGuard(repository, auditService, properties,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private AdminAuthProperties properties() {
        AdminAuthProperties props = new AdminAuthProperties();
        props.getLockout().setEnabled(true);
        props.getLockout().setAccountMaxFailures(1);
        props.getLockout().setIpUserMaxFailures(1);
        props.getLockout().setWindow(Duration.ofMinutes(15));
        props.getLockout().setLockDuration(Duration.ofMinutes(15));
        return props;
    }

    @Test
    void ensureNotLockedThrowsWhenAccountLocked() {
        LoginAttempt locked = new LoginAttempt(UUID.randomUUID(), LoginAttemptScope.ACCOUNT, "operator1");
        locked.registerFailure(NOW_ODT, 1, Duration.ofMinutes(15), Duration.ofMinutes(15));
        when(repository.findByScopeAndSubject(LoginAttemptScope.ACCOUNT, "operator1"))
                .thenReturn(Optional.of(locked));
        when(repository.findByScopeAndSubject(eq(LoginAttemptScope.IP_USER), any()))
                .thenReturn(Optional.empty());

        TooManyRequestsException error = assertThrows(TooManyRequestsException.class,
                () -> guard.ensureNotLocked("operator1", IP));

        assertEquals("auth.too_many_attempts", error.getCode());
    }

    @Test
    void ensureNotLockedPassesWhenNoHistory() {
        when(repository.findByScopeAndSubject(any(), any())).thenReturn(Optional.empty());

        guard.ensureNotLocked("operator1", IP);
    }

    @Test
    void registerFailureLocksAndWritesAudit() {
        when(repository.findForUpdate(eq(LoginAttemptScope.ACCOUNT), eq("operator1")))
                .thenReturn(Optional.of(new LoginAttempt(UUID.randomUUID(),
                        LoginAttemptScope.ACCOUNT, "operator1")));
        when(repository.findForUpdate(eq(LoginAttemptScope.IP_USER), any()))
                .thenReturn(Optional.of(new LoginAttempt(UUID.randomUUID(),
                        LoginAttemptScope.IP_USER, LoginAttempt.ipUserSubject(IP, "operator1"))));

        guard.registerFailure("operator1", IP);

        // Порог = 1, поэтому обе области (account и ip_user) блокируются одной
        // неудачей → ровно два события auth.account_locked.
        verify(auditService, times(2)).recordIndependently(eq("operator1"), eq("auth.account_locked"),
                eq("admin_user"), eq("operator1"), isNull(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void registerSuccessResetsBothCounters() {
        LoginAttempt account = new LoginAttempt(UUID.randomUUID(), LoginAttemptScope.ACCOUNT, "operator1");
        account.registerFailure(NOW_ODT, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttempt ipUser = new LoginAttempt(UUID.randomUUID(), LoginAttemptScope.IP_USER,
                LoginAttempt.ipUserSubject(IP, "operator1"));
        ipUser.registerFailure(NOW_ODT, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        when(repository.findForUpdate(eq(LoginAttemptScope.ACCOUNT), eq("operator1")))
                .thenReturn(Optional.of(account));
        when(repository.findForUpdate(eq(LoginAttemptScope.IP_USER), any()))
                .thenReturn(Optional.of(ipUser));

        guard.registerSuccess("operator1", IP);

        assertEquals(0, account.getFailureCount());
        assertEquals(0, ipUser.getFailureCount());
        verify(repository).save(account);
        verify(repository).save(ipUser);
    }

    @Test
    void disabledLockoutIsNoOp() {
        properties.getLockout().setEnabled(false);

        guard.ensureNotLocked("operator1", IP);
        guard.registerFailure("operator1", IP);
        guard.registerSuccess("operator1", IP);

        verifyNoInteractions(repository);
        verifyNoInteractions(auditService);
    }
}
