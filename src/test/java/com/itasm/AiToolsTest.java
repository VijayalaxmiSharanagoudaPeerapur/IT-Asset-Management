package com.itasm;

import com.itasm.ai.AiTools;
import com.itasm.domain.TicketPriority;
import com.itasm.dto.asset.AssetResponse;
import com.itasm.dto.employee.EmployeeResponse;
import com.itasm.dto.request.EmployeeAssetRequestDTO;
import com.itasm.dto.ticket.TicketResponse;
import com.itasm.security.CurrentUser;
import com.itasm.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiToolsTest {

    @Mock
    private EmployeeService employeeService;
    @Mock
    private AssetService assetService;
    @Mock
    private TicketService ticketService;
    @Mock
    private SoftwareLicenseService softwareService;
    @Mock
    private EmployeeAssetRequestService requestService;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private CurrentUser currentUser;

    private AiTools aiTools;

    @BeforeEach
    void setUp() {
        aiTools = new AiTools(employeeService, assetService, ticketService, softwareService, requestService, dashboardService, currentUser);
    }

    @Test
    void getMyProfile_DelegatesToEmployeeService() {
        EmployeeResponse mockResponse = new EmployeeResponse(
                1L, "EMP-001", "John", "Doe", "john@corp.com", "IT", "Engineer", "555-1234", true, "john"
        );
        when(employeeService.getMe()).thenReturn(mockResponse);

        EmployeeResponse res = aiTools.getMyProfile().apply(new AiTools.EmptyRequest());

        assertNotNull(res);
        assertEquals("EMP-001", res.employeeCode());
        assertEquals("John", res.firstName());
        verify(employeeService).getMe();
    }

    @Test
    void getMyAssets_DelegatesToAssetService() {
        AssetResponse asset = mock(AssetResponse.class);
        when(assetService.myAssets()).thenReturn(List.of(asset));

        List<AssetResponse> res = aiTools.getMyAssets().apply(new AiTools.EmptyRequest());

        assertEquals(1, res.size());
        verify(assetService).myAssets();
    }

    @Test
    void getMyTickets_DelegatesToTicketService() {
        TicketResponse ticket = mock(TicketResponse.class);
        when(ticketService.myTickets(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(ticket)));

        List<TicketResponse> res = aiTools.getMyTickets().apply(new AiTools.EmptyRequest());

        assertEquals(1, res.size());
        verify(ticketService).myTickets(any(Pageable.class));
    }

    @Test
    void createTicket_MapsRequestAndInvokesTicketService() {
        TicketResponse ticketResp = mock(TicketResponse.class);
        when(ticketService.create(any())).thenReturn(ticketResp);

        AiTools.CreateTicketReq req = new AiTools.CreateTicketReq("Broken Screen", "Dropped laptop", "HIGH", 50L);
        TicketResponse res = aiTools.createTicket().apply(req);

        assertNotNull(res);
        verify(ticketService).create(argThat(tr ->
                "Broken Screen".equals(tr.title()) &&
                TicketPriority.HIGH == tr.priority() &&
                Long.valueOf(50L).equals(tr.assetId())
        ));
    }

    @Test
    void createAssetRequest_MapsRequestAndInvokesRequestService() {
        EmployeeAssetRequestDTO reqResp = mock(EmployeeAssetRequestDTO.class);
        when(requestService.createRequest(any())).thenReturn(reqResp);

        AiTools.CreateAssetReq req = new AiTools.CreateAssetReq("Second Monitor", "Dual screen setup");
        EmployeeAssetRequestDTO res = aiTools.createAssetRequest().apply(req);

        assertNotNull(res);
        verify(requestService).createRequest(argThat(dto ->
                "Second Monitor".equals(dto.requestedItem()) &&
                "Dual screen setup".equals(dto.reason())
        ));
    }
}
