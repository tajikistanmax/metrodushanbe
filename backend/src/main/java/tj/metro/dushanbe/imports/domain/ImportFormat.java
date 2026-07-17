package tj.metro.dushanbe.imports.domain;

import java.util.List;

/**
 * Обменный формат источника импорта (INT-04: «импорт GeoJSON/GTFS/CSV»). Значения
 * совпадают с CHECK-констрейнтом {@code ck_import_job_format} (V025) и попадают в
 * колонку {@code import_job.format}.
 *
 * <p>Формат отделён от {@link ImportJob#getType()} намеренно: {@code type} отвечает на
 * вопрос «что импортировали» (сеть, тарифы), {@code format} — «в каком виде пришёл
 * источник». Это даёт оператору фильтровать ленту джобов по формату, не разбирая
 * составные строки вида {@code network_geojson}.
 *
 * <p>Не enum: остальные строковые атрибуты {@link ImportJob} ({@code type},
 * {@code status}) в этом модуле тоже живут как строковые константы — держим один тон.
 */
public final class ImportFormat {

    /** GeoJSON FeatureCollection (образец — {@code data/demo-network.geojson}). */
    public static final String GEOJSON = "geojson";

    /** GTFS-фид: ZIP с CSV-файлами (agency/routes/stops/trips/stop_times/shapes/transfers). */
    public static final String GTFS = "gtfs";

    /** Плоская таблица станций/линий: заголовок + строки. */
    public static final String CSV = "csv";

    private static final List<String> CODES = List.of(GEOJSON, GTFS, CSV);

    private ImportFormat() {
    }

    /** Поддерживаемые форматы (совпадают с CHECK в V025). */
    public static List<String> codes() {
        return CODES;
    }

    public static boolean isSupported(String format) {
        return CODES.contains(format);
    }
}
