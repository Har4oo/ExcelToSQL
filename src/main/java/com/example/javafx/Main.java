package com.example.javafx;

import com.example.javafx.database.DatabaseConfig;
import com.example.javafx.database.ExcelToPostgreSQL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 320, 240);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        //createSampleExcelFile();

        importExcelToPostgreSQLDemo();
        //System.out.println(System.getProperty("javafx.runtime.version"));
        //launch();
    }
    public static void importExcelToPostgreSQLDemo() {
        String excelFilePath = "sample_data.xlsx";
        String tableName = "employees";

        System.out.println("=== Excel to PostgreSQL Import Demo ===");
        System.out.println();

        // Test database connection first
        System.out.println("Testing database connection...");
        if (!DatabaseConfig.testConnection()) {
            System.err.println("Failed to connect to database. Please check:");
            System.err.println("1. PostgreSQL is running");
            System.err.println("2. Database exists");
            System.err.println("3. application.properties has correct credentials");
            return;
        }else {
            System.out.println("Database connection successful!\n");
        }
        try {
            ExcelToPostgreSQL importer = new ExcelToPostgreSQL();
            int rowsImported = importer.importExcelToTable(excelFilePath, tableName, true);

            System.out.println("\n=== Import Complete ===");
            System.out.println("Table: " + tableName);
            System.out.println("Rows imported: " + rowsImported);

        } catch (IOException e) {
            System.err.println("Error reading Excel file: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void createSampleExcelFile() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Employees");

            // Create header row
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("ID");
            headerRow.createCell(1).setCellValue("Name");
            headerRow.createCell(2).setCellValue("Department");
            headerRow.createCell(3).setCellValue("Salary");
            headerRow.createCell(4).setCellValue("Hire Date");

            // Sample data
            Object[][] data = {
                    {1, "John Smith", "Engineering", 75000, "2020-01-15"},
                    {2, "Jane Doe", "Marketing", 65000, "2019-06-20"},
                    {3, "Bob Johnson", "Sales", 55000, "2021-03-10"},
                    {4, "Alice Brown", "Engineering", 80000, "2018-11-05"},
                    {5, "Charlie Wilson", "HR", 50000, "2022-02-28"},
                    {6, "Diana Ross", "Marketing", 70000, "2020-09-12"},
                    {7, "Edward Miller", "Engineering", 85000, "2017-07-22"},
                    {8, "Fiona Davis", "Sales", 60000, "2021-05-18"},
                    {9, "George Clark", "HR", 52000, "2022-08-30"},
                    {10, "Helen White", "Engineering", 90000, "2016-04-14"}
            };

            for (int i = 0; i < data.length; i++) {
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue((Integer) data[i][0]);
                row.createCell(1).setCellValue((String) data[i][1]);
                row.createCell(2).setCellValue((String) data[i][2]);
                row.createCell(3).setCellValue((Integer) data[i][3]);
                row.createCell(4).setCellValue((String) data[i][4]);
            }

            for (int i = 0; i < headerRow.getRowNum(); i++) {
                sheet.autoSizeColumn(i);
            }
            try (FileOutputStream out = new FileOutputStream("sample_data.xlsx")) {
                workbook.write(out);

            }
        } catch (IOException e) {
            System.err.println("Error creating sample file: " + e.getMessage());
        }
    }

    /**
     * Reads an Excel file and prints its contents to the console
     *
     * @param filePath the path to the Excel file to read
     */
    public static void readAndPrintExcelFile(String filePath) {
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(filePath))) {
            System.out.println("Reading file: " + filePath);
            System.out.println("Number of sheets: " + workbook.getNumberOfSheets());
            System.out.println();

            // Iterate through all sheets
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                System.out.println("Sheet: \"" + sheet.getSheetName() + "\"");
                System.out.println("-".repeat(60));

                // Iterate through all rows
                for (Row row : sheet) {
                    StringBuilder rowBuilder = new StringBuilder();

                    // Iterate through all cells in the row
                    for (Cell cell : row) {
                        String cellValue = getCellValueAsString(cell);
                        rowBuilder.append(String.format("%-20s", cellValue));
                    }
                    System.out.println(rowBuilder);
                }
                System.out.println("-".repeat(60));
                System.out.println("Total rows: " + (sheet.getPhysicalNumberOfRows() - 1)); // excluding header
                System.out.println();
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }

    /**
     * Helper method to get cell value as string based on cell type
     *
     * @param cell the cell to read
     * @return the cell value as a string
     */
    private static String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                // Check if it's a date
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    yield String.valueOf((int) cell.getNumericCellValue());
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK -> "";
            default -> "?";
        };
    }
}