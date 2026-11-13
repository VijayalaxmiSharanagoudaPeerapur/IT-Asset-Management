package com.itasm.service;

import com.itasm.dto.asset.AssetHistoryResponse;
import com.itasm.dto.asset.AssetResponse;
import com.itasm.dto.employee.EmployeeResponse;
import com.itasm.dto.notification.NotificationResponse;
import com.itasm.dto.ticket.TicketResponse;
import com.itasm.entity.*;
import org.springframework.stereotype.Component;

@Component
public class Mapper {

    public EmployeeResponse toEmployee(Employee e) {
        return new EmployeeResponse(
                e.getId(), e.getEmployeeCode(), e.getFirstName(), e.getLastName(), e.getEmail(),
                e.getDepartment(), e.getDesignation(), e.getPhone(), e.isActive(),
                e.getUser() != null ? e.getUser().getUsername() : null
        );
    }

    public AssetResponse toAsset(Asset a) {
        Employee emp = a.getAssignedEmployee();
        return new AssetResponse(
                a.getId(), a.getAssetTag(), a.getSerialNumber(), a.getType(), a.getBrand(), a.getModel(),
                a.getPurchaseDate(), a.getWarrantyExpiry(), a.getPrice(), a.getStatus(),
                emp != null ? emp.getId() : null,
                emp != null ? emp.getFullName() : null,
                a.getNotes(), a.getCreatedAt(), a.getRiskScore()
        );
    }

    public AssetHistoryResponse toHistory(AssetHistory h) {
        return new AssetHistoryResponse(
                h.getId(), h.getAction(), h.getFromStatus(), h.getToStatus(),
                h.getEmployee() != null ? h.getEmployee().getFullName() : null,
                h.getPerformedBy() != null ? h.getPerformedBy().getUsername() : null,
                h.getNotes(), h.getCreatedAt()
        );
    }

    public TicketResponse toTicket(Ticket t) {
        return new TicketResponse(
                t.getId(), t.getTicketNumber(), t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(),
                t.getAsset() != null ? t.getAsset().getId() : null,
                t.getAsset() != null ? t.getAsset().getAssetTag() : null,
                t.getReportedBy().getId(), t.getReportedBy().getFullName(),
                t.getAssignedTo() != null ? t.getAssignedTo().getId() : null,
                t.getAssignedTo() != null ? t.getAssignedTo().getFullName() : null,
                t.getResolutionNotes(), t.getCreatedAt(), t.getResolvedAt(), t.getSlaStatus()
        );
    }

    public NotificationResponse toNotification(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getMessage(),
                n.getTicket() != null ? n.getTicket().getId() : null,
                n.isReadFlag(), n.getCreatedAt()
        );
    }

    public com.itasm.dto.procurement.VendorDTO toVendor(Vendor v) {
        if (v == null) return null;
        return new com.itasm.dto.procurement.VendorDTO(
                v.getId(), v.getName(), v.getContactPerson(), v.getEmail(),
                v.getPhone(), v.getAddress(), v.isActive(), v.getCreatedAt()
        );
    }

    public com.itasm.dto.procurement.PurchaseOrderDTO toPurchaseOrder(PurchaseOrder po) {
        if (po == null) return null;
        return new com.itasm.dto.procurement.PurchaseOrderDTO(
                po.getId(), po.getPoNumber(), toVendor(po.getVendor()),
                po.getQuantity(), po.getTotalCost(), po.getOrderDate(),
                po.getExpectedDelivery(), po.getReceivedDate(),
                po.getStatus(), po.getNotes(), po.getCreatedAt(), po.getUpdatedAt()
        );
    }

    public com.itasm.dto.audit.AuditLogDTO toAuditLog(AuditLog a) {
        if (a == null) return null;
        com.itasm.dto.audit.AuditUserDTO userDto = a.getPerformedBy() != null ?
                new com.itasm.dto.audit.AuditUserDTO(a.getPerformedBy().getId(), a.getPerformedBy().getUsername()) : null;
        return new com.itasm.dto.audit.AuditLogDTO(
                a.getId(), a.getEntityType(), a.getEntityId(), a.getAction(),
                a.getOldValue(), a.getNewValue(), userDto, a.getTimestamp()
        );
    }
}
