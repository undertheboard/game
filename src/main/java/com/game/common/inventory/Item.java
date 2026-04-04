package com.game.common.inventory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Represents an item in the game, loaded from the JSON item database.
 */
public class Item {
    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("tags")
    private List<ItemTag> tags;

    @JsonProperty("value")
    private int value;

    @JsonProperty("weight")
    private double weight;

    @JsonProperty("stackable")
    private boolean stackable;

    @JsonProperty("maxStack")
    private int maxStack;

    @JsonProperty("properties")
    private Map<String, Object> properties;

    public Item() {}

    public Item(String id, String name, String description, List<ItemTag> tags,
                int value, double weight, boolean stackable, int maxStack) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.tags = tags;
        this.value = value;
        this.weight = weight;
        this.stackable = stackable;
        this.maxStack = maxStack;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<ItemTag> getTags() { return tags; }
    public void setTags(List<ItemTag> tags) { this.tags = tags; }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public boolean isStackable() { return stackable; }
    public void setStackable(boolean stackable) { this.stackable = stackable; }

    public int getMaxStack() { return maxStack; }
    public void setMaxStack(int maxStack) { this.maxStack = maxStack; }

    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }

    public boolean hasTag(ItemTag tag) {
        return tags != null && tags.contains(tag);
    }

    @Override
    public String toString() {
        return "Item{id='" + id + "', name='" + name + "', tags=" + tags + "}";
    }
}
