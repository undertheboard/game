package com.game.common.stats;

/**
 * A modifier applied to a specific stat.
 * Supports additive and multiplicative operations.
 */
public class StatModifier {
    public enum Operation {
        /** Flat addition: finalValue += amount */
        ADD_FLAT,
        /** Percentage addition: finalValue += baseValue * amount */
        ADD_PERCENT,
        /** Multiplication applied after all additions: finalValue *= (1 + amount) */
        MULTIPLY
    }

    private final StatType statType;
    private final double amount;
    private final Operation operation;
    private final String source;

    public StatModifier(StatType statType, double amount, Operation operation, String source) {
        this.statType = statType;
        this.amount = amount;
        this.operation = operation;
        this.source = source;
    }

    public StatType getStatType() { return statType; }
    public double getAmount() { return amount; }
    public Operation getOperation() { return operation; }
    public String getSource() { return source; }

    @Override
    public String toString() {
        return "StatModifier{stat=" + statType + ", op=" + operation + ", amount=" + amount
                + ", source='" + source + "'}";
    }
}
