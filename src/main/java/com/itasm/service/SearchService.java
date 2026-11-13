package com.itasm.service;

import com.itasm.entity.Employee;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.EmployeeRepository;
import com.itasm.repository.TicketRepository;
import com.itasm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private final AssetRepository assetRepository;
    private final EmployeeRepository employeeRepository;
    private final TicketRepository ticketRepository;
    private final CurrentUser currentUser;

    public List<Map<String, Object>> globalSearch(String query) {
        List<Map<String, Object>> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        boolean isAdmin = currentUser.isAdmin();
        Long myEmployeeId = null;
        if (!isAdmin) {
            try {
                Employee me = currentUser.requireEmployee();
                myEmployeeId = me != null ? me.getId() : null;
            } catch (Exception e) {
                // If not linked to employee profile, non-admin has no accessible records
                return results;
            }
        }

        final Long finalMyEmpId = myEmployeeId;

        // Search Assets (Admins see all; employees see only their assigned assets)
        assetRepository.findAll().stream()
                .filter(a -> isAdmin || (finalMyEmpId != null && a.getAssignedEmployee() != null && finalMyEmpId.equals(a.getAssignedEmployee().getId())))
                .filter(a -> a.getAssetTag().toLowerCase().contains(query.toLowerCase()) || 
                             a.getModel().toLowerCase().contains(query.toLowerCase()) ||
                             a.getSerialNumber().toLowerCase().contains(query.toLowerCase()))
                .forEach(a -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "Asset");
                    map.put("id", a.getId());
                    map.put("title", a.getAssetTag() + " - " + a.getModel());
                    map.put("status", a.getStatus().name());
                    results.add(map);
                });

        // Search Employees (Admins see all; employees see only their own profile)
        employeeRepository.findAll().stream()
                .filter(e -> isAdmin || (finalMyEmpId != null && finalMyEmpId.equals(e.getId())))
                .filter(e -> e.getFullName().toLowerCase().contains(query.toLowerCase()) || 
                             e.getDepartment().toLowerCase().contains(query.toLowerCase()))
                .forEach(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "Employee");
                    map.put("id", e.getId());
                    map.put("title", e.getFullName());
                    map.put("status", e.getDepartment());
                    results.add(map);
                });

        // Search Tickets (Admins see all; employees see only tickets they reported or are assigned to)
        ticketRepository.findAll().stream()
                .filter(t -> isAdmin || (finalMyEmpId != null && (
                        (t.getReportedBy() != null && finalMyEmpId.equals(t.getReportedBy().getId())) ||
                        (t.getAssignedTo() != null && finalMyEmpId.equals(t.getAssignedTo().getId()))
                )))
                .filter(t -> t.getTicketNumber().toLowerCase().contains(query.toLowerCase()) || 
                             t.getTitle().toLowerCase().contains(query.toLowerCase()))
                .forEach(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "Ticket");
                    map.put("id", t.getId());
                    map.put("title", t.getTicketNumber() + " - " + t.getTitle());
                    map.put("status", t.getStatus().name());
                    results.add(map);
                });

        return results;
    }
}
