package tj.metro.dushanbe.identity.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Роль оператора консоли (RBAC, ТЗ §9.2).
 *
 * <p>Роли упорядочены по объёму прав: каждая следующая включает предыдущую.
 * Проверка доступа выполняется через {@link #includes(AdminRole)} — сравнение
 * по {@link #rank}, а не перечислением ролей в каждой точке вызова.
 */
public enum AdminRole {

    /** Только чтение: карточки, аналитика, аудит. */
    VIEWER(0),

    /** Операционный контур: обращения, инциденты, сервисные уведомления. */
    OPERATOR(1),

    /** Контент и справочники: новости, тарифы, станции, линии. */
    EDITOR(2),

    /** Полный доступ, включая управление пользователями. */
    SUPERADMIN(3);

    private final int rank;

    AdminRole(int rank) {
        this.rank = rank;
    }

    /** Значение для БД и REST — нижний регистр (совпадает с chk_admin_user_role). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Истинно, если роль покрывает права требуемой роли. */
    public boolean includes(AdminRole required) {
        return rank >= required.rank;
    }

    /** Разбор кода из БД/REST; {@link Optional#empty()} для неизвестного значения. */
    public static Optional<AdminRole> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(role -> role.code().equals(code.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    /** Все коды ролей — для сообщений валидации и справочника UI. */
    public static List<String> codes() {
        return Arrays.stream(values()).map(AdminRole::code).toList();
    }
}
