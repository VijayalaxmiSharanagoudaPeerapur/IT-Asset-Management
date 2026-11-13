package com.itasm;

import com.itasm.domain.AssetStatus;
import com.itasm.entity.Asset;
import com.itasm.entity.PurchaseOrder;
import com.itasm.entity.Vendor;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.PurchaseOrderRepository;
import com.itasm.repository.VendorRepository;
import com.itasm.service.ProcurementService;
import com.itasm.dto.procurement.PurchaseOrderDTO;
import com.itasm.dto.procurement.VendorDTO;
import com.itasm.service.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcurementTest {

    @Mock
    private VendorRepository vendorRepository;
    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private AssetRepository assetRepository;
    @Spy
    private Mapper mapper = new Mapper();

    @InjectMocks
    private ProcurementService procurementService;

    private Vendor vendor;
    private PurchaseOrder purchaseOrder;

    @BeforeEach
    void setUp() {
        vendor = Vendor.builder()
                .id(1L)
                .name("Dell Enterprise Direct")
                .contactPerson("Michael Scott")
                .email("procurement@dell.com")
                .build();

        purchaseOrder = PurchaseOrder.builder()
                .id(501L)
                .poNumber("PO-2026-001")
                .vendor(vendor)
                .quantity(3)
                .totalCost(new BigDecimal("3000.00"))
                .orderDate(LocalDate.now().minusDays(5))
                .status("PENDING")
                .build();
    }

    @Test
    void createVendor_SavesAndReturnsVendorDTO() {
        when(vendorRepository.save(vendor)).thenReturn(vendor);

        VendorDTO saved = procurementService.createVendor(vendor);

        assertNotNull(saved);
        assertEquals("Dell Enterprise Direct", saved.name());
        verify(vendorRepository).save(vendor);
    }

    @Test
    void createPurchaseOrder_AssociatesExistingVendor() {
        when(vendorRepository.findById(1L)).thenReturn(Optional.of(vendor));
        when(purchaseOrderRepository.save(purchaseOrder)).thenReturn(purchaseOrder);

        PurchaseOrderDTO savedPo = procurementService.createPurchaseOrder(purchaseOrder);

        assertNotNull(savedPo);
        assertNotNull(savedPo.vendor());
        assertEquals("Dell Enterprise Direct", savedPo.vendor().name());
        verify(purchaseOrderRepository).save(purchaseOrder);
    }

    @Test
    void markDelivered_UpdatesPoAndAutoGeneratesAssets() {
        when(purchaseOrderRepository.findById(501L)).thenReturn(Optional.of(purchaseOrder));

        PurchaseOrderDTO deliveredPo = procurementService.markDelivered(501L);

        assertEquals("DELIVERED", deliveredPo.status());
        assertEquals(LocalDate.now(), deliveredPo.receivedDate());

        ArgumentCaptor<Asset> assetCaptor = ArgumentCaptor.forClass(Asset.class);
        verify(assetRepository, times(3)).save(assetCaptor.capture());

        List<Asset> createdAssets = assetCaptor.getAllValues();
        assertEquals(3, createdAssets.size());

        for (int i = 0; i < createdAssets.size(); i++) {
            Asset asset = createdAssets.get(i);
            assertEquals("AUTO-PO-2026-001-" + (i + 1), asset.getAssetTag());
            assertEquals(AssetStatus.PROCURED, asset.getStatus());
            assertEquals(new BigDecimal("1000.00"), asset.getPrice());
            assertEquals("Dell Enterprise Direct", asset.getBrand());
        }
    }

    @Test
    void markDelivered_WhenAlreadyDelivered_IsIdempotent() {
        purchaseOrder.setStatus("DELIVERED");
        when(purchaseOrderRepository.findById(501L)).thenReturn(Optional.of(purchaseOrder));

        PurchaseOrderDTO res = procurementService.markDelivered(501L);

        assertEquals("DELIVERED", res.status());
        verifyNoInteractions(assetRepository);
    }

    @Test
    void purchaseOrder_Serialization_IncludesVendorAndFieldsWithoutLazyOrProxyError() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.findAndRegisterModules();
        String json = objectMapper.writeValueAsString(purchaseOrder);
        assertNotNull(json);
        assertTrue(json.contains("PO-2026-001"));
        assertTrue(json.contains("Dell Enterprise Direct"));

        PurchaseOrderDTO dto = mapper.toPurchaseOrder(purchaseOrder);
        String dtoJson = objectMapper.writeValueAsString(dto);
        assertNotNull(dtoJson);
        assertTrue(dtoJson.contains("PO-2026-001"));
        assertTrue(dtoJson.contains("Dell Enterprise Direct"));
    }
}
