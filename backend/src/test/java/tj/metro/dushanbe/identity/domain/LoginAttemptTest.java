package tj.metro.dushanbe.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Доменная логика счётчика неудач и блокировки (аудит-пункт 9). */
class LoginAttemptTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 7, 20, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK = Duration.ofMinutes(15);

    private LoginAttempt attempt() {
        return new LoginAttempt(UUID.randomUUID(), LoginAttemptScope.ACCOUNT, "operator1");
    }

    @Test
    void locksExactlyAtThreshold() {
        LoginAttempt attempt = attempt();

        assertFalse(attempt.registerFailure(T0, 3, WINDOW, LOCK));
        assertFalse(attempt.registerFailure(T0.plusSeconds(1), 3, WINDOW, LOCK));
        boolean locked = attempt.registerFailure(T0.plusSeconds(2), 3, WINDOW, LOCK);

        assertTrue(locked);
        assertTrue(attempt.isLocked(T0.plusSeconds(2)));
    }

    @Test
    void lockExpiresAfterLockDuration() {
        LoginAttempt attempt = attempt();
        attempt.registerFailure(T0, 1, WINDOW, LOCK);

        assertTrue(attempt.isLocked(T0.plusMinutes(14)));
        assertFalse(attempt.isLocked(T0.plusMinutes(16)));
    }

    @Test
    void retryAfterRoundsUpAndIsZeroWhenUnlocked() {
        LoginAttempt attempt = attempt();
        attempt.registerFailure(T0, 1, WINDOW, LOCK);

        assertEquals(LOCK.toSeconds(), attempt.retryAfterSeconds(T0));
        assertEquals(0L, attempt.retryAfterSeconds(T0.plusMinutes(20)));
    }

    @Test
    void staleWindowRestartsTheCount() {
        LoginAttempt attempt = attempt();
        attempt.registerFailure(T0, 3, WINDOW, LOCK);
        attempt.registerFailure(T0.plusMinutes(1), 3, WINDOW, LOCK);

        // Третья неудача уже за окном первой — серия считается прерванной, счёт с нуля,
        // поэтому блокировки нет (это лишь «первая» неудача новой серии).
        boolean locked = attempt.registerFailure(T0.plusMinutes(20), 3, WINDOW, LOCK);

        assertFalse(locked);
        assertEquals(1, attempt.getFailureCount());
    }

    @Test
    void thresholdLockResetsCounterSoOperatorGetsFullBudgetAfterUnlock() {
        LoginAttempt attempt = attempt();
        attempt.registerFailure(T0, 2, WINDOW, LOCK);
        attempt.registerFailure(T0.plusSeconds(1), 2, WINDOW, LOCK); // блокировка

        // После включения блокировки счётчик обнулён — не блокируемся каждой следующей.
        assertEquals(0, attempt.getFailureCount());
    }

    @Test
    void successResetClearsEverything() {
        LoginAttempt attempt = attempt();
        attempt.registerFailure(T0, 5, WINDOW, LOCK);
        attempt.reset();

        assertEquals(0, attempt.getFailureCount());
        assertFalse(attempt.isLocked(T0));
    }

    @Test
    void ipUserSubjectIsStableAndOrdered() {
        assertEquals("203.0.113.1|operator1",
                LoginAttempt.ipUserSubject("203.0.113.1", "operator1"));
        assertEquals("unknown|operator1",
                LoginAttempt.ipUserSubject(null, "operator1"));
    }
}
