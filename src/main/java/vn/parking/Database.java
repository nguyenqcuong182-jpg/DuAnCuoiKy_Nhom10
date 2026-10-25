package vn.parking;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {
    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/parking_management?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true";

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        String url = setting("PARKING_DB_URL", DEFAULT_URL);
        String user = setting("PARKING_DB_USER", "root");
        String password = setting("PARKING_DB_PASSWORD", "");
        return DriverManager.getConnection(url, user, password);
    }

    private static String setting(String name, String fallback) {
        String value = System.getProperty(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}