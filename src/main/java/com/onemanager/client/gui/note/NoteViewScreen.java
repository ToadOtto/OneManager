package com.onemanager.client.gui.note;

import com.onemanager.client.gui.UiSoundHelper;
import com.onemanager.item.ModItems;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class NoteViewScreen extends BaseOwoScreen<FlowLayout> {
    private final String noteTitle;
    private final List<String> pages;
    private final String author;
    private final net.minecraft.client.gui.screens.Screen parentScreen;
    private int currentPageIndex = 0;

    private LabelComponent pageIndicator;
    private LabelComponent pageTextLabel;

    public NoteViewScreen(String noteTitle, List<String> pages, String author, net.minecraft.client.gui.screens.Screen parentScreen) {
        super(Component.literal("Чтение записки"));
        this.noteTitle = noteTitle != null && !noteTitle.trim().isEmpty() ? noteTitle.trim() : "Записка";
        this.pages = pages != null && !pages.isEmpty() ? new ArrayList<>(pages) : List.of("(Пустая записка)");
        this.author = author != null && !author.isBlank() ? author.trim() : "Неизвестный автор";
        this.parentScreen = parentScreen;
    }

    public NoteViewScreen(String noteTitle, List<String> pages, String author) {
        this(noteTitle, pages, author, null);
    }

    public NoteViewScreen(String noteTitle, List<String> pages) {
        this(noteTitle, pages, "Неизвестный автор", null);
    }

    @Override
    public void onClose() {
        if (this.parentScreen != null) {
            Minecraft.getInstance().setScreen(this.parentScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout card = Containers.verticalFlow(Sizing.fixed(260), Sizing.content());
        card.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF323846)));
        card.padding(Insets.of(10));
        card.gap(4);

        // Header
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.gap(6);

        ItemComponent icon = Components.item(new ItemStack(ModItems.NOTE.get()));
        icon.sizing(Sizing.fixed(16), Sizing.fixed(16));
        header.child(icon);

        LabelComponent titleLabel = Components.label(Component.literal(noteTitle).withStyle(ChatFormatting.WHITE));
        titleLabel.sizing(Sizing.fixed(140), Sizing.content());
        header.child(titleLabel);

        header.child(Components.spacer());

        pageIndicator = Components.label(Component.literal("").withStyle(ChatFormatting.GRAY));
        header.child(pageIndicator);

        FlowLayout closeBtn = createSmallButton("✕", this::onClose);
        header.child(closeBtn);
        card.child(header);

        // Author subtext
        LabelComponent authorLabel = Components.label(Component.literal("Автор: " + author).withStyle(ChatFormatting.GRAY));
        authorLabel.margins(Insets.bottom(2));
        card.child(authorLabel);

        // Divider
        card.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF222630)));

        // Page text area
        FlowLayout textContainer = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        textContainer.padding(Insets.of(6));

        pageTextLabel = Components.label(Component.empty());
        pageTextLabel.sizing(Sizing.fill(100), Sizing.content());
        textContainer.child(pageTextLabel);

        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(160), textContainer);
        scroll.surface(Surface.flat(0xFF0F1117).and(Surface.outline(0xFF232732)));
        card.child(scroll);

        // Bottom navigation & close
        FlowLayout navRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        navRow.verticalAlignment(VerticalAlignment.CENTER);
        navRow.gap(4);

        FlowLayout prevBtn = createFlatButton(Component.literal("◀").withStyle(ChatFormatting.WHITE), 28, 20, () -> {
            if (currentPageIndex > 0) {
                currentPageIndex--;
                updatePage();
            }
        });

        FlowLayout nextBtn = createFlatButton(Component.literal("▶").withStyle(ChatFormatting.WHITE), 28, 20, () -> {
            if (currentPageIndex < pages.size() - 1) {
                currentPageIndex++;
                updatePage();
            }
        });

        navRow.child(prevBtn);
        navRow.child(nextBtn);
        navRow.child(Components.spacer());

        FlowLayout doneBtn = createFlatButton(Component.literal("Закрыть").withStyle(ChatFormatting.WHITE), 64, 20, this::onClose);
        navRow.child(doneBtn);

        card.child(navRow);

        rootComponent.child(card);
        updatePage();
    }

    private void updatePage() {
        if (currentPageIndex < 0) currentPageIndex = 0;
        if (currentPageIndex >= pages.size()) currentPageIndex = pages.size() - 1;

        if (pageIndicator != null) {
            pageIndicator.text(Component.literal((currentPageIndex + 1) + " / " + pages.size()).withStyle(ChatFormatting.GRAY));
        }
        if (pageTextLabel != null) {
            String text = pages.get(currentPageIndex);
            pageTextLabel.text(Component.literal(text).withStyle(ChatFormatting.WHITE));
        }
    }

    private FlowLayout createFlatButton(Component text, int width, int height, Runnable action) {
        FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(height));
        btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        btn.surface(Surface.flat(0xFF1E222A).and(Surface.outline(0xFF353B47)));
        btn.cursorStyle(CursorStyle.HAND);
        btn.child(Components.label(text));
        btn.mouseEnter().subscribe(() -> btn.surface(Surface.flat(0xFF2A2E38).and(Surface.outline(0xFF4A5263))));
        btn.mouseLeave().subscribe(() -> btn.surface(Surface.flat(0xFF1E222A).and(Surface.outline(0xFF353B47))));
        btn.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                UiSoundHelper.playButtonClick();
                action.run();
                return true;
            }
            return false;
        });
        return btn;
    }

    private FlowLayout createSmallButton(String symbol, Runnable action) {
        FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(16), Sizing.fixed(16));
        btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        btn.surface(Surface.flat(0xFF1E222A).and(Surface.outline(0xFF353B47)));
        btn.cursorStyle(CursorStyle.HAND);
        btn.child(Components.label(Component.literal(symbol).withStyle(ChatFormatting.WHITE)));
        btn.mouseEnter().subscribe(() -> btn.surface(Surface.flat(0xFF2A2E38).and(Surface.outline(0xFF4A5263))));
        btn.mouseLeave().subscribe(() -> btn.surface(Surface.flat(0xFF1E222A).and(Surface.outline(0xFF353B47))));
        btn.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                UiSoundHelper.playButtonClick();
                action.run();
                return true;
            }
            return false;
        });
        return btn;
    }
}

