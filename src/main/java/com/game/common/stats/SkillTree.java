package com.game.common.stats;

import java.util.*;

/**
 * Skill Tree system. Skills can be unlocked and grant StatModifiers.
 * Skills form a DAG where each skill may require prerequisite skills.
 */
public class SkillTree {
    private final Map<String, SkillNode> skills = new LinkedHashMap<>();
    private final Set<String> unlockedSkills = new HashSet<>();
    private int availablePoints = 0;

    public SkillTree() {
        buildDefaultTree();
    }

    private void buildDefaultTree() {
        // Tier 1 – no prerequisites
        addSkill(new SkillNode("POWER_I", "Power I", "Increases attack by 5", 1,
                Collections.emptyList(),
                List.of(new StatModifier(StatType.ATTACK, 5, StatModifier.Operation.ADD_FLAT, "POWER_I"))));

        addSkill(new SkillNode("VITALITY_I", "Vitality I", "Increases max HP by 20", 1,
                Collections.emptyList(),
                List.of(new StatModifier(StatType.MAX_HP, 20, StatModifier.Operation.ADD_FLAT, "VITALITY_I"))));

        addSkill(new SkillNode("SWIFT_I", "Swift I", "Increases speed by 2", 1,
                Collections.emptyList(),
                List.of(new StatModifier(StatType.SPEED, 2, StatModifier.Operation.ADD_FLAT, "SWIFT_I"))));

        // Tier 2 – require Tier 1
        addSkill(new SkillNode("POWER_II", "Power II", "+10% attack", 2,
                List.of("POWER_I"),
                List.of(new StatModifier(StatType.ATTACK, 0.10, StatModifier.Operation.ADD_PERCENT, "POWER_II"))));

        addSkill(new SkillNode("VITALITY_II", "Vitality II", "+25% max HP", 2,
                List.of("VITALITY_I"),
                List.of(new StatModifier(StatType.MAX_HP, 0.25, StatModifier.Operation.ADD_PERCENT, "VITALITY_II"))));

        addSkill(new SkillNode("CRITICAL_I", "Critical Strike I", "+5% crit chance", 2,
                List.of("POWER_I"),
                List.of(new StatModifier(StatType.CRITICAL_CHANCE, 0.05, StatModifier.Operation.ADD_FLAT, "CRITICAL_I"))));

        // Tier 3 – evolution skills
        addSkill(new SkillNode("BERSERKER", "Berserker", "Attack x1.5, Defense -30%", 3,
                List.of("POWER_II", "CRITICAL_I"),
                List.of(
                        new StatModifier(StatType.ATTACK, 0.5, StatModifier.Operation.MULTIPLY, "BERSERKER"),
                        new StatModifier(StatType.DEFENSE, -0.30, StatModifier.Operation.ADD_PERCENT, "BERSERKER"))));

        addSkill(new SkillNode("PALADIN", "Paladin", "+50% HP and defense", 3,
                List.of("VITALITY_II"),
                List.of(
                        new StatModifier(StatType.MAX_HP, 0.50, StatModifier.Operation.MULTIPLY, "PALADIN"),
                        new StatModifier(StatType.DEFENSE, 0.50, StatModifier.Operation.MULTIPLY, "PALADIN"))));
    }

    public void addSkill(SkillNode node) {
        skills.put(node.getId(), node);
    }

    public void addPoints(int points) {
        availablePoints += points;
    }

    public int getAvailablePoints() { return availablePoints; }

    /**
     * Unlocks a skill and applies its modifiers to the given StatSheet.
     */
    public boolean unlockSkill(String skillId, StatSheet sheet) {
        SkillNode node = skills.get(skillId);
        if (node == null || unlockedSkills.contains(skillId)) return false;
        if (availablePoints < node.getCost()) return false;
        for (String req : node.getPrerequisites()) {
            if (!unlockedSkills.contains(req)) return false;
        }
        unlockedSkills.add(skillId);
        availablePoints -= node.getCost();
        node.getModifiers().forEach(sheet::addModifier);
        return true;
    }

    public boolean isUnlocked(String skillId) { return unlockedSkills.contains(skillId); }
    public Map<String, SkillNode> getSkills() { return Collections.unmodifiableMap(skills); }

    /** Represents a single node in the skill tree. */
    public static class SkillNode {
        private final String id;
        private final String name;
        private final String description;
        private final int cost;
        private final List<String> prerequisites;
        private final List<StatModifier> modifiers;

        public SkillNode(String id, String name, String description, int cost,
                         List<String> prerequisites, List<StatModifier> modifiers) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.cost = cost;
            this.prerequisites = prerequisites;
            this.modifiers = modifiers;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getCost() { return cost; }
        public List<String> getPrerequisites() { return prerequisites; }
        public List<StatModifier> getModifiers() { return modifiers; }
    }
}
