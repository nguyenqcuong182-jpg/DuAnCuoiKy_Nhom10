package vn.parking;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ParkingController {
    private final ParkingService parkingService;

    public ParkingController(ParkingService parkingService) {
        this.parkingService = parkingService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("currentDate", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        model.addAttribute("dbAvailable", false);
        model.addAttribute("dashboard", new ParkingService.Dashboard(0, 0, 0, BigDecimal.ZERO));
        model.addAttribute("slots", List.of());
        model.addAttribute("vehicleRates", List.of());
        model.addAttribute("activeParkings", List.of());
        model.addAttribute("history", List.of());
        try {
            model.addAttribute("dashboard", parkingService.getDashboard());
            model.addAttribute("slots", parkingService.getSlots());
            model.addAttribute("vehicleRates", parkingService.getVehicleRates());
            model.addAttribute("activeParkings", parkingService.getActiveParkings());
            model.addAttribute("history", parkingService.getRecentHistory());
            model.addAttribute("dbAvailable", true);
        } catch (SQLException exception) {
            model.addAttribute("databaseMessage",
                    "Không kết nối được MySQL. Hãy bật MySQL, chạy sql/schema.sql và kiểm tra PARKING_DB_*.");
        }
        return "dashboard";
    }

    @PostMapping("/check-in")
    public String checkIn(@RequestParam("licensePlate") String licensePlate,
                          @RequestParam("vehicleType") String vehicleType,
                          RedirectAttributes redirectAttributes) {
        try {
            String result = parkingService.checkIn(licensePlate, vehicleType);
            String kind = result.startsWith("Đã nhận xe") ? "success" : "warning";
            addMessage(redirectAttributes, result, kind);
        } catch (IllegalArgumentException exception) {
            addMessage(redirectAttributes, exception.getMessage(), "error");
        } catch (SQLException exception) {
            addDatabaseMessage(redirectAttributes);
        }
        return "redirect:/";
    }

    @PostMapping("/check-out")
    public String checkOut(@RequestParam("licensePlate") String licensePlate,
                           RedirectAttributes redirectAttributes) {
        try {
            ParkingService.Checkout checkout = parkingService.checkOut(licensePlate);
            if (checkout == null) {
                addMessage(redirectAttributes, "Không tìm thấy xe đang gửi với biển số này.", "warning");
            } else {
                String result = "Xe %s rời ô %s. Phí %d giờ: %s VND."
                        .formatted(checkout.plate(), checkout.slot(), checkout.billableHours(),
                                checkout.fee().toPlainString());
                addMessage(redirectAttributes, result, "success");
            }
        } catch (IllegalArgumentException exception) {
            addMessage(redirectAttributes, exception.getMessage(), "error");
        } catch (SQLException exception) {
            addDatabaseMessage(redirectAttributes);
        }
        return "redirect:/";
    }

    @PostMapping("/update-plate")
    public String updatePlate(@RequestParam("currentPlate") String currentPlate,
                              @RequestParam("newPlate") String newPlate,
                              RedirectAttributes redirectAttributes) {
        try {
            String result = parkingService.updateActiveLicensePlate(currentPlate, newPlate);
            String kind = result.startsWith("Đã cập nhật") ? "success" : "warning";
            addMessage(redirectAttributes, result, kind);
        } catch (IllegalArgumentException exception) {
            addMessage(redirectAttributes, exception.getMessage(), "error");
        } catch (SQLException exception) {
            addDatabaseMessage(redirectAttributes);
        }
        return "redirect:/";
    }

    @PostMapping("/slots/add")
    public String addSlot(@RequestParam("slotNumber") String slotNumber,
                          @RequestParam("vehicleType") String vehicleType,
                          RedirectAttributes redirectAttributes) {
        try {
            addOperationResult(redirectAttributes, parkingService.addSlot(slotNumber, vehicleType));
        } catch (IllegalArgumentException exception) {
            addMessage(redirectAttributes, exception.getMessage(), "error");
        } catch (SQLException exception) {
            addDatabaseMessage(redirectAttributes);
        }
        return "redirect:/";
    }

    @PostMapping("/slots/update")
    public String updateSlot(@RequestParam("currentSlotNumber") String currentSlotNumber,
                             @RequestParam("slotNumber") String slotNumber,
                             @RequestParam("vehicleType") String vehicleType,
                             RedirectAttributes redirectAttributes) {
        try {
            addOperationResult(redirectAttributes,
                    parkingService.updateAvailableSlot(currentSlotNumber, slotNumber, vehicleType));
        } catch (IllegalArgumentException exception) {
            addMessage(redirectAttributes, exception.getMessage(), "error");
        } catch (SQLException exception) {
            addDatabaseMessage(redirectAttributes);
        }
        return "redirect:/";
    }

    @PostMapping("/slots/delete")
    public String deleteSlot(@RequestParam("slotNumber") String slotNumber,
                             RedirectAttributes redirectAttributes) {
        try {
            addOperationResult(redirectAttributes, parkingService.deleteAvailableSlot(slotNumber));
        } catch (IllegalArgumentException exception) {
            addMessage(redirectAttributes, exception.getMessage(), "error");
        } catch (SQLException exception) {
            addDatabaseMessage(redirectAttributes);
        }
        return "redirect:/";
    }

    @PostMapping("/rates/update")
    public String updateRate(@RequestParam("vehicleType") String vehicleType,
                             @RequestParam("hourlyRate") BigDecimal hourlyRate,
                             RedirectAttributes redirectAttributes) {
        try {
            addOperationResult(redirectAttributes, parkingService.updateVehicleRate(vehicleType, hourlyRate));
        } catch (IllegalArgumentException exception) {
            addMessage(redirectAttributes, exception.getMessage(), "error");
        } catch (SQLException exception) {
            addDatabaseMessage(redirectAttributes);
        }
        return "redirect:/";
    }

    private void addOperationResult(RedirectAttributes redirectAttributes, String result) {
        addMessage(redirectAttributes, result, result.startsWith("Đã ") ? "success" : "warning");
    }

    private void addDatabaseMessage(RedirectAttributes redirectAttributes) {
        addMessage(redirectAttributes,
                "Không thể thao tác với MySQL. Hãy kiểm tra máy chủ và cấu hình PARKING_DB_*.", "error");
    }

    private void addMessage(RedirectAttributes redirectAttributes, String message, String kind) {
        redirectAttributes.addFlashAttribute("toast", message);
        redirectAttributes.addFlashAttribute("toastKind", kind);
    }
}
