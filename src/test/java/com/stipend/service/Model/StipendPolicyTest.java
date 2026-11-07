package com.stipend.service.Model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StipendPolicyTest {

    @Test
    void validateAcceptsIgireAttendanceTiers() {
        assertDoesNotThrow(() -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier("0", "50"),
                tier("85", "75"),
                tier("90", "90"),
                tier("95", "100")
        ).validate());
    }

    @Test
    void getPaymentPercentageUsesAttendanceThresholds() {
        StipendPolicy policy = policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier("0", "50"),
                tier("85", "75"),
                tier("90", "90"),
                tier("95", "100")
        );

        assertEquals(new BigDecimal("50"), policy.getPaymentPercentageForAttendance(
                new BigDecimal("84.99")
        ));
        assertEquals(new BigDecimal("75"), policy.getPaymentPercentageForAttendance(
                new BigDecimal("85")
        ));
        assertEquals(new BigDecimal("90"), policy.getPaymentPercentageForAttendance(
                new BigDecimal("94.99")
        ));
        assertEquals(new BigDecimal("100"), policy.getPaymentPercentageForAttendance(
                new BigDecimal("95")
        ));
    }

    @Test
    void validateRejectsMissingEffectiveStartDate() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                null,
                null,
                tier("0", "100")
        ).validate());
    }

    @Test
    void validateRejectsEndDateBeforeStartDate() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 31),
                tier("0", "100")
        ).validate());
    }

    @Test
    void validateRejectsMissingTiers() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null
        ).validate());
    }

    @Test
    void validateRejectsNullTierEntries() {
        StipendPolicy policy = policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier("0", "100")
        );
        List<StipendTier> tiers = new ArrayList<>();
        tiers.add(null);
        policy.setTiers(tiers);

        assertThrows(IllegalArgumentException.class, policy::validate);
    }

    @Test
    void validateRejectsMissingZeroThreshold() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier("85", "75")
        ).validate());
    }

    @Test
    void validateRejectsDuplicateThresholds() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier("0", "50"),
                tier("85", "75"),
                tier("85", "90")
        ).validate());
    }

    @Test
    void validateRejectsInvalidAttendanceThresholdsAndPaymentPercentages() {
        for (String value : List.of("-0.01", "100.01")) {
            assertThrows(IllegalArgumentException.class, () -> policy(
                    LocalDate.of(2026, 1, 1),
                    null,
                    tier("0", "100"),
                    tier(value, "50")
            ).validate());
        }
        for (String percentage : List.of("-0.01", "100.01")) {
            assertThrows(IllegalArgumentException.class, () -> policy(
                    LocalDate.of(2026, 1, 1),
                    null,
                    tier("0", percentage)
            ).validate());
        }
    }

    @Test
    void getPaymentPercentageRejectsInvalidAttendancePercentages() {
        StipendPolicy policy = policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier("0", "50")
        );
        for (BigDecimal percentage : List.of(
                new BigDecimal("-0.01"),
                new BigDecimal("100.01")
        )) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> policy.getPaymentPercentageForAttendance(percentage)
            );
        }
    }

    private StipendPolicy policy(
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            StipendTier... tiers
    ) {
        return StipendPolicy.builder()
                .effectiveFrom(effectiveFrom)
                .effectiveTo(effectiveTo)
                .tiers(List.of(tiers))
                .build();
    }

    private StipendTier tier(String minimumAttendance, String percentage) {
        return StipendTier.builder()
                .minimumAttendancePercentage(new BigDecimal(minimumAttendance))
                .paymentPercentage(new BigDecimal(percentage))
                .build();
    }
}
