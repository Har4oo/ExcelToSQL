package com.example.javafx.database;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ExcelToPostgreSQL {

    /** Where diagnostic messages go. The UI wires this to the on-screen log panel. */
    private Consumer<String> logger = System.out::println;

    public void setLogger(Consumer<String> logger) {
        this.logger = (logger != null) ? logger : System.out::println;
    }

    private void log(String message) {
        logger.accept(message);
    }

    /** Normalised column category we bind against. Binding is driven by the COLUMN type,
     *  never by the per-cell type, so a stray numeric cell in a TEXT column can't break the INSERT. */
    private enum ColType { TEXT, BIGINT, DECIMAL, DATE, BOOLEAN }

    public int importExcelToTable(String filePath, String tableName, boolean dropIfExists) throws IOException, SQLException {

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
            Sheet sheet = workbook.getSheetAt(0);

            List<String> headers = getHeaders(sheet);
            if (headers.isEmpty()) {
                throw new SQLException("No usable columns were found on the first row of the sheet.");
            }
            List<String> columnTypes = inferColumnTypes(sheet, headers);

            createTable(tableName, headers, columnTypes, dropIfExists);

            return insertData(tableName, headers, columnTypes, sheet);
        }
    }

    // ----------------------------------------------------------------- preview / analysis

    /** Column name + inferred SQL type, used by the UI preview. */
    public static class ColumnMeta {
        public final String name;
        public final String type;
        public ColumnMeta(String name, String type) { this.name = name; this.type = type; }
    }

    /** Lightweight snapshot of the sheet for the on-screen preview. */
    public static class Preview {
        public final List<ColumnMeta> columns = new ArrayList<>();
        public final List<List<String>> rows = new ArrayList<>();
    }

    public Preview loadPreview(String filePath, int maxRows) throws IOException {
        Preview preview = new Preview();
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<String> headers = getHeaders(sheet);
            List<String> types = inferColumnTypes(sheet, headers);
            for (int i = 0; i < headers.size(); i++) {
                preview.columns.add(new ColumnMeta(headers.get(i), types.get(i)));
            }

            int lastRow = sheet.getLastRowNum();
            for (int i = 1; i <= lastRow && preview.rows.size() < maxRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                List<String> values = new ArrayList<>();
                for (int col = 0; col < headers.size(); col++) {
                    values.add(cellAsString(row.getCell(col)));
                }
                preview.rows.add(values);
            }
        }
        return preview;
    }

    // ----------------------------------------------------------------- headers

    private List<String> getHeaders(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return headers;
        }

        // Use the last cell index (not getPhysicalNumberOfCells) so blank header cells in the
        // middle don't shrink the range and shift every following column out of alignment.
        int columnCount = headerRow.getLastCellNum();

        for (int i = 0; i < columnCount; i++) {
            Cell cell = headerRow.getCell(i);
            String headerValue = cellAsString(cell).trim();

            // Never skip a column: a blank header still occupies a physical column position, and the
            // data readers below address columns by index. Give it a stable placeholder name instead.
            String header = headerValue.isEmpty()
                    ? "column_" + (i + 1)
                    : sanitizeColumnName(headerValue);
            if (header.isEmpty()) {
                header = "column_" + (i + 1);
            }

            int count = 1;
            String uniqueHeader = header;
            while (headers.contains(uniqueHeader)) {
                uniqueHeader = header + "_" + count;
                count++;
            }
            headers.add(uniqueHeader);
        }

        return headers;
    }

    // ----------------------------------------------------------------- type inference

    private List<String> inferColumnTypes(Sheet sheet, List<String> headers) {
        List<String> types = new ArrayList<>();
        for (int col = 0; col < headers.size(); col++) {
            types.add(inferColumnType(sheet, col, headers.get(col)));
        }
        return types;
    }

    private String inferColumnType(Sheet sheet, int columnIndex, String columnName) {
        boolean hasIntegers = false;
        boolean hasDecimals = false;
        boolean hasDates = false;
        boolean hasBooleans = false;
        boolean hasStrings = false;
        int nonEmptyCount = 0;

        int sheetLastRowNum = sheet.getLastRowNum();
        for (int i = 1; i <= sheetLastRowNum; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell cell = row.getCell(columnIndex);
            if (isBlank(cell)) continue;

            nonEmptyCount++;

            CellType type = effectiveType(cell);
            switch (type) {
                case STRING:
                    hasStrings = true;
                    break;
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        hasDates = true;
                    } else {
                        double value = numericValue(cell);
                        if (value != Math.floor(value) || Double.isInfinite(value)) {
                            hasDecimals = true;
                        } else {
                            hasIntegers = true;
                        }
                    }
                    break;
                case BOOLEAN:
                    hasBooleans = true;
                    break;
                default:
                    hasStrings = true;
            }
        }

        String detectedType;
        if (nonEmptyCount == 0) {
            detectedType = "TEXT";
        }
        // String wins over everything: if even a single value is text, a numeric/boolean column
        // type would reject it at INSERT time. TEXT is unbounded so nothing gets truncated either.
        else if (hasStrings) {
            detectedType = "TEXT";
        }
        else if (hasBooleans && !hasDates && !hasDecimals && !hasIntegers) {
            detectedType = "BOOLEAN";
        }
        else if (hasDates && !hasIntegers && !hasDecimals && !hasBooleans) {
            detectedType = "DATE";
        }
        else if (hasDecimals) {
            detectedType = "DOUBLE PRECISION";
        }
        else if (hasIntegers) {
            // BIGINT, not INTEGER: 10-digit identifiers (ЕГН, ЛНЧ, phone numbers) overflow INT4.
            detectedType = "BIGINT";
        }
        else {
            detectedType = "TEXT";
        }

        log("Column '" + columnName + "' detected as: " + detectedType +
                " (strings=" + hasStrings + ", dates=" + hasDates + ", integers=" + hasIntegers +
                ", decimals=" + hasDecimals + ", booleans=" + hasBooleans + ", nonEmpty=" + nonEmptyCount + ")");

        return detectedType;
    }

    private ColType baseType(String sqlType) {
        if (sqlType.startsWith("BIGINT") || sqlType.startsWith("INTEGER")) return ColType.BIGINT;
        if (sqlType.startsWith("DOUBLE") || sqlType.startsWith("DECIMAL") || sqlType.startsWith("NUMERIC")) return ColType.DECIMAL;
        if (sqlType.startsWith("DATE")) return ColType.DATE;
        if (sqlType.startsWith("BOOLEAN")) return ColType.BOOLEAN;
        return ColType.TEXT;
    }

    private String sanitizeColumnName(String name) {
        String sanitized = name.replaceAll("[^\\p{L}\\p{Nd}_]", "_");

        if (sanitized.matches("^[0-9].*")) {
            sanitized = "col_" + sanitized;
        }

        return sanitized.toLowerCase();
    }

    /** Double-quote an identifier so Cyrillic names, reserved words and odd characters are always legal. */
    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    // ----------------------------------------------------------------- DDL

    private void createTable(String tableName, List<String> headers, List<String> columnTypes, boolean dropIfExists) throws SQLException {
        StringBuilder sql = new StringBuilder();

        if (dropIfExists) {
            sql.append("DROP TABLE IF EXISTS ").append(quote(tableName)).append(";\n");
        }

        sql.append("CREATE TABLE IF NOT EXISTS ").append(quote(tableName)).append(" (\n");

        boolean hasIdColumn = headers.stream().anyMatch(h -> h.equalsIgnoreCase("id"));

        if (!hasIdColumn) {
            sql.append("    id SERIAL PRIMARY KEY,\n");
        }

        for (int i = 0; i < headers.size(); i++) {
            sql.append("    ").append(quote(headers.get(i))).append(" ").append(columnTypes.get(i));

            if (headers.get(i).equalsIgnoreCase("id")) {
                sql.append(" PRIMARY KEY");
            }

            if (i < headers.size() - 1) {
                sql.append(",");
            }
            sql.append("\n");
        }

        sql.append(");");

        log("Creating table with SQL:\n" + sql + "\n");

        try (Connection conn = DatabaseConfig.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
            log("Table '" + tableName + "' created successfully.");
        } catch (SQLException e) {
            throw new SQLException("Failed while creating table:\n" + describe(e), e.getSQLState(), e);
        }
    }

    // ----------------------------------------------------------------- data

    private int insertData(String tableName, List<String> headers, List<String> columnTypes, Sheet sheet) throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(quote(tableName)).append(" (");

        for (int i = 0; i < headers.size(); i++) {
            sql.append(quote(headers.get(i)));
            if (i < headers.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(") VALUES (");

        for (int i = 0; i < headers.size(); i++) {
            sql.append("?");
            if (i < headers.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(")");

        log("Insert statement:\n" + sql + "\n");

        List<ColType> colTypes = new ArrayList<>();
        for (String t : columnTypes) {
            colTypes.add(baseType(t));
        }

        int rowsInserted = 0;
        int currentExcelRow = 0;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                currentExcelRow = i + 1; // 1-based, matches what the user sees in Excel
                Row row = sheet.getRow(i);
                if (row == null) continue;

                boolean isRowEmpty = true;
                for (int col = 0; col < headers.size(); col++) {
                    if (!isBlank(row.getCell(col))) {
                        isRowEmpty = false;
                        break;
                    }
                }
                if (isRowEmpty) {
                    continue;
                }

                for (int col = 0; col < headers.size(); col++) {
                    bindValue(preparedStatement, col + 1, row.getCell(col), colTypes.get(col));
                }

                preparedStatement.addBatch();
                rowsInserted++;

                if (rowsInserted % 100 == 0) {
                    preparedStatement.executeBatch();
                }
            }

            preparedStatement.executeBatch();
        } catch (SQLException e) {
            throw new SQLException("Insert failed near Excel row " + currentExcelRow + ":\n" + describe(e),
                    e.getSQLState(), e);
        }

        log(rowsInserted + " rows inserted into '" + tableName + "'.");
        return rowsInserted;
    }

    /** Bind a cell according to the resolved COLUMN type, coercing the cell value as needed. */
    private void bindValue(PreparedStatement pstmt, int index, Cell cell, ColType type) throws SQLException {
        if (isBlank(cell)) {
            pstmt.setNull(index, sqlTypeCode(type));
            return;
        }

        switch (type) {
            case BIGINT: {
                Long v = cellAsLong(cell);
                if (v == null) pstmt.setNull(index, Types.BIGINT);
                else pstmt.setLong(index, v);
                break;
            }
            case DECIMAL: {
                Double d = cellAsDouble(cell);
                if (d == null) pstmt.setNull(index, Types.DOUBLE);
                else pstmt.setDouble(index, d);
                break;
            }
            case DATE: {
                java.sql.Date d = cellAsDate(cell);
                if (d == null) pstmt.setNull(index, Types.DATE);
                else pstmt.setDate(index, d);
                break;
            }
            case BOOLEAN: {
                Boolean b = cellAsBoolean(cell);
                if (b == null) pstmt.setNull(index, Types.BOOLEAN);
                else pstmt.setBoolean(index, b);
                break;
            }
            default:
                pstmt.setString(index, cellAsString(cell));
        }
    }

    private int sqlTypeCode(ColType type) {
        switch (type) {
            case BIGINT:  return Types.BIGINT;
            case DECIMAL: return Types.DOUBLE;
            case DATE:    return Types.DATE;
            case BOOLEAN: return Types.BOOLEAN;
            default:      return Types.VARCHAR;
        }
    }

    // ----------------------------------------------------------------- cell helpers

    private boolean isBlank(Cell cell) {
        if (cell == null) return true;
        CellType type = effectiveType(cell);
        if (type == CellType.BLANK || type == CellType._NONE) return true;
        if (type == CellType.STRING) {
            String s = cell.getStringCellValue();
            return s == null || s.trim().isEmpty();
        }
        return false;
    }

    /** Resolve FORMULA cells to their cached result type; return the plain type otherwise. */
    private CellType effectiveType(Cell cell) {
        if (cell == null) return CellType._NONE;
        CellType type = cell.getCellType();
        return (type == CellType.FORMULA) ? cell.getCachedFormulaResultType() : type;
    }

    private double numericValue(Cell cell) {
        return cell.getNumericCellValue();
    }

    private Long cellAsLong(Cell cell) {
        if (isBlank(cell)) return null;
        switch (effectiveType(cell)) {
            case NUMERIC:
                return (long) cell.getNumericCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue() ? 1L : 0L;
            case STRING:
                try {
                    return Long.parseLong(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            default:
                return null;
        }
    }

    private Double cellAsDouble(Cell cell) {
        if (isBlank(cell)) return null;
        switch (effectiveType(cell)) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue() ? 1.0 : 0.0;
            case STRING:
                try {
                    return Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            default:
                return null;
        }
    }

    private java.sql.Date cellAsDate(Cell cell) {
        if (isBlank(cell)) return null;
        if (effectiveType(cell) == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return new java.sql.Date(cell.getDateCellValue().getTime());
        }
        return null;
    }

    private Boolean cellAsBoolean(Cell cell) {
        if (isBlank(cell)) return null;
        switch (effectiveType(cell)) {
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case NUMERIC:
                return cell.getNumericCellValue() != 0;
            case STRING:
                String s = cell.getStringCellValue().trim();
                if (s.equalsIgnoreCase("true") || s.equals("1")) return Boolean.TRUE;
                if (s.equalsIgnoreCase("false") || s.equals("0")) return Boolean.FALSE;
                return null;
            default:
                return null;
        }
    }

    /** A clean string rendering suitable for a TEXT column and for the preview (no scientific notation, no ".0"). */
    private String cellAsString(Cell cell) {
        if (cell == null) return "";
        switch (effectiveType(cell)) {
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return Boolean.toString(cell.getBooleanCellValue());
            case BLANK:
            case _NONE:
                return "";
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
                }
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    return Long.toString((long) v);
                }
                return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
            default:
                return cell.toString();
        }
    }

    /** Flatten a SQLException chain — including the batch driver's getNextException — into a readable block. */
    public static String describe(SQLException e) {
        StringBuilder sb = new StringBuilder();
        SQLException current = e;
        int depth = 0;
        while (current != null && depth < 20) {
            sb.append("  • ").append(current.getClass().getSimpleName())
                    .append(": ").append(current.getMessage());
            if (current.getSQLState() != null) {
                sb.append(" [SQLState=").append(current.getSQLState()).append("]");
            }
            sb.append("\n");
            current = current.getNextException();
            depth++;
        }
        return sb.toString();
    }
}
