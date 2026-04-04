package com.game.common.stats;

import java.util.*;

/**
 * Manages a character's base stats and the dynamic StatModifier system.
 * Final stat value = (base + sumFlat) * (1 + sumPercent) * multiply
 */
public class StatSheet {
    private final Map<StatType, Double> baseStats = new EnumMap<>(StatType.class);
    private final List<StatModifier> modifiers = new ArrayList<>();

    public StatSheet() {
        // Default base values
        baseStats.put(StatType.MAX_HP, 100.0);
        baseStats.put(StatType.ATTACK, 10.0);
        baseStats.put(StatType.DEFENSE, 5.0);
        baseStats.put(StatType.SPEED, 5.0);
        baseStats.put(StatType.MAGIC_POWER, 5.0);
        baseStats.put(StatType.MAGIC_RESIST, 5.0);
        baseStats.put(StatType.CRITICAL_CHANCE, 0.05);
        baseStats.put(StatType.CRITICAL_MULTIPLIER, 1.5);
        baseStats.put(StatType.DODGE_CHANCE, 0.03);
        baseStats.put(StatType.RANGE, 1.0);
    }

    public void setBase(StatType type, double value) {
        baseStats.put(type, value);
    }

    public double getBase(StatType type) {
        return baseStats.getOrDefault(type, 0.0);
    }

    public void addModifier(StatModifier modifier) {
        modifiers.add(modifier);
    }

    public void removeModifier(StatModifier modifier) {
        modifiers.remove(modifier);
    }

    public void removeModifiersFromSource(String source) {
        modifiers.removeIf(m -> source.equals(m.getSource()));
    }

    public List<StatModifier> getModifiers() {
        return Collections.unmodifiableList(modifiers);
    }

    /**
     * Calculates the final value of a stat after applying all modifiers.
     */
    public double getFinalValue(StatType type) {
        double base = getBase(type);
        double flatSum = 0.0;
        double percentSum = 0.0;
        double multiplier = 1.0;

        for (StatModifier mod : modifiers) {
            if (mod.getStatType() != type) continue;
            switch (mod.getOperation()) {
                case ADD_FLAT -> flatSum += mod.getAmount();
                case ADD_PERCENT -> percentSum += mod.getAmount();
                case MULTIPLY -> multiplier *= (1.0 + mod.getAmount());
            }
        }
        return (base + flatSum) * (1.0 + percentSum) * multiplier;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("StatSheet{\n");
        for (StatType type : StatType.values()) {
            sb.append("  ").append(type).append(" = ").append(getFinalValue(type)).append('\n');
        }
        sb.append('}');
        return sb.toString();
    }
}
