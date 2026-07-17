package tj.metro.dushanbe.audit.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tj.metro.dushanbe.audit.domain.AuditEvent;
import tj.metro.dushanbe.audit.repository.AuditEventRepository;

/**
 * Сервис аудита действий (ТЗ §6.2.10, BR-ADM-1). Единая точка записи событий
 * изменения критичных сущностей: актор, время, снимки до/после.
 *
 * <p>Журнал append-only — сервис только создаёт события ({@link #record}); методов
 * изменения/удаления нет. {@link #record} выполняется без своей транзакции
 * ({@code Propagation.REQUIRED}) и присоединяется к транзакции вызывающей
 * write-операции — так изменение сущности и запись аудита фиксируются атомарно
 * (либо оба, либо ни одного). «Сейчас» берётся из инжектируемого {@link Clock}
 * — для детерминированной тестируемости.
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Фиксирует событие аудита.
     *
     * @param actor      субъект действия (в dev — заголовок {@code X-Admin-Actor})
     * @param action     машиночитаемый код действия, например {@code line.create}
     * @param entityType тип сущности (line|station|station_exit|accessibility_feature|alert|news)
     * @param entityId   стабильный код/слаг сущности
     * @param before     снимок состояния до операции (null для создания)
     * @param after      снимок состояния после операции (null, если сущность удалена без снимка)
     * @return сохранённое событие аудита
     */
    @Transactional
    public AuditEvent record(String actor, String action, String entityType, String entityId,
                             Map<String, Object> before, Map<String, Object> after) {
        return persist(actor, action, entityType, entityId, before, after);
    }

    /**
     * Фиксирует событие в СОБСТВЕННОЙ транзакции ({@code REQUIRES_NEW}).
     *
     * <p>Нужен там, где вызывающий метод завершается исключением, но событие обязано
     * сохраниться: например, неудачная попытка входа. При обычном {@link #record}
     * такая запись присоединилась бы к транзакции вызывающего и откатилась вместе
     * с ней — то есть журнал терял бы ровно те события, ради которых ведётся.
     *
     * <p>Для успешных операций используйте {@link #record}: там атомарность с
     * изменением сущности важнее.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent recordIndependently(String actor, String action, String entityType,
                                          String entityId, Map<String, Object> before,
                                          Map<String, Object> after) {
        return persist(actor, action, entityType, entityId, before, after);
    }

    private AuditEvent persist(String actor, String action, String entityType, String entityId,
                               Map<String, Object> before, Map<String, Object> after) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(), actor, action, entityType, entityId,
                before, after, OffsetDateTime.now(clock));
        return repository.save(event);
    }
}
