package com.onemanager.client.gui.law;

import com.onemanager.client.gui.CharHistoryScreen;
import com.onemanager.client.gui.components.MultiLineEditBoxComponent;
import com.onemanager.client.gui.components.TextureIconComponent;
import com.onemanager.network.EditLawPacket;
import com.onemanager.network.NetworkHandler;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LawEditScreen extends BaseOwoScreen<FlowLayout> {
    private final UUID lawId;
    private final String lawTitle;
    private final List<String> pages;
    private final Screen parentScreen;
    private int currentPageIndex = 0;

    private MultiLineEditBoxComponent pageEditor;
    private LabelComponent pageIndicator;

    public LawEditScreen(UUID lawId, String lawTitle, List<String> initialPages, Screen parentScreen) {
        super(Component.literal("Редактирование закона"));
        this.lawId = lawId;
        this.lawTitle = lawTitle != null && !lawTitle.isBlank() ? lawTitle : "Государственный закон";
        this.pages = new ArrayList<>();
        if (initialPages == null || initialPages.isEmpty()) {
            this.pages.add("");
        } else {
            this.pages.addAll(initialPages);
        }
        this.parentScreen = parentScreen;
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        try {
            if (this.uiAdapter != null && this.uiAdapter.charTyped(codePoint, modifiers)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        try {
            if (this.uiAdapter != null && this.uiAdapter.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout card = Containers.verticalFlow(Sizing.fixed(270), Sizing.content());
        card.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF323846)));
        card.padding(Insets.of(10));
        card.gap(5);

        // Header
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.gap(6);

        header.child(new TextureIconComponent(CharHistoryScreen.ICON_MONARCH_CROWN, 14));

        LabelComponent titleLabel = Components.label(Component.literal(lawTitle).withStyle(ChatFormatting.GOLD));
        titleLabel.sizing(Sizing.fixed(180), Sizing.content());
        header.child(titleLabel);

        header.child(Components.spacer());

        FlowLayout closeBtn = createSmallButton("✕", this::onClose);
        header.child(closeBtn);
        card.child(header);

        // Subtitle info
        card.child(Components.label(Component.literal("Редактирование государственного закона").withStyle(ChatFormatting.DARK_GRAY)));

        // Divider
        card.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF222630)));

        // Page navigation row
        FlowLayout navRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        navRow.verticalAlignment(VerticalAlignment.CENTER);
        navRow.gap(4);

        FlowLayout prevBtn = createFlatButton(Component.literal("◀").withStyle(ChatFormatting.WHITE), 22, 16, () -> {
            if (currentPageIndex > 0) {
                switchPage(currentPageIndex - 1);
            }
        });

        FlowLayout nextBtn = createFlatButton(Component.literal("▶").withStyle(ChatFormatting.WHITE), 22, 16, () -> {
            if (currentPageIndex < pages.size() - 1) {
                switchPage(currentPageIndex + 1);
            }
        });

        pageIndicator = Components.label(Component.literal("").withStyle(ChatFormatting.GRAY));

        FlowLayout addPageBtn = createFlatButton(Component.literal("+ Стр.").withStyle(ChatFormatting.GREEN), 42, 16, () -> {
            if (pages.size() < 16) {
                saveCurrentPage();
                pages.add("");
                switchPage(pages.size() - 1);
            }
        });

        FlowLayout delPageBtn = createFlatButton(Component.literal("– Стр.").withStyle(ChatFormatting.RED), 42, 16, () -> {
            if (pages.size() > 1) {
                pages.remove(currentPageIndex);
                if (currentPageIndex >= pages.size()) {
                    currentPageIndex = pages.size() - 1;
                }
                loadCurrentPage();
            }
        });

        navRow.child(prevBtn);
        navRow.child(nextBtn);
        navRow.child(pageIndicator);
        navRow.child(Components.spacer());
        navRow.child(addPageBtn);
        navRow.child(delPageBtn);
        card.child(navRow);

        // Page editor
        pageEditor = new MultiLineEditBoxComponent(this.font, 250, 120, Component.literal("Текст закона..."), Component.literal("Страница"));
        pageEditor.setCharacterLimit(500);
        pageEditor.margins(Insets.bottom(14));
        card.child(pageEditor);

        // Action buttons
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        actions.verticalAlignment(VerticalAlignment.CENTER);
        actions.gap(6);

        FlowLayout cancelBtn = createFlatButton(Component.literal("Отмена").withStyle(ChatFormatting.WHITE), 100, 22, this::onClose);
        FlowLayout saveBtn = createFlatButton(
                Component.literal("Завершить ✔").withStyle(ChatFormatting.GREEN),
                144, 22,
                0xFF1B3D20, 0xFF27542E, 0xFF388E3C,
                this::saveLaw
        );

        actions.child(cancelBtn);
        actions.child(saveBtn);
        card.child(actions);

        rootComponent.child(card);

        loadCurrentPage();
    }

    private void saveCurrentPage() {
        if (pageEditor != null && currentPageIndex >= 0 && currentPageIndex < pages.size()) {
            pages.set(currentPageIndex, pageEditor.getValue());
        }
    }

    private void loadCurrentPage() {
        if (currentPageIndex < 0) currentPageIndex = 0;
        if (currentPageIndex >= pages.size()) currentPageIndex = pages.size() - 1;

        if (pageEditor != null) {
            pageEditor.setValue(pages.get(currentPageIndex));
        }
        if (pageIndicator != null) {
            pageIndicator.text(Component.literal("Стр. " + (currentPageIndex + 1) + " / " + pages.size()).withStyle(ChatFormatting.GRAY));
        }
    }

    private void switchPage(int newIndex) {
        saveCurrentPage();
        currentPageIndex = newIndex;
        loadCurrentPage();
    }

    private void saveLaw() {
        saveCurrentPage();
        NetworkHandler.sendToServer(new EditLawPacket(lawId, pages));
        this.onClose();
    }

    private FlowLayout createFlatButton(Component text, int width, int height, Runnable action) {
        return createFlatButton(text, width, height, 0xFF1E222A, 0xFF2A2E38, 0xFF353B47, action);
    }

    private FlowLayout createFlatButton(Component text, int width, int height, int bgColor, int hoverColor, int borderColor, Runnable action) {
        FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(height));
        btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor)));
        btn.cursorStyle(CursorStyle.HAND);
        btn.child(Components.label(text));
        btn.mouseEnter().subscribe(() -> btn.surface(Surface.flat(hoverColor).and(Surface.outline(borderColor))));
        btn.mouseLeave().subscribe(() -> btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor))));
        btn.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
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
                action.run();
                return true;
            }
            return false;
        });
        return btn;
    }
}
