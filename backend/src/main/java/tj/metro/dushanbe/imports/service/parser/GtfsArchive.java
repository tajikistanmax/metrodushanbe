package tj.metro.dushanbe.imports.service.parser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Распаковка GTFS-архива: ZIP → {@code имя файла → его текст в UTF-8}. Общий код двух
 * потребителей — импорта сети ({@link GtfsImportParser}) и импорта тарифов GTFS Fares v2
 * ({@link FaresGtfsImportParser}); оба читают ОДИН и тот же контейнер, различаясь лишь
 * набором интересных им файлов, поэтому второе чтение ZIP не заводится.
 *
 * <p>Внешней библиотеки нет намеренно: GTFS — это ZIP + CSV, и {@link ZipInputStream}
 * с {@link CsvTable} закрывают задачу целиком (см. описание {@link GtfsImportParser}).
 */
final class GtfsArchive {

    /**
     * Потолок на распакованный размер одного файла архива (zip-бомба: маленький ZIP
     * распаковывается в гигабайты и кладёт heap). 32 МиБ с запасом хватает на stop_times
     * городского фида.
     */
    private static final long MAX_ENTRY_BYTES = 32L * 1024 * 1024;

    private GtfsArchive() {
    }

    /**
     * Читает из архива только файлы из {@code relevantFiles}: остальное содержимое фида
     * потребителя не касается и незачем держать в heap.
     *
     * @throws IOException вход не ZIP, файл превышает лимит распаковки либо в архиве нет
     *         ни одного ожидаемого файла. Вызывающий обязан превратить это в отчёт
     *         (topLevelErrors), а не выпустить наружу — см. контракт парсеров.
     */
    static Map<String, String> read(byte[] source, Set<String> relevantFiles) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(source), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = fileName(entry.getName());
                if (!relevantFiles.contains(name)) {
                    continue;
                }
                files.put(name, new String(readLimited(zip, name), StandardCharsets.UTF_8));
            }
        }
        if (files.isEmpty()) {
            throw new IOException("в архиве нет ни одного файла GTFS "
                    + relevantFiles.stream().sorted().toList() + " — это не GTFS-фид");
        }
        return files;
    }

    /** Часть фидов упакована с вложенной папкой — сравниваем по имени файла. */
    private static String fileName(String entryName) {
        int slash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
        return slash >= 0 ? entryName.substring(slash + 1) : entryName;
    }

    private static byte[] readLimited(InputStream in, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) > 0) {
            total += read;
            if (total > MAX_ENTRY_BYTES) {
                throw new IOException("файл " + name + " в архиве превышает допустимые "
                        + (MAX_ENTRY_BYTES / (1024 * 1024)) + " МиБ в распакованном виде");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
