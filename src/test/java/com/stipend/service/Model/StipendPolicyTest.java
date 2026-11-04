package com.stipend.service.Model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StipendPolicyTest {

    @Test
    void validateAcceptsContiguousTiersCoveringAllAbsenceCounts() {
        StipendPolicy policy = policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier(0, 2, "100"),
                tier(3, 3, "50"),
                tier(4, null, "0")
        );

        assertDoesNotThrow(policy::validate);
    }

    @Test
    void validateRejectsMissingEffectiveStartDate() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                null,
                null,
                tier(0, null, "100")
        ).validate());
    }

    @Test
    void validateRejectsEndDateBeforeStartDate() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 31),
                tier(0, null, "100")
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
                tier(0, null, "100")
        );
        List<StipendTier> tiers = new ArrayList<>();
        tiers.add(null);
        policy.setTiers(tiers);

        assertThrows(IllegalArgumentException.class, policy::validate);
    }

    @Test
    void validateRejectsNegativeMinimum() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier(-1, null, "100")
        ).validate());
    }

    @Test
    void validateRejectsMaximumBelowMinimum() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier(2, 1, "100")
        ).validate());
    }

    @Test
    void validateRejectsOverlappingTiers() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier(0, 3, "100"),
                tier(3, null, "50")
        ).validate());
    }

    @Test
    void validateRejectsGapsBetweenTiers() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier(0, 1, "100"),
                tier(3, null, "50")
        ).validate());
    }

    @Test
    void validateRejectsTiersThatDoNotStartAtZero() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier(1, null, "100")
        ).validate());
    }

    @Test
    void validateRejectsAnUnboundedTierBeforeTheFinalTier() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier(0, null, "100"),
                tier(1, null, "50")
        ).validate());
    }

    @Test
    void validateRejectsFiniteFinalTier() {
        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier(0, 5, "100")
        ).validate());
    }

    @Test
    void validateRejectsPercentagesOutsideZeroToOneHundred() {
        for (String percentage : List.of("-0.01", "100.01")) {
            assertThrows(IllegalArgumentException.class, () -> policy(
                    LocalDate.of(2026, 1, 1),
                    null,
                    tier(0, null, percentage)
            ).validate());
        }
    }

    @Test
    void validateRejectsMissingPaymentPercentage() {
        StipendTier tier = tier(0, null, "100");
        tier.setPaymentPercentage(null);

        assertThrows(IllegalArgumentException.class, () -> policy(
                LocalDate.of(2026, 1, 1),
                null,
                tier
        ).validate());
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

    private StipendTier tier(int minimum, Integer maximum, String percentage) {
        return StipendTier.builder()
                .minimumAbsences(minimum)
                .maximumAbsences(maximum)
                .paymentPercentage(new BigDecimal(percentage))
                .build();
    }
}
