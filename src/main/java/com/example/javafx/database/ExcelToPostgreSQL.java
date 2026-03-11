package com.example.javafx.database;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ExcelToPostgreSQL {

    public int importExcelToTable(String filePath, String tableName, boolean dropIfExists) throws IOException, SQLException {

        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
            Sheet sheet = workbook.getSheetAt(0);
            

            List<String> headers = getHeaders(sheet);

            List<String> columnTypes = inferColumnTypes(sheet, headers);

            createTable(tableName, headers, columnTypes, dropIfExists);

            return insertData(tableName, headers, sheet);
        }
    }

    private List<String> getHeaders(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        Row headerRow = sheet.getRow(0);

        int columnCount = headerRow.getPhysicalNumberOfCells();

        for (int i = 0; i < columnCount; i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null || cell.getCellType() == CellType.BLANK) {
                continue;
            }

            String headerValue = cell.getStringCellValue();
            if (headerValue == null || headerValue.trim().isEmpty()) {
                continue;
            }
            String header = sanitizeColumnName(headerValue.trim());

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
    private List<String> inferColumnTypes(Sheet sheet, List<String> headers) {
        List<String> types = new ArrayList<>();

        for (int col = 0; col < headers.size(); col++) {
            String type = inferColumnType(sheet, col);
            types.add(type);
        }
        
        return types;
    }

    private String inferColumnType(Sheet sheet, int columnIndex) {
        boolean hasIntegers = false;
        boolean hasDecimals = false;
        boolean hasDates = false;
        boolean hasBooleans = false;
        boolean hasStrings = false;
        int maxLength = 0;
        int nonEmptyCount = 0;

        Row headerRow = sheet.getRow(0);
        String columnName = headerRow.getCell(columnIndex).getStringCellValue();

        int sheetLastRowNum = sheet.getLastRowNum();
        for (int i = 1; i <= sheetLastRowNum; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            Cell cell = row.getCell(columnIndex);
            if (cell == null || cell.getCellType() == CellType.BLANK) continue;
            
            nonEmptyCount++;

            switch (cell.getCellType()) {
                case STRING:
                    hasStrings = true;
                    maxLength = Math.max(maxLength, cell.getStringCellValue().length());
                    break;
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        hasDates = true;
                    } else {
                        double value = cell.getNumericCellValue();
                        if (value != Math.floor(value)) {
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
        else if (hasBooleans && !hasStrings && !hasDecimals && !hasIntegers && !hasDates) {
            detectedType = "BOOLEAN";
        }
        else if (hasDates && !hasStrings && !hasIntegers && !hasDecimals) {
            detectedType = "DATE";
        }
        else if (hasDecimals) {
            detectedType = "DECIMAL(15, 2)";
        }
        else if (hasIntegers) {
            detectedType = "INTEGER";
        }
        else if (hasStrings) {
            if (maxLength > 0 && maxLength <= 255) {
                detectedType = "VARCHAR(" + (maxLength + 50) + ")";
            } else {
                detectedType = "TEXT";
            }
        }
        else {
            detectedType = "TEXT";
        }
        System.out.println("Column '" + columnName + "' detected as: " + detectedType +
            " (strings=" + hasStrings + ", dates=" + hasDates + ", integers=" + hasIntegers +
            ", decimals=" + hasDecimals + ", booleans=" + hasBooleans + ")");

        return detectedType;
    }

    private String sanitizeColumnName(String name) {
        String sanitized = name.replaceAll("[^\\p{L}\\p{Nd}_]", "_");

        if (sanitized.matches("^[0-9].*")) {
            sanitized = "col_" + sanitized;
        }

        return sanitized.toLowerCase();
    }

    private void createTable(String tableName, List<String> headers, List<String> columnTypes, boolean dropIfExists) throws SQLException {
        StringBuilder sql = new StringBuilder();
        
        if (dropIfExists) {
            sql.append("DROP TABLE IF EXISTS ").append(tableName).append(";\n");
        }
        
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

        boolean hasIdColumn = headers.stream().anyMatch(h -> h.equalsIgnoreCase("id"));

        if (!hasIdColumn) {
            sql.append("    id SERIAL PRIMARY KEY,\n");
        }

        for (int i = 0; i < headers.size(); i++) {
            sql.append("    ").append(headers.get(i)).append(" ").append(columnTypes.get(i));

            if (headers.get(i).equalsIgnoreCase("id")) {
                sql.append(" PRIMARY KEY");
            }

            if (i < headers.size() - 1) {
                sql.append(",");
            }
            sql.append("\n");
        }
        
        sql.append(");");
        
        System.out.println("Creating table with SQL:\n" + sql + "\n");
        
        try (Connection conn = DatabaseConfig.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
            System.out.println("Table '" + tableName + "' created successfully.");
        }
    }

    private int insertData(String tableName, List<String> headers, Sheet sheet) throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");
        
        for (int i = 0; i < headers.size(); i++) {
            sql.append(headers.get(i));
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
        
        int rowsInserted = 0;
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement preparedStatement = conn.prepareStatement(sql.toString())) {

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                boolean isRowEmpty = true;
                for (int col = 0; col < headers.size(); col++) {
                    Cell cell = row.getCell(col);
                    if (cell != null && cell.getCellType() != CellType.BLANK) {
                        isRowEmpty = false;
                        break;
                    }
                }

                if (isRowEmpty) {
                    continue;
                }

                for (int col = 0; col < headers.size(); col++) {
                    Cell cell = row.getCell(col);
                    setPreparedStatementValue(preparedStatement, col + 1, cell);
                }

                preparedStatement.addBatch();
                rowsInserted++;

                if (rowsInserted % 100 == 0) {
                    preparedStatement.executeBatch();
                }
            }

            preparedStatement.executeBatch();
        }
        
        System.out.println(rowsInserted + " rows inserted into '" + tableName + "'.");
        return rowsInserted;
    }

    private void setPreparedStatementValue(PreparedStatement pstmt, int index, Cell cell) throws SQLException {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            pstmt.setNull(index, Types.NULL);
            return;
        }
        
        switch (cell.getCellType()) {
            case STRING:
                pstmt.setString(index, cell.getStringCellValue());
                break;
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    pstmt.setDate(index, new java.sql.Date(cell.getDateCellValue().getTime()));
                } else {
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        pstmt.setInt(index, (int) value);
                    } else {
                        pstmt.setDouble(index, value);
                    }
                }
                break;
            case BOOLEAN:
                pstmt.setBoolean(index, cell.getBooleanCellValue());
                break;
            case FORMULA:
                switch (cell.getCachedFormulaResultType()) {
                    case STRING:
                        pstmt.setString(index, cell.getStringCellValue());
                        break;
                    case NUMERIC:
                        pstmt.setDouble(index, cell.getNumericCellValue());
                        break;
                    case BOOLEAN:
                        pstmt.setBoolean(index, cell.getBooleanCellValue());
                        break;
                    default:
                        pstmt.setString(index, cell.toString());
                }
                break;
            default:
                pstmt.setString(index, cell.toString());
        }
    }
}
