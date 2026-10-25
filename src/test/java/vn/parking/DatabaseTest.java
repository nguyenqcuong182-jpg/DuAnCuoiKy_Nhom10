package vn.parking;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DatabaseTest {
    @Test
    void opensConnectionUsingEnvironmentOverridesOrDefaults() throws SQLException {
        String expectedUrl = setting("PARKING_DB_URL",
                "jdbc:mysql://localhost:3306/parking_management?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true");
        String expectedUser = setting("PARKING_DB_USER", "root");
        String expectedPassword = setting("PARKING_DB_PASSWORD", "");
        Connection expectedConnection = mock(Connection.class);

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                    .thenReturn(expectedConnection);

            assertSame(expectedConnection, Database.getConnection());

            driverManager.verify(() -> DriverManager.getConnection(expectedUrl, expectedUser, expectedPassword));
        }
    }

    @Test
    void prefersSystemPropertiesWhenDatabaseSettingsAreProvided() throws SQLException {
        String originalUrl = System.getProperty("PARKING_DB_URL");
        String originalUser = System.getProperty("PARKING_DB_USER");
        String originalPassword = System.getProperty("PARKING_DB_PASSWORD");
        String expectedUrl = "jdbc:mysql://localhost:3307/parking_override";
        String expectedUser = "dbuser";
        String expectedPassword = "secret";
        Connection expectedConnection = mock(Connection.class);

        try {
            System.setProperty("PARKING_DB_URL", expectedUrl);
            System.setProperty("PARKING_DB_USER", expectedUser);
            System.setProperty("PARKING_DB_PASSWORD", expectedPassword);

            try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
                driverManager.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                        .thenReturn(expectedConnection);

                assertSame(expectedConnection, Database.getConnection());

                driverManager.verify(() -> DriverManager.getConnection(expectedUrl, expectedUser, expectedPassword));
            }
        } finally {
            restoreSystemProperty("PARKING_DB_URL", originalUrl);
            restoreSystemProperty("PARKING_DB_USER", originalUser);
            restoreSystemProperty("PARKING_DB_PASSWORD", originalPassword);
        }
    }

    private static String setting(String name, String fallback) {
        String value = System.getProperty(name);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void restoreSystemProperty(String name, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, originalValue);
        }
    }
}