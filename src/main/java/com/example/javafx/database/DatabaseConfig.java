package com.example.javafx.database;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Database configuration and connection manager.
 * Loads connection settings from application.properties file.
 */
public class DatabaseConfig {

    private static final String PROPERTIES_FILE = "application.properties";
    private static Properties properties;

    static {
        loadProperties();
    }

    /**
     * Load database properties from the configuration file
     */
    private static void loadProperties() {
        properties = new Properties();
        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + PROPERTIES_FILE + " in classpath");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error loading database properties", e);
        }
    }

    /**
     * Get a connection to the PostgreSQL database
     * 
     * @return Connection object
     * @throws SQLException if connection fails
     */
    public static Connection getConnection() throws SQLException {
        String url = properties.getProperty("db.url");
        String username = properties.getProperty("db.username");
        String password = properties.getProperty("db.password");

        return DriverManager.getConnection(url, username, password);
    }

    /**
     * Get the database URL
     */
    public static String getUrl() {
        return properties.getProperty("db.url");
    }

    /**
     * Get the database username
     */
    public static String getUsername() {
        return properties.getProperty("db.username");
    }

    /**
     * Get the database schema
     */
    public static String getSchema() {
        return properties.getProperty("db.schema", "public");
    }

    /**
     * Test the database connection
     * 
     * @return true if connection is successful
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            return false;
        }
    }
}
