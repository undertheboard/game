package com.game.common.entity;

import com.game.common.inventory.Inventory;
import com.game.common.stats.SkillTree;

/**
 * Player entity with inventory and skill tree.
 */
public class Player extends Entity {
    private final Inventory inventory;
    private final SkillTree skillTree;
    private String role;
    private int level;
    private long experience;
    private String connectionId;

    public Player(String name, double x, double y) {
        super(name, x, y);
        this.inventory = new Inventory();
        this.skillTree = new SkillTree();
        this.role = "PLAYER";
        this.level = 1;
        this.experience = 0;
    }

    public Inventory getInventory() { return inventory; }
    public SkillTree getSkillTree() { return skillTree; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isAdmin() { return "ADMIN".equals(role); }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public long getExperience() { return experience; }
    public void addExperience(long xp) {
        this.experience += xp;
        checkLevelUp();
    }

    private void checkLevelUp() {
        long xpNeeded = (long) (100 * Math.pow(1.5, level - 1));
        while (experience >= xpNeeded) {
            experience -= xpNeeded;
            level++;
            skillTree.addPoints(1);
            xpNeeded = (long) (100 * Math.pow(1.5, level - 1));
        }
    }

    public String getConnectionId() { return connectionId; }
    public void setConnectionId(String connectionId) { this.connectionId = connectionId; }
}
