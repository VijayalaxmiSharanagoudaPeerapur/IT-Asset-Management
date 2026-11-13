package com.itasm.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public enum SoftwareLicenseStatus {
    ACTIVE,
    EXPIRING_SOON,
    EXPIRING,
    EXPIRED,
    PERPETUAL;

    /**
     * Classifies a software license status based on non-overlapping expiry windows:
     * - PERPETUAL: when expirationDate is null
     * - EXPIRED: when expirationDate is strictly before today
     * - EXPIRING_SOON: within 30 days (0 to 30 days inclusive)
     * - EXPIRING: 31 to 90 days inclusive
     * - ACTIVE: more than 90 days remaining
     */
    public static SoftwareLicenseStatus fromExpirationDate(LocalDate expirationDate, LocalDate today) {
        if (expirationDate == null) {
            return PERPETUAL;
        }
        if (expirationDate.isBefore(today)) {
            return EXPIRED;
        }
        long days = ChronoUnit.DAYS.between(today, expirationDate);
        if (days <= 30) {
            return EXPIRING_SOON;
        }
        if (days <= 90) {
            return EXPIRING;
        }
        return ACTIVE;
    }
}
