package com.itasm.config;

import com.itasm.domain.*;
import com.itasm.entity.*;
import com.itasm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserAccountRepository userAccountRepository;
    private final EmployeeRepository employeeRepository;
    private final AssetRepository assetRepository;
    private final AssetHistoryRepository historyRepository;
    private final TicketRepository ticketRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner seedData() {
        return args -> {
            if (userAccountRepository.count() > 0) {
                return;
            }

            UserAccount admin = userAccountRepository.save(UserAccount.builder()
                    .username("admin")
                    .email("admin@itasm.local")
                    .password(passwordEncoder.encode("Admin@123"))
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build());

            UserAccount vijayUser = userAccountRepository.save(UserAccount.builder()
                    .username("vijay")
                    .email("vijay@itasm.local")
                    .password(passwordEncoder.encode("Vijay@123"))
                    .role(Role.EMPLOYEE)
                    .enabled(true)
                    .build());

            UserAccount rahulUser = userAccountRepository.save(UserAccount.builder()
                    .username("rahul")
                    .email("rahul@itasm.local")
                    .password(passwordEncoder.encode("Rahul@123"))
                    .role(Role.EMPLOYEE)
                    .enabled(true)
                    .build());

            Employee itSupport = employeeRepository.save(Employee.builder()
                    .employeeCode("EMP-001")
                    .firstName("Priya")
                    .lastName("Sharma")
                    .email("priya.it@itasm.local")
                    .department("IT Support")
                    .designation("IT Technician")
                    .phone("9876543210")
                    .active(true)
                    .user(admin)
                    .build());

            Employee vijay = employeeRepository.save(Employee.builder()
                    .employeeCode("EMP-102")
                    .firstName("Vijay")
                    .lastName("Peerapur")
                    .email("vijay@itasm.local")
                    .department("Engineering")
                    .designation("Software Engineer")
                    .phone("9000000102")
                    .active(true)
                    .user(vijayUser)
                    .build());

            Employee rahul = employeeRepository.save(Employee.builder()
                    .employeeCode("EMP-110")
                    .firstName("Rahul")
                    .lastName("Patil")
                    .email("rahul@itasm.local")
                    .department("Finance")
                    .designation("Analyst")
                    .phone("9000000110")
                    .active(true)
                    .user(rahulUser)
                    .build());

            Asset l102 = saveAsset(admin, "L102", "SN-DELL-105", "Laptop", "Dell", "Latitude 5540",
                    AssetStatus.ASSIGNED, vijay, "Assigned to Vijay");
            saveAsset(admin, "L105", "SN-DELL-110", "Laptop", "Dell", "Latitude 5440",
                    AssetStatus.AVAILABLE, null, "In stock");
            Asset l201 = saveAsset(admin, "M201", "SN-HP-201", "Monitor", "HP", "E24 G5",
                    AssetStatus.REPAIR, null, "Screen flicker");
            saveAsset(admin, "L090", "SN-LEN-090", "Laptop", "Lenovo", "ThinkPad T14",
                    AssetStatus.RETIRED, null, "End of life");
            saveAsset(admin, "L118", "SN-DELL-118", "Laptop", "Dell", "XPS 13",
                    AssetStatus.ASSIGNED, rahul, "Assigned to Rahul");

            Ticket wifi = ticketRepository.save(Ticket.builder()
                    .ticketNumber("TCK-0105")
                    .title("Laptop is not connecting to Wi-Fi")
                    .description("Dell L102 cannot join office SSID after sleep.")
                    .priority(TicketPriority.HIGH)
                    .status(TicketStatus.OPEN)
                    .asset(l102)
                    .reportedBy(vijay)
                    .build());

            ticketRepository.save(Ticket.builder()
                    .ticketNumber("TCK-0106")
                    .title("Monitor flicker")
                    .description("HP monitor M201 flickers after 10 minutes.")
                    .priority(TicketPriority.MEDIUM)
                    .status(TicketStatus.IN_PROGRESS)
                    .asset(l201)
                    .reportedBy(rahul)
                    .assignedTo(itSupport)
                    .build());

            notificationRepository.save(Notification.builder()
                    .user(admin)
                    .message("New ticket TCK-0105 (HIGH): Laptop is not connecting to Wi-Fi")
                    .ticket(wifi)
                    .readFlag(false)
                    .build());
            notificationRepository.save(Notification.builder()
                    .user(vijayUser)
                    .message("Your ticket TCK-0105 was created and is OPEN.")
                    .ticket(wifi)
                    .readFlag(false)
                    .build());
        };
    }

    private Asset saveAsset(UserAccount admin, String tag, String serial, String type, String brand, String model,
                            AssetStatus status, Employee employee, String notes) {
        Asset asset = assetRepository.save(Asset.builder()
                .assetTag(tag)
                .serialNumber(serial)
                .type(type)
                .brand(brand)
                .model(model)
                .purchaseDate(LocalDate.of(2024, 6, 15))
                .warrantyExpiry(LocalDate.of(2027, 6, 15))
                .status(status)
                .assignedEmployee(employee)
                .notes(notes)
                .build());
        historyRepository.save(AssetHistory.builder()
                .asset(asset)
                .action(HistoryAction.CREATED)
                .toStatus(AssetStatus.AVAILABLE)
                .performedBy(admin)
                .notes("Asset registered")
                .build());
        if (employee != null) {
            historyRepository.save(AssetHistory.builder()
                    .asset(asset)
                    .action(HistoryAction.ASSIGNED)
                    .fromStatus(AssetStatus.AVAILABLE)
                    .toStatus(AssetStatus.ASSIGNED)
                    .employee(employee)
                    .performedBy(admin)
                    .notes("Assigned to " + employee.getFullName())
                    .build());
        }
        if (status == AssetStatus.REPAIR) {
            historyRepository.save(AssetHistory.builder()
                    .asset(asset)
                    .action(HistoryAction.SENT_FOR_REPAIR)
                    .fromStatus(AssetStatus.AVAILABLE)
                    .toStatus(AssetStatus.REPAIR)
                    .performedBy(admin)
                    .notes(notes)
                    .build());
        }
        if (status == AssetStatus.RETIRED) {
            historyRepository.save(AssetHistory.builder()
                    .asset(asset)
                    .action(HistoryAction.RETIRED)
                    .fromStatus(AssetStatus.AVAILABLE)
                    .toStatus(AssetStatus.RETIRED)
                    .performedBy(admin)
                    .notes(notes)
                    .build());
        }
        return asset;
    }
}
