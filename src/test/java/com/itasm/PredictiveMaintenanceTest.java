package com.itasm;

import com.itasm.domain.AssetStatus;
import com.itasm.domain.HistoryAction;
import com.itasm.domain.Role;
import com.itasm.entity.Asset;
import com.itasm.entity.AssetHistory;
import com.itasm.entity.Notification;
import com.itasm.entity.UserAccount;
import com.itasm.repository.AssetHistoryRepository;
import com.itasm.repository.AssetRepository;
import com.itasm.repository.NotificationRepository;
import com.itasm.repository.UserAccountRepository;
import com.itasm.service.AssetScheduledTasks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PredictiveMaintenanceTest {

    @Mock
    private AssetRepository assetRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private AssetHistoryRepository assetHistoryRepository;

    @InjectMocks
    private AssetScheduledTasks assetScheduledTasks;

    private UserAccount adminUser;

    @BeforeEach
    void setUp() {
        adminUser = UserAccount.builder()
                .id(1L)
                .username("admin")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    void calculateRiskScores_ComputesPointsCorrectly() {
        // Asset aged 3 years (30 pts), 2 repairs (30 pts), expired warranty (20 pts) -> Total = 80 (Critical)
        Asset asset1 = Asset.builder()
                .id(101L)
                .assetTag("AST-OLD-01")
                .brand("Dell")
                .model("OptiPlex 7080")
                .purchaseDate(LocalDate.now().minusYears(3))
                .warrantyExpiry(LocalDate.now().minusDays(15))
                .riskScore(0)
                .build();

        AssetHistory h1 = AssetHistory.builder().toStatus(AssetStatus.REPAIR).build();
        AssetHistory h2 = AssetHistory.builder().toStatus(AssetStatus.REPAIR).build();
        AssetHistory h3 = AssetHistory.builder().toStatus(AssetStatus.AVAILABLE).build();

        when(assetRepository.findAll()).thenReturn(List.of(asset1));
        when(assetHistoryRepository.findByAsset_IdOrderByCreatedAtDesc(101L)).thenReturn(List.of(h1, h2, h3));

        assetScheduledTasks.calculateRiskScores();

        assertEquals(80, asset1.getRiskScore());
        verify(assetRepository).save(asset1);
    }

    @Test
    void calculateRiskScores_CapsAgeScoreAtFifty() {
        // Asset aged 8 years (80 pts -> capped at 50), 0 repairs, active warranty -> Total = 50
        Asset assetOld = Asset.builder()
                .id(102L)
                .assetTag("AST-VINTAGE")
                .brand("HP")
                .model("ProBook")
                .purchaseDate(LocalDate.now().minusYears(8))
                .warrantyExpiry(LocalDate.now().plusYears(1))
                .riskScore(0)
                .build();

        when(assetRepository.findAll()).thenReturn(List.of(assetOld));
        when(assetHistoryRepository.findByAsset_IdOrderByCreatedAtDesc(102L)).thenReturn(List.of());

        assetScheduledTasks.calculateRiskScores();

        assertEquals(50, assetOld.getRiskScore());
        verify(assetRepository).save(assetOld);
    }

    @Test
    void checkWarrantyExpirations_NotifiesAdminForExpiringAssets() {
        Asset expiringAsset = Asset.builder()
                .id(103L)
                .assetTag("AST-EXP-01")
                .brand("Apple")
                .model("MacBook Air")
                .warrantyExpiry(LocalDate.now().plusDays(15)) // within 30 days
                .build();

        Asset safeAsset = Asset.builder()
                .id(104L)
                .assetTag("AST-SAFE-02")
                .brand("Lenovo")
                .model("ThinkPad")
                .warrantyExpiry(LocalDate.now().plusDays(90)) // > 30 days
                .build();

        when(assetRepository.findAll()).thenReturn(List.of(expiringAsset, safeAsset));
        when(userAccountRepository.findByRole(Role.ADMIN)).thenReturn(List.of(adminUser));

        assetScheduledTasks.checkWarrantyExpirations();

        verify(notificationRepository, times(1)).save(argThat(notif ->
                notif.getMessage().contains("Warranty for Asset AST-EXP-01") &&
                notif.getMessage().contains("MacBook Air")
        ));
    }
}
