package com.pcmanager.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class LawEntry {
    private final UUID id;
    private final String title;
    private final List<String> pages;
    private final String author;
    private final List<String> editors;
    private final String createdDate;
    private String lastModifiedDate;

    public LawEntry(UUID id, String title, List<String> pages, String author, List<String> editors, String createdDate, String lastModifiedDate) {
        this.id = id != null ? id : UUID.randomUUID();
        this.title = title != null ? title.trim() : "";
        this.pages = pages != null ? new ArrayList<>(pages) : new ArrayList<>();
        this.author = author != null && !author.isBlank() ? author.trim() : "Неизвестный автор";
        this.editors = editors != null ? new ArrayList<>(editors) : new ArrayList<>();
        this.createdDate = createdDate != null ? createdDate : "";
        this.lastModifiedDate = lastModifiedDate != null && !lastModifiedDate.isBlank() ? lastModifiedDate : this.createdDate;
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

    public String getAuthor() {
        return author;
    }

    public List<String> getEditors() {
        return Collections.unmodifiableList(editors);
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public String getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(String lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate != null ? lastModifiedDate : "";
    }

    public void addEditor(String editor) {
        if (editor != null && !editor.isBlank() && !editors.contains(editor.trim())) {
            editors.add(editor.trim());
        }
    }

    public void updatePages(List<String> newPages, String editor, String modifiedDate) {
        this.pages.clear();
        if (newPages != null) {
            this.pages.addAll(newPages);
        }
        if (editor != null && !editor.isBlank()) {
            addEditor(editor);
        }
        this.lastModifiedDate = modifiedDate != null ? modifiedDate : "";
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("title", title);
        tag.putString("author", author);
        tag.putString("createdDate", createdDate);
        tag.putString("lastModifiedDate", lastModifiedDate);

        ListTag pagesTag = new ListTag();
        for (String p : pages) {
            pagesTag.add(StringTag.valueOf(p));
        }
        tag.put("pages", pagesTag);

        ListTag editorsTag = new ListTag();
        for (String ed : editors) {
            editorsTag.add(StringTag.valueOf(ed));
        }
        tag.put("editors", editorsTag);

        return tag;
    }

    public static LawEntry fromNBT(CompoundTag tag) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        String title = tag.getString("title");
        String author = tag.contains("author", Tag.TAG_STRING) ? tag.getString("author") : "Неизвестный автор";
        String createdDate = tag.getString("createdDate");
        String lastModifiedDate = tag.getString("lastModifiedDate");

        List<String> pages = new ArrayList<>();
        if (tag.contains("pages", Tag.TAG_LIST)) {
            ListTag list = tag.getList("pages", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                pages.add(list.getString(i));
            }
        }

        List<String> editors = new ArrayList<>();
        if (tag.contains("editors", Tag.TAG_LIST)) {
            ListTag list = tag.getList("editors", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                editors.add(list.getString(i));
            }
        }

        return new LawEntry(id, title, pages, author, editors, createdDate, lastModifiedDate);
    }
}
