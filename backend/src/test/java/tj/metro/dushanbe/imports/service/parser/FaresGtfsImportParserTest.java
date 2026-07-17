package tj.metro.dushanbe.imports.service.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import tj.metro.dushanbe.imports.service.parser.FaresGtfsImportParser.FareImportOptions;
import tj.metro.dushanbe.imports.service.parser.FaresGtfsImportParser.FareParseResult;
import tj.metro.dushanbe.imports.service.parser.FaresGtfsImportParser.ParsedFareProduct;

/**
 * Тесты разбора GTFS Fares v2. Фикстуры-архивы собираются программно
 * ({@link ZipOutputStream}), как и в {@link GtfsImportParserTest}: бинарников в
 * репозитории быть не должно, а содержимое фида видно прямо в тесте.
 */
class FaresGtfsImportParserTest {

    private final FaresGtfsImportParser parser = new FaresGtfsImportParser();

    // ---- Фикстуры ---------------------------------------------------------

    /** Минимальный валидный фид тарифов: два продукта, две категории пассажиров. */
    private static Map<String, String> validFeed() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("agency.txt", """
                agency_id,agency_name,agency_url,agency_timezone,agency_lang
                MD,Метро Душанбе,https://metro.tj,Asia/Dushanbe,ru
                """);
        files.put("rider_categories.txt", """
                rider_category_id,rider_category_name,is_default_fare_category
                adult,Взрослый,1
                student,Студент,0
                """);
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                SINGLE,Разовая поездка,adult,3.00,TJS
                STUDENT-MONTH,Студенческий проездной,student,25.50,TJS
                """);
        return files;
    }

    private static byte[] zip(Map<String, String> files) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return buffer.toByteArray();
    }

    private static ParsedFareProduct find(FareParseResult result, String code) {
        return result.products().stream().filter(p -> code.equals(p.code())).findFirst().orElseThrow();
    }

    private static ParsedFareProduct byRef(FareParseResult result, String idFragment) {
        return result.products().stream().filter(p -> p.ref().contains(idFragment))
                .findFirst().orElseThrow();
    }

    private static boolean hasWarning(ParsedFareProduct product, String fragment) {
        return product.warnings().stream().anyMatch(w -> w.contains(fragment));
    }

    // ---- Валидный фид -----------------------------------------------------

    @Test
    void validFeedProducesProductsWithPriceCurrencyAndCategory() {
        FareParseResult result = parser.parse(zip(validFeed()), FareImportOptions.defaults());

        assertFalse(result.rejected(), "валидный фид не должен отвергаться: " + result.topLevelErrors());
        assertEquals(List.of("SINGLE", "STUDENT-MONTH"),
                result.products().stream().map(ParsedFareProduct::code).toList());

        ParsedFareProduct single = find(result, "SINGLE");
        assertTrue(single.valid(), "ошибки продукта: " + single.errors());
        assertEquals(0, new BigDecimal("3.00").compareTo(single.amount()));
        assertEquals("TJS", single.currency());
        assertEquals("adult", single.riderCategory());
        assertEquals("Разовая поездка", single.name().get("ru"));
    }

    @Test
    void riderCategoryIdMatchingModelCategoryLiterallyIsAccepted() {
        FareParseResult result = parser.parse(zip(validFeed()), FareImportOptions.defaults());

        assertEquals("student", find(result, "STUDENT-MONTH").riderCategory());
    }

    @Test
    void feedRefsPointAtSourceLinesForTheReport() {
        FareParseResult result = parser.parse(zip(validFeed()), FareImportOptions.defaults());

        assertTrue(find(result, "SINGLE").ref().contains("fare_products.txt"));
        assertTrue(find(result, "SINGLE").ref().contains("строка 2"), find(result, "SINGLE").ref());
    }

    // ---- validity_minutes: не импортируется, но фиксируется в отчёте -------

    @Test
    void everyProductWarnsThatValidityIsNotExpressibleInGtfs() {
        FareParseResult result = parser.parse(zip(validFeed()), FareImportOptions.defaults());

        for (ParsedFareProduct product : result.products()) {
            // продукт применяется — отсутствие срока не делает его невалидным (колонка nullable)
            assertTrue(product.valid(), "ошибки: " + product.errors());
            assertTrue(hasWarning(product, "validity_minutes"),
                    "оператор обязан увидеть, чему проставить срок: " + product.warnings());
            assertTrue(hasWarning(product, "не задаётся"));
        }
    }

    // ---- Категории пассажиров ---------------------------------------------

    @Test
    void unknownRiderCategoryIsAnErrorWithItsDeclarationLineNumber() {
        Map<String, String> files = validFeed();
        files.put("rider_categories.txt", """
                rider_category_id,rider_category_name,is_default_fare_category
                adult,Взрослый,1
                RC_KID,Дети до 7 лет,0
                """);
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                SINGLE,Разовая поездка,adult,3.00,TJS
                KID,Детский билет,RC_KID,1.00,TJS
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        assertFalse(result.rejected(), "одна битая категория — не повод отвергать весь фид");
        assertTrue(find(result, "SINGLE").valid(), "остальные продукты выживают");

        ParsedFareProduct kid = byRef(result, "KID");
        assertFalse(kid.valid(), "неизвестная категория — ошибка, а не тихое приведение");
        String message = kid.errors().getFirst();
        // номер строки объявления категории — иначе оператору негде её искать
        assertTrue(message.contains("rider_categories.txt:строка 3"), message);
        assertTrue(message.contains("RC_KID"), message);
        assertTrue(message.contains("Дети до 7 лет"), message);
        // подсказка, как это чинится
        assertTrue(message.contains("riderCategories"), message);
    }

    @Test
    void explicitRiderCategoryMappingResolvesFeedSpecificIds() {
        Map<String, String> files = validFeed();
        files.put("rider_categories.txt", """
                rider_category_id,rider_category_name,is_default_fare_category
                RC_KID,Дети до 7 лет,0
                """);
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                KID,Детский билет,RC_KID,1.00,TJS
                """);

        FareParseResult result = parser.parse(zip(files), new FareImportOptions(null, "RC_KID:child"));

        ParsedFareProduct kid = find(result, "KID");
        assertTrue(kid.valid(), "ошибки: " + kid.errors());
        assertEquals("child", kid.riderCategory());
    }

    @Test
    void productWithoutRiderCategoryFallsBackToAllAndWarns() {
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,amount,currency
                SINGLE,Разовая поездка,3.00,TJS
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        ParsedFareProduct single = find(result, "SINGLE");
        assertTrue(single.valid(), "ошибки: " + single.errors());
        assertEquals("all", single.riderCategory());
        assertTrue(hasWarning(single, "принята 'all'"), "дефолт обязан быть виден: " + single.warnings());
    }

    @Test
    void danglingRiderCategoryReferenceIsAnError() {
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                SINGLE,Разовая поездка,RC_GHOST,3.00,TJS
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        ParsedFareProduct single = byRef(result, "SINGLE");
        assertFalse(single.valid());
        assertTrue(single.errors().getFirst().contains("rider_categories.txt"), single.errors().toString());
        assertTrue(single.errors().getFirst().contains("RC_GHOST"));
    }

    @Test
    void invalidRiderCategoryMappingParameterRejectsTheWholeImport() {
        FareParseResult result = parser.parse(zip(validFeed()),
                new FareImportOptions(null, "RC_KID:малыши"));

        assertTrue(result.rejected(), "цель соответствия обязана быть категорией справочника");
        assertTrue(result.topLevelErrors().getFirst().contains("riderCategories"));
    }

    @Test
    void malformedRiderCategoryMappingParameterRejectsTheWholeImport() {
        FareParseResult result = parser.parse(zip(validFeed()), new FareImportOptions(null, "RC_KID"));

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("RC_KID"));
    }

    // ---- Носитель ---------------------------------------------------------

    @Test
    void fareMediaIsNotImportedButReportedWithItsReadableName() {
        Map<String, String> files = validFeed();
        files.put("fare_media.txt", """
                fare_media_id,fare_media_name,fare_media_type
                CARD,Транспортная карта,2
                """);
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,fare_media_id,amount,currency
                SINGLE,Разовая поездка,adult,CARD,3.00,TJS
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        ParsedFareProduct single = find(result, "SINGLE");
        assertTrue(single.valid(), "носитель не мешает импортировать цену: " + single.errors());
        assertTrue(hasWarning(single, "Транспортная карта"), single.warnings().toString());
        assertTrue(hasWarning(single, "носитель не хранит"));
    }

    @Test
    void duplicateFareProductIdIsAnErrorBecauseModelKeepsOneProductPerCode() {
        Map<String, String> files = validFeed();
        // В GTFS ключ — тройка (id, rider_category_id, fare_media_id): такой фид легален
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,fare_media_id,amount,currency
                SINGLE,Разовая поездка (карта),adult,CARD,3.00,TJS
                SINGLE,Разовая поездка (наличные),adult,CASH,3.50,TJS
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        assertEquals(2, result.products().size());
        assertTrue(result.products().get(0).valid(), "первая строка применяется");
        ParsedFareProduct duplicate = result.products().get(1);
        assertFalse(duplicate.valid(), "вторая цена под тем же кодом молча не проглатывается");
        assertTrue(duplicate.errors().getFirst().contains("уже встречался"), duplicate.errors().toString());
        assertTrue(duplicate.ref().contains("строка 3"), duplicate.ref());
    }

    // ---- Код продукта -----------------------------------------------------

    @Test
    void productIdIsNormalizedToCodeFormatAndTheChangeIsReported() {
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                single ride,Разовая поездка,adult,3.00,TJS
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        ParsedFareProduct single = find(result, "SINGLE-RIDE");
        assertTrue(single.valid(), "ошибки: " + single.errors());
        assertTrue(hasWarning(single, "приведён к коду справочника"), single.warnings().toString());
        assertTrue(hasWarning(single, "single ride"), "исходный id обязан быть в отчёте");
    }

    @Test
    void productIdThatCannotBecomeACodeIsAnError() {
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                X,Слишком короткий,adult,3.00,TJS
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        ParsedFareProduct product = result.products().getFirst();
        assertFalse(product.valid());
        assertNull(product.code());
        assertTrue(product.errors().getFirst().contains("2..64"), product.errors().toString());
    }

    @Test
    void twoIdsCollapsingToTheSameCodeIsAnError() {
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                single ride,Разовая (пробел),adult,3.00,TJS
                single-ride,Разовая (дефис),adult,3.00,TJS
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        assertTrue(result.products().get(0).valid());
        assertFalse(result.products().get(1).valid(), "коллизия кодов не должна быть тихой перезаписью");
        assertTrue(result.products().get(1).errors().getFirst().contains("уже занятый"));
    }

    // ---- Цена -------------------------------------------------------------

    @Test
    void brokenPriceIsAnErrorNotADefault() {
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                NOPRICE,Без цены,adult,,TJS
                NAN,Не число,adult,бесплатно,TJS
                NEG,Отрицательная,adult,-1.00,TJS
                NOCUR,Без валюты,adult,3.00,
                BADCUR,Кривая валюта,adult,3.00,сомони
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        assertTrue(result.products().stream().noneMatch(ParsedFareProduct::valid),
                "ни одна из этих цен не должна быть применена");
        assertTrue(byRef(result, "NOPRICE").errors().getFirst().contains("amount"));
        assertTrue(byRef(result, "NAN").errors().getFirst().contains("числом"));
        assertTrue(byRef(result, "NEG").errors().getFirst().contains("отрицательным"));
        assertTrue(byRef(result, "NOCUR").errors().getFirst().contains("currency"));
        assertTrue(byRef(result, "BADCUR").errors().getFirst().contains("ISO-4217"));
    }

    @Test
    void zeroPriceIsValidBecauseFreeFaresExist() {
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                FREE,Бесплатный проезд,adult,0.00,TJS
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        assertTrue(find(result, "FREE").valid(), "0 — законная цена: " + find(result, "FREE").errors());
    }

    // ---- i18n -------------------------------------------------------------

    @Test
    void missingTranslationsAreFilledFromFeedLanguageAndWarned() {
        FareParseResult result = parser.parse(zip(validFeed()), FareImportOptions.defaults());

        ParsedFareProduct single = find(result, "SINGLE");
        assertEquals("Разовая поездка", single.name().get("ru"));
        assertEquals("Разовая поездка", single.name().get("tg"));
        assertEquals("Разовая поездка", single.name().get("en"));
        assertTrue(hasWarning(single, "'tg'"));
        assertTrue(hasWarning(single, "'en'"));
        assertTrue(single.warnings().stream().anyMatch(w -> w.contains("требуется перевод")));
        assertTrue(single.valid(), "заглушка перевода не отменяет импорт цены");
    }

    @Test
    void descriptionIsTemporarilyCopiedFromNameAndWarned() {
        FareParseResult result = parser.parse(zip(validFeed()), FareImportOptions.defaults());

        ParsedFareProduct single = find(result, "SINGLE");
        // description_i18n обязателен, а колонки описания в GTFS нет вовсе
        assertEquals("Разовая поездка", single.description().get("ru"));
        assertTrue(hasWarning(single, "описания тарифа в GTFS Fares v2 нет"),
                single.warnings().toString());
    }

    @Test
    void explicitLanguageOptionOverridesAgencyLang() {
        FareParseResult result = parser.parse(zip(validFeed()), new FareImportOptions("tg", null));

        ParsedFareProduct single = find(result, "SINGLE");
        assertEquals("Разовая поездка", single.name().get("tg"));
        assertTrue(hasWarning(single, "'ru'"), single.warnings().toString());
    }

    @Test
    void feedWithoutAgencyLangAndWithoutOptionIsRejectedWithExplanation() {
        Map<String, String> files = validFeed();
        files.remove("agency.txt");

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("agency_lang"));
        assertTrue(result.topLevelErrors().getFirst().contains("lang"));
    }

    @Test
    void productWithoutNameIsAnError() {
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name,rider_category_id,amount,currency
                NONAME,,adult,3.00,TJS
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        ParsedFareProduct product = byRef(result, "NONAME");
        assertFalse(product.valid());
        assertTrue(product.errors().getFirst().contains("fare_product_name"));
    }

    // ---- Невалидный вход: отчёт, а не исключение ---------------------------

    @Test
    void emptyBodyIsReportedAsError() {
        FareParseResult result = parser.parse(new byte[0], FareImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("пустое"));
    }

    @Test
    void notAZipIsReportedAsError() {
        FareParseResult result = parser.parse("это вовсе не архив".getBytes(StandardCharsets.UTF_8),
                FareImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("GTFS"));
    }

    /** Фид сети без тарифов — типичная ошибка оператора: подсказываем нужный эндпоинт. */
    @Test
    void networkOnlyFeedIsRejectedWithAHintAtTheOtherEndpoint() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("agency.txt", """
                agency_id,agency_name,agency_url,agency_timezone,agency_lang
                MD,Метро Душанбе,https://metro.tj,Asia/Dushanbe,ru
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("fare_products.txt"));
        assertTrue(result.topLevelErrors().getFirst().contains("/v1/admin/imports/gtfs"));
    }

    @Test
    void missingRequiredColumnIsReportedWithItsName() {
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", """
                fare_product_id,fare_product_name
                SINGLE,Разовая поездка
                """);

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("amount"));
        assertTrue(result.topLevelErrors().getFirst().contains("currency"));
    }

    @Test
    void emptyProductTableIsRejected() {
        Map<String, String> files = validFeed();
        files.put("fare_products.txt", "fare_product_id,fare_product_name,amount,currency\n");

        FareParseResult result = parser.parse(zip(files), FareImportOptions.defaults());

        assertTrue(result.rejected());
        assertTrue(result.topLevelErrors().getFirst().contains("ни одной строки"));
    }

    @Test
    void nestedFolderInArchiveIsSupported() {
        Map<String, String> nested = new LinkedHashMap<>();
        validFeed().forEach((name, content) -> nested.put("fares-2026/" + name, content));

        FareParseResult result = parser.parse(zip(nested), FareImportOptions.defaults());

        assertFalse(result.rejected(), "фид во вложенной папке — обычное дело: " + result.topLevelErrors());
        assertEquals("adult", find(result, "SINGLE").riderCategory());
    }
}
