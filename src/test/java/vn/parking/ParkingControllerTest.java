package vn.parking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class ParkingControllerTest {
    // Stable fixtures keep these tests independent from a database or system locale.
    private static final String PLATE = "59A-12345";
    private ParkingService parkingService;
    private ParkingController controller;

    @BeforeEach
    void setUp() {
        parkingService = mock(ParkingService.class);
        controller = new ParkingController(parkingService);
    }

    @Test
    void dashboardLoadsAllDataWhenDatabaseIsAvailable() throws SQLException {
        ParkingService.Dashboard dashboard = new ParkingService.Dashboard(17, 12, 5, BigDecimal.valueOf(25000));
        when(parkingService.getDashboard()).thenReturn(dashboard);
        when(parkingService.getSlots()).thenReturn(List.of(new ParkingService.Slot("M01", "MOTORCYCLE", "AVAILABLE")));
        when(parkingService.getVehicleRates()).thenReturn(
                List.of(new ParkingService.VehicleRate("MOTORCYCLE", BigDecimal.valueOf(5000))));
        when(parkingService.getActiveParkings()).thenReturn(List.of());
        when(parkingService.getRecentHistory()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.dashboard(model);

        assertEquals("dashboard", view);
        assertTrue((Boolean) model.getAttribute("dbAvailable"));
        assertEquals(dashboard, model.getAttribute("dashboard"));
        assertFalse(((List<?>) model.getAttribute("slots")).isEmpty());
        assertFalse(((List<?>) model.getAttribute("vehicleRates")).isEmpty());
        verify(parkingService).getRecentHistory();
    }

    @Test
    void dashboardShowsFallbackWhenDatabaseIsUnavailable() throws SQLException {
        when(parkingService.getDashboard()).thenThrow(new SQLException("database offline"));
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("dashboard", controller.dashboard(model));

        assertEquals(false, model.getAttribute("dbAvailable"));
        assertTrue(((String) model.getAttribute("databaseMessage")).contains("MySQL"));
        assertEquals(0, ((ParkingService.Dashboard) model.getAttribute("dashboard")).totalSlots());
    }

    @Test
    void checkInAddsSuccessFlashMessage() throws SQLException {
        String result = "Đã nhận xe " + PLATE + " vào ô M01.";
        when(parkingService.checkIn(PLATE, "CAR")).thenReturn(result);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        assertEquals("redirect:/", controller.checkIn(PLATE, "CAR", attributes));

        assertEquals(result, attributes.getFlashAttributes().get("toast"));
        assertEquals("success", attributes.getFlashAttributes().get("toastKind"));
    }

    @Test
    void checkInAddsErrorFlashMessageForInvalidInput() throws SQLException {
        when(parkingService.checkIn(PLATE, "INVALID")).thenThrow(new IllegalArgumentException("Loại xe không hợp lệ."));
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        controller.checkIn(PLATE, "INVALID", attributes);

        assertEquals("Loại xe không hợp lệ.", attributes.getFlashAttributes().get("toast"));
        assertEquals("error", attributes.getFlashAttributes().get("toastKind"));
    }

    @Test
    void checkInAddsWarningWhenNoSuitableSlotIsAvailable() throws SQLException {
        String result = "Không còn ô trống phù hợp cho CAR.";
        when(parkingService.checkIn(PLATE, "CAR")).thenReturn(result);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        controller.checkIn(PLATE, "CAR", attributes);

        assertEquals(result, attributes.getFlashAttributes().get("toast"));
        assertEquals("warning", attributes.getFlashAttributes().get("toastKind"));
    }

    @Test
    void checkInAddsDatabaseErrorWhenServiceFails() throws SQLException {
        when(parkingService.checkIn(PLATE, "CAR")).thenThrow(new SQLException("database offline"));
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        controller.checkIn(PLATE, "CAR", attributes);

        assertTrue(((String) attributes.getFlashAttributes().get("toast")).contains("MySQL"));
        assertEquals("error", attributes.getFlashAttributes().get("toastKind"));
    }

    @Test
    void checkOutShowsCalculatedFee() throws SQLException {
        LocalDateTime enteredAt = LocalDateTime.of(2026, 10, 16, 8, 0);
        ParkingService.Checkout checkout = new ParkingService.Checkout(
                PLATE, "C01", enteredAt, enteredAt.plusHours(2), 2, BigDecimal.valueOf(30000));
        when(parkingService.checkOut(PLATE)).thenReturn(checkout);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        controller.checkOut(PLATE, attributes);

        assertEquals("Xe " + PLATE + " rời ô C01. Phí 2 giờ: 30000 VND.",
                attributes.getFlashAttributes().get("toast"));
        assertEquals("success", attributes.getFlashAttributes().get("toastKind"));
    }

    @Test
    void checkOutWarnsWhenVehicleIsNotFound() throws SQLException {
        when(parkingService.checkOut(PLATE)).thenReturn(null);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        controller.checkOut(PLATE, attributes);

        assertEquals("Không tìm thấy xe đang gửi với biển số này.", attributes.getFlashAttributes().get("toast"));
        assertEquals("warning", attributes.getFlashAttributes().get("toastKind"));
    }

    @Test
    void checkOutAddsDatabaseErrorWhenServiceFails() throws SQLException {
        when(parkingService.checkOut(PLATE)).thenThrow(new SQLException("database offline"));
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        controller.checkOut(PLATE, attributes);

        assertTrue(((String) attributes.getFlashAttributes().get("toast")).contains("MySQL"));
        assertEquals("error", attributes.getFlashAttributes().get("toastKind"));
    }

    @Test
    void updatePlateAddsSuccessFlashMessage() throws SQLException {
        String result = "Đã cập nhật biển số " + PLATE + " thành 51B-67890.";
        when(parkingService.updateActiveLicensePlate(PLATE, "51B-67890")).thenReturn(result);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        assertEquals("redirect:/", controller.updatePlate(PLATE, "51B-67890", attributes));

        assertEquals(result, attributes.getFlashAttributes().get("toast"));
        assertEquals("success", attributes.getFlashAttributes().get("toastKind"));
    }

    @Test
    void updatePlateWarnsWhenNewPlateIsAlreadyInUse() throws SQLException {
        String result = "Biển số 51B-67890 đang được sử dụng bởi xe khác.";
        when(parkingService.updateActiveLicensePlate(PLATE, "51B-67890")).thenReturn(result);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        controller.updatePlate(PLATE, "51B-67890", attributes);

        assertEquals(result, attributes.getFlashAttributes().get("toast"));
        assertEquals("warning", attributes.getFlashAttributes().get("toastKind"));
    }

    @Test
    void addSlotShowsSuccessMessage() throws SQLException {
        when(parkingService.addSlot("M11", "MOTORCYCLE")).thenReturn("Đã thêm ô M11.");
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        assertEquals("redirect:/", controller.addSlot("M11", "MOTORCYCLE", attributes));

        assertEquals("Đã thêm ô M11.", attributes.getFlashAttributes().get("toast"));
        assertEquals("success", attributes.getFlashAttributes().get("toastKind"));
    }

    @Test
    void updateRateShowsSuccessMessage() throws SQLException {
        String result = "Đã cập nhật giá CAR thành 20000 VND/giờ.";
        when(parkingService.updateVehicleRate("CAR", BigDecimal.valueOf(20000))).thenReturn(result);
        RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

        assertEquals("redirect:/", controller.updateRate("CAR", BigDecimal.valueOf(20000), attributes));

        assertEquals(result, attributes.getFlashAttributes().get("toast"));
        assertEquals("success", attributes.getFlashAttributes().get("toastKind"));
    }
}