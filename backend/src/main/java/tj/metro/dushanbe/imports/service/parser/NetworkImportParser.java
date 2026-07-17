package tj.metro.dushanbe.imports.service.parser;

import java.util.List;
import tj.metro.dushanbe.imports.service.NetworkImportValidator.ParsedFeature;

/**
 * Разбор источника импорта сети конкретного формата (INT-04: GeoJSON/GTFS/CSV) в общий
 * набор {@link ParsedFeature}. Реализации отвечают только за «понять вход»; применение
 * фич (апсерт линий/станций, счётчики, аудит) — единое для всех форматов и живёт в
 * {@link tj.metro.dushanbe.imports.service.ImportService}, поэтому новый формат не
 * требует второго конвейера: достаточно нового бина-парсера.
 *
 * <p>Вход — сырые байты, а не строка: GTFS приходит ZIP-архивом. Текстовые форматы
 * (geojson/csv) декодируют байты в UTF-8 сами.
 *
 * <p>Реализации обязаны быть stateless и НЕ бросать исключений на битом входе:
 * любой мусор должен превращаться в {@link ParseResult#topLevelErrors()} либо в ошибки
 * конкретных фич (IMP-03) — оператор должен увидеть отчёт, а не 500.
 */
public interface NetworkImportParser {

    /** Код формата, который разбирает парсер (см. {@code ImportFormat}). */
    String format();

    /** Разбирает источник; исключения наружу не выпускает (см. контракт класса). */
    ParseResult parse(byte[] source, ImportOptions options);

    /**
     * Параметры запуска импорта, которых нет в самом источнике.
     *
     * @param language язык содержимого источника (tg|ru|en) — для одноязычных форматов
     *        (GTFS). null ⇒ парсер определяет язык сам (GTFS — по agency.txt:agency_lang).
     * @param status статус жизненного цикла для импортируемых линий/станций, если
     *        источник его не несёт (GTFS). Форматы со своей колонкой статуса (geojson,
     *        csv) параметр игнорируют.
     */
    record ImportOptions(String language, String status) {

        /** Статус по умолчанию: GTFS-фид описывает действующее движение. */
        public static final String DEFAULT_STATUS = "active";

        public static ImportOptions defaults() {
            return new ImportOptions(null, DEFAULT_STATUS);
        }

        /** Нормализует пустые значения к дефолтам, чтобы парсеры не проверяли это каждый раз. */
        public ImportOptions {
            language = (language == null || language.isBlank()) ? null : language.trim();
            status = (status == null || status.isBlank()) ? DEFAULT_STATUS : status.trim();
        }
    }

    /**
     * Итог разбора: либо набор фич, либо ошибки верхнего уровня (вход нечитаем как
     * целое — не JSON, не ZIP, нет обязательного файла/колонки). Ошибки верхнего уровня
     * означают, что применять нечего: джоб сразу failed.
     */
    record ParseResult(List<ParsedFeature> features, List<String> topLevelErrors) {

        public ParseResult {
            features = features == null ? List.of() : List.copyOf(features);
            topLevelErrors = topLevelErrors == null ? List.of() : List.copyOf(topLevelErrors);
        }

        public static ParseResult of(List<ParsedFeature> features) {
            return new ParseResult(features, List.of());
        }

        /** Вход отвергнут целиком: ни одна фича не будет применена. */
        public static ParseResult reject(String... messages) {
            return new ParseResult(List.of(), List.of(messages));
        }

        public static ParseResult reject(List<String> messages) {
            return new ParseResult(List.of(), messages);
        }

        public boolean rejected() {
            return !topLevelErrors.isEmpty();
        }
    }
}
