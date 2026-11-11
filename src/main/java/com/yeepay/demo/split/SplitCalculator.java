package com.yeepay.demo.split;

import com.yeepay.demo.common.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits an order amount among merchants by ratio.
 *
 * <p>Callers rely on one invariant: the shares always add up to the order amount, so the clearing
 * system can settle the order without a difference. Every share except the last is rounded half-up
 * to the cent; the last party in rule order takes the remainder, which absorbs the rounding
 * difference.
 */
public final class SplitCalculator {

    private static final RoundingMode SHARE_ROUNDING = RoundingMode.HALF_UP;

    /** Returns one share per rule, in rule order. */
    public List<SplitShare> split(BigDecimal orderAmount, List<SplitRule> rules) {
        BigDecimal amount = Money.requireValid(orderAmount, "orderAmount");
        requireRatiosSumToOne(rules);

        int lastIndex = rules.size() - 1;
        List<SplitShare> shares = new ArrayList<>(rules.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (SplitRule rule : rules.subList(0, lastIndex)) {
            BigDecimal share = amount.multiply(rule.ratio()).setScale(Money.SCALE, SHARE_ROUNDING);
            shares.add(new SplitShare(rule.merchantNo(), share));
            allocated = allocated.add(share);
        }
        shares.add(new SplitShare(rules.get(lastIndex).merchantNo(), remainderFor(amount, allocated)));
        return List.copyOf(shares);
    }

    private static void requireRatiosSumToOne(List<SplitRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("rules must not be empty");
        }
        BigDecimal ratioSum = rules.stream().map(SplitRule::ratio).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (ratioSum.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("split ratios must add up to 1, but add up to " + ratioSum);
        }
    }

    private static BigDecimal remainderFor(BigDecimal amount, BigDecimal allocated) {
        BigDecimal remainder = amount.subtract(allocated);
        if (remainder.signum() < 0) {
            throw new IllegalArgumentException(
                    "order amount " + amount + " is too small to split: rounded shares already take " + allocated);
        }
        return remainder;
    }
}
