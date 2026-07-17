package tj.metro.dushanbe.imports.service.parser;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tj.metro.dushanbe.fare.service.FareService;

/**
 * Парсер тарифных продуктов из GTFS Fares v2 (INT-04, FAR-01/02). Читает тот же
 * ZIP-контейнер, что и {@link GtfsImportParser} ({@link GtfsArchive} + {@link CsvTable}),
 * но разбирает {@code fare_products.txt}/{@code rider_categories.txt}/{@code fare_media.txt}
 * и отдаёт продукты, а не фичи сети.
 *
 * <h2>Почему отдельный парсер, а не режим GtfsImportParser</h2>
 * Общего у них — только контейнер. Приёмник другой ({@code fare_product} вместо
 * {@code metro_line}/{@code metro_station}), путь записи другой ({@code AdminFareService}),
 * вид джоба другой ({@code fare_gtfs}), и в одном фиде тарифы могут быть без сети и
 * наоборот. Слить их в один класс значило бы получить {@code if (mode)} на каждом шаге и
 * ParsedFeature с половиной пустых полей. Дублирования при этом нет: чтение ZIP вынесено
 * в {@link GtfsArchive}, CSV — общий {@link CsvTable}.
 *
 * <h2>Отображение GTFS Fares v2 → {@code fare_product}</h2>
 * <table border="1">
 *   <caption>Что во что превращается</caption>
 *   <tr><th>источник</th><th>приёмник</th><th>правило</th></tr>
 *   <tr><td>{@code fare_products.fare_product_id}</td><td>{@code code}</td>
 *       <td>нормализуется под формат кодов справочника ({@code ^[A-Z0-9][A-Z0-9_-]{1,63}$}):
 *           верхний регистр, недопустимые символы → {@code -}. Любое изменение строки —
 *           предупреждение с исходным id; коллизия после нормализации — ошибка.</td></tr>
 *   <tr><td>{@code fare_products.fare_product_name}</td><td>{@code name_i18n}</td>
 *       <td>одноязычная строка → полный i18n-объект (см. ниже). Нет имени — ошибка:
 *           {@code name_i18n} обязателен и выдумывать его неоткуда.</td></tr>
 *   <tr><td>{@code fare_products.amount} + {@code currency}</td><td>{@code amount}/{@code currency}</td>
 *       <td>1:1. Оба обязательны и обязаны быть валидны (сумма ≥ 0, валюта — ISO-4217
 *           из трёх букв); нарушение — ошибка строки, а не «поправим по умолчанию»:
 *           это цена билета.</td></tr>
 *   <tr><td>{@code fare_products.rider_category_id} + {@code rider_categories.txt}</td>
 *       <td>{@code rider_category}</td><td>см. раздел «Категории пассажиров».</td></tr>
 * </table>
 *
 * <h2>Категории пассажиров</h2>
 * Модель допускает ровно {@link FareService#RIDER_CATEGORIES} (chk_fare_category, V018),
 * а {@code rider_category_id} в фиде — произвольная строка ({@code RC_ADULT}, {@code kids},
 * …). Правила разрешения, по убыванию приоритета:
 * <ol>
 *   <li>явное соответствие из параметра импорта {@code riderCategories}
 *       ({@code RC_ADULT:adult,RC_KID:child}) — оператор говорит, что во что ложится;</li>
 *   <li>сам {@code rider_category_id} без учёта регистра совпал с допустимой категорией
 *       ({@code adult} → {@code adult}) — это не догадка, а буквальное равенство;</li>
 *   <li>категория у продукта не указана вовсе → {@code all} (продукт для всех) +
 *       предупреждение;</li>
 *   <li>иначе — <b>ошибка</b> строки с номером строки объявления категории и подсказкой,
 *       как задать соответствие. Тихо привести «RC_KID» к «child» по созвучию нельзя:
 *       ошибка в категории — это льгота не тому пассажиру.</li>
 * </ol>
 * Ссылка на {@code rider_category_id}, которого нет в {@code rider_categories.txt}, —
 * тоже ошибка (битый фид), а {@code is_default_fare_category} игнорируется: «категория
 * по умолчанию» в фиде и {@code all} в модели — разные утверждения (в большинстве фидов
 * default — это «взрослый», а не «любой»).
 *
 * <h2>Срок действия ({@code validity_minutes}) — не импортируется</h2>
 * В GTFS Fares v2 такого поля <b>нет</b>: спецификация выражает время через
 * {@code timeframes.txt} (окна времени суток, когда тариф применим — это не срок годности
 * билета) и {@code fare_transfer_rules.duration_limit} (окно пересадки между двумя
 * поездками — это тоже не срок годности). Вывести одно из другого нельзя, а поставить
 * «дефолт» — значит назначить билету срок, которого никто не утверждал.
 * Решение: {@code validity_minutes} импортом <b>не задаётся вовсе</b>. Колонка
 * nullable (V018), поэтому новый продукт создаётся без срока, а у существующего
 * прежнее значение сохраняется (см. {@code FareImportService}). На каждый продукт
 * заводится предупреждение — оператор получает точный список «чему проставить срок».
 *
 * <h2>Носитель ({@code fare_media.txt}) — не импортируется</h2>
 * Модель MVP не различает носитель (карта/QR/наличные): у {@code fare_product} нет такого
 * поля, и заводить его импортом — это менять предметную модель под формат, а не наоборот.
 * Файл читается только ради читаемых имён в отчёте: если продукт ссылается на
 * {@code fare_media_id}, цена импортируется, а привязка к носителю попадает в
 * предупреждение. Побочный эффект: ключ {@code fare_products.txt} — тройка
 * (id, rider_category_id, fare_media_id), поэтому один {@code fare_product_id} законно
 * встречается несколько раз; в модели это один продукт на код, так что вторая и
 * последующие строки с тем же id — ошибка с указанием строки-дубликата.
 *
 * <h2>Описание и одноязычность</h2>
 * {@code description_i18n} обязателен, а колонки описания в GTFS нет вовсе: временно
 * дублируется название + предупреждение. Язык фида и заполнение недостающих tg/ru/en —
 * ровно как в {@link GtfsImportParser} (параметр {@code lang}, иначе
 * {@code agency.txt:agency_lang}; недостающие языки получают строку языка фида и
 * предупреждение на каждый). Молчаливое дублирование недопустимо: оно неотличимо от
 * настоящего перевода.
 */
@Component
public class FaresGtfsImportParser {

    private static final String AGENCY = "agency.txt";
    private static final String FARE_PRODUCTS = "fare_products.txt";
    private static final String RIDER_CATEGORIES_FILE = "rider_categories.txt";
    private static final String FARE_MEDIA = "fare_media.txt";

    /** Читаем только эти файлы: сеть в том же архиве — задача GtfsImportParser. */
    private static final Set<String> RELEVANT_FILES =
            Set.of(AGENCY, FARE_PRODUCTS, RIDER_CATEGORIES_FILE, FARE_MEDIA);

    private static final List<String> REQUIRED_LANGUAGES = List.of("tg", "ru", "en");

    /** Формат стабильного кода справочника — тот же, что у FareCreateRequest.code. */
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,63}$");

    /** Символы, допустимые в коде; остальное нормализация заменяет на '-'. */
    private static final Pattern CODE_FORBIDDEN = Pattern.compile("[^A-Z0-9_-]");

    /** ISO-4217 alpha-3 — как того требует и GTFS (fare_products.currency), и V018. */
    private static final Pattern CURRENCY = Pattern.compile("^[A-Z]{3}$");

    /**
     * Параметры импорта тарифов, которых нет в фиде.
     *
     * @param language язык строк фида (tg|ru|en); null ⇒ берётся из agency.txt:agency_lang
     * @param riderCategories явное соответствие категорий фида категориям модели в виде
     *        {@code RC_ADULT:adult,RC_KID:child}; null/пусто ⇒ соответствий нет и годится
     *        только буквальное совпадение id с {@link FareService#RIDER_CATEGORIES}
     */
    public record FareImportOptions(String language, String riderCategories) {

        public static FareImportOptions defaults() {
            return new FareImportOptions(null, null);
        }

        public FareImportOptions {
            language = (language == null || language.isBlank()) ? null : language.trim();
            riderCategories = (riderCategories == null || riderCategories.isBlank())
                    ? null : riderCategories.trim();
        }
    }

    /**
     * Разобранный тарифный продукт. {@code validityMinutes} здесь нет намеренно — GTFS
     * его не несёт (см. описание класса), и наличие поля в этой записи создавало бы
     * ложное впечатление, что его есть откуда взять.
     *
     * @param ref      ссылка на строку источника для отчёта (IMP-03)
     * @param errors   непусто ⇒ продукт не применяется
     * @param warnings продукт применяется, но с оговоркой (что проверить руками)
     */
    public record ParsedFareProduct(String ref, String code, Map<String, String> name,
                                    Map<String, String> description, BigDecimal amount,
                                    String currency, String riderCategory,
                                    List<String> errors, List<String> warnings) {

        public ParsedFareProduct {
            errors = errors == null ? List.of() : List.copyOf(errors);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    /**
     * Итог разбора: либо продукты, либо ошибки верхнего уровня (не ZIP, нет
     * fare_products.txt, не определён язык фида) — применять нечего, джоб сразу failed.
     */
    public record FareParseResult(List<ParsedFareProduct> products, List<String> topLevelErrors) {

        public FareParseResult {
            products = products == null ? List.of() : List.copyOf(products);
            topLevelErrors = topLevelErrors == null ? List.of() : List.copyOf(topLevelErrors);
        }

        public static FareParseResult of(List<ParsedFareProduct> products) {
            return new FareParseResult(products, List.of());
        }

        public static FareParseResult reject(String... messages) {
            return new FareParseResult(List.of(), List.of(messages));
        }

        public static FareParseResult reject(List<String> messages) {
            return new FareParseResult(List.of(), messages);
        }

        public boolean rejected() {
            return !topLevelErrors.isEmpty();
        }
    }

    /** Разбирает фид; исключений наружу не выпускает — любой мусор становится отчётом. */
    public FareParseResult parse(byte[] source, FareImportOptions options) {
        FareImportOptions effective = options == null ? FareImportOptions.defaults() : options;
        if (source == null || source.length == 0) {
            return FareParseResult.reject("тело импорта пустое: ожидался GTFS-фид (ZIP-архив)");
        }
        Map<String, String> files;
        try {
            files = GtfsArchive.read(source, RELEVANT_FILES);
        } catch (IOException ex) {
            return FareParseResult.reject("не удалось прочитать GTFS-архив (ZIP): " + ex.getMessage());
        }
        if (!files.containsKey(FARE_PRODUCTS)) {
            return FareParseResult.reject("в GTFS-архиве нет обязательного файла " + FARE_PRODUCTS
                    + " — фид не содержит тарифов GTFS Fares v2 (возможно, это фид сети:"
                    + " его импортирует POST /v1/admin/imports/gtfs)");
        }

        CsvTable products = CsvTable.parse(files.get(FARE_PRODUCTS));
        List<String> topLevel = new ArrayList<>();
        List<String> missing = products.missingColumns(List.of("fare_product_id", "amount", "currency"));
        if (!missing.isEmpty()) {
            topLevel.add("в файле " + FARE_PRODUCTS + " нет обязательных колонок: " + missing);
        }
        String language = resolveLanguage(effective, files, topLevel);
        Map<String, String> categoryMap = parseCategoryMap(effective.riderCategories(), topLevel);
        if (!topLevel.isEmpty()) {
            return FareParseResult.reject(topLevel);
        }
        if (products.rows().isEmpty()) {
            return FareParseResult.reject("в " + FARE_PRODUCTS + " нет ни одной строки — импортировать нечего");
        }

        RiderCategoryIndex categories = indexRiderCategories(files);
        Map<String, String> media = indexFareMedia(files);

        List<ParsedFareProduct> parsed = new ArrayList<>();
        Set<String> seenCodes = new LinkedHashSet<>();
        Set<String> seenProductIds = new LinkedHashSet<>();
        for (CsvTable.Row row : products.rows()) {
            parsed.add(parseProduct(products, row, language, categories, categoryMap, media,
                    seenProductIds, seenCodes));
        }
        return FareParseResult.of(parsed);
    }

    // ---- Продукт ----------------------------------------------------------

    private static ParsedFareProduct parseProduct(CsvTable products, CsvTable.Row row, String language,
                                                  RiderCategoryIndex categories,
                                                  Map<String, String> categoryMap,
                                                  Map<String, String> media,
                                                  Set<String> seenProductIds, Set<String> seenCodes) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String productId = products.get(row, "fare_product_id");
        String ref = ref(FARE_PRODUCTS, row, productId);
        if (productId == null) {
            errors.add("обязательное поле 'fare_product_id' пусто — продукту нечем задать стабильный код");
            return new ParsedFareProduct(ref, null, null, null, null, null, null, errors, warnings);
        }

        // Ключ fare_products.txt — тройка (id, rider_category_id, fare_media_id): один id
        // законно встречается несколько раз, а в модели это один продукт на код.
        if (!seenProductIds.add(productId)) {
            errors.add("fare_product_id '" + productId + "' уже встречался выше: в GTFS такие строки"
                    + " различаются rider_category_id/fare_media_id, а справочник хранит один продукт"
                    + " на код — строка пропущена. Разделите продукт на разные fare_product_id либо"
                    + " импортируйте нужный вариант отдельным фидом");
            return new ParsedFareProduct(ref, null, null, null, null, null, null, errors, warnings);
        }

        String code = normalizeCode(productId, errors, warnings);
        if (code != null && !seenCodes.add(code)) {
            errors.add("после приведения к формату кода справочника id '" + productId + "' даёт код '"
                    + code + "', уже занятый другим продуктом этого фида — переименуйте fare_product_id");
            code = null;
        }

        Map<String, String> name = null;
        Map<String, String> description = null;
        String rawName = products.get(row, "fare_product_name");
        if (rawName == null) {
            errors.add("у продукта нет fare_product_name — нечем заполнить название тарифа");
        } else {
            name = i18n(rawName, language, "название тарифа '" + productId + "'", warnings);
            // Колонки описания в GTFS Fares v2 нет вовсе, а description_i18n обязателен.
            description = i18n(rawName, language, "описание тарифа '" + productId + "'", warnings);
            warnings.add("описания тарифа в GTFS Fares v2 нет как поля — временно продублировано"
                    + " название («" + rawName + "»), требуется осмысленное описание оператором");
        }

        BigDecimal amount = amount(products.get(row, "amount"), errors);
        String currency = currency(products.get(row, "currency"), errors);
        String riderCategory = riderCategory(products, row, categories, categoryMap, errors, warnings);
        fareMedia(products, row, media, warnings);

        // Срок действия GTFS не выражает — импортом не задаётся (см. описание класса).
        warnings.add("срок действия (validity_minutes) в GTFS Fares v2 отсутствует как поле и импортом"
                + " не задаётся: у нового продукта он останется пустым, у существующего сохранится"
                + " прежний. Проставьте срок вручную, если тариф ограничен по времени");

        return new ParsedFareProduct(ref, code, name, description, amount, currency, riderCategory,
                errors, warnings);
    }

    /**
     * {@code single_ride} → {@code SINGLE_RIDE}: справочник требует код формата
     * {@code ^[A-Z0-9][A-Z0-9_-]{1,63}$}, а GTFS не ограничивает fare_product_id ничем.
     * Приведение — не выдумывание данных, но оно не должно быть невидимым: любое
     * изменение строки попадает в предупреждение с исходным id.
     */
    private static String normalizeCode(String productId, List<String> errors, List<String> warnings) {
        String code = CODE_FORBIDDEN.matcher(productId.toUpperCase(Locale.ROOT)).replaceAll("-");
        if (!CODE.matcher(code).matches()) {
            errors.add("fare_product_id '" + productId + "' невозможно привести к коду справочника"
                    + " (нужно 2..64 символов A-Z, 0-9, '_' или '-', первый — буква или цифра);"
                    + " получилось '" + code + "' — задайте продукту пригодный id");
            return null;
        }
        if (!code.equals(productId)) {
            warnings.add("fare_product_id '" + productId + "' приведён к коду справочника '" + code
                    + "' (верхний регистр, недопустимые символы заменены на '-') — проверьте, что"
                    + " повторные импорты этого фида дают тот же код");
        }
        return code;
    }

    private static BigDecimal amount(String value, List<String> errors) {
        if (value == null) {
            errors.add("обязательное поле 'amount' пусто — это цена билета, подставлять её неоткуда");
            return null;
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            errors.add("поле 'amount' должно быть числом, получено: " + value);
            return null;
        }
        if (parsed.signum() < 0) {
            errors.add("поле 'amount' не может быть отрицательным, получено: " + value);
            return null;
        }
        if (parsed.scale() > 2 || parsed.precision() - parsed.scale() > 8) {
            errors.add("поле 'amount' не помещается в numeric(10,2) справочника, получено: " + value);
            return null;
        }
        return parsed;
    }

    private static String currency(String value, List<String> errors) {
        if (value == null) {
            errors.add("обязательное поле 'currency' пусто: цена без валюты — не цена");
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(normalized).matches()) {
            errors.add("поле 'currency' должно быть кодом ISO-4217 из трёх букв (например TJS),"
                    + " получено: " + value);
            return null;
        }
        return normalized;
    }

    // ---- Категории пассажиров ---------------------------------------------

    private static String riderCategory(CsvTable products, CsvTable.Row row,
                                        RiderCategoryIndex categories, Map<String, String> categoryMap,
                                        List<String> errors, List<String> warnings) {
        String categoryId = products.get(row, "rider_category_id");
        if (categoryId == null) {
            warnings.add("категория пассажира у продукта не указана (rider_category_id пуст) —"
                    + " принята 'all' (тариф для всех); проверьте, так ли это");
            return "all";
        }
        String explicit = categoryMap.get(categoryId.toLowerCase(Locale.ROOT));
        if (explicit != null) {
            return explicit;
        }
        String literal = categoryId.toLowerCase(Locale.ROOT);
        if (FareService.RIDER_CATEGORIES.contains(literal)) {
            return literal;
        }
        // Не нашли соответствия — сообщаем максимально предметно: где категория объявлена,
        // как она называется в фиде и что именно оператору сделать.
        Integer line = categories.lineOf(categoryId);
        String name = categories.nameOf(categoryId);
        if (line == null) {
            errors.add("продукт ссылается на rider_category_id '" + categoryId + "', которого нет в "
                    + RIDER_CATEGORIES_FILE + " — фид противоречив");
            return null;
        }
        errors.add("категория пассажира '" + categoryId + "'"
                + (name == null ? "" : " («" + name + "»)")
                + ", объявленная в " + RIDER_CATEGORIES_FILE + ":строка " + line
                + ", не соответствует ни одной категории справочника " + FareService.RIDER_CATEGORIES
                + " — задайте соответствие параметром riderCategories, например '"
                + categoryId + ":adult'");
        return null;
    }

    /** Разбирает {@code RC_ADULT:adult,RC_KID:child}; ошибка синтаксиса — отказ импорта. */
    private static Map<String, String> parseCategoryMap(String raw, List<String> topLevel) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null) {
            return map;
        }
        for (String pair : raw.split(",")) {
            String item = pair.trim();
            if (item.isEmpty()) {
                continue;
            }
            int colon = item.lastIndexOf(':');
            if (colon <= 0 || colon == item.length() - 1) {
                topLevel.add("параметр riderCategories: элемент '" + item + "' не в формате"
                        + " '<rider_category_id>:<категория>' (пример: RC_ADULT:adult,RC_KID:child)");
                continue;
            }
            String feedId = item.substring(0, colon).trim();
            String target = item.substring(colon + 1).trim().toLowerCase(Locale.ROOT);
            if (!FareService.RIDER_CATEGORIES.contains(target)) {
                topLevel.add("параметр riderCategories: '" + target + "' не является категорией"
                        + " справочника (допустимо: " + FareService.RIDER_CATEGORIES + ")");
                continue;
            }
            map.put(feedId.toLowerCase(Locale.ROOT), target);
        }
        return map;
    }

    /** Объявленные фидом категории: где объявлены (номер строки) и как называются. */
    private record RiderCategoryIndex(Map<String, Integer> lines, Map<String, String> names) {

        Integer lineOf(String categoryId) {
            return lines.get(categoryId);
        }

        String nameOf(String categoryId) {
            return names.get(categoryId);
        }
    }

    private static RiderCategoryIndex indexRiderCategories(Map<String, String> files) {
        Map<String, Integer> lines = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        String text = files.get(RIDER_CATEGORIES_FILE);
        if (text == null) {
            return new RiderCategoryIndex(lines, names);
        }
        CsvTable table = CsvTable.parse(text);
        if (!table.hasColumn("rider_category_id")) {
            return new RiderCategoryIndex(lines, names);
        }
        for (CsvTable.Row row : table.rows()) {
            String id = table.get(row, "rider_category_id");
            if (id == null) {
                continue;
            }
            lines.putIfAbsent(id, row.lineNumber());
            String name = table.get(row, "rider_category_name");
            if (name != null) {
                names.putIfAbsent(id, name);
            }
        }
        return new RiderCategoryIndex(lines, names);
    }

    // ---- Носитель ---------------------------------------------------------

    /** Носителя модель не хранит — фиксируем факт в отчёте, продукт применяем. */
    private static void fareMedia(CsvTable products, CsvTable.Row row, Map<String, String> media,
                                  List<String> warnings) {
        String mediaId = products.get(row, "fare_media_id");
        if (mediaId == null) {
            return;
        }
        String name = media.get(mediaId);
        warnings.add("продукт привязан к носителю fare_media_id '" + mediaId + "'"
                + (name == null ? "" : " («" + name + "»)")
                + ", а справочник тарифов носитель не хранит — цена импортирована без него;"
                + " если цена зависит от носителя, заведите отдельные продукты вручную");
    }

    private static Map<String, String> indexFareMedia(Map<String, String> files) {
        Map<String, String> names = new LinkedHashMap<>();
        String text = files.get(FARE_MEDIA);
        if (text == null) {
            return names;
        }
        CsvTable table = CsvTable.parse(text);
        if (!table.hasColumn("fare_media_id")) {
            return names;
        }
        for (CsvTable.Row row : table.rows()) {
            String id = table.get(row, "fare_media_id");
            String name = table.get(row, "fare_media_name");
            if (id != null && name != null) {
                names.putIfAbsent(id, name);
            }
        }
        return names;
    }

    // ---- i18n -------------------------------------------------------------

    /** См. одноимённый приём в {@link GtfsImportParser}: заглушка + предупреждение на язык. */
    private static Map<String, String> i18n(String value, String language, String what,
                                            List<String> warnings) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(language, value);
        for (String other : REQUIRED_LANGUAGES) {
            if (other.equals(language)) {
                continue;
            }
            map.put(other, value);
            warnings.add(what + ": в GTFS нет перевода на '" + other + "' — временно подставлено значение"
                    + " языка фида '" + language + "' («" + value + "»), требуется перевод оператором");
        }
        return map;
    }

    private static String resolveLanguage(FareImportOptions options, Map<String, String> files,
                                          List<String> topLevel) {
        String explicit = normalizeLanguage(options.language());
        if (explicit != null) {
            if (!REQUIRED_LANGUAGES.contains(explicit)) {
                topLevel.add("недопустимый язык фида '" + options.language()
                        + "' в параметре lang (допустимо: " + REQUIRED_LANGUAGES + ")");
            }
            return explicit;
        }
        String fromAgency = agencyLanguage(files);
        if (fromAgency == null) {
            topLevel.add("не удалось определить язык фида: в " + AGENCY + " нет agency_lang — "
                    + "укажите язык параметром lang (" + REQUIRED_LANGUAGES + "), "
                    + "иначе непонятно, на какой язык класть названия тарифов из GTFS");
            return null;
        }
        if (!REQUIRED_LANGUAGES.contains(fromAgency)) {
            topLevel.add("язык фида '" + fromAgency + "' (" + AGENCY + ":agency_lang) не поддерживается "
                    + "(допустимо: " + REQUIRED_LANGUAGES + ") — укажите язык параметром lang");
            return null;
        }
        return fromAgency;
    }

    private static String agencyLanguage(Map<String, String> files) {
        String agency = files.get(AGENCY);
        if (agency == null) {
            return null;
        }
        CsvTable table = CsvTable.parse(agency);
        for (CsvTable.Row row : table.rows()) {
            String value = normalizeLanguage(table.get(row, "agency_lang"));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** {@code ru-RU}/{@code RU} → {@code ru}: agency_lang приходит как BCP-47. */
    private static String normalizeLanguage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        int dash = normalized.indexOf('-');
        return dash > 0 ? normalized.substring(0, dash) : normalized;
    }

    private static String ref(String file, CsvTable.Row row, String id) {
        String base = file + ":строка " + row.lineNumber();
        return id == null ? base : base + " (" + id + ")";
    }
}
