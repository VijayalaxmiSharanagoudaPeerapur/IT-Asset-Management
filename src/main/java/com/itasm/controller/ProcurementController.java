package com.itasm.controller;

import com.itasm.dto.procurement.PurchaseOrderDTO;
import com.itasm.dto.procurement.VendorDTO;
import com.itasm.entity.PurchaseOrder;
import com.itasm.entity.Vendor;
import com.itasm.service.ProcurementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/procurement")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ProcurementController {

    private final ProcurementService procurementService;

    @GetMapping("/vendors")
    public List<VendorDTO> getVendors() {
        return procurementService.getAllVendors();
    }

    @PostMapping("/vendors")
    public VendorDTO createVendor(@RequestBody Vendor vendor) {
        return procurementService.createVendor(vendor);
    }

    @GetMapping("/orders")
    public List<PurchaseOrderDTO> getPurchaseOrders() {
        return procurementService.getAllPurchaseOrders();
    }

    @PostMapping("/orders")
    public PurchaseOrderDTO createPurchaseOrder(@RequestBody PurchaseOrder order) {
        return procurementService.createPurchaseOrder(order);
    }

    @PostMapping("/orders/{id}/deliver")
    public PurchaseOrderDTO markDelivered(@PathVariable Long id) {
        return procurementService.markDelivered(id);
    }
}
