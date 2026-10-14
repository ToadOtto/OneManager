package com.onemanager.client.gui.note;

import com.onemanager.client.ClientPacketHandler;
import com.onemanager.client.gui.CharHistoryScreen;
import com.onemanager.client.gui.components.EditBoxComponent;
import com.onemanager.client.gui.components.MultiLineEditBoxComponent;
import com.onemanager.client.gui.components.TextureIconComponent;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.item.ModItems;
import com.onemanager.item.NoteItem;
import com.onemanager.network.EnactLawPacket;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.SaveNotePacket;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class NoteEditScreen extends BaseOwoScreen<FlowLayout> {
    private final InteractionHand hand;
    private final boolean isMainHand;
    private final List<String> pages;
    private String initialTitle;
    private int currentPageIndex = 0;
    private boolean sign = false;

    private EditBoxComponent titleBox;
    private MultiLineEditBoxComponent pageEditor;
    private LabelComponent pageIndicator;

    public NoteEditScreen(InteractionHand hand, ItemStack stack) {
        super(Component.literal("Редактирование записки"));
        this.hand = hand;
        this.isMainHand = hand == InteractionHand.MAIN_HAND;
        this.initialTitle = NoteItem.getTitle(stack);
        List<String> existingPages = NoteItem.getPages(stack);
        this.pages = new ArrayList<>();
        if (existingPages.isEmpty()) {
            this.pages.add("");
        } else {
            this.pages.addAll(existingPages);
        }
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
        } catch (Throwable t) {
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        try {
            if (this.uiAdapter != null && this.uiAdapter.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        } catch (Throwable t) {
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

        ItemComponent icon = Components.item(new ItemStack(ModItems.NOTE.get()));
        icon.sizing(Sizing.fixed(16), Sizing.fixed(16));
        header.child(icon);

        header.child(Components.label(Component.literal("Записка").withStyle(ChatFormatting.WHITE)));
        header.child(Components.spacer());

        FlowLayout closeBtn = createSmallButton("✕", this::onClose);
        header.child(closeBtn);
        card.child(header);

        // Divider
        card.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF222630)));

        // Title input row
        FlowLayout titleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.verticalAlignment(VerticalAlignment.CENTER);
        titleRow.gap(4);
        titleRow.child(Components.label(Component.literal("Название:").withStyle(ChatFormatting.GRAY)));

        titleBox = new EditBoxComponent(this.font, 180, 16, Component.literal("Название записки"));
        titleBox.setMaxLength(32);
        titleBox.setValue(initialTitle != null ? initialTitle : "");
        titleBox.setHint(Component.literal("Без названия...").withStyle(ChatFormatting.DARK_GRAY));
        titleRow.child(titleBox);
        card.child(titleRow);

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

        // Page editor with bottom margin to reserve room for character count display
        pageEditor = new MultiLineEditBoxComponent(this.font, 250, 120, Component.literal("Текст записки..."), Component.literal("Страница"));
        pageEditor.setCharacterLimit(500);
        pageEditor.margins(Insets.bottom(14));
        card.child(pageEditor);

        // Sign checkbox row (bottom right)
        FlowLayout signRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(16));
        signRow.verticalAlignment(VerticalAlignment.CENTER);
        signRow.child(Components.spacer());

        FlowLayout signCheckbox = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(16));
        signCheckbox.verticalAlignment(VerticalAlignment.CENTER);
        signCheckbox.gap(3);
        signCheckbox.cursorStyle(CursorStyle.HAND);

        LabelComponent checkMark = Components.label(Component.literal(sign ? "[✔] " : "[  ] ").withStyle(sign ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        LabelComponent signLabel = Components.label(Component.literal("Подписать").withStyle(sign ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        signCheckbox.child(checkMark);
        signCheckbox.child(signLabel);

        signCheckbox.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                sign = !sign;
                checkMark.text(Component.literal(sign ? "[✔] " : "[  ] ").withStyle(sign ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                signLabel.text(Component.literal("Подписать").withStyle(sign ? ChatFormatting.WHITE : ChatFormatting.GRAY));
                return true;
            }
            return false;
        });
        signRow.child(signCheckbox);
        card.child(signRow);

        // Check if player is Knyaz
        Minecraft mc = Minecraft.getInstance();
        boolean isKnyaz = false;
        if (mc.player != null) {
            CharacterWorldData worldData = ClientPacketHandler.lastKnownWorldData;
            if (worldData != null && worldData.isKnyaz(mc.player.getUUID())) {
                isKnyaz = true;
            }
        }

        // Bottom action buttons
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        actions.verticalAlignment(VerticalAlignment.CENTER);

        if (isKnyaz) {
            actions.gap(5);
            FlowLayout saveDraftBtn = createFlatButton(Component.literal("Сохранить").withStyle(ChatFormatting.WHITE), 72, 22, this::saveDraft);

            // Center button "Закон" with monarch crown icon and gold outline
            FlowLayout lawBtn = Containers.horizontalFlow(Sizing.fixed(88), Sizing.fixed(22));
            lawBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            lawBtn.surface(Surface.flat(0xFF231D12).and(Surface.outline(0xFFD4AF37)));
            lawBtn.cursorStyle(CursorStyle.HAND);
            lawBtn.gap(4);
            lawBtn.child(new TextureIconComponent(CharHistoryScreen.ICON_MONARCH_CROWN, 12));
            lawBtn.child(Components.label(Component.literal("Закон").withStyle(ChatFormatting.GOLD)));
            lawBtn.mouseEnter().subscribe(() -> lawBtn.surface(Surface.flat(0xFF332A18).and(Surface.outline(0xFFFFD700))));
            lawBtn.mouseLeave().subscribe(() -> lawBtn.surface(Surface.flat(0xFF231D12).and(Surface.outline(0xFFD4AF37))));
            lawBtn.mouseDown().subscribe((mouseX, mouseY, button) -> {
                if (button == 0) {
                    openEnactLawConfirmation();
                    return true;
                }
                return false;
            });

            // Right button "Записка"
            FlowLayout noteBtn = createFlatButton(
                    Component.literal("Записка 🔒").withStyle(ChatFormatting.GREEN),
                    80, 22,
                    0xFF1B3D20, 0xFF27542E, 0xFF388E3C,
                    this::openSealConfirmation
            );

            actions.child(saveDraftBtn);
            actions.child(lawBtn);
            actions.child(noteBtn);
        } else {
            actions.gap(6);
            FlowLayout saveDraftBtn = createFlatButton(Component.literal("Сохранить").withStyle(ChatFormatting.WHITE), 100, 22, this::saveDraft);
            FlowLayout sealBtn = createFlatButton(
                    Component.literal("Завершить 🔒").withStyle(ChatFormatting.GREEN),
                    144, 22,
                    0xFF1B3D20, 0xFF27542E, 0xFF388E3C,
                    this::openSealConfirmation
            );

            actions.child(saveDraftBtn);
            actions.child(sealBtn);
        }
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

    private void saveDraft() {
        saveCurrentPage();
        String title = titleBox != null ? titleBox.getValue().trim() : "";
        NetworkHandler.sendToServer(new SaveNotePacket(isMainHand, title, pages, false, sign));
        this.onClose();
    }

    private void openEnactLawConfirmation() {
        saveCurrentPage();
        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(260), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFFD4AF37)));
        modal.padding(Insets.of(12));
        modal.gap(8);
        modal.zIndex(20);

        OverlayContainer<FlowLayout> overlay = Containers.overlay(modal);

        modal.child(Components.label(Component.literal("Издать государственный закон?").withStyle(ChatFormatting.GOLD)));
        LabelComponent warnLabel = Components.label(Component.literal("Записка исчезнет из инвентаря, а закон вступит в силу и появится во вкладке «Законы» государства.").withStyle(ChatFormatting.GRAY));
        warnLabel.maxWidth(236);
        modal.child(warnLabel);

        FlowLayout btnRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        btnRow.gap(6);

        FlowLayout cancelBtn = createFlatButton(Component.literal("Отмена").withStyle(ChatFormatting.WHITE), 110, 22, overlay::remove);
        FlowLayout confirmBtn = createFlatButton(
                Component.literal("Утвердить закон").withStyle(ChatFormatting.GOLD),
                120, 22,
                0xFF2A2415, 0xFF3A301A, 0xFFD4AF37,
                () -> {
                    overlay.remove();
                    String title = titleBox != null ? titleBox.getValue().trim() : "";
                    NetworkHandler.sendToServer(new EnactLawPacket(isMainHand, title, pages));
                    this.onClose();
                }
        );

        btnRow.child(cancelBtn);
        btnRow.child(confirmBtn);
        modal.child(btnRow);

        this.uiAdapter.rootComponent.child(overlay);
    }

    private void openSealConfirmation() {
        saveCurrentPage();
        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(260), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF323846)));
        modal.padding(Insets.of(12));
        modal.gap(8);
        modal.zIndex(20);

        OverlayContainer<FlowLayout> overlay = Containers.overlay(modal);

        modal.child(Components.label(Component.literal("Завершить записку?").withStyle(ChatFormatting.WHITE)));
        LabelComponent warnLabel = Components.label(Component.literal("После завершения текст будет заблокирован навсегда, и записку можно будет отправить другому персонажу.").withStyle(ChatFormatting.GRAY));
        warnLabel.maxWidth(236);
        modal.child(warnLabel);

        FlowLayout btnRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        btnRow.gap(6);

        FlowLayout cancelBtn = createFlatButton(Component.literal("Отмена").withStyle(ChatFormatting.WHITE), 110, 22, overlay::remove);
        FlowLayout confirmBtn = createFlatButton(
                Component.literal("Да, завершить").withStyle(ChatFormatting.GREEN),
                120, 22,
                0xFF1B3D20, 0xFF27542E, 0xFF388E3C,
                () -> {
                    overlay.remove();
                    String title = titleBox != null ? titleBox.getValue().trim() : "";
                    NetworkHandler.sendToServer(new SaveNotePacket(isMainHand, title, pages, true, sign));
                    this.onClose();
                }
        );

        btnRow.child(cancelBtn);
        btnRow.child(confirmBtn);
        modal.child(btnRow);

        this.uiAdapter.rootComponent.child(overlay);
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

