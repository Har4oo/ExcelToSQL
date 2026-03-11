module com.example.javafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires jdk.compiler;
    requires java.sql;


    opens com.example.javafx to javafx.fxml;
    opens com.example.javafx.controller to javafx.fxml;
    exports com.example.javafx;
    exports com.example.javafx.controller;
}