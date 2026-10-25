package vn.parking;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ParkingService {
    public record Slot(String number, String type, String status) {
    }

    public record VehicleRate(String type, BigDecimal hourlyRate) {
    }

    public record ActiveParking(String plate, String type, String slot, LocalDateTime enteredAt) {
    }

    public record Checkout(String plate, String slot, LocalDateTime enteredAt,
                           LocalDateTime exitedAt, long billableHours, BigDecimal fee) {
    }

    public record Dashboard(int totalSlots, int availableSlots, int activeVehicles,
                            BigDecimal todayRevenue) {
    }

    public record ParkingHistory(String plate, String type, String slot, LocalDateTime enteredAt,
                                 LocalDateTime exitedAt, BigDecimal fee) {
    }

    public Dashboard getDashboard() throws SQLException {
        String sql = "SELECT "
                + "(SELECT COUNT(*) FROM parking_slots) AS total_slots, "
                + "(SELECT COUNT(*) FROM parking_slots WHERE status = 'AVAILABLE') AS available_slots, "
                + "(SELECT COUNT(*) FROM parking_sessions WHERE exited_at IS NULL) AS active_vehicles, "
                + "(SELECT COALESCE(SUM(fee), 0) FROM parking_sessions WHERE DATE(exited_at) = CURRENT_DATE) AS today_revenue";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            result.next();
            return new Dashboard(result.getInt("total_slots"), result.getInt("available_slots"),
                    result.getInt("active_vehicles"), result.getBigDecimal("today_revenue"));
        }
    }

    public List<Slot> getSlots() throws SQLException {
        String sql = "SELECT slot_number, vehicle_type, status FROM parking_slots ORDER BY slot_number";
        List<Slot> slots = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                slots.add(new Slot(result.getString("slot_number"), result.getString("vehicle_type"),
                        result.getString("status")));
            }
        }
        return slots;
    }

    public List<VehicleRate> getVehicleRates() throws SQLException {
        String sql = "SELECT vehicle_type, hourly_rate FROM vehicle_rates ORDER BY vehicle_type";
        List<VehicleRate> rates = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rates.add(new VehicleRate(result.getString("vehicle_type"),
                        result.getBigDecimal("hourly_rate")));
            }
        }
        return rates;
    }

    public String addSlot(String slotNumber, String vehicleType) throws SQLException {
        String number = normalizeSlotNumber(slotNumber);
        validateVehicleType(vehicleType);
        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement findSlot = connection.prepareStatement(
                    "SELECT 1 FROM parking_slots WHERE slot_number = ?")) {
                findSlot.setString(1, number);
                try (ResultSet result = findSlot.executeQuery()) {
                    if (result.next()) {
                        return "Mã ô " + number + " đã tồn tại.";
                    }
                }
            }
            try (PreparedStatement insertSlot = connection.prepareStatement(
                    "INSERT INTO parking_slots (slot_number, vehicle_type, status) VALUES (?, ?, 'AVAILABLE')")) {
                insertSlot.setString(1, number);
                insertSlot.setString(2, vehicleType);
                insertSlot.executeUpdate();
            }
            return "Đã thêm ô " + number + ".";
        }
    }

    public String updateAvailableSlot(String currentSlotNumber, String newSlotNumber,
                                     String vehicleType) throws SQLException {
        String currentNumber = normalizeSlotNumber(currentSlotNumber);
        String newNumber = normalizeSlotNumber(newSlotNumber);
        validateVehicleType(vehicleType);

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String status;
                try (PreparedStatement findSlot = connection.prepareStatement(
                        "SELECT status FROM parking_slots WHERE slot_number = ? FOR UPDATE")) {
                    findSlot.setString(1, currentNumber);
                    try (ResultSet result = findSlot.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return "Không tìm thấy ô " + currentNumber + ".";
                        }
                        status = result.getString("status");
                    }
                }
                if (!"AVAILABLE".equals(status)) {
                    connection.rollback();
                    return "Chỉ có thể sửa ô đang trống.";
                }

                if (!currentNumber.equals(newNumber)) {
                    try (PreparedStatement findDuplicate = connection.prepareStatement(
                            "SELECT 1 FROM parking_slots WHERE slot_number = ?")) {
                        findDuplicate.setString(1, newNumber);
                        try (ResultSet result = findDuplicate.executeQuery()) {
                            if (result.next()) {
                                connection.rollback();
                                return "Mã ô " + newNumber + " đã tồn tại.";
                            }
                        }
                    }
                    if (hasSessionHistory(connection, currentNumber)) {
                        connection.rollback();
                        return "Không thể đổi mã ô đã có lịch sử gửi xe.";
                    }
                }

                try (PreparedStatement updateSlot = connection.prepareStatement(
                        "UPDATE parking_slots SET slot_number = ?, vehicle_type = ? "
                                + "WHERE slot_number = ? AND status = 'AVAILABLE'")) {
                    updateSlot.setString(1, newNumber);
                    updateSlot.setString(2, vehicleType);
                    updateSlot.setString(3, currentNumber);
                    if (updateSlot.executeUpdate() != 1) {
                        connection.rollback();
                        return "Không thể cập nhật ô " + currentNumber + ".";
                    }
                }
                connection.commit();
                return "Đã cập nhật ô " + currentNumber + " thành " + newNumber + ".";
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public String deleteAvailableSlot(String slotNumber) throws SQLException {
        String number = normalizeSlotNumber(slotNumber);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String status;
                try (PreparedStatement findSlot = connection.prepareStatement(
                        "SELECT status FROM parking_slots WHERE slot_number = ? FOR UPDATE")) {
                    findSlot.setString(1, number);
                    try (ResultSet result = findSlot.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return "Không tìm thấy ô " + number + ".";
                        }
                        status = result.getString("status");
                    }
                }
                if (!"AVAILABLE".equals(status)) {
                    connection.rollback();
                    return "Chỉ có thể xóa ô đang trống.";
                }
                if (hasSessionHistory(connection, number)) {
                    connection.rollback();
                    return "Không thể xóa ô đã có lịch sử gửi xe.";
                }
                try (PreparedStatement deleteSlot = connection.prepareStatement(
                        "DELETE FROM parking_slots WHERE slot_number = ? AND status = 'AVAILABLE'")) {
                    deleteSlot.setString(1, number);
                    if (deleteSlot.executeUpdate() != 1) {
                        connection.rollback();
                        return "Không thể xóa ô " + number + ".";
                    }
                }
                connection.commit();
                return "Đã xóa ô " + number + ".";
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public String updateVehicleRate(String vehicleType, BigDecimal hourlyRate) throws SQLException {
        validateVehicleType(vehicleType);
        if (hourlyRate == null || hourlyRate.signum() <= 0
                || hourlyRate.stripTrailingZeros().scale() > 0
                || hourlyRate.precision() > 12) {
            throw new IllegalArgumentException("Giá phải là số nguyên dương, tối đa 12 chữ số.");
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement updateRate = connection.prepareStatement(
                     "UPDATE vehicle_rates SET hourly_rate = ? WHERE vehicle_type = ?")) {
            updateRate.setBigDecimal(1, hourlyRate);
            updateRate.setString(2, vehicleType);
            if (updateRate.executeUpdate() != 1) {
                throw new SQLException("Không tìm thấy cấu hình giá cho loại xe " + vehicleType + ".");
            }
            return "Đã cập nhật giá " + vehicleType + " thành " + hourlyRate.toPlainString() + " VND/giờ.";
        }
    }

    public String checkIn(String licensePlate, String vehicleType) throws SQLException {
        String plate = normalizePlate(licensePlate);
        if (!List.of("MOTORCYCLE", "CAR", "TRUCK").contains(vehicleType)) {
            throw new IllegalArgumentException("Loại xe không hợp lệ.");
        }

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement duplicate = connection.prepareStatement(
                        "SELECT 1 FROM parking_sessions WHERE license_plate = ? AND exited_at IS NULL")) {
                    duplicate.setString(1, plate);
                    try (ResultSet result = duplicate.executeQuery()) {
                        if (result.next()) {
                            connection.rollback();
                            return "Xe " + plate + " đang ở trong bãi.";
                        }
                    }
                }

                String slotNumber;
                try (PreparedStatement findSlot = connection.prepareStatement(
                        "SELECT slot_number FROM parking_slots WHERE vehicle_type = ? AND status = 'AVAILABLE' "
                                + "ORDER BY slot_number LIMIT 1 FOR UPDATE")) {
                    findSlot.setString(1, vehicleType);
                    try (ResultSet result = findSlot.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return "Không còn ô trống phù hợp cho " + vehicleType + ".";
                        }
                        slotNumber = result.getString("slot_number");
                    }
                }

                try (PreparedStatement updateSlot = connection.prepareStatement(
                        "UPDATE parking_slots SET status = 'OCCUPIED' WHERE slot_number = ?")) {
                    updateSlot.setString(1, slotNumber);
                    updateSlot.executeUpdate();
                }
                try (PreparedStatement insertSession = connection.prepareStatement(
                        "INSERT INTO parking_sessions (license_plate, vehicle_type, slot_number) VALUES (?, ?, ?)")) {
                    insertSession.setString(1, plate);
                    insertSession.setString(2, vehicleType);
                    insertSession.setString(3, slotNumber);
                    insertSession.executeUpdate();
                }
                connection.commit();
                return "Đã nhận xe " + plate + " vào ô " + slotNumber + ".";
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public Checkout checkOut(String licensePlate) throws SQLException {
        String plate = normalizePlate(licensePlate);
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String sql = "SELECT session_id, slot_number, vehicle_type, entry_at FROM parking_sessions "
                        + "WHERE license_plate = ? AND exited_at IS NULL FOR UPDATE";
                long sessionId;
                String slotNumber;
                String vehicleType;
                LocalDateTime enteredAt;
                try (PreparedStatement findSession = connection.prepareStatement(sql)) {
                    findSession.setString(1, plate);
                    try (ResultSet result = findSession.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return null;
                        }
                        sessionId = result.getLong("session_id");
                        slotNumber = result.getString("slot_number");
                        vehicleType = result.getString("vehicle_type");
                        enteredAt = result.getTimestamp("entry_at").toLocalDateTime();
                    }
                }

                BigDecimal hourlyRate;
                try (PreparedStatement findRate = connection.prepareStatement(
                        "SELECT hourly_rate FROM vehicle_rates WHERE vehicle_type = ?")) {
                    findRate.setString(1, vehicleType);
                    try (ResultSet result = findRate.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException("Chưa cấu hình giá cho loại xe " + vehicleType + ".");
                        }
                        hourlyRate = result.getBigDecimal("hourly_rate");
                    }
                }

                LocalDateTime exitedAt = LocalDateTime.now();
                long minutes = Math.max(0, Duration.between(enteredAt, exitedAt).toMinutes());
                long billableHours = Math.max(1, (minutes + 59) / 60);
                BigDecimal fee = hourlyRate.multiply(BigDecimal.valueOf(billableHours));
                try (PreparedStatement updateSession = connection.prepareStatement(
                        "UPDATE parking_sessions SET exited_at = ?, fee = ? WHERE session_id = ?")) {
                    updateSession.setTimestamp(1, Timestamp.valueOf(exitedAt));
                    updateSession.setBigDecimal(2, fee);
                    updateSession.setLong(3, sessionId);
                    updateSession.executeUpdate();
                }
                try (PreparedStatement updateSlot = connection.prepareStatement(
                        "UPDATE parking_slots SET status = 'AVAILABLE' WHERE slot_number = ?")) {
                    updateSlot.setString(1, slotNumber);
                    updateSlot.executeUpdate();
                }
                connection.commit();
                return new Checkout(plate, slotNumber, enteredAt, exitedAt, billableHours, fee);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public String updateActiveLicensePlate(String currentLicensePlate, String newLicensePlate) throws SQLException {
        String currentPlate = normalizePlate(currentLicensePlate);
        String newPlate = normalizePlate(newLicensePlate);
        if (currentPlate.equals(newPlate)) {
            return "Biển số xe không thay đổi.";
        }

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long sessionId;
                try (PreparedStatement findSession = connection.prepareStatement(
                        "SELECT session_id FROM parking_sessions "
                                + "WHERE license_plate = ? AND exited_at IS NULL FOR UPDATE")) {
                    findSession.setString(1, currentPlate);
                    try (ResultSet result = findSession.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return "Không tìm thấy xe đang gửi với biển số " + currentPlate + ".";
                        }
                        sessionId = result.getLong("session_id");
                    }
                }

                try (PreparedStatement findDuplicate = connection.prepareStatement(
                        "SELECT 1 FROM parking_sessions WHERE license_plate = ? AND exited_at IS NULL")) {
                    findDuplicate.setString(1, newPlate);
                    try (ResultSet result = findDuplicate.executeQuery()) {
                        if (result.next()) {
                            connection.rollback();
                            return "Biển số " + newPlate + " đang được sử dụng bởi xe khác.";
                        }
                    }
                }

                try (PreparedStatement updateSession = connection.prepareStatement(
                        "UPDATE parking_sessions SET license_plate = ? "
                                + "WHERE session_id = ? AND exited_at IS NULL")) {
                    updateSession.setString(1, newPlate);
                    updateSession.setLong(2, sessionId);
                    if (updateSession.executeUpdate() != 1) {
                        connection.rollback();
                        return "Không tìm thấy xe đang gửi với biển số " + currentPlate + ".";
                    }
                }
                connection.commit();
                return "Đã cập nhật biển số " + currentPlate + " thành " + newPlate + ".";
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public List<ActiveParking> getActiveParkings() throws SQLException {
        String sql = "SELECT license_plate, vehicle_type, slot_number, entry_at FROM parking_sessions "
                + "WHERE exited_at IS NULL ORDER BY entry_at";
        List<ActiveParking> parkings = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                parkings.add(new ActiveParking(result.getString("license_plate"),
                        result.getString("vehicle_type"), result.getString("slot_number"),
                        result.getTimestamp("entry_at").toLocalDateTime()));
            }
        }
        return parkings;
    }

    public List<ParkingHistory> getRecentHistory() throws SQLException {
        String sql = "SELECT license_plate, vehicle_type, slot_number, entry_at, exited_at, fee "
                + "FROM parking_sessions ORDER BY session_id DESC LIMIT 30";
        List<ParkingHistory> history = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                Timestamp exit = result.getTimestamp("exited_at");
                history.add(new ParkingHistory(result.getString("license_plate"),
                        result.getString("vehicle_type"), result.getString("slot_number"),
                        result.getTimestamp("entry_at").toLocalDateTime(),
                        exit == null ? null : exit.toLocalDateTime(), result.getBigDecimal("fee")));
            }
        }
        return history;
    }

    private String normalizePlate(String licensePlate) {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new IllegalArgumentException("Biển số xe không được để trống.");
        }
        return licensePlate.trim().toUpperCase();
    }

    private String normalizeSlotNumber(String slotNumber) {
        if (slotNumber == null || !slotNumber.trim().matches("[A-Za-z0-9-]{1,10}")) {
            throw new IllegalArgumentException("Mã ô chỉ được gồm 1-10 chữ cái, chữ số hoặc dấu gạch ngang.");
        }
        return slotNumber.trim().toUpperCase();
    }

    private void validateVehicleType(String vehicleType) {
        if (!List.of("MOTORCYCLE", "CAR", "TRUCK").contains(vehicleType)) {
            throw new IllegalArgumentException("Loại xe không hợp lệ.");
        }
    }

    private boolean hasSessionHistory(Connection connection, String slotNumber) throws SQLException {
        try (PreparedStatement findHistory = connection.prepareStatement(
                "SELECT 1 FROM parking_sessions WHERE slot_number = ? LIMIT 1")) {
            findHistory.setString(1, slotNumber);
            try (ResultSet result = findHistory.executeQuery()) {
                return result.next();
            }
        }
    }
}