package com.example.javafx.controller;

import com.example.javafx.database.DatabaseConfig;
import com.example.javafx.database.ExcelToPostgreSQL;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.prefs.Preferences;

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
    private TableView<ObservableList<String>> previewTableView;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label progressLabel;

    @FXML
    private TextArea logArea;

    @FXML
    private Button clearLogButton;

    @FXML
    private Button copyLogButton;


    @FXML
    private Button loadPreviewButton;

    @FXML
    private Button importButton;

    @FXML
    private Button cancelButton;


    private File selectedFile;

    private Preferences prefs;

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(MainController.class);

        hostField.setText(prefs.get("host", "localhost"));
        portField.setText(prefs.get("port", "5432"));
        databaseField.setText(prefs.get("database", ""));
        schemaField.setText(prefs.get("schema", "public"));
        usernameField.setText(prefs.get("username", "postgres"));

        tableNameField.setText(prefs.get("tableName", ""));
        dropTableCheckBox.setSelected(prefs.getBoolean("dropTable", true));
        createIdCheckBox.setSelected(prefs.getBoolean("createId", true));

        String lastFilePath = prefs.get("filePath", "");
        if (!lastFilePath.isEmpty()) {
            File savedFile = new File(lastFilePath);
            if (savedFile.exists()) {
                selectedFile = savedFile;
                filePathField.setText(lastFilePath);
                progressLabel.setText("Restored previous file: " + savedFile.getName());
            }
        }

        importButton.setDisable(true);
        progressBar.setProgress(0);

        filePathField.textProperty().addListener((observable, oldValue, newValue) -> {
            updateImportButtonState();
        });
        updateImportButtonState();

        System.out.println("MainController initialized and preferences loaded!");
    }

    public void saveSessionData() {
        prefs.put("host", hostField.getText().trim());
        prefs.put("port", portField.getText().trim());
        prefs.put("database", databaseField.getText().trim());
        prefs.put("schema", schemaField.getText().trim());
        prefs.put("username", usernameField.getText().trim());
        prefs.put("filePath", filePathField.getText().trim());
        prefs.put("tableName", tableNameField.getText().trim());
        prefs.putBoolean("dropTable", dropTableCheckBox.isSelected());
        prefs.putBoolean("createId", createIdCheckBox.isSelected());
        System.out.println("Session data saved successfully.");
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
        if (selectedFile == null) {
            showProgressError("Please select an Excel file first" );
            return;
        }

        progressLabel.setText("Loading preview..." );
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        loadPreviewButton.setDisable(true);
        log("Loading preview from: " + selectedFile.getAbsolutePath());

        final File fileToPreview = selectedFile;
        Task<ExcelToPostgreSQL.Preview> previewTask = new Task<>() {
            @Override
            protected ExcelToPostgreSQL.Preview call() throws Exception {
                ExcelToPostgreSQL importer = new ExcelToPostgreSQL();
                importer.setLogger(MainController.this::log);
                return importer.loadPreview(fileToPreview.getAbsolutePath(), 100);
            }
        };

        previewTask.setOnSucceeded(e -> {
            populatePreview(previewTask.getValue());
            showProgressSuccess("Preview loaded: " + previewTask.getValue().rows.size() + " rows shown." );
            progressBar.setProgress(0);
            loadPreviewButton.setDisable(false);
        });

        previewTask.setOnFailed(e -> {
            Throwable error = previewTask.getException();
            showProgressError("Error loading preview: " + error.getMessage());
            logThrowable("Preview failed", error);
            progressBar.setProgress(0);
            loadPreviewButton.setDisable(false);
        });

        new Thread(previewTask).start();
    }

    private void populatePreview(ExcelToPostgreSQL.Preview preview) {
        previewTableView.getColumns().clear();
        previewTableView.getItems().clear();

        for (int i = 0; i < preview.columns.size(); i++) {
            final int colIndex = i;
            ExcelToPostgreSQL.ColumnMeta meta = preview.columns.get(i);
            TableColumn<ObservableList<String>, String> column =
                    new TableColumn<>(meta.name + "\n(" + meta.type + ")");
            column.setCellValueFactory(cellData -> {
                ObservableList<String> row = cellData.getValue();
                String value = (colIndex < row.size()) ? row.get(colIndex) : "";
                return new javafx.beans.property.SimpleStringProperty(value);
            });
            column.setPrefWidth(140);
            previewTableView.getColumns().add(column);
        }

        ObservableList<ObservableList<String>> items = FXCollections.observableArrayList();
        for (java.util.List<String> row : preview.rows) {
            items.add(FXCollections.observableArrayList(row));
        }
        previewTableView.setItems(items);
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

        // Apply the connection details currently shown in the form, so Import works even if the
        // user never pressed "Test Connection" (otherwise it silently falls back to application.properties).
        applyConnectionFromForm();

        progressLabel.setText("Importing data... Please wait." );
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        importButton.setDisable(true);
        loadPreviewButton.setDisable(true);

        final String finalTableName = tableName;

        log("========================================================");
        log("Starting import of '" + selectedFile.getName() + "' into table '" + finalTableName +
                "' (dropIfExists=" + dropIfExists + ")");

        Task<Integer> importTask = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                ExcelToPostgreSQL importer = new ExcelToPostgreSQL();
                importer.setLogger(MainController.this::log);
                return importer.importExcelToTable(selectedFile.getAbsolutePath(), finalTableName, dropIfExists);
            }
        };

        importTask.setOnSucceeded(e -> {
            int rowsInserted = importTask.getValue();
            showProgressSuccess("Success! " + rowsInserted + " rows inserted into '" + finalTableName + "'." );
            log("Import finished successfully: " + rowsInserted + " rows.");
            progressBar.setProgress(1.0);
            importButton.setDisable(false);
            loadPreviewButton.setDisable(false);
        });

        importTask.setOnFailed(e -> {
            Throwable error = importTask.getException();
            showProgressError("Import failed: " + rootMessage(error));
            logThrowable("IMPORT FAILED", error);
            progressBar.setProgress(0);
            importButton.setDisable(false);
            loadPreviewButton.setDisable(false);
        });
        saveSessionData();
        new Thread(importTask).start();
    }

    @FXML
    private void onClearLogButtonClick() {
        logArea.clear();
    }

    @FXML
    private void onCopyLogButtonClick() {
        ClipboardContent content = new ClipboardContent();
        content.putString(logArea.getText());
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** Thread-safe append to the on-screen log, with a timestamp. */
    private void log(String message) {
        String stamped = "[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] " + message;
        System.out.println(stamped);
        if (Platform.isFxApplicationThread()) {
            logArea.appendText(stamped + "\n");
        } else {
            Platform.runLater(() -> logArea.appendText(stamped + "\n"));
        }
    }

    /** Log a full throwable, including its cause chain and stack trace, so SQL errors are debuggable. */
    private void logThrowable(String title, Throwable error) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println(title + ": " + error.getMessage());
        Throwable cause = error.getCause();
        while (cause != null) {
            pw.println("Caused by: " + cause.getClass().getName() + ": " + cause.getMessage());
            cause = cause.getCause();
        }
        pw.println("--- stack trace ---");
        error.printStackTrace(pw);
        log(sw.toString());
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return (msg != null) ? msg.split("\n")[0] : cause.getClass().getSimpleName();
    }

    /** Push the connection details from the form into DatabaseConfig for the next getConnection(). */
    private void applyConnectionFromForm() {
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String database = databaseField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        String url = String.format("jdbc:postgresql://%s:%s/%s",
                host.isEmpty() ? "localhost" : host,
                port.isEmpty() ? "5432" : port,
                database);
        DatabaseConfig.setCredentials(url, username, password);
    }

    @FXML
    private void onCancelButtonClick() {
        System.out.println("Cancel button clicked!");
        saveSessionData();
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