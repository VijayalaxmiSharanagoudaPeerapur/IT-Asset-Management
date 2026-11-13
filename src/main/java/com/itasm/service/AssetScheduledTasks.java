package com.itasm.service;

import com.itasm.domain.Role;
import com.itasm.entity.Asset;
import com.itasm.entity.Notification;
import com.itasm.entity.UserAccount;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.NotificationRepository;
import com.itasm.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetScheduledTasks {

    private final AssetRepository assetRepository;
    private final NotificationRepository notificationRepository;
    private final UserAccountRepository userAccountRepository;
    private final com.itasm.repository.AssetHistoryRepository assetHistoryRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void checkWarrantyExpirations() {
        log.info("Running scheduled task to check for warranty expirations");
        
        LocalDate thirtyDaysFromNow = LocalDate.now().plusDays(30);
        List<Asset> expiringAssets = assetRepository.findAll().stream()
                .filter(a -> a.getWarrantyExpiry() != null)
                .filter(a -> a.getWarrantyExpiry().isBefore(thirtyDaysFromNow) && a.getWarrantyExpiry().isAfter(LocalDate.now().minusDays(1)))
                .toList();

        if (expiringAssets.isEmpty()) {
            return;
        }

        List<UserAccount> admins = userAccountRepository.findByRole(Role.ADMIN);
        
        for (Asset asset : expiringAssets) {
            String message = String.format("Warranty for Asset %s (%s) is expiring on %s", 
                    asset.getAssetTag(), asset.getModel(), asset.getWarrantyExpiry());
            
            for (UserAccount admin : admins) {
                notificationRepository.save(Notification.builder()
                        .user(admin)
                        .message(message)
                        .readFlag(false)
                        .build());
            }
        }
        
        log.info("Generated warranty expiration notifications for {} assets", expiringAssets.size());
    }

    @Scheduled(cron = "0 0 3 * * *") // Run daily at 3 AM
    @Transactional
    public void calculateRiskScores() {
        log.info("Running scheduled task to calculate asset risk scores");
        List<Asset> allAssets = assetRepository.findAll();
        for (Asset asset : allAssets) {
            int score = 0;
            // 1. Age (10 points per year, cap at 50)
            if (asset.getPurchaseDate() != null) {
                int age = LocalDate.now().getYear() - asset.getPurchaseDate().getYear();
                if (age > 0) {
                    score += Math.min(age * 10, 50);
                }
            }
            
            // 2. Repairs (15 points per repair)
            long repairCount = assetHistoryRepository.findByAsset_IdOrderByCreatedAtDesc(asset.getId()).stream()
                    .filter(h -> h.getToStatus() == com.itasm.domain.AssetStatus.REPAIR)
                    .count();
            score += repairCount * 15;
            
            // 3. Warranty Expiration (20 for expired, 10 for <30 days)
            if (asset.getWarrantyExpiry() != null) {
                if (asset.getWarrantyExpiry().isBefore(LocalDate.now())) {
                    score += 20;
                } else if (asset.getWarrantyExpiry().isBefore(LocalDate.now().plusDays(30))) {
                    score += 10;
                }
            }
            
            asset.setRiskScore(score);
            assetRepository.save(asset);
        }
        log.info("Updated risk scores for {} assets", allAssets.size());
    }
}
