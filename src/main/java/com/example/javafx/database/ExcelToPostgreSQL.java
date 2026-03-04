package com.example.javafx.database;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an Excel file and creates a corresponding PostgreSQL table with dynamic schema.
 * This class automatically infers column types from Excel data and creates the table
 * without requiring a predefined entity class.
 */
public class ExcelToPostgreSQL {

    /**
     * Main method to import an Excel file into PostgreSQL
     * 
     * @param filePath     Path to the Excel file
     * @param tableName    Name of the table to create (will be created if not exists)
     * @param dropIfExists Whether to drop the table if it already exists
     * @return Number of rows imported
     */
    public int importExcelToTable(String filePath, String tableName, boolean dropIfExists) throws IOException, SQLException {
        // Read Excel file
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
            Sheet sheet = workbook.getSheetAt(0); // Use first sheet
            
            // Get headers from first row
            List<String> headers = getHeaders(sheet);
            
            // Infer column types from data rows
            List<String> columnTypes = inferColumnTypes(sheet);
            
            // Create or replace table
            createTable(tableName, headers, columnTypes, dropIfExists);
            
            // Insert data
            return insertData(tableName, headers, sheet);
        }
    }

    /**
     * Extract headers from the first row of the Excel sheet
     */
    private List<String> getHeaders(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        
        for (Cell cell : headerRow) {
            String header = sanitizeColumnName(cell.getStringCellValue().trim());
            headers.add(header);
        }
        
        return headers;
    }

    /**
     * Infer PostgreSQL column types based on Excel data
     * Analyzes all data rows to determine the best type for each column
     */
    private List<String> inferColumnTypes(Sheet sheet) {
        List<String> types = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        int columnCount = headerRow.getPhysicalNumberOfCells();
        
        for (int col = 0; col < columnCount; col++) {
            String type = inferColumnType(sheet, col);
            types.add(type);
        }
        
        return types;
    }

    /**
     * Infer the type of a specific column by analyzing all its values
     */
    private String inferColumnType(Sheet sheet, int columnIndex) {
        boolean hasIntegers = true;
        boolean hasDecimals = true;
        boolean hasDates = true;
        boolean hasBooleans = true;
        int maxLength = 0;
        
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            
            Cell cell = row.getCell(columnIndex);
            if (cell == null || cell.getCellType() == CellType.BLANK) continue;
            
            switch (cell.getCellType()) {
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        hasIntegers = false;
                        hasDecimals = false;
                        hasBooleans = false;
                    } else {
                        double value = cell.getNumericCellValue();
                        if (value != Math.floor(value)) {
                            hasIntegers = false;
                        }
                        hasDates = false;
                        hasBooleans = false;
                    }
                    break;
                case STRING:
                    hasIntegers = false;
                    hasDecimals = false;
                    hasDates = false;
                    hasBooleans = false;
                    maxLength = Math.max(maxLength, cell.getStringCellValue().length());
                    break;
                case BOOLEAN:
                    hasIntegers = false;
                    hasDecimals = false;
                    hasDates = false;
                    break;
                default:
                    hasIntegers = false;
                    hasDecimals = false;
                    hasDates = false;
                    hasBooleans = false;
            }
        }
        
        // Determine the most appropriate type
        if (hasDates && !hasIntegers && !hasDecimals && !hasBooleans) {
            return "DATE";
        }
        if (hasBooleans && !hasIntegers && !hasDecimals) {
            return "BOOLEAN";
        }
        if (hasIntegers && !hasDecimals) {
            return "INTEGER";
        }
        if (hasDecimals) {
            return "DECIMAL(15, 2)";
        }
        
        // Default to TEXT or VARCHAR
        if (maxLength > 0 && maxLength <= 255) {
            return "VARCHAR(" + (maxLength + 50) + ")"; // Add buffer
        }
        return "TEXT";
    }

    /**
     * Sanitize column name to be PostgreSQL-compatible
     */
    private String sanitizeColumnName(String name) {
        // Replace spaces and special characters with underscores
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        // Ensure it doesn't start with a number
        if (sanitized.matches("^[0-9].*")) {
            sanitized = "col_" + sanitized;
        }
        // Convert to lowercase for PostgreSQL convention
        return sanitized.toLowerCase();
    }

    /**
     * Create the PostgreSQL table with the inferred schema
     */
    private void createTable(String tableName, List<String> headers, List<String> columnTypes, boolean dropIfExists) throws SQLException {
        StringBuilder sql = new StringBuilder();
        
        if (dropIfExists) {
            sql.append("DROP TABLE IF EXISTS ").append(tableName).append(";\n");
        }
        
        sql.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

        // Check if 'id' column already exists in headers
        boolean hasIdColumn = headers.stream().anyMatch(h -> h.equalsIgnoreCase("id"));

        // Only add auto-generated id if not present in Excel
        if (!hasIdColumn) {
            sql.append("    id SERIAL PRIMARY KEY,\n"); // Auto-increment ID
        }

        for (int i = 0; i < headers.size(); i++) {
            sql.append("    ").append(headers.get(i)).append(" ").append(columnTypes.get(i));

            // Make the Excel's ID column the primary key if it exists
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

    /**
     * Insert data from Excel into the PostgreSQL table
     */
    private int insertData(String tableName, List<String> headers, Sheet sheet) throws SQLException {
        // Build INSERT statement
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
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            
            // Skip header row (start from row 1)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                for (int col = 0; col < headers.size(); col++) {
                    Cell cell = row.getCell(col);
                    setPreparedStatementValue(pstmt, col + 1, cell);
                }
                
                pstmt.addBatch();
                rowsInserted++;
                
                // Execute batch every 100 rows for performance
                if (rowsInserted % 100 == 0) {
                    pstmt.executeBatch();
                }
            }
            
            // Execute remaining batch
            pstmt.executeBatch();
        }
        
        System.out.println(rowsInserted + " rows inserted into '" + tableName + "'.");
        return rowsInserted;
    }

    /**
     * Set a PreparedStatement parameter based on cell type
     */
    private void setPreparedStatementValue(PreparedStatement pstmt, int index, Cell cell) throws SQLException {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            pstmt.setNull(index, Types.VARCHAR);
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
                // Evaluate formula and set the result
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
