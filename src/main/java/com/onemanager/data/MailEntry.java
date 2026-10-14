package com.onemanager.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents a received letter/note in a player's mailbox.
 * Strictly anonymous: no sender UUID or author name is stored.
 */
public class MailEntry {
    private final UUID id;
    private final String title;
    private final List<String> pages;
    private final String date;
    private final String author;
    private boolean claimed;

    public MailEntry(UUID id, String title, List<String> pages, String date, String author, boolean claimed) {
        this.id = id != null ? id : UUID.randomUUID();
        this.title = title != null ? title : "";
        this.pages = pages != null ? new ArrayList<>(pages) : new ArrayList<>();
        this.date = date != null ? date : "";
        this.author = author != null && !author.isBlank() ? author.trim() : "Неизвестный автор";
        this.claimed = claimed;
    }

    public MailEntry(UUID id, String title, List<String> pages, String date, boolean claimed) {
        this(id, title, pages, date, "Неизвестный автор", claimed);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getPages() {
        return Collections.unmodifiableList(pages);
    }

    public String getDate() {
        return date;
    }

    public String getAuthor() {
        return author != null && !author.isBlank() ? author : "Неизвестный автор";
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("title", title);
        tag.putString("date", date);
        tag.putString("author", getAuthor());
        tag.putBoolean("claimed", claimed);

        ListTag pagesTag = new ListTag();
        for (String page : pages) {
            pagesTag.add(StringTag.valueOf(page));
        }
        tag.put("pages", pagesTag);

        return tag;
    }

    public static MailEntry fromNBT(CompoundTag tag) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        String title = tag.getString("title");
        String date = tag.getString("date");
        String author = tag.contains("author", Tag.TAG_STRING) ? tag.getString("author") : "Неизвестный автор";
        boolean claimed = tag.getBoolean("claimed");

        List<String> pages = new ArrayList<>();
        if (tag.contains("pages", Tag.TAG_LIST)) {
            ListTag list = tag.getList("pages", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                pages.add(list.getString(i));
            }
        }

        return new MailEntry(id, title, pages, date, author, claimed);
    }
}
