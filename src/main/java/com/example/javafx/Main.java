package com.example.javafx;

import com.example.javafx.database.DatabaseConfig;
import com.example.javafx.database.ExcelToPostgreSQL;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.sql.SQLException;

public class Main extends Application{

    public static void main(String[] args) {
        importExcelToPostgreSQLDemo();
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
        Parent root = loader.load();


        com.example.javafx.controller.MainController controller = loader.getController();
        primaryStage.setOnCloseRequest(event -> {
            controller.saveSessionData();
        });

        Scene scene = new Scene(root);
        primaryStage.setTitle("Excel to PostgreSQL Importer");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.show();
    }

    public static void importExcelToPostgreSQLDemo() {
        String excelFilePath = "sample_data.xlsx";
        String tableName = excelFilePath.substring(0,excelFilePath.lastIndexOf("."));

        System.out.println("=== Excel to PostgreSQL Import Demo ===");
        System.out.println();

        System.out.println("Testing database connection.");
        if (!DatabaseConfig.testConnection()) {
            System.err.println("Failed to connect to database. Please check:");
            System.err.println("1. PostgreSQL is running");
            System.err.println("2. Database exists");
            System.err.println("3. application.properties has correct credentials");
            return;
        } else {
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
}
