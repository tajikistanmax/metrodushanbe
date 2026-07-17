package tj.metro.dushanbe.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationMessageTest {

    private static final Map<String, String> I18N =
            Map.of("tg", "Салом", "ru", "Привет", "en", "Hello");
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-07-17T10:00:00Z");

    @Test
    void newMessageStartsAsDraftWithoutSentAt() {
        var message = message(null);

        assertEquals(NotificationStatus.DRAFT, message.getStatus());
        assertNull(message.getSentAt());
        assertTrue(message.networkWide());
    }

    @Test
    void moveToSentStampsSentAt() {
        var message = message(null);
        message.moveTo(NotificationStatus.SENDING, AT);

        message.moveTo(NotificationStatus.SENT, AT);

        // Инвариант chk_notification_message_sent: sent обязан нести факт отправки.
        assertEquals(NotificationStatus.SENT, message.getStatus());
        assertEquals(AT, message.getSentAt());
    }

    @Test
    void moveToScheduledWithoutScheduledAtIsRejected() {
        var message = message(null);

        // Инвариант chk_notification_message_scheduled ловится здесь, а не в БД.
        assertThrows(IllegalStateException.class,
                () -> message.moveTo(NotificationStatus.SCHEDULED, AT));
        assertEquals(NotificationStatus.DRAFT, message.getStatus());
    }

    @Test
    void moveToScheduledKeepsItsTime() {
        var scheduledAt = AT.plusHours(3);
        var message = message(scheduledAt);

        message.moveTo(NotificationStatus.SCHEDULED, AT);

        assertEquals(NotificationStatus.SCHEDULED, message.getStatus());
        assertEquals(scheduledAt, message.getScheduledAt());
        assertNull(message.getSentAt());
    }

    @Test
    void cancellingDoesNotFakeSentAt() {
        var message = message(null);

        message.moveTo(NotificationStatus.CANCELLED, AT);

        assertEquals(NotificationStatus.CANCELLED, message.getStatus());
        assertNull(message.getSentAt());
    }

    @Test
    void addTargetLinksBackToMessage() {
        var message = message(null);

        var target = message.addTarget(TargetType.LINE, "L1");

        assertEquals(1, message.getTargets().size());
        assertFalse(message.networkWide());
        // Без обратной ссылки message_id ушёл бы в БД как NULL.
        assertEquals(message, target.getMessage());
        assertEquals(TargetType.LINE, target.getTargetType());
        assertTrue(target.matches(TargetType.LINE, "L1"));
        assertFalse(target.matches(TargetType.STATION, "L1"));
    }

    @Test
    void replaceTargetsClearsPreviousAddressing() {
        var message = message(null);
        message.addTarget(TargetType.STATION, "ST-1");

        message.replaceTargets(List.of());

        assertTrue(message.getTargets().isEmpty());
        assertTrue(message.networkWide());
    }

    @Test
    void hasChannelReflectsJsonbArray() {
        var message = message(null);

        assertTrue(message.hasChannel(NotificationChannel.IN_APP));
        assertTrue(message.hasChannel(NotificationChannel.EMAIL));
        assertFalse(message.hasChannel(NotificationChannel.SMS));
    }

    @Test
    void constructorDefensivelyCopiesChannels() {
        var channels = new ArrayList<>(List.of("in_app"));
        var message = new NotificationMessage(UUID.randomUUID(), "NTF-1", null, null,
                NotificationType.INFO, I18N, I18N, channels, null, "operator1");

        channels.add("sms");

        assertEquals(List.of("in_app"), message.getChannels());
    }

    @Test
    void updateReplacesContentButNotStatus() {
        var message = message(null);
        var newI18n = Map.of("tg", "Нав", "ru", "Новое", "en", "New");

        message.update(NotificationType.PROMO, newI18n, newI18n, List.of("push"), AT);

        assertEquals(NotificationType.PROMO, message.getType());
        assertEquals(newI18n, message.getTitleI18n());
        assertEquals(List.of("push"), message.getChannels());
        assertEquals(AT, message.getScheduledAt());
        assertEquals(NotificationStatus.DRAFT, message.getStatus());
    }

    @Test
    void templateOriginIsKeptOnlyAsATrace() {
        var message = new NotificationMessage(UUID.randomUUID(), "NTF-2", "TPL-DELAY", "ALERT-9",
                NotificationType.INCIDENT, I18N, I18N, List.of("in_app"), null, "operator1");

        // Тексты — копия: от шаблона сообщение больше не зависит (шапка V022).
        assertEquals("TPL-DELAY", message.getTemplateCode());
        assertEquals("ALERT-9", message.getAlertCode());
        assertEquals(I18N, message.getTitleI18n());
    }

    @Test
    void onCreateSetsTimestampsAndDoesNotOverrideCreatedAt() {
        var message = message(null);
        assertNull(message.getCreatedAt());

        message.onCreate();
        var createdAt = message.getCreatedAt();
        message.onCreate();

        assertNotNull(createdAt);
        assertEquals(createdAt, message.getCreatedAt());
        assertNotNull(message.getUpdatedAt());
    }

    @Test
    void onUpdateSetsUpdatedAt() {
        var message = message(null);
        message.onCreate();
        var before = message.getUpdatedAt();

        message.onUpdate();

        assertTrue(message.getUpdatedAt().isAfter(before) || message.getUpdatedAt().isEqual(before));
    }

    private static NotificationMessage message(OffsetDateTime scheduledAt) {
        return new NotificationMessage(UUID.randomUUID(), "NTF-2026-001", null, null,
                NotificationType.INFO, I18N, I18N, List.of("in_app", "email"),
                scheduledAt, "operator1");
    }
}
