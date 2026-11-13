package com.itasm.service;

import com.itasm.domain.AssetStatus;
import com.itasm.domain.HistoryAction;
import com.itasm.dto.asset.*;
import com.itasm.entity.Asset;
import com.itasm.entity.AssetHistory;
import com.itasm.entity.Employee;
import com.itasm.exception.DuplicateResourceException;
import com.itasm.exception.InvalidOperationException;
import com.itasm.exception.ResourceNotFoundException;
import com.itasm.repository.AssetHistoryRepository;
import com.itasm.repository.AssetRepository;
import com.itasm.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetHistoryRepository historyRepository;
    private final EmployeeService employeeService;
    private final Mapper mapper;
    private final CurrentUser currentUser;

    public Page<AssetResponse> search(String q, AssetStatus status, Pageable pageable) {
        requireAdmin();
        return assetRepository.search(q, status, pageable).map(mapper::toAsset);
    }

    public AssetResponse get(Long id) {
        Asset asset = find(id);
        if (!currentUser.isAdmin()) {
            Employee me = currentUser.requireEmployee();
            if (asset.getAssignedEmployee() == null || !asset.getAssignedEmployee().getId().equals(me.getId())) {
                throw new AccessDeniedException("You can only view your own assigned assets");
            }
        }
        return mapper.toAsset(asset);
    }

    public List<AssetResponse> byEmployee(Long employeeId) {
        if (!currentUser.isAdmin()) {
            Employee me = currentUser.requireEmployee();
            if (!me.getId().equals(employeeId)) {
                throw new AccessDeniedException("Employees can only view their own assets");
            }
        }
        return assetRepository.findByAssignedEmployee_Id(employeeId).stream().map(mapper::toAsset).toList();
    }

    public List<AssetResponse> myAssets() {
        return byEmployee(currentUser.requireEmployee().getId());
    }

    public List<AssetHistoryResponse> history(Long assetId) {
        Asset asset = find(assetId);
        if (!currentUser.isAdmin()) {
            Employee me = currentUser.requireEmployee();
            if (asset.getAssignedEmployee() == null || !asset.getAssignedEmployee().getId().equals(me.getId())) {
                throw new AccessDeniedException("You can only view history of your own assigned assets");
            }
        }
        return historyRepository.findByAsset_IdOrderByCreatedAtDesc(assetId).stream().map(mapper::toHistory).toList();
    }

    @Transactional
    public AssetResponse create(AssetRequest request) {
        requireAdmin();
        if (assetRepository.existsByAssetTag(request.assetTag())
                || assetRepository.existsBySerialNumber(request.serialNumber())) {
            throw new DuplicateResourceException("Asset tag or serial number already exists");
        }
        if (request.status() == AssetStatus.ASSIGNED) {
            throw new InvalidOperationException("Create the asset as AVAILABLE, then assign it");
        }
        Asset asset = Asset.builder()
                .assetTag(request.assetTag())
                .serialNumber(request.serialNumber())
                .type(request.type())
                .brand(request.brand())
                .model(request.model())
                .purchaseDate(request.purchaseDate())
                .warrantyExpiry(request.warrantyExpiry())
                .price(request.price())
                .status(request.status() == null ? AssetStatus.AVAILABLE : request.status())
                .notes(request.notes())
                .build();
        assetRepository.save(asset);
        record(asset, HistoryAction.CREATED, null, asset.getStatus(), null, "Asset registered");
        return mapper.toAsset(asset);
    }

    @Transactional
    public AssetResponse update(Long id, AssetRequest request) {
        requireAdmin();
        Asset asset = find(id);
        asset.setType(request.type());
        asset.setBrand(request.brand());
        asset.setModel(request.model());
        asset.setPurchaseDate(request.purchaseDate());
        asset.setWarrantyExpiry(request.warrantyExpiry());
        asset.setPrice(request.price());
        asset.setNotes(request.notes());
        record(asset, HistoryAction.UPDATED, asset.getStatus(), asset.getStatus(), asset.getAssignedEmployee(), "Asset details updated");
        return mapper.toAsset(asset);
    }

    @Transactional
    public AssetResponse assign(Long assetId, AssignAssetRequest request) {
        requireAdmin();
        Asset asset = find(assetId);
        if (asset.getStatus() == AssetStatus.RETIRED) {
            throw new InvalidOperationException("Cannot assign a retired asset");
        }
        if (asset.getStatus() == AssetStatus.REPAIR) {
            throw new InvalidOperationException("Cannot assign an asset that is under repair");
        }
        if (asset.getAssignedEmployee() != null) {
            throw new InvalidOperationException("Asset is already assigned. Unassign it first to prevent duplicate assignment.");
        }
        Employee employee = employeeService.find(request.employeeId());
        if (!employee.isActive()) {
            throw new InvalidOperationException("Cannot assign asset to an inactive employee");
        }
        AssetStatus from = asset.getStatus();
        asset.setAssignedEmployee(employee);
        asset.setStatus(AssetStatus.ASSIGNED);
        record(asset, HistoryAction.ASSIGNED, from, AssetStatus.ASSIGNED, employee,
                request.notes() != null ? request.notes() : "Assigned to " + employee.getFullName());
        return mapper.toAsset(asset);
    }

    @Transactional
    public AssetResponse unassign(Long assetId, String notes) {
        requireAdmin();
        Asset asset = find(assetId);
        if (asset.getAssignedEmployee() == null) {
            throw new InvalidOperationException("Asset is not currently assigned");
        }
        Employee previous = asset.getAssignedEmployee();
        AssetStatus from = asset.getStatus();
        asset.setAssignedEmployee(null);
        if (asset.getStatus() != AssetStatus.REPAIR && asset.getStatus() != AssetStatus.RETIRED) {
            asset.setStatus(AssetStatus.AVAILABLE);
        }
        record(asset, HistoryAction.UNASSIGNED, from, asset.getStatus(), previous,
                notes != null ? notes : "Unassigned from " + previous.getFullName());
        return mapper.toAsset(asset);
    }

    @Transactional
    public AssetResponse changeStatus(Long assetId, AssetStatusRequest request) {
        requireAdmin();
        Asset asset = find(assetId);
        AssetStatus from = asset.getStatus();
        AssetStatus to = request.status();
        if (from == to) {
            return mapper.toAsset(asset);
        }
        HistoryAction action = HistoryAction.STATUS_CHANGED;
        if (to == AssetStatus.REPAIR) {
            action = HistoryAction.SENT_FOR_REPAIR;
        } else if (from == AssetStatus.REPAIR && (to == AssetStatus.AVAILABLE || to == AssetStatus.ASSIGNED)) {
            action = HistoryAction.REPAIRED;
            if (to == AssetStatus.ASSIGNED && asset.getAssignedEmployee() == null) {
                throw new InvalidOperationException("Assign the asset to an employee before marking it ASSIGNED");
            }
        } else if (to == AssetStatus.RETIRED) {
            action = HistoryAction.RETIRED;
            asset.setAssignedEmployee(null);
        } else if (to == AssetStatus.ASSIGNED && asset.getAssignedEmployee() == null) {
            throw new InvalidOperationException("Use the assign API to set ASSIGNED");
        } else if (to == AssetStatus.AVAILABLE) {
            asset.setAssignedEmployee(null);
        }
        asset.setStatus(to);
        record(asset, action, from, to, asset.getAssignedEmployee(), request.notes());
        return mapper.toAsset(asset);
    }

    @Transactional
    public BulkOperationResponse bulkAssign(BulkAssignRequest request) {
        requireAdmin();
        if (request == null || request.assetIds() == null || request.assetIds().isEmpty()) {
            return BulkOperationResponse.failed(0, "No assets selected for bulk assignment", List.of("Asset list cannot be empty"));
        }

        Employee employee;
        try {
            employee = employeeService.find(request.employeeId());
        } catch (ResourceNotFoundException e) {
            return BulkOperationResponse.failed(request.assetIds().size(), "Invalid target employee", List.of(e.getMessage()));
        }
        if (!employee.isActive()) {
            return BulkOperationResponse.failed(request.assetIds().size(), "Cannot assign to inactive employee",
                    List.of("Employee '" + employee.getFullName() + "' (" + employee.getEmployeeCode() + ") is inactive"));
        }

        // Validate EVERY asset before making any mutations!
        List<String> errors = new ArrayList<>();
        List<Asset> assets = new ArrayList<>();

        for (Long id : request.assetIds()) {
            Optional<Asset> opt = assetRepository.findById(id);
            if (opt.isEmpty()) {
                errors.add("Asset #" + id + " not found in database");
                continue;
            }
            Asset a = opt.get();
            if (a.getStatus() == AssetStatus.RETIRED) {
                errors.add("Asset " + a.getAssetTag() + " (" + a.getModel() + ") is RETIRED and cannot be assigned");
            } else if (a.getStatus() == AssetStatus.REPAIR) {
                errors.add("Asset " + a.getAssetTag() + " (" + a.getModel() + ") is under REPAIR and cannot be assigned");
            } else if (a.getAssignedEmployee() != null) {
                errors.add("Asset " + a.getAssetTag() + " is already assigned to " + a.getAssignedEmployee().getFullName());
            } else {
                assets.add(a);
            }
        }

        if (!errors.isEmpty()) {
            return BulkOperationResponse.failed(request.assetIds().size(),
                    "Bulk assignment aborted: validation failed for " + errors.size() + " asset(s)", errors);
        }

        List<AssetResponse> updated = new ArrayList<>();
        String notes = request.notes() != null && !request.notes().isBlank() ? request.notes() : "Bulk assigned to " + employee.getFullName();
        for (Asset asset : assets) {
            AssetStatus from = asset.getStatus();
            asset.setAssignedEmployee(employee);
            asset.setStatus(AssetStatus.ASSIGNED);
            record(asset, HistoryAction.ASSIGNED, from, AssetStatus.ASSIGNED, employee, notes);
            updated.add(mapper.toAsset(asset));
        }

        return BulkOperationResponse.ok("Successfully assigned " + updated.size() + " asset(s) to " + employee.getFullName(), updated);
    }

    @Transactional
    public BulkOperationResponse bulkChangeStatus(BulkStatusRequest request) {
        requireAdmin();
        if (request == null || request.assetIds() == null || request.assetIds().isEmpty()) {
            return BulkOperationResponse.failed(0, "No assets selected for status update", List.of("Asset list cannot be empty"));
        }
        if (request.status() == null) {
            return BulkOperationResponse.failed(request.assetIds().size(), "Target status is required", List.of("Status cannot be null"));
        }
        if (request.status() == AssetStatus.ASSIGNED) {
            return BulkOperationResponse.failed(request.assetIds().size(),
                    "Invalid status transition",
                    List.of("Cannot bulk change status to ASSIGNED directly without an assigned employee. Please use 'Bulk Assign'."));
        }

        List<String> errors = new ArrayList<>();
        List<Asset> assets = new ArrayList<>();

        for (Long id : request.assetIds()) {
            Optional<Asset> opt = assetRepository.findById(id);
            if (opt.isEmpty()) {
                errors.add("Asset #" + id + " not found in database");
                continue;
            }
            assets.add(opt.get());
        }

        if (!errors.isEmpty()) {
            return BulkOperationResponse.failed(request.assetIds().size(),
                    "Bulk status update aborted: validation failed for " + errors.size() + " asset(s)", errors);
        }

        List<AssetResponse> updated = new ArrayList<>();
        for (Asset asset : assets) {
            AssetStatus from = asset.getStatus();
            AssetStatus to = request.status();
            if (from == to) {
                updated.add(mapper.toAsset(asset));
                continue;
            }

            HistoryAction action = HistoryAction.STATUS_CHANGED;
            if (to == AssetStatus.REPAIR) {
                action = HistoryAction.SENT_FOR_REPAIR;
                asset.setAssignedEmployee(null);
            } else if (from == AssetStatus.REPAIR && to == AssetStatus.AVAILABLE) {
                action = HistoryAction.REPAIRED;
                asset.setAssignedEmployee(null);
            } else if (to == AssetStatus.RETIRED) {
                action = HistoryAction.RETIRED;
                asset.setAssignedEmployee(null);
            } else if (to == AssetStatus.AVAILABLE) {
                asset.setAssignedEmployee(null);
            }

            asset.setStatus(to);
            record(asset, action, from, to, asset.getAssignedEmployee(), request.notes());
            updated.add(mapper.toAsset(asset));
        }

        return BulkOperationResponse.ok("Successfully updated status to " + request.status() + " for " + updated.size() + " asset(s)", updated);
    }

    @Transactional
    public BulkOperationResponse bulkRetire(BulkRetireRequest request) {
        requireAdmin();
        if (request == null || request.assetIds() == null || request.assetIds().isEmpty()) {
            return BulkOperationResponse.failed(0, "No assets selected to retire", List.of("Asset list cannot be empty"));
        }

        List<String> errors = new ArrayList<>();
        List<Asset> assets = new ArrayList<>();

        for (Long id : request.assetIds()) {
            Optional<Asset> opt = assetRepository.findById(id);
            if (opt.isEmpty()) {
                errors.add("Asset #" + id + " not found in database");
                continue;
            }
            Asset a = opt.get();
            if (a.getStatus() == AssetStatus.RETIRED) {
                errors.add("Asset " + a.getAssetTag() + " is already RETIRED");
            } else {
                assets.add(a);
            }
        }

        if (!errors.isEmpty()) {
            return BulkOperationResponse.failed(request.assetIds().size(),
                    "Bulk retire aborted: validation failed for " + errors.size() + " asset(s)", errors);
        }

        List<AssetResponse> updated = new ArrayList<>();
        String reason = request.reason() != null && !request.reason().isBlank() ? request.reason() : "Bulk retired by administrator";
        for (Asset asset : assets) {
            AssetStatus from = asset.getStatus();
            Employee prev = asset.getAssignedEmployee();
            asset.setAssignedEmployee(null);
            asset.setStatus(AssetStatus.RETIRED);
            record(asset, HistoryAction.RETIRED, from, AssetStatus.RETIRED, prev, reason);
            updated.add(mapper.toAsset(asset));
        }

        return BulkOperationResponse.ok("Successfully retired " + updated.size() + " asset(s)", updated);
    }

    @Transactional
    public List<AssetResponse> bulkAssign(List<Long> assetIds, Long employeeId, String notes) {
        BulkOperationResponse res = bulkAssign(new BulkAssignRequest(assetIds, employeeId, notes));
        if (!res.success()) {
            throw new InvalidOperationException(res.message() + ": " + String.join(", ", res.errors()));
        }
        return res.updatedAssets();
    }

    @Transactional
    public List<AssetResponse> bulkChangeStatus(List<Long> assetIds, AssetStatus status, String notes) {
        BulkOperationResponse res = bulkChangeStatus(new BulkStatusRequest(assetIds, status, notes));
        if (!res.success()) {
            throw new InvalidOperationException(res.message() + ": " + String.join(", ", res.errors()));
        }
        return res.updatedAssets();
    }

    public Asset find(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found: " + id));
    }

    private void record(Asset asset, HistoryAction action, AssetStatus from, AssetStatus to,
                        Employee employee, String notes) {
        historyRepository.save(AssetHistory.builder()
                .asset(asset)
                .action(action)
                .fromStatus(from)
                .toStatus(to)
                .employee(employee)
                .performedBy(currentUser.requireUser())
                .notes(notes)
                .build());
    }

    private void requireAdmin() {
        if (!currentUser.isAdmin()) {
            throw new AccessDeniedException("Only admin can manage assets");
        }
    }

    public List<AssetResponse> getHighRiskAssets() {
        requireAdmin();
        return assetRepository.findTop20ByRiskScoreGreaterThanOrderByRiskScoreDesc(30).stream()
                .map(mapper::toAsset)
                .toList();
    }
}
