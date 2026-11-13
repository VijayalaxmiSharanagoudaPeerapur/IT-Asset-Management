package com.itasm.controller;

import com.itasm.domain.AssetStatus;
import com.itasm.dto.PageResponse;
import com.itasm.dto.asset.*;
import com.itasm.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
@Tag(name = "3. Asset Management")
public class AssetController {

    private final AssetService assetService;
    private final com.itasm.service.QrCodeService qrCodeService;
    private final com.itasm.service.AssetCsvService assetCsvService;

    @GetMapping
    @Operation(summary = "Search/filter assets with pagination")
    public PageResponse<AssetResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return PageResponse.of(assetService.search(q, status,
                PageRequest.of(page, size, Sort.by("id").descending())));
    }

    @GetMapping("/{id}")
    public AssetResponse get(@PathVariable Long id) {
        return assetService.get(id);
    }

    @GetMapping("/me")
    public List<AssetResponse> myAssets() {
        return assetService.myAssets();
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Full lifecycle history of an asset")
    public List<AssetHistoryResponse> history(@PathVariable Long id) {
        return assetService.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse create(@Valid @RequestBody AssetRequest request) {
        return assetService.create(request);
    }

    @PutMapping("/{id}")
    public AssetResponse update(@PathVariable Long id, @Valid @RequestBody AssetRequest request) {
        return assetService.update(id, request);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assign an available asset to an employee")
    public AssetResponse assign(@PathVariable Long id, @Valid @RequestBody AssignAssetRequest request) {
        return assetService.assign(id, request);
    }

    @PostMapping("/{id}/unassign")
    public AssetResponse unassign(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String notes = body == null ? null : body.get("notes");
        return assetService.unassign(id, notes);
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Change asset status (AVAILABLE / ASSIGNED / REPAIR / RETIRED)")
    public AssetResponse status(@PathVariable Long id, @Valid @RequestBody AssetStatusRequest request) {
        return assetService.changeStatus(id, request);
    }

    @PostMapping("/bulk-assign")
    @Operation(summary = "Bulk assign assets to an employee (Admin only)")
    public BulkOperationResponse bulkAssign(@Valid @RequestBody BulkAssignRequest request) {
        return assetService.bulkAssign(request);
    }

    @PostMapping("/bulk-status")
    @Operation(summary = "Bulk change asset status (Admin only)")
    public BulkOperationResponse bulkStatus(@Valid @RequestBody BulkStatusRequest request) {
        return assetService.bulkChangeStatus(request);
    }

    @PostMapping("/bulk-retire")
    @Operation(summary = "Bulk retire assets (Admin only)")
    public BulkOperationResponse bulkRetire(@Valid @RequestBody BulkRetireRequest request) {
        return assetService.bulkRetire(request);
    }

    @PostMapping(value = "/bulk-export", produces = "text/csv")
    @Operation(summary = "Bulk export selected assets to CSV (Admin only)")
    public byte[] bulkExportCsv(@RequestBody(required = false) BulkExportRequest request) throws Exception {
        List<Long> ids = request != null ? request.assetIds() : null;
        return assetCsvService.exportAssetsToCsv(ids);
    }

    @GetMapping(value = "/{id}/qrcode", produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Generate QR code for the asset")
    public byte[] generateQrCode(@PathVariable Long id) throws Exception {
        // Just verify it exists
        AssetResponse asset = assetService.get(id); 
        String url = "http://localhost:8080/assets/" + asset.id();
        return qrCodeService.generateQrCodeImage(url, 250, 250);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export assets to CSV")
    public byte[] exportCsv() throws Exception {
        return assetCsvService.exportAssetsToCsv();
    }

    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import assets from CSV")
    public Map<String, String> importCsv(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) throws Exception {
        int count = assetCsvService.importAssetsFromCsv(file);
        return Map.of("message", "Successfully imported " + count + " assets.");
    }

    @GetMapping("/high-risk")
    @Operation(summary = "Get high risk assets (Admin only)")
    public List<AssetResponse> highRisk() {
        return assetService.getHighRiskAssets();
    }
}
