package tj.metro.dushanbe.imports.service.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный CSV-ридер (RFC 4180) для импорта: заголовок + строки, доступ к ячейкам по
 * имени колонки и с сохранением НОМЕРА ИСХОДНОЙ СТРОКИ файла — без него отчёт об ошибках
 * (IMP-03) бесполезен оператору.
 *
 * <p>Своя реализация, а не библиотека: обоим потребителям (плоский CSV и CSV внутри
 * GTFS-архива) нужно ~100 строк — кавычки, удвоенная кавычка внутри поля, CRLF/LF, BOM.
 * Тянуть ради этого новую зависимость в {@code pom.xml} несоразмерно.
 *
 * <p>Разбор не бросает исключений: «рваные» строки (число ячеек ≠ числу колонок
 * заголовка) остаются как есть, решение о том, ошибка это или нет, принимает вызывающий
 * парсер — он же формирует человекочитаемое сообщение с номером строки.
 */
public final class CsvTable {

    /** Сигнатура UTF-8 (BOM) в начале файла. */
    private static final char BOM = (char) 0xFEFF;

    /**
     * Строка файла.
     *
     * @param lineNumber номер строки в исходном файле, 1-based (строка заголовка = 1);
     *        поле в кавычках может содержать перевод строки — номер указывает на начало
     *        записи
     */
    public record Row(int lineNumber, List<String> cells) {
    }

    private final List<String> header;
    private final Map<String, Integer> columnIndex;
    private final List<Row> rows;

    private CsvTable(List<String> header, List<Row> rows) {
        this.header = header;
        this.rows = rows;
        this.columnIndex = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            // при дублирующихся колонках выигрывает первая — так же ведут себя GTFS-валидаторы
            columnIndex.putIfAbsent(header.get(i), i);
        }
    }

    /** Разбирает текст CSV; пустой вход даёт таблицу без заголовка и без строк. */
    public static CsvTable parse(String text) {
        List<Row> all = tokenize(text);
        if (all.isEmpty()) {
            return new CsvTable(List.of(), List.of());
        }
        List<String> header = new ArrayList<>();
        for (String cell : all.get(0).cells()) {
            header.add(cell.trim());
        }
        return new CsvTable(List.copyOf(header), List.copyOf(all.subList(1, all.size())));
    }

    /** Колонки заголовка в порядке файла. */
    public List<String> header() {
        return header;
    }

    /** Строки данных (без заголовка). */
    public List<Row> rows() {
        return rows;
    }

    public boolean hasColumn(String column) {
        return columnIndex.containsKey(column);
    }

    /** Из {@code required} — те, которых нет в заголовке (для сообщения об ошибке). */
    public List<String> missingColumns(List<String> required) {
        List<String> missing = new ArrayList<>();
        for (String column : required) {
            if (!hasColumn(column)) {
                missing.add(column);
            }
        }
        return missing;
    }

    /**
     * Значение ячейки по имени колонки: обрезанное по краям, пустое → null (в CSV нет
     * разницы между «пусто» и «не указано», а вызывающим удобнее один null-случай).
     * Нет такой колонки или строка короче заголовка → null.
     */
    public String get(Row row, String column) {
        Integer index = columnIndex.get(column);
        if (index == null || index >= row.cells().size()) {
            return null;
        }
        String value = row.cells().get(index).trim();
        return value.isEmpty() ? null : value;
    }

    // ---- Разбор -----------------------------------------------------------

    private static List<Row> tokenize(String text) {
        List<Row> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        // BOM: Excel и часть GTFS-фидов пишут UTF-8 с сигнатурой — иначе первая колонка
        // заголовка получит невидимый префикс и перестанет находиться по имени.
        String input = (text.charAt(0) == BOM) ? text.substring(1) : text;

        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean rowStarted = false;
        int line = 1;
        int rowStartLine = 1;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < input.length() && input.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    cell.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    rowStarted = true;
                    inQuotes = true;
                }
                case ',' -> {
                    rowStarted = true;
                    cells.add(cell.toString());
                    cell.setLength(0);
                }
                case '\r' -> { // часть CRLF — игнорируем, перенос обрабатывает '\n'
                }
                case '\n' -> {
                    cells.add(cell.toString());
                    cell.setLength(0);
                    addRow(rows, rowStartLine, cells, rowStarted);
                    cells.clear();
                    line++;
                    rowStartLine = line;
                    rowStarted = false;
                }
                default -> {
                    rowStarted = true;
                    cell.append(c);
                }
            }
        }
        if (rowStarted || !cell.isEmpty() || !cells.isEmpty()) {
            cells.add(cell.toString());
            addRow(rows, rowStartLine, cells, rowStarted);
        }
        return rows;
    }

    /** Пустые строки файла пропускаем: разделители блоков не должны становиться ошибками. */
    private static void addRow(List<Row> rows, int lineNumber, List<String> cells, boolean rowStarted) {
        if (!rowStarted && cells.size() <= 1) {
            return;
        }
        rows.add(new Row(lineNumber, List.copyOf(cells)));
    }
}
