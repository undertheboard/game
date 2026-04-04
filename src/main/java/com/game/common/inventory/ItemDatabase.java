package com.game.common.inventory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JSON-backed item database. Loads item definitions from items.json on the classpath.
 */
public class ItemDatabase {
    private static final Logger LOGGER = Logger.getLogger(ItemDatabase.class.getName());
    private static final String ITEMS_RESOURCE = "/items.json";

    private final Map<String, Item> items = new HashMap<>();

    public ItemDatabase() {
        load();
    }

    private void load() {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = getClass().getResourceAsStream(ITEMS_RESOURCE)) {
            if (is == null) {
                LOGGER.warning("items.json not found on classpath, item database is empty.");
                return;
            }
            List<Item> list = mapper.readValue(is, new TypeReference<List<Item>>() {});
            for (Item item : list) {
                items.put(item.getId(), item);
            }
            LOGGER.info("Loaded " + items.size() + " items from database.");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load item database", e);
        }
    }

    public Item getItem(String id) {
        return items.get(id);
    }

    public Map<String, Item> getAllItems() {
        return Collections.unmodifiableMap(items);
    }

    public boolean hasItem(String id) {
        return items.containsKey(id);
    }

    public int size() {
        return items.size();
    }
}
