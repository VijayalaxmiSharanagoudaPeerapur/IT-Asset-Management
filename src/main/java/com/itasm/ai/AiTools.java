package com.itasm.ai;

import com.itasm.domain.*;
import com.itasm.dto.asset.*;
import com.itasm.dto.employee.*;
import com.itasm.dto.request.*;
import com.itasm.dto.software.*;
import com.itasm.dto.ticket.*;
import com.itasm.dto.dashboard.*;
import com.itasm.security.CurrentUser;
import com.itasm.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AiTools {

    private final EmployeeService employeeService;
    private final AssetService assetService;
    private final TicketService ticketService;
    private final SoftwareLicenseService softwareService;
    private final EmployeeAssetRequestService requestService;
    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    public record EmptyRequest() {}

    // ----------------------------------------------------
    // EMPLOYEE TOOLS
    // ----------------------------------------------------

    @Bean
    @Description("Get the profile information of the currently authenticated user (Employee or Admin). Use this to find out who the user is.")
    public Function<EmptyRequest, EmployeeResponse> getMyProfile() {
        return req -> { log.info("AI: getMyProfile"); return employeeService.getMe(); };
    }

    public record UpdateMyProfileRequest(String phone, String password) {}
    @Bean
    @Description("Update the authenticated user's own profile (phone or password).")
    public Function<UpdateMyProfileRequest, EmployeeResponse> updateMyProfile() {
        return req -> {
            log.info("AI: updateMyProfile");
            return employeeService.updateMe(new EmployeeRequest(null, null, null, null, null, null, req.phone(), null, req.password()));
        };
    }

    @Bean
    @Description("Get the list of IT assets assigned to the currently authenticated employee.")
    public Function<EmptyRequest, List<AssetResponse>> getMyAssets() {
        return req -> { log.info("AI: getMyAssets"); return assetService.myAssets(); };
    }

    public record GetAssetHistoryRequest(Long assetId) {}
    @Bean
    @Description("Get the history of a specific asset. You must be assigned to this asset or be an admin.")
    public Function<GetAssetHistoryRequest, List<AssetHistoryResponse>> getMyAssetHistory() {
        return req -> { log.info("AI: getMyAssetHistory"); return assetService.history(req.assetId()); };
    }

    @Bean
    @Description("Get the list of support tickets created by the currently authenticated employee.")
    public Function<EmptyRequest, List<TicketResponse>> getMyTickets() {
        return req -> { log.info("AI: getMyTickets"); return ticketService.myTickets(Pageable.unpaged()).getContent(); };
    }

    public record CreateTicketReq(String title, String description, String priority, Long assetId) {}
    @Bean
    @Description("Create a new IT support ticket for the currently authenticated employee.")
    public Function<CreateTicketReq, TicketResponse> createTicket() {
        return req -> {
            log.info("AI: createTicket");
            TicketPriority p = TicketPriority.LOW;
            try { if (req.priority() != null) p = TicketPriority.valueOf(req.priority().toUpperCase()); } catch (Exception e) {}
            return ticketService.create(new TicketRequest(req.title(), req.description(), p, req.assetId(), null));
        };
    }

    @Bean
    @Description("Get the asset requests submitted by the currently authenticated employee.")
    public Function<EmptyRequest, List<EmployeeAssetRequestDTO>> getMyAssetRequests() {
        return req -> { log.info("AI: getMyAssetRequests"); return requestService.getMyRequests(); };
    }

    public record CreateAssetReq(String requestedItem, String reason) {}
    @Bean
    @Description("Submit a new asset request for the currently authenticated employee.")
    public Function<CreateAssetReq, EmployeeAssetRequestDTO> createAssetRequest() {
        return req -> { log.info("AI: createAssetRequest"); return requestService.createRequest(new CreateAssetRequestDTO(req.requestedItem(), req.reason())); };
    }

    @Bean
    @Description("Get software licenses assigned to the currently authenticated employee.")
    public Function<EmptyRequest, List<SoftwareAssignmentDTO>> getMySoftware() {
        return req -> { log.info("AI: getMySoftware"); return softwareService.getMySoftware(); };
    }

    // ----------------------------------------------------
    // ADMIN TOOLS
    // ----------------------------------------------------

    public record SearchRequest(String q) {}

    @Bean
    @Description("Admin ONLY: Search or list all employees.")
    public Function<SearchRequest, List<EmployeeResponse>> getEmployees() {
        return req -> { log.info("AI: getEmployees"); return employeeService.search(req.q(), null, null, Pageable.unpaged()).getContent(); };
    }

    public record CreateEmployeeReq(String employeeCode, String firstName, String lastName, String email, String department, String designation, String phone, String username, String password) {}
    @Bean
    @Description("Admin ONLY: Create a new employee account.")
    public Function<CreateEmployeeReq, EmployeeResponse> createEmployee() {
        return req -> { log.info("AI: createEmployee"); return employeeService.create(new EmployeeRequest(req.employeeCode(), req.firstName(), req.lastName(), req.email(), req.department(), req.designation(), req.phone(), req.username(), req.password())); };
    }

    public record DeactivateEmployeeReq(Long id) {}
    @Bean
    @Description("Admin ONLY: Deactivate an employee. Ensure assets are unassigned first.")
    public Function<DeactivateEmployeeReq, String> deactivateEmployee() {
        return req -> { log.info("AI: deactivateEmployee"); employeeService.deactivate(req.id()); return "Employee deactivated successfully."; };
    }

    @Bean
    @Description("Admin ONLY: Search or list all assets.")
    public Function<SearchRequest, List<AssetResponse>> getAssets() {
        return req -> { log.info("AI: getAssets"); return assetService.search(req.q(), null, Pageable.unpaged()).getContent(); };
    }

    public record CreateNewAssetReq(String assetTag, String serialNumber, String type, String brand, String model, java.time.LocalDate purchaseDate, java.time.LocalDate warrantyExpiry, java.math.BigDecimal price, String status, String notes) {}
    @Bean
    @Description("Admin ONLY: Create a new IT asset.")
    public Function<CreateNewAssetReq, AssetResponse> createAsset() {
        return req -> {
            log.info("AI: createAsset");
            AssetStatus st = AssetStatus.AVAILABLE;
            try { if(req.status()!=null) st = AssetStatus.valueOf(req.status().toUpperCase()); } catch(Exception e){}
            return assetService.create(new AssetRequest(req.assetTag(), req.serialNumber(), req.type(), req.brand(), req.model(), req.purchaseDate(), req.warrantyExpiry(), req.price(), st, req.notes()));
        };
    }

    public record AssignAssetReq(Long assetId, Long employeeId, String notes) {}
    @Bean
    @Description("Admin ONLY: Assign an asset to an employee.")
    public Function<AssignAssetReq, AssetResponse> assignAsset() {
        return req -> { log.info("AI: assignAsset"); return assetService.assign(req.assetId(), new AssignAssetRequest(req.employeeId(), req.notes())); };
    }

    public record UnassignAssetReq(Long assetId, String notes) {}
    @Bean
    @Description("Admin ONLY: Unassign an asset from its current employee.")
    public Function<UnassignAssetReq, AssetResponse> unassignAsset() {
        return req -> { log.info("AI: unassignAsset"); return assetService.unassign(req.assetId(), req.notes()); };
    }

    @Bean
    @Description("Admin ONLY: Get all tickets.")
    public Function<SearchRequest, List<TicketResponse>> getAllTickets() {
        return req -> { log.info("AI: getAllTickets"); return ticketService.search(req.q(), null, null, Pageable.unpaged()).getContent(); };
    }

    public record UpdateTicketReq(Long ticketId, String status, String resolutionNotes) {}
    @Bean
    @Description("Admin ONLY: Update ticket status (e.g. IN_PROGRESS, RESOLVED) and add resolution notes.")
    public Function<UpdateTicketReq, TicketResponse> updateTicket() {
        return req -> { log.info("AI: updateTicket"); return ticketService.changeStatus(req.ticketId(), new TicketStatusRequest(TicketStatus.valueOf(req.status().toUpperCase()), req.resolutionNotes())); };
    }

    public record ResolveAssetRequestReq(Long requestId, String status, String adminNotes) {}
    @Bean
    @Description("Admin ONLY: Approve or Reject an asset request (APPROVED or REJECTED).")
    public Function<ResolveAssetRequestReq, EmployeeAssetRequestDTO> resolveAssetRequest() {
        return req -> { log.info("AI: resolveAssetRequest"); return requestService.resolveRequest(req.requestId(), new ResolveAssetRequestDTO(RequestStatus.valueOf(req.status().toUpperCase()), req.adminNotes())); };
    }

    @Bean
    @Description("Admin ONLY: Get dashboard analytics overview (counts).")
    public Function<EmptyRequest, DashboardStats> getAnalytics() {
        return req -> { log.info("AI: getAnalytics"); return dashboardService.stats(); };
    }
}
