package com.onemanager.client.gui.note;

import com.onemanager.client.ClientPacketHandler;
import com.onemanager.client.gui.UiSoundHelper;
import com.onemanager.client.gui.components.EditBoxComponent;
import com.onemanager.data.ActiveCharacter;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.item.ModItems;
import com.onemanager.item.NoteItem;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.RequestCharHistoryC2SPacket;
import com.onemanager.network.SendNotePacket;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class SendNoteScreen extends BaseOwoScreen<FlowLayout> {
    private final InteractionHand hand;
    private final boolean isMainHand;
    private final ItemStack noteStack;
    private final String noteTitle;
    private final List<String> pages;

    public record RecipientEntry(UUID uuid, String displayName, String subName) {
        public RecipientEntry {
            if (displayName == null || displayName.isEmpty()) displayName = "Игрок";
            if (subName == null) subName = "";
        }
    }

    private final List<RecipientEntry> allRecipients = new ArrayList<>();
    private UUID selectedRecipientUuid = null;

    private FlowLayout recipientListContainer;
    private ScrollContainer<FlowLayout> scrollContainer;
    private FlowLayout sendBtn;
    private EditBoxComponent searchBox;
    private boolean hideAuthor = false;

    public SendNoteScreen(InteractionHand hand, ItemStack stack) {
        super(Component.literal("Отправка записки"));
        this.hand = hand;
        this.isMainHand = hand == InteractionHand.MAIN_HAND;
        this.noteStack = stack;
        this.noteTitle = NoteItem.getTitle(stack);
        this.pages = NoteItem.getPages(stack);
        collectRecipients();
    }

    public void collectRecipients() {
        allRecipients.clear();
        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = mc.player != null ? mc.player.getUUID() : null;

        Map<UUID, RecipientEntry> map = new LinkedHashMap<>();

        // 1. ALWAYS add local player first with highest priority
        String myPlayerName = mc.player != null ? mc.player.getName().getString() : "Вы";
        String myCharName = "";
        CharacterWorldData worldData = ClientPacketHandler.lastKnownWorldData;
        if (worldData != null && myUuid != null) {
            PlayerCharacterData myPd = worldData.getPlayerData(myUuid);
            if (myPd != null && myPd.hasActiveCharacter() && myPd.getActiveCharacter().getName() != null && !myPd.getActiveCharacter().getName().isBlank()) {
                myCharName = myPd.getActiveCharacter().getName().trim();
            }
        }
        String myDisplay = !myCharName.isEmpty() ? myCharName + " (" + myPlayerName + ")" : myPlayerName;
        if (myUuid != null) {
            map.put(myUuid, new RecipientEntry(myUuid, myDisplay, "(Вы)"));
        } else {
            UUID dummy = UUID.randomUUID();
            map.put(dummy, new RecipientEntry(dummy, "Вы", "(Вы)"));
        }

        // 2. From Online Player list (tab list)
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info == null || info.getProfile() == null) continue;
                UUID id = info.getProfile().getId();
                if (id == null || id.equals(myUuid)) continue;
                String pName = info.getProfile().getName();
                if (pName == null || pName.isBlank()) pName = "Игрок";

                String cName = "";
                if (worldData != null) {
                    PlayerCharacterData pd = worldData.getPlayerData(id);
                    if (pd != null && pd.hasActiveCharacter() && pd.getActiveCharacter().getName() != null && !pd.getActiveCharacter().getName().isBlank()) {
                        cName = pd.getActiveCharacter().getName().trim();
                    }
                }
                String disp = !cName.isEmpty() ? cName + " (" + pName + ")" : pName;
                map.put(id, new RecipientEntry(id, disp, ""));
            }
        }

        // 3. From World Data (database / offline characters)
        if (worldData != null) {
            for (PlayerCharacterData pd : worldData.getAllPlayerData().values()) {
                if (pd == null || pd.getPlayerUuid() == null) continue;
                UUID id = pd.getPlayerUuid();
                if (map.containsKey(id)) continue;

                String cName = pd.hasActiveCharacter() && pd.getActiveCharacter().getName() != null ? pd.getActiveCharacter().getName().trim() : "";
                String pName = pd.getLastKnownPlayerName() != null ? pd.getLastKnownPlayerName().trim() : "";
                String disp = !cName.isEmpty() ? (!pName.isEmpty() ? cName + " (" + pName + ")" : cName) : (!pName.isEmpty() ? pName : "Персонаж");
                map.put(id, new RecipientEntry(id, disp, "(Офлайн)"));
            }
        }

        allRecipients.addAll(map.values());
        if (selectedRecipientUuid == null && !allRecipients.isEmpty()) {
            selectedRecipientUuid = allRecipients.get(0).uuid();
        }
    }

    public void refreshRecipients() {
        collectRecipients();
        populateRecipients(searchBox != null ? searchBox.getValue().trim().toLowerCase() : "");
        updateSendButtonState();
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
        card.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF7A6242)));
        card.padding(Insets.of(6, 8, 6, 8));
        card.gap(4);

        // Header (compact 16px)
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(16));
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.gap(5);

        ItemComponent icon = Components.item(new ItemStack(ModItems.NOTE.get()));
        icon.sizing(Sizing.fixed(14), Sizing.fixed(14));
        header.child(icon);

        header.child(Components.label(Component.literal("Отправка записки").withStyle(ChatFormatting.GOLD)));
        header.child(Components.spacer());

        FlowLayout closeBtn = createSmallButton("✕", this::onClose);
        header.child(closeBtn);
        card.child(header);

        // Divider
        card.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF384354)));

        // Note info + Quick send to self in one unified row (16px)
        String displayTitle = noteTitle.isEmpty() ? "(Без названия)" : noteTitle;
        FlowLayout infoRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(16));
        infoRow.verticalAlignment(VerticalAlignment.CENTER);
        infoRow.gap(4);

        infoRow.child(Components.label(Component.literal("Записка: ").withStyle(ChatFormatting.GRAY)));
        LabelComponent titleLbl = Components.label(Component.literal(displayTitle).withStyle(ChatFormatting.WHITE));
        titleLbl.sizing(Sizing.fixed(100), Sizing.content());
        infoRow.child(titleLbl);

        infoRow.child(Components.spacer());

        FlowLayout sendSelfBtn = createFlatButton(Component.literal("✉ Себе").withStyle(ChatFormatting.AQUA), 65, 16, () -> {
            if (Minecraft.getInstance().player != null) {
                openSendConfirmationModal(Minecraft.getInstance().player.getUUID());
            }
        });
        sendSelfBtn.tooltip(Component.literal("Отправить записку себе (быстрый тест)").withStyle(ChatFormatting.GRAY));
        infoRow.child(sendSelfBtn);

        card.child(infoRow);

        // Search box (16px)
        searchBox = new EditBoxComponent(this.font, 244, 16, Component.literal("Поиск"));
        searchBox.setHint(Component.literal("Поиск получателя...").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(query -> populateRecipients(query.trim().toLowerCase()));
        card.child(searchBox);

        // Recipients list - populated BEFORE wrapping into scroll container
        recipientListContainer = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        recipientListContainer.gap(2);
        recipientListContainer.padding(Insets.of(2));

        populateRecipients("");

        // Adaptive scroll height so the card never exceeds screen bounds
        int availableScroll = Math.max(45, this.height - 128);
        int scrollHeight = Math.min(80, availableScroll);

        scrollContainer = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(scrollHeight), recipientListContainer);
        scrollContainer.surface(Surface.flat(0xFF0F1117).and(Surface.outline(0xFF232732)));
        card.child(scrollContainer);

        // Hide authorship checkbox (only for copies)
        if (NoteItem.isCopy(noteStack)) {
            FlowLayout anonRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(16));
            anonRow.verticalAlignment(VerticalAlignment.CENTER);
            anonRow.gap(4);
            anonRow.cursorStyle(CursorStyle.HAND);

            LabelComponent checkMark = Components.label(Component.literal(hideAuthor ? "[✔] " : "[  ] ").withStyle(hideAuthor ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            LabelComponent anonLabel = Components.label(Component.literal("Скрыть авторство").withStyle(ChatFormatting.GRAY));

            anonRow.child(checkMark);
            anonRow.child(anonLabel);

            anonRow.mouseDown().subscribe((mouseX, mouseY, button) -> {
                if (button == 0) {
                    UiSoundHelper.playButtonClick();
                    hideAuthor = !hideAuthor;
                    checkMark.text(Component.literal(hideAuthor ? "[✔] " : "[  ] ").withStyle(hideAuthor ? ChatFormatting.GREEN : ChatFormatting.GRAY));
                    anonLabel.text(Component.literal("Скрыть авторство").withStyle(hideAuthor ? ChatFormatting.WHITE : ChatFormatting.GRAY));
                    return true;
                }
                return false;
            });
            card.child(anonRow);
        }

        // Bottom action buttons (20px)
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        actions.verticalAlignment(VerticalAlignment.CENTER);
        actions.gap(5);

        FlowLayout previewBtn = createFlatButton(Component.literal("Прочесть").withStyle(ChatFormatting.WHITE), 74, 20, () -> {
            Minecraft.getInstance().setScreen(new NoteViewScreen(noteTitle, pages));
        });

        sendBtn = createFlatButton(Component.literal("Отправить ✉").withStyle(ChatFormatting.GRAY), 165, 20, this::sendNote);
        updateSendButtonState();

        actions.child(previewBtn);
        actions.child(sendBtn);
        card.child(actions);

        rootComponent.child(card);
    }

    private void populateRecipients(String query) {
        if (recipientListContainer == null) return;
        recipientListContainer.clearChildren();

        List<RecipientEntry> filtered = allRecipients.stream()
                .filter(r -> query.isEmpty() || r.displayName().toLowerCase().contains(query) || r.subName().toLowerCase().contains(query))
                .toList();

        if (filtered.isEmpty()) {
            recipientListContainer.child(Components.label(Component.literal("Получатели не найдены").withStyle(ChatFormatting.DARK_GRAY)).margins(Insets.of(6)));
            return;
        }

        for (RecipientEntry r : filtered) {
            boolean isSelected = r.uuid().equals(selectedRecipientUuid);
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.padding(Insets.horizontal(5));
            row.gap(4);
            row.cursorStyle(CursorStyle.HAND);

            int bg = isSelected ? 0xFF243628 : 0xFF14171E;
            int border = isSelected ? 0xFF4CAF50 : 0xFF282F3D;
            row.surface(Surface.flat(bg).and(Surface.outline(border)));

            row.child(Components.label(Component.literal(r.displayName()).withStyle(isSelected ? ChatFormatting.GREEN : ChatFormatting.WHITE)));
            if (!r.subName().isEmpty()) {
                row.child(Components.label(Component.literal(r.subName()).withStyle(ChatFormatting.DARK_GRAY)));
            }

            row.mouseDown().subscribe((mouseX, mouseY, button) -> {
                if (button == 0) {
                    UiSoundHelper.playButtonClick();
                    selectedRecipientUuid = r.uuid();
                    populateRecipients(searchBox != null ? searchBox.getValue().trim().toLowerCase() : "");
                    updateSendButtonState();
                    return true;
                }
                return false;
            });

            recipientListContainer.child(row);
        }
    }

    private void updateSendButtonState() {
        if (sendBtn == null) return;
        boolean canSend = selectedRecipientUuid != null;
        sendBtn.clearChildren();
        if (canSend) {
            sendBtn.surface(Surface.flat(0xFF264D2B).and(Surface.outline(0xFF4CAF50)));
            sendBtn.child(Components.label(Component.literal("Отправить ✉").withStyle(ChatFormatting.GREEN)));
        } else {
            sendBtn.surface(Surface.flat(0xFF1E212A).and(Surface.outline(0xFF2E3342)));
            sendBtn.child(Components.label(Component.literal("Выберите получателя").withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    private void sendNote() {
        if (selectedRecipientUuid == null) return;
        openSendConfirmationModal(selectedRecipientUuid);
    }

    private void openSendConfirmationModal(UUID targetUuid) {
        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(250), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF323846)));
        modal.padding(Insets.of(10));
        modal.gap(6);
        modal.zIndex(20);

        OverlayContainer<FlowLayout> overlay = Containers.overlay(modal);

        modal.child(Components.label(Component.literal("Отправка записки").withStyle(ChatFormatting.WHITE)));

        LabelComponent warnLabel = Components.label(Component.literal("⚠ Записка исчезнет из инвентаря после отправки!").withStyle(ChatFormatting.GOLD));
        warnLabel.maxWidth(230);
        modal.child(warnLabel);

        LabelComponent hintLabel = Components.label(Component.literal("Подсказка: вы можете сделать копию записки, совместив её с бумагой на верстаке.").withStyle(ChatFormatting.GRAY));
        hintLabel.maxWidth(230);
        modal.child(hintLabel);

        FlowLayout btnRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        btnRow.gap(6);

        FlowLayout cancelBtn = createFlatButton(Component.literal("Отмена").withStyle(ChatFormatting.WHITE), 100, 20, overlay::remove);
        FlowLayout confirmBtn = createFlatButton(
                Component.literal("Отправить").withStyle(ChatFormatting.GREEN),
                120, 20,
                0xFF1B3D20, 0xFF27542E, 0xFF388E3C,
                () -> {
                    overlay.remove();
                    NetworkHandler.sendToServer(new SendNotePacket(isMainHand, targetUuid, hideAuthor));
                    this.onClose();
                }
        );

        btnRow.child(cancelBtn);
        btnRow.child(confirmBtn);
        modal.child(btnRow);

        this.uiAdapter.rootComponent.child(overlay);
    }

    private FlowLayout createFlatButton(Component text, int width, int height, Runnable action) {
        return createFlatButton(text, width, height, 0xFF222733, 0xFF2F3747, 0xFF3B4456, action);
    }

    private FlowLayout createFlatButton(Component text, int width, int height, int bgColor, int hoverColor, int borderColor, Runnable action) {
        FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(height));
        btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor)));
        btn.cursorStyle(CursorStyle.HAND);
        btn.child(Components.label(text));
        btn.mouseEnter().subscribe(() -> {
            if (btn != sendBtn || selectedRecipientUuid != null) {
                btn.surface(Surface.flat(hoverColor).and(Surface.outline(borderColor)));
            }
        });
        btn.mouseLeave().subscribe(() -> {
            if (btn == sendBtn) {
                updateSendButtonState();
            } else {
                btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor)));
            }
        });
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
        btn.surface(Surface.flat(0xFF222733).and(Surface.outline(0xFF3B4456)));
        btn.cursorStyle(CursorStyle.HAND);
        btn.child(Components.label(Component.literal(symbol).withStyle(ChatFormatting.WHITE)));
        btn.mouseEnter().subscribe(() -> btn.surface(Surface.flat(0xFF353D4F).and(Surface.outline(0xFF5A6882))));
        btn.mouseLeave().subscribe(() -> btn.surface(Surface.flat(0xFF222733).and(Surface.outline(0xFF3B4456))));
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

