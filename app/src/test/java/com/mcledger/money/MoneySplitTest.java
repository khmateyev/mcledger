package com.mcledger.money;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mcledger.money.CurrencyCode.AED;
import static com.mcledger.money.CurrencyCode.BHD;
import static com.mcledger.money.RemainderAllocation.FIRST;
import static com.mcledger.money.RemainderAllocation.LAST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneySplitTest {

    @Test
    void e10SplitsIntoThreeInstalmentsThatSumExactly() {
        // Acceptance criterion 7 claims 3 x 3.334; that sums to 10.002, so it is rejected.
        final List<Money> parts = Money.of(BHD, "10.000").split(3, FIRST);
        assertEquals(List.of(Money.of(BHD, "3.334"), Money.of(BHD, "3.333"), Money.of(BHD, "3.333")), parts);
        assertEquals(Money.of(BHD, "10.000"), sum(parts));
    }

    @Test
    void remainderCanGoToTheLastParts() {
        assertEquals(List.of(Money.of(BHD, "3.333"), Money.of(BHD, "3.333"), Money.of(BHD, "3.334")),
                Money.of(BHD, "10.000").split(3, LAST));
    }

    @Test
    void remainderOfMoreThanOneUnitIsSpreadOneUnitPerPart() {
        // 0.05 AED = 5 units / 3 -> 2, 2, 1
        assertEquals(List.of(Money.of(AED, "0.02"), Money.of(AED, "0.02"), Money.of(AED, "0.01")),
                Money.of(AED, "0.05").split(3, FIRST));
    }

    @Test
    void evenSplitHasNoRemainder() {
        assertEquals(List.of(Money.of(AED, "300.00"), Money.of(AED, "300.00")),
                Money.of(AED, "600.00").split(2, FIRST));
    }

    @Test
    void negativeAmountsSplitWithTheSameSign() {
        final List<Money> parts = Money.of(BHD, "-10.000").split(3, FIRST);
        assertEquals(List.of(Money.of(BHD, "-3.334"), Money.of(BHD, "-3.333"), Money.of(BHD, "-3.333")), parts);
        assertEquals(Money.of(BHD, "-10.000"), sum(parts));
    }

    @Test
    void amountSmallerThanPartsGivesZeros() {
        assertEquals(List.of(Money.of(AED, "0.01"), Money.zero(AED), Money.zero(AED)),
                Money.of(AED, "0.01").split(3, FIRST));
    }

    @Test
    void partsAlwaysSumToTheOriginal() {
        for (int units = -50; units <= 50; units++) {
            final Money total = Money.of(BHD, java.math.BigDecimal.valueOf(units, 3));
            for (int n = 1; n <= 7; n++) {
                assertEquals(total, sum(total.split(n, FIRST)), () -> "split of " + total);
                assertEquals(total, sum(total.split(n, LAST)), () -> "split of " + total);
            }
        }
    }

    @Test
    void invalidPartCountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(AED, "1.00").split(0, FIRST));
    }

    private static Money sum(List<Money> parts) {
        return parts.stream().reduce(Money.zero(parts.get(0).currency()), Money::plus);
    }
}
