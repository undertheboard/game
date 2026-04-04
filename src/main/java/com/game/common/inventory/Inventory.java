package com.game.common.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Player/entity inventory with weight and stack management.
 */
public class Inventory {
    private static final int DEFAULT_MAX_SLOTS = 40;

    private final List<ItemStack> slots;
    private final int maxSlots;
    private double maxWeight;

    public Inventory() {
        this(DEFAULT_MAX_SLOTS, 100.0);
    }

    public Inventory(int maxSlots, double maxWeight) {
        this.maxSlots = maxSlots;
        this.maxWeight = maxWeight;
        this.slots = new ArrayList<>(maxSlots);
    }

    /** Attempts to add an item. Returns true if successful. */
    public boolean addItem(Item item, int quantity) {
        if (item.isStackable()) {
            for (ItemStack stack : slots) {
                if (stack.getItem().getId().equals(item.getId())
                        && stack.getQuantity() < item.getMaxStack()) {
                    int space = item.getMaxStack() - stack.getQuantity();
                    int added = Math.min(space, quantity);
                    stack.setQuantity(stack.getQuantity() + added);
                    quantity -= added;
                    if (quantity == 0) return true;
                }
            }
        }
        // Need new slots
        while (quantity > 0 && slots.size() < maxSlots) {
            int batch = item.isStackable() ? Math.min(quantity, item.getMaxStack()) : 1;
            slots.add(new ItemStack(item, batch));
            quantity -= batch;
        }
        return quantity == 0;
    }

    public boolean removeItem(String itemId, int quantity) {
        for (int i = slots.size() - 1; i >= 0 && quantity > 0; i--) {
            ItemStack stack = slots.get(i);
            if (stack.getItem().getId().equals(itemId)) {
                int removed = Math.min(stack.getQuantity(), quantity);
                stack.setQuantity(stack.getQuantity() - removed);
                quantity -= removed;
                if (stack.getQuantity() == 0) {
                    slots.remove(i);
                }
            }
        }
        return quantity == 0;
    }

    public int countItem(String itemId) {
        return slots.stream()
                .filter(s -> s.getItem().getId().equals(itemId))
                .mapToInt(ItemStack::getQuantity)
                .sum();
    }

    public boolean hasItem(String itemId) {
        return countItem(itemId) > 0;
    }

    public double getTotalWeight() {
        return slots.stream()
                .mapToDouble(s -> s.getItem().getWeight() * s.getQuantity())
                .sum();
    }

    public List<ItemStack> getSlots() { return Collections.unmodifiableList(slots); }
    public int getMaxSlots() { return maxSlots; }
    public double getMaxWeight() { return maxWeight; }
    public void setMaxWeight(double maxWeight) { this.maxWeight = maxWeight; }

    /** Simple inner class for an item+quantity pair. */
    public static class ItemStack {
        private final Item item;
        private int quantity;

        public ItemStack(Item item, int quantity) {
            this.item = item;
            this.quantity = quantity;
        }

        public Item getItem() { return item; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }
}
