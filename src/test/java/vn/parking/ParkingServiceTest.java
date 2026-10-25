package vn.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ParkingServiceTest {
    // Stable fixture values keep tests independent from a live database.
    private static final String PLATE = "59A-12345";
    private ParkingService parkingService;

    @BeforeEach
    void setUp() {
        parkingService = new ParkingService();
    }

    @Test
    void checkInRejectsBlankPlateBeforeOpeningConnection() {
        assertEquals("Biển số xe không được để trống.",
                assertThrows(IllegalArgumentException.class, () -> parkingService.checkIn(" ", "CAR"))
                        .getMessage());
    }

    @Test
    void checkInRejectsUnsupportedVehicleTypeBeforeOpeningConnection() {
        assertEquals("Loại xe không hợp lệ.",
                assertThrows(IllegalArgumentException.class, () -> parkingService.checkIn(PLATE, "BUS"))
                        .getMessage());
    }

    @Test
    void checkOutRejectsMissingPlateBeforeOpeningConnection() {
        assertEquals("Biển số xe không được để trống.",
                assertThrows(IllegalArgumentException.class, () -> parkingService.checkOut(null)).getMessage());
    }

    @Test
    void updateActiveLicensePlateRejectsBlankPlateBeforeOpeningConnection() {
        assertEquals("Biển số xe không được để trống.",
                assertThrows(IllegalArgumentException.class,
                        () -> parkingService.updateActiveLicensePlate(PLATE, " ")).getMessage());
    }

    @Test
    void addSlotRejectsInvalidSlotNumberBeforeOpeningConnection() {
        assertEquals("Mã ô chỉ được gồm 1-10 chữ cái, chữ số hoặc dấu gạch ngang.",
                assertThrows(IllegalArgumentException.class,
                        () -> parkingService.addSlot("M-01-too-long", "CAR")).getMessage());
    }

    @Test
    void addSlotRejectsUnsupportedVehicleTypeBeforeOpeningConnection() {
        assertEquals("Loại xe không hợp lệ.",
                assertThrows(IllegalArgumentException.class,
                        () -> parkingService.addSlot("M11", "BUS")).getMessage());
    }

    @Test
    void updateAvailableSlotRejectsInvalidNumberBeforeOpeningConnection() {
        assertEquals("Mã ô chỉ được gồm 1-10 chữ cái, chữ số hoặc dấu gạch ngang.",
                assertThrows(IllegalArgumentException.class,
                        () -> parkingService.updateAvailableSlot("M01", " ", "CAR")).getMessage());
    }

    @Test
    void deleteAvailableSlotRejectsInvalidNumberBeforeOpeningConnection() {
        assertEquals("Mã ô chỉ được gồm 1-10 chữ cái, chữ số hoặc dấu gạch ngang.",
                assertThrows(IllegalArgumentException.class,
                        () -> parkingService.deleteAvailableSlot("")).getMessage());
    }

    @Test
    void updateVehicleRateRejectsNonPositiveRateBeforeOpeningConnection() {
        assertEquals("Giá phải là số nguyên dương, tối đa 12 chữ số.",
                assertThrows(IllegalArgumentException.class,
                        () -> parkingService.updateVehicleRate("CAR", BigDecimal.ZERO)).getMessage());
    }

    @Test
    void updateVehicleRateRejectsFractionalRateBeforeOpeningConnection() {
        assertEquals("Giá phải là số nguyên dương, tối đa 12 chữ số.",
                assertThrows(IllegalArgumentException.class,
                        () -> parkingService.updateVehicleRate("CAR", new BigDecimal("15000.5"))).getMessage());
    }

    @Test
    void checkInNormalizesPlateAndCommitsWhenSlotIsAvailable() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement duplicateStatement = mock(PreparedStatement.class);
        PreparedStatement findSlotStatement = mock(PreparedStatement.class);
        PreparedStatement updateSlotStatement = mock(PreparedStatement.class);
        PreparedStatement insertSessionStatement = mock(PreparedStatement.class);
        ResultSet duplicateRows = mock(ResultSet.class);
        ResultSet slotRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(duplicateStatement, findSlotStatement,
                updateSlotStatement, insertSessionStatement);
        when(duplicateStatement.executeQuery()).thenReturn(duplicateRows);
        when(duplicateRows.next()).thenReturn(false);
        when(findSlotStatement.executeQuery()).thenReturn(slotRows);
        when(slotRows.next()).thenReturn(true);
        when(slotRows.getString("slot_number")).thenReturn("C01");

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            String result = parkingService.checkIn(" 59a-12345 ", "CAR");

            assertEquals("Đã nhận xe " + PLATE + " vào ô C01.", result);
            verify(duplicateStatement).setString(1, PLATE);
            verify(connection).commit();
            verify(connection, never()).rollback();
        }
    }

    @Test
    void checkInRollsBackWhenPlateIsAlreadyActive() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement duplicateStatement = mock(PreparedStatement.class);
        ResultSet duplicateRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(duplicateStatement);
        when(duplicateStatement.executeQuery()).thenReturn(duplicateRows);
        when(duplicateRows.next()).thenReturn(true);

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            String result = parkingService.checkIn(PLATE, "CAR");

            assertEquals("Xe " + PLATE + " đang ở trong bãi.", result);
            verify(connection).rollback();
            verify(connection, never()).commit();
        }
    }

    @Test
    void checkInRollsBackWhenNoCompatibleSlotIsAvailable() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement duplicateStatement = mock(PreparedStatement.class);
        PreparedStatement findSlotStatement = mock(PreparedStatement.class);
        ResultSet duplicateRows = mock(ResultSet.class);
        ResultSet slotRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(duplicateStatement, findSlotStatement);
        when(duplicateStatement.executeQuery()).thenReturn(duplicateRows);
        when(duplicateRows.next()).thenReturn(false);
        when(findSlotStatement.executeQuery()).thenReturn(slotRows);
        when(slotRows.next()).thenReturn(false);

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            assertEquals("Không còn ô trống phù hợp cho CAR.", parkingService.checkIn(PLATE, "CAR"));
            verify(connection).rollback();
            verify(connection, never()).commit();
        }
    }

    @Test
    void checkOutReturnsNullAndRollsBackWhenPlateIsNotActive() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement findSessionStatement = mock(PreparedStatement.class);
        ResultSet sessionRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(findSessionStatement);
        when(findSessionStatement.executeQuery()).thenReturn(sessionRows);
        when(sessionRows.next()).thenReturn(false);

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            assertNull(parkingService.checkOut(PLATE));
            verify(connection).rollback();
            verify(connection, never()).commit();
        }
    }

    @Test
    void updateActiveLicensePlateNormalizesAndCommitsChange() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement findSessionStatement = mock(PreparedStatement.class);
        PreparedStatement findDuplicateStatement = mock(PreparedStatement.class);
        PreparedStatement updateSessionStatement = mock(PreparedStatement.class);
        ResultSet sessionRows = mock(ResultSet.class);
        ResultSet duplicateRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(findSessionStatement, findDuplicateStatement,
                updateSessionStatement);
        when(findSessionStatement.executeQuery()).thenReturn(sessionRows);
        when(sessionRows.next()).thenReturn(true);
        when(sessionRows.getLong("session_id")).thenReturn(42L);
        when(findDuplicateStatement.executeQuery()).thenReturn(duplicateRows);
        when(duplicateRows.next()).thenReturn(false);
        when(updateSessionStatement.executeUpdate()).thenReturn(1);

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            assertEquals("Đã cập nhật biển số " + PLATE + " thành 51B-67890.",
                    parkingService.updateActiveLicensePlate(" 59a-12345 ", " 51b-67890 "));

            verify(findSessionStatement).setString(1, PLATE);
            verify(findDuplicateStatement).setString(1, "51B-67890");
            verify(updateSessionStatement).setString(1, "51B-67890");
            verify(updateSessionStatement).setLong(2, 42L);
            verify(connection).commit();
            verify(connection, never()).rollback();
        }
    }

    @Test
    void updateActiveLicensePlateRollsBackWhenNewPlateIsAlreadyActive() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement findSessionStatement = mock(PreparedStatement.class);
        PreparedStatement findDuplicateStatement = mock(PreparedStatement.class);
        ResultSet sessionRows = mock(ResultSet.class);
        ResultSet duplicateRows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(findSessionStatement, findDuplicateStatement);
        when(findSessionStatement.executeQuery()).thenReturn(sessionRows);
        when(sessionRows.next()).thenReturn(true);
        when(sessionRows.getLong("session_id")).thenReturn(42L);
        when(findDuplicateStatement.executeQuery()).thenReturn(duplicateRows);
        when(duplicateRows.next()).thenReturn(true);

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            assertEquals("Biển số 51B-67890 đang được sử dụng bởi xe khác.",
                    parkingService.updateActiveLicensePlate(PLATE, "51B-67890"));

            verify(connection).rollback();
            verify(connection, never()).commit();
        }
    }

    @Test
    void checkOutRoundsPartialHoursUpAndReleasesSlot() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement findSessionStatement = mock(PreparedStatement.class);
        PreparedStatement findRateStatement = mock(PreparedStatement.class);
        PreparedStatement updateSessionStatement = mock(PreparedStatement.class);
        PreparedStatement updateSlotStatement = mock(PreparedStatement.class);
        ResultSet sessionRows = mock(ResultSet.class);
        ResultSet rateRows = mock(ResultSet.class);
        LocalDateTime enteredAt = LocalDateTime.now().minusMinutes(121);
        when(connection.prepareStatement(anyString())).thenReturn(findSessionStatement, findRateStatement,
                updateSessionStatement, updateSlotStatement);
        when(findSessionStatement.executeQuery()).thenReturn(sessionRows);
        when(sessionRows.next()).thenReturn(true);
        when(sessionRows.getLong("session_id")).thenReturn(42L);
        when(sessionRows.getString("slot_number")).thenReturn("C01");
        when(sessionRows.getString("vehicle_type")).thenReturn("CAR");
        when(sessionRows.getTimestamp("entry_at")).thenReturn(Timestamp.valueOf(enteredAt));
        when(findRateStatement.executeQuery()).thenReturn(rateRows);
        when(rateRows.next()).thenReturn(true);
        when(rateRows.getBigDecimal("hourly_rate")).thenReturn(BigDecimal.valueOf(15000));

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            ParkingService.Checkout checkout = parkingService.checkOut(PLATE);

            assertEquals(3, checkout.billableHours());
            assertEquals(BigDecimal.valueOf(45000), checkout.fee());
            assertEquals("C01", checkout.slot());
            verify(connection).commit();
            verify(updateSlotStatement).setString(1, "C01");
        }
    }

    @Test
    void dashboardMapsAggregateQueryResult() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getInt("total_slots")).thenReturn(17);
        when(rows.getInt("available_slots")).thenReturn(12);
        when(rows.getInt("active_vehicles")).thenReturn(5);
        when(rows.getBigDecimal("today_revenue")).thenReturn(BigDecimal.valueOf(45000));

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            ParkingService.Dashboard dashboard = parkingService.getDashboard();

            assertEquals(new ParkingService.Dashboard(17, 12, 5, BigDecimal.valueOf(45000)), dashboard);
        }
    }

    @Test
    void slotsMapsRowsIntoSlotRecords() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("slot_number")).thenReturn("M01");
        when(rows.getString("vehicle_type")).thenReturn("MOTORCYCLE");
        when(rows.getString("status")).thenReturn("AVAILABLE");

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            assertEquals(java.util.List.of(new ParkingService.Slot("M01", "MOTORCYCLE", "AVAILABLE")),
                    parkingService.getSlots());
        }
    }

    @Test
    void activeParkingsMapsEntryTimestamp() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        LocalDateTime enteredAt = LocalDateTime.of(2026, 10, 16, 9, 30);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("license_plate")).thenReturn(PLATE);
        when(rows.getString("vehicle_type")).thenReturn("CAR");
        when(rows.getString("slot_number")).thenReturn("C01");
        when(rows.getTimestamp("entry_at")).thenReturn(Timestamp.valueOf(enteredAt));

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            assertEquals(java.util.List.of(new ParkingService.ActiveParking(PLATE, "CAR", "C01", enteredAt)),
                    parkingService.getActiveParkings());
        }
    }

    @Test
    void recentHistoryMapsOpenSessionWithoutFee() throws SQLException {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        LocalDateTime enteredAt = LocalDateTime.of(2026, 10, 16, 9, 30);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("license_plate")).thenReturn(PLATE);
        when(rows.getString("vehicle_type")).thenReturn("CAR");
        when(rows.getString("slot_number")).thenReturn("C01");
        when(rows.getTimestamp("entry_at")).thenReturn(Timestamp.valueOf(enteredAt));
        when(rows.getTimestamp("exited_at")).thenReturn(null);
        when(rows.getBigDecimal("fee")).thenReturn(null);

        try (MockedStatic<Database> database = mockStatic(Database.class)) {
            database.when(Database::getConnection).thenReturn(connection);

            assertEquals(java.util.List.of(new ParkingService.ParkingHistory(
                    PLATE, "CAR", "C01", enteredAt, null, null)), parkingService.getRecentHistory());
        }
    }
}