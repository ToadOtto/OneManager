package com.onemanager.profession;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Represents a registered profession with English identifier in memory,
 * localized Russian title, category (MAIN or SECONDARY), Minecraft icon,
 * and detailed description.
 */
public class Profession {
    private final int id;
    private final String title;
    private final ProfessionCategory type;
    private final Supplier<? extends Item> iconSupplier;
    private final String description;

    public Profession(int id, String title, ProfessionCategory type, Item iconItem, String description) {
        this(id, title, type, () -> iconItem != null ? iconItem : Items.PAPER, description);
    }

    public Profession(int id, String title, ProfessionCategory type, Supplier<? extends Item> iconSupplier, String description) {
        this.id = id;
        this.title = Objects.requireNonNull(title, "Profession title cannot be null");
        this.type = Objects.requireNonNull(type, "Profession category cannot be null");
        this.iconSupplier = iconSupplier != null ? iconSupplier : () -> Items.PAPER;
        this.description = description != null ? description : "";
    }

    public int getId() {
        return id;
    }

    public String getIdString() {
        return String.valueOf(id);
    }

    public String getTitle() {
        return title;
    }

    public ProfessionCategory getType() {
        return type;
    }

    public int getMaxOccupants() {
        return type.getMaxOccupants();
    }

    public Item getIconItem() {
        Item item = iconSupplier.get();
        return item != null ? item : Items.PAPER;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return "Profession{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", type=" + type +
                '}';
    }
}
