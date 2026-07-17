package tj.metro.dushanbe.notification.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * Защита от возврата к HHH90003004 (NTF-01).
 *
 * <p>Ловушка не видна ни компилятору, ни обычному тесту с моками: fetch join
 * коллекции вместе с {@code Pageable} собирается, работает и даже отдаёт
 * правильные страницы — но LIMIT в SQL не уходит, Hibernate вычитывает всю
 * таблицу и режет страницу в памяти. То есть пагинация цела на вид и мертва по
 * сути, а замечают это на проде. Единственное надёжное место поймать такое —
 * форма самих запросов репозитория, поэтому проверка тут и стоит.
 */
class NotificationMessageRepositoryContractTest {

    private static final List<Method> QUERIES =
            Arrays.stream(NotificationMessageRepository.class.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Query.class))
                    .toList();

    /** Fetch join коллекции + пагинация = резка страницы в памяти. Совмещать нельзя. */
    @Test
    void noQueryCombinesACollectionFetchJoinWithPagination() {
        for (Method method : QUERIES) {
            if (!fetchesCollection(method)) {
                continue;
            }
            assertFalse(paged(method),
                    method.getName() + ": fetch join коллекции с Pageable/Page — это HHH90003004, "
                            + "страница режется в памяти. Нужна двухшаговая выборка: "
                            + "findIdPage(...) → findAllWithTargetsByIdIn(...)");
        }
    }

    /**
     * Шаг 1 обязан существовать и обязан быть страницей идентификаторов: именно
     * отсутствие коллекций в нём и делает LIMIT настоящим.
     */
    @Test
    void theIdPageStepSelectsIdsOnlyAndCarriesNoFetchJoin() throws Exception {
        for (String name : List.of("findIdPage", "findIdPageByStatus")) {
            Method step = QUERIES.stream()
                    .filter(method -> method.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Шаг 1 ленты пропал: " + name));

            assertTrue(paged(step), name + " обязан принимать Pageable и возвращать Page");
            assertFalse(fetchesCollection(step),
                    name + ": в шаге 1 не должно быть fetch join — иначе LIMIT снова уедет в память");
            assertTrue(query(step).contains("select m.id"),
                    name + " обязан выбирать только идентификаторы");
        }
    }

    /** Шаг 2 обязан задавать порядок сам: `in` его не сохраняет. */
    @Test
    void theFetchJoinStepRestatesTheOrderBecauseInDoesNotKeepIt() throws Exception {
        Method step = NotificationMessageRepository.class
                .getDeclaredMethod("findAllWithTargetsByIdIn", java.util.Collection.class);
        String jpql = query(step);

        assertTrue(jpql.contains("left join fetch"), "таргеты страницы обязаны приходить одним запросом");
        assertTrue(jpql.contains("order by"), "без order by страница придёт в произвольном порядке");
        assertTrue(jpql.contains("m.code"), "порядок обязан заканчиваться уникальным полем");
    }

    private static boolean fetchesCollection(Method method) {
        return query(method).contains("join fetch");
    }

    private static boolean paged(Method method) {
        return Arrays.stream(method.getParameterTypes()).anyMatch(Pageable.class::isAssignableFrom)
                || Page.class.isAssignableFrom(method.getReturnType());
    }

    private static String query(Method method) {
        Query annotation = method.getAnnotation(Query.class);
        String jpql = annotation.value().isEmpty() ? annotation.countQuery() : annotation.value();
        return jpql.toLowerCase(Locale.ROOT);
    }
}
