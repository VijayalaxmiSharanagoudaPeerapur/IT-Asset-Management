package com.itasm.service;

import com.itasm.entity.Asset;
import com.itasm.entity.PurchaseOrder;
import com.itasm.entity.Vendor;
import com.itasm.exception.ResourceNotFoundException;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.PurchaseOrderRepository;
import com.itasm.repository.VendorRepository;
import com.itasm.domain.AssetStatus;
import com.itasm.dto.procurement.PurchaseOrderDTO;
import com.itasm.dto.procurement.VendorDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProcurementService {

    private final VendorRepository vendorRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AssetRepository assetRepository;
    private final Mapper mapper;

    public List<VendorDTO> getAllVendors() {
        return vendorRepository.findAll().stream()
                .map(mapper::toVendor)
                .toList();
    }

    @Transactional
    public VendorDTO createVendor(Vendor vendor) {
        return mapper.toVendor(vendorRepository.save(vendor));
    }

    public List<PurchaseOrderDTO> getAllPurchaseOrders() {
        return purchaseOrderRepository.findAll().stream()
                .map(mapper::toPurchaseOrder)
                .toList();
    }

    @Transactional
    public PurchaseOrderDTO createPurchaseOrder(PurchaseOrder po) {
        if (po.getVendor() != null && po.getVendor().getId() != null) {
            Vendor vendor = vendorRepository.findById(po.getVendor().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));
            po.setVendor(vendor);
        }
        return mapper.toPurchaseOrder(purchaseOrderRepository.save(po));
    }

    @Transactional
    public PurchaseOrderDTO markDelivered(Long poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase Order not found"));
        
        if (!"DELIVERED".equals(po.getStatus())) {
            po.setStatus("DELIVERED");
            po.setReceivedDate(LocalDate.now());
            
            java.math.BigDecimal unitPrice = po.getTotalCost() != null ? 
                po.getTotalCost().divide(new java.math.BigDecimal(po.getQuantity()), 2, java.math.RoundingMode.HALF_UP) : null;
                
            // Auto-generate assets in PROCURED status
            for (int i = 0; i < po.getQuantity(); i++) {
                Asset asset = Asset.builder()
                        .assetTag("AUTO-" + po.getPoNumber() + "-" + (i + 1))
                        .serialNumber("SN-" + po.getPoNumber() + "-" + (i + 1))
                        .type("Hardware") // Default type, can be updated later
                        .brand(po.getVendor().getName())
                        .model("From PO " + po.getPoNumber())
                        .status(AssetStatus.PROCURED)
                        .purchaseDate(po.getOrderDate())
                        .price(unitPrice)
                        .build();
                assetRepository.save(asset);
            }
        }
        return mapper.toPurchaseOrder(po);
    }
}
