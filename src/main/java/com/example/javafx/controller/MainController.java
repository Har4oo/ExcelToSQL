package com.example.javafx.controller;

import com.example.javafx.database.DatabaseConfig;
import com.example.javafx.database.ExcelToPostgreSQL;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class MainController {

    @FXML
    private TextField filePathField;

    @FXML
    private Button browseButton;

    @FXML
    private TextField hostField;

    @FXML
    private TextField portField;

    @FXML
    private TextField databaseField;

    @FXML
    private TextField schemaField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button testConnectionButton;

    @FXML
    private Label connectionStatusLabel;

    @FXML
    private TextField tableNameField;

    @FXML
    private CheckBox dropTableCheckBox;

    @FXML
    private CheckBox createIdCheckBox;

    @FXML
    private TableView<Object> previewTableView;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label progressLabel;


    @FXML
    private Button loadPreviewButton;

    @FXML
    private Button importButton;

    @FXML
    private Button cancelButton;


    private File selectedFile;


    @FXML
    public void initialize() {

//        hostField.setText("localhost" );
//        portField.setText("5432" );
//        schemaField.setText("public" );
//        usernameField.setText("postgres" );
//        progressBar.setProgress(0);
//        progressLabel.setText("Ready" );


        importButton.setDisable(true);

        filePathField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateImportButtonState();
        });

        System.out.println("MainController initialized successfully!" );
    }

    @FXML
    private void onBrowseButtonClick() {
        System.out.println("Browse button clicked!" );

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Excel File" );


        FileChooser.ExtensionFilter excelFilter = new FileChooser.ExtensionFilter("Excel Files (*.xlsx, *.xls)", "*.xlsx", "*.xls" );
        fileChooser.getExtensionFilters().add(excelFilter);


        Stage stage = (Stage) browseButton.getScene().getWindow();


        selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            filePathField.setText(selectedFile.getAbsolutePath());
            progressLabel.setText("File selected: " + selectedFile.getName());
            System.out.println("Selected file: " + selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void onTestConnectionButtonClick() {
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String database = databaseField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (database.isEmpty() || username.isEmpty()) {
            showConnectionError("Please enter database and username" );
            return;
        }

        connectionStatusLabel.setText("Testing connection..." );
        connectionStatusLabel.setStyle("-fx-text-fill: orange;" );
        testConnectionButton.setDisable(true);

        String url = String.format("jdbc:postgresql://%s:%s/%s",
                host.isEmpty() ? "localhost" : host,
                port.isEmpty() ? "5432" : port,
                database);

        Task<Void> testTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, username, password)) {
                    DatabaseConfig.setCredentials(url, username, password);
                }
                return null;
            }
        };

        testTask.setOnSucceeded(e -> {
            showConnectionSuccess("Connection successful!" );
            testConnectionButton.setDisable(false);
            updateImportButtonState();
        });

        testTask.setOnFailed(e -> {
            showConnectionError("Connection failed: " + testTask.getException().getMessage());
            testConnectionButton.setDisable(false);
        });

        new Thread(testTask).start();
    }

    @FXML
    private void onLoadPreviewButtonClick() {
        System.out.println("Load Preview button clicked!" );

        if (selectedFile == null) {
            showProgressError("Please select an Excel file first" );
            return;
        }

        progressLabel.setText("Loading preview..." );
        progressBar.setProgress(-1);

        try {
            System.out.println("Loading preview from: " + selectedFile.getAbsolutePath());

            Thread.sleep(500);

            showProgressSuccess("Preview loaded successfully! (simulated)" );
            progressBar.setProgress(1);

        } catch (Exception e) {
            showProgressError("Error loading preview: " + e.getMessage());
            progressBar.setProgress(0);
        }
    }

    @FXML
    private void onImportButtonClick() {
        if (selectedFile == null) return;

        String tableName = tableNameField.getText().trim();
        if (tableName.isEmpty()) {
            tableName = selectedFile.getName().substring(0, selectedFile.getName().lastIndexOf('.'));
            tableName = tableName.replaceAll("[^a-zA-Z0-9_]", "_" ).toLowerCase();
        }

        boolean dropIfExists = dropTableCheckBox.isSelected();

        progressLabel.setText("Importing data... Please wait." );
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        importButton.setDisable(true);
        loadPreviewButton.setDisable(true);

        final String finalTableName = tableName;

        Task<Integer> importTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                ExcelToPostgreSQL importer = new ExcelToPostgreSQL();

                return importer.importExcelToTable(selectedFile.getAbsolutePath(), finalTableName, dropIfExists);
            }
        };

        importTask.setOnSucceeded(e -> {
            int rowsInserted = importTask.getValue();
            showProgressSuccess("Success! " + rowsInserted + " rows inserted into '" + finalTableName + "'." );
            progressBar.setProgress(1.0);
            importButton.setDisable(false);
            loadPreviewButton.setDisable(false);
        });

        importTask.setOnFailed(e -> {
            Throwable error = importTask.getException();
            showProgressError("Import failed: " + error.getMessage());
            progressBar.setProgress(0);
            importButton.setDisable(false);
            loadPreviewButton.setDisable(false);
            error.printStackTrace();
        });
        new Thread(importTask).start();
    }

    @FXML
    private void onCancelButtonClick() {
        System.out.println("Cancel button clicked!" );

        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    private void updateImportButtonState() {
        boolean hasFile = !filePathField.getText().isEmpty();
        boolean hasDatabase = !databaseField.getText().trim().isEmpty();

        importButton.setDisable(!(hasFile && hasDatabase));
    }

    private void showConnectionSuccess(String message) {
        connectionStatusLabel.setText(message);
        connectionStatusLabel.setStyle("-fx-text-fill: green;" );
    }

    private void showConnectionError(String message) {
        connectionStatusLabel.setText(message);
        connectionStatusLabel.setStyle("-fx-text-fill: red;" );
    }


    private void showProgressSuccess(String message) {
        progressLabel.setText(message);
        progressLabel.setStyle("-fx-text-fill: green;" );
    }

    private void showProgressError(String message) {
        progressLabel.setText(message);
        progressLabel.setStyle("-fx-text-fill: red;" );
    }
}