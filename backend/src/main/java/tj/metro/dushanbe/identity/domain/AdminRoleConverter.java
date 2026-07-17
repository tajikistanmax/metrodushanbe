package tj.metro.dushanbe.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Хранит роль в БД её кодом в нижнем регистре.
 *
 * <p>{@code @Enumerated(STRING)} записал бы {@code SUPERADMIN} и нарушил
 * {@code chk_admin_user_role}, который требует нижний регистр — тот же формат,
 * что и REST. Конвертер держит эти три представления согласованными.
 */
@Converter(autoApply = false)
public class AdminRoleConverter implements AttributeConverter<AdminRole, String> {

    @Override
    public String convertToDatabaseColumn(AdminRole attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public AdminRole convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return AdminRole.fromCode(dbData).orElseThrow(() ->
                new IllegalStateException("Неизвестная роль в admin_user.role: " + dbData));
    }
}
