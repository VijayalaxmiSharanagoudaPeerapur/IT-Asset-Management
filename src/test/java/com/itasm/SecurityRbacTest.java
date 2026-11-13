package com.itasm;

import com.itasm.controller.*;
import com.itasm.domain.*;
import com.itasm.dto.asset.*;
import com.itasm.dto.employee.*;
import com.itasm.dto.request.*;
import com.itasm.dto.software.*;
import com.itasm.dto.ticket.*;
import com.itasm.entity.*;
import com.itasm.exception.GlobalExceptionHandler;
import com.itasm.repository.*;
import com.itasm.security.CurrentUser;
import com.itasm.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class SecurityRbacTest {

    @Mock
    private CurrentUser currentUser;

    // Repositories
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private AssetRepository assetRepository;
    @Mock
    private AssetHistoryRepository assetHistoryRepository;
    @Mock
    private SoftwareLicenseRepository softwareLicenseRepository;
    @Mock
    private SoftwareAssignmentRepository softwareAssignmentRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private EmployeeAssetRequestRepository employeeAssetRequestRepository;
    @Mock
    private SecurityEventRepository securityEventRepository;

    // Services
    private EmployeeService employeeService;
    private AssetService assetService;
    private SoftwareLicenseService softwareLicenseService;
    private TicketService ticketService;
    private EmployeeAssetRequestService employeeAssetRequestService;
    private SearchService searchService;
    private Mapper mapper;

    // Controllers & MockMvc
    private MockMvc employeeMockMvc;
    private MockMvc assetMockMvc;
    private MockMvc softwareMockMvc;
    private MockMvc ticketMockMvc;
    private MockMvc assetRequestMockMvc;
    private MockMvc securityEventMockMvc;

    private Employee employee1;
    private Employee employee2;
    private Asset asset1;
    private Ticket ticket1;

    @BeforeEach
    void setUp() {
        mapper = new Mapper();

        employee1 = Employee.builder()
                .id(1L)
                .employeeCode("EMP-001")
                .firstName("Vijay")
                .lastName("Kumar")
                .email("vijay@itasm.local")
                .department("Engineering")
                .active(true)
                .build();

        employee2 = Employee.builder()
                .id(2L)
                .employeeCode("EMP-002")
                .firstName("Rahul")
                .lastName("Sharma")
                .email("rahul@itasm.local")
                .department("HR")
                .active(true)
                .build();

        asset1 = Asset.builder()
                .id(10L)
                .assetTag("AST-001")
                .serialNumber("SN-12345")
                .brand("Dell")
                .model("Latitude 5420")
                .type("Laptop")
                .status(AssetStatus.ASSIGNED)
                .assignedEmployee(employee1)
                .build();

        ticket1 = Ticket.builder()
                .id(100L)
                .ticketNumber("TCK-0001")
                .title("VPN Connection Issue")
                .description("Cannot connect to corporate VPN")
                .status(TicketStatus.OPEN)
                .priority(TicketPriority.HIGH)
                .reportedBy(employee1)
                .build();

        employeeService = new EmployeeService(
                employeeRepository, userAccountRepository, assetRepository,
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mapper, mock(AuditLogService.class), currentUser
        );

        assetService = new AssetService(
                assetRepository, assetHistoryRepository, employeeService, mapper, currentUser
        );

        softwareLicenseService = new SoftwareLicenseService(
                softwareLicenseRepository, softwareAssignmentRepository, employeeService, currentUser
        );

        ticketService = new TicketService(
                ticketRepository, notificationRepository, userAccountRepository,
                employeeService, assetService, mapper, currentUser
        );

        employeeAssetRequestService = new EmployeeAssetRequestService(
                employeeAssetRequestRepository, employeeService, currentUser
        );

        searchService = new SearchService(
                assetRepository, employeeRepository, ticketRepository, currentUser
        );

        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

        EmployeeController employeeController = new EmployeeController(employeeService, assetService);
        employeeMockMvc = MockMvcBuilders.standaloneSetup(employeeController)
                .setControllerAdvice(exceptionHandler).build();

        AssetController assetController = new AssetController(
                assetService, mock(QrCodeService.class), mock(AssetCsvService.class)
        );
        assetMockMvc = MockMvcBuilders.standaloneSetup(assetController)
                .setControllerAdvice(exceptionHandler).build();

        SoftwareLicenseController softwareController = new SoftwareLicenseController(softwareLicenseService);
        softwareMockMvc = MockMvcBuilders.standaloneSetup(softwareController)
                .setControllerAdvice(exceptionHandler).build();

        TicketController ticketController = new TicketController(ticketService);
        ticketMockMvc = MockMvcBuilders.standaloneSetup(ticketController)
                .setControllerAdvice(exceptionHandler).build();

        EmployeeAssetRequestController assetRequestController = new EmployeeAssetRequestController(employeeAssetRequestService);
        assetRequestMockMvc = MockMvcBuilders.standaloneSetup(assetRequestController)
                .setControllerAdvice(exceptionHandler).build();

        SecurityEventController securityEventController = new SecurityEventController(securityEventRepository, currentUser);
        securityEventMockMvc = MockMvcBuilders.standaloneSetup(securityEventController)
                .setControllerAdvice(exceptionHandler).build();
    }

    // =========================================================================
    // 1. Employee Management RBAC & Self-Service
    // =========================================================================
    @Nested
    @DisplayName("Employee Management RBAC")
    class EmployeeRbacTests {

        @Test
        @DisplayName("Admin can search employees (200 OK)")
        void admin_CanSearchEmployees() throws Exception {
            when(currentUser.isAdmin()).thenReturn(true);
            when(employeeRepository.search(any(), any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(employee1)));

            employeeMockMvc.perform(get("/api/employees"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].employeeCode").value("EMP-001"));
        }

        @Test
        @DisplayName("Employee cannot search all employees (403 Forbidden)")
        void employee_CannotSearchAllEmployees() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            employeeMockMvc.perform(get("/api/employees"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message").value("You do not have permission for this action"));
        }

        @Test
        @DisplayName("Employee can view their own profile via /me (200 OK)")
        void employee_CanViewOwnProfile() throws Exception {
            when(currentUser.requireEmployee()).thenReturn(employee1);

            employeeMockMvc.perform(get("/api/employees/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Vijay"));
        }

        @Test
        @DisplayName("Employee viewing another employee's profile is denied (403 Forbidden)")
        void employee_CannotViewOtherEmployeeProfile() throws Exception {
            when(employeeRepository.findById(2L)).thenReturn(Optional.of(employee2));
            when(currentUser.isAdmin()).thenReturn(false);
            when(currentUser.requireEmployee()).thenReturn(employee1); // id = 1L != 2L

            employeeMockMvc.perform(get("/api/employees/2"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Employee cannot create a new employee account (403 Forbidden)")
        void employee_CannotCreateEmployee() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            String requestBody = """
                    {
                        "employeeCode": "EMP-999",
                        "firstName": "New",
                        "lastName": "User",
                        "email": "new.user@itasm.local",
                        "department": "IT",
                        "username": "newuser"
                    }
                    """;

            employeeMockMvc.perform(post("/api/employees")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 2. Asset Management RBAC & Self-Service
    // =========================================================================
    @Nested
    @DisplayName("Asset Management RBAC")
    class AssetRbacTests {

        @Test
        @DisplayName("Admin can search corporate assets (200 OK)")
        void admin_CanSearchAssets() throws Exception {
            when(currentUser.isAdmin()).thenReturn(true);
            when(assetRepository.search(any(), any(), any()))
                    .thenReturn(new PageImpl<>(List.of(asset1)));

            assetMockMvc.perform(get("/api/assets"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].assetTag").value("AST-001"));
        }

        @Test
        @DisplayName("Employee cannot search corporate assets (403 Forbidden)")
        void employee_CannotSearchAssets() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            assetMockMvc.perform(get("/api/assets"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Employee can view their own assigned assets via /me (200 OK)")
        void employee_CanViewOwnAssets() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);
            when(currentUser.requireEmployee()).thenReturn(employee1);
            when(assetRepository.findByAssignedEmployee_Id(1L)).thenReturn(List.of(asset1));

            assetMockMvc.perform(get("/api/assets/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].assetTag").value("AST-001"));
        }

        @Test
        @DisplayName("Employee viewing an unassigned or another employee's asset is denied (403 Forbidden)")
        void employee_CannotViewAnotherEmployeeAsset() throws Exception {
            Asset otherAsset = Asset.builder()
                    .id(20L)
                    .assetTag("AST-002")
                    .assignedEmployee(employee2)
                    .build();

            when(assetRepository.findById(20L)).thenReturn(Optional.of(otherAsset));
            when(currentUser.isAdmin()).thenReturn(false);
            when(currentUser.requireEmployee()).thenReturn(employee1); // employee1 != employee2

            assetMockMvc.perform(get("/api/assets/20"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Employee cannot create or register a hardware asset (403 Forbidden)")
        void employee_CannotCreateAsset() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            String requestBody = """
                    {
                        "assetTag": "AST-NEW-01",
                        "serialNumber": "SN-9999",
                        "type": "Laptop",
                        "brand": "Lenovo",
                        "model": "ThinkPad",
                        "status": "AVAILABLE"
                    }
                    """;

            assetMockMvc.perform(post("/api/assets")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 3. Software License RBAC & Self-Service
    // =========================================================================
    @Nested
    @DisplayName("Software License RBAC")
    class SoftwareLicenseRbacTests {

        @Test
        @DisplayName("Employee can view own software assignments via /me (200 OK)")
        void employee_CanViewOwnSoftware() throws Exception {
            SoftwareLicense license = SoftwareLicense.builder()
                    .id(1L)
                    .name("IntelliJ IDEA")
                    .build();

            SoftwareAssignment assignment = SoftwareAssignment.builder()
                    .id(100L)
                    .license(license)
                    .employee(employee1)
                    .build();

            when(currentUser.isAdmin()).thenReturn(false);
            when(currentUser.requireEmployee()).thenReturn(employee1);
            when(softwareAssignmentRepository.findByEmployee_Id(1L)).thenReturn(List.of(assignment));

            softwareMockMvc.perform(get("/api/software/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].licenseName").value("IntelliJ IDEA"));
        }

        @Test
        @DisplayName("Employee cannot create software licenses (403 Forbidden)")
        void employee_CannotCreateSoftwareLicense() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            String requestBody = """
                    {
                        "name": "Figma Enterprise",
                        "vendor": "Figma",
                        "licenseKey": "FIGMA-KEY-1234",
                        "totalSeats": 50
                    }
                    """;

            softwareMockMvc.perform(post("/api/software")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Employee cannot assign software licenses (403 Forbidden)")
        void employee_CannotAssignSoftwareLicense() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            String requestBody = """
                    {
                        "employeeId": 1
                    }
                    """;

            softwareMockMvc.perform(post("/api/software/1/assign")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 4. Ticket Management RBAC & Ownership
    // =========================================================================
    @Nested
    @DisplayName("Ticket Management RBAC")
    class TicketRbacTests {

        @Test
        @DisplayName("Employee can view their own reported ticket (200 OK)")
        void employee_CanViewOwnTicket() throws Exception {
            when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket1));
            when(currentUser.isAdmin()).thenReturn(false);
            when(currentUser.requireEmployee()).thenReturn(employee1);

            ticketMockMvc.perform(get("/api/tickets/100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ticketNumber").value("TCK-0001"));
        }

        @Test
        @DisplayName("Employee cannot view another employee's ticket (403 Forbidden)")
        void employee_CannotViewAnotherEmployeeTicket() throws Exception {
            Ticket ticketOfEmployee2 = Ticket.builder()
                    .id(200L)
                    .ticketNumber("TCK-0002")
                    .title("HR Portal Access")
                    .reportedBy(employee2)
                    .build();

            when(ticketRepository.findById(200L)).thenReturn(Optional.of(ticketOfEmployee2));
            when(currentUser.isAdmin()).thenReturn(false);
            when(currentUser.requireEmployee()).thenReturn(employee1); // employee1 != employee2

            ticketMockMvc.perform(get("/api/tickets/200"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }

        @Test
        @DisplayName("Employee cannot change ticket status (403 Forbidden)")
        void employee_CannotChangeTicketStatus() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            String requestBody = """
                    {
                        "status": "RESOLVED",
                        "resolutionNotes": "Self resolved"
                    }
                    """;

            ticketMockMvc.perform(post("/api/tickets/100/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Employee cannot reassign ticket to another employee (403 Forbidden)")
        void employee_CannotAssignTicket() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            String requestBody = """
                    {
                        "employeeId": 2
                    }
                    """;

            ticketMockMvc.perform(post("/api/tickets/100/assign")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 5. Asset Requests RBAC
    // =========================================================================
    @Nested
    @DisplayName("Asset Requests RBAC")
    class AssetRequestRbacTests {

        @Test
        @DisplayName("Admin can view all company asset requests (200 OK)")
        void admin_CanViewAllRequests() throws Exception {
            when(currentUser.isAdmin()).thenReturn(true);
            when(employeeAssetRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

            assetRequestMockMvc.perform(get("/api/asset-requests"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee cannot view company-wide asset requests (403 Forbidden)")
        void employee_CannotViewAllRequests() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            assetRequestMockMvc.perform(get("/api/asset-requests"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Employee can view their own requests via /me (200 OK)")
        void employee_CanViewMyRequests() throws Exception {
            when(currentUser.requireEmployee()).thenReturn(employee1);
            when(employeeAssetRequestRepository.findByEmployee_IdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

            assetRequestMockMvc.perform(get("/api/asset-requests/me"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee cannot approve or reject asset requests (403 Forbidden)")
        void employee_CannotResolveAssetRequest() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            String requestBody = """
                    {
                        "status": "APPROVED",
                        "adminNotes": "Self approved"
                    }
                    """;

            assetRequestMockMvc.perform(post("/api/asset-requests/1/resolve")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 6. Security Events RBAC
    // =========================================================================
    @Nested
    @DisplayName("Security Events Monitoring RBAC")
    class SecurityEventsRbacTests {

        @Test
        @DisplayName("Admin can view security events (200 OK)")
        void admin_CanViewSecurityEvents() throws Exception {
            when(currentUser.isAdmin()).thenReturn(true);
            when(securityEventRepository.findTop100ByOrderByTimestampDesc()).thenReturn(List.of());

            securityEventMockMvc.perform(get("/api/security-events"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Employee cannot view security audit events (403 Forbidden)")
        void employee_CannotViewSecurityEvents() throws Exception {
            when(currentUser.isAdmin()).thenReturn(false);

            securityEventMockMvc.perform(get("/api/security-events"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403));
        }
    }

    // =========================================================================
    // 7. Global Search Scoping & Data Isolation
    // =========================================================================
    @Nested
    @DisplayName("Search Isolation RBAC")
    class SearchIsolationTests {

        @Test
        @DisplayName("Employee search isolates results to own records only")
        void employee_SearchIsolatesToOwnRecords() {
            when(currentUser.isAdmin()).thenReturn(false);
            when(currentUser.requireEmployee()).thenReturn(employee1); // id = 1L

            when(assetRepository.findAll()).thenReturn(List.of(asset1)); // assigned to employee1
            when(employeeRepository.findAll()).thenReturn(List.of(employee1, employee2));
            when(ticketRepository.findAll()).thenReturn(List.of(ticket1)); // reported by employee1

            List<java.util.Map<String, Object>> results = searchService.globalSearch("a");

            // Must NOT include employee2 ("Rahul")
            boolean foundRahul = results.stream().anyMatch(m -> "Employee".equals(m.get("type")) && "Rahul Sharma".equals(m.get("title")));
            assertFalse(foundRahul, "Employee search must not expose other employees' profile records");

            // Must include employee1 ("Vijay")
            boolean foundVijay = results.stream().anyMatch(m -> "Employee".equals(m.get("type")) && "Vijay Kumar".equals(m.get("title")));
            assertTrue(foundVijay);
        }

        @Test
        @DisplayName("Admin search returns company-wide records")
        void admin_SearchReturnsCompanyWideRecords() {
            when(currentUser.isAdmin()).thenReturn(true);

            when(assetRepository.findAll()).thenReturn(List.of(asset1));
            when(employeeRepository.findAll()).thenReturn(List.of(employee1, employee2));
            when(ticketRepository.findAll()).thenReturn(List.of(ticket1));

            List<java.util.Map<String, Object>> results = searchService.globalSearch("a");

            boolean foundRahul = results.stream().anyMatch(m -> "Employee".equals(m.get("type")) && "Rahul Sharma".equals(m.get("title")));
            assertTrue(foundRahul, "Admin search must include all matching employees");
        }
    }
}
