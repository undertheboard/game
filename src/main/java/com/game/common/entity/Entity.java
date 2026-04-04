package com.game.common.entity;

import com.game.common.inventory.Inventory;
import com.game.common.stats.StatSheet;

import java.util.UUID;

/**
 * Base class for all entities in the game world.
 */
public class Entity {
    protected final String id;
    protected String name;
    protected double x;
    protected double y;
    protected double hp;
    protected final StatSheet stats;
    protected boolean alive;

    public Entity(String name, double x, double y) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.x = x;
        this.y = y;
        this.stats = new StatSheet();
        this.hp = stats.getFinalValue(com.game.common.stats.StatType.MAX_HP);
        this.alive = true;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getHp() { return hp; }
    public void setHp(double hp) {
        this.hp = Math.max(0, hp);
        if (this.hp == 0) this.alive = false;
    }

    public boolean isAlive() { return alive; }
    public StatSheet getStats() { return stats; }

    public void applyDamage(double damage) {
        setHp(hp - damage);
    }

    public void heal(double amount) {
        double maxHp = stats.getFinalValue(com.game.common.stats.StatType.MAX_HP);
        setHp(Math.min(hp + amount, maxHp));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id='" + id + "', name='" + name + "', hp=" + hp + "}";
    }
}
