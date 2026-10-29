package com.onemanager.client.gui;

import com.onemanager.client.gui.components.EditBoxComponent;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.SubmitTreasuryReasonC2SPacket;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class TreasuryReasonScreen extends BaseOwoScreen<StackLayout> {
    private final int amount;
    private final boolean demolition;
    private final String coords;
    private EditBoxComponent reasonBox;
    private LabelComponent errorLabel;
    private boolean submitted = false;

    public TreasuryReasonScreen(int amount, boolean demolition, String coords) {
        super(Component.literal(demolition ? "Причина сноса хранилища" : "Причина списания:"));
        this.amount = amount;
        this.demolition = demolition;
        this.coords = coords != null ? coords : "";
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::stack);
    }

    @Override
    protected void build(StackLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(260), Sizing.content());
        int outlineColor = demolition ? 0xFF7A3036 : 0xFF4A3E18;
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(outlineColor)));
        modal.padding(Insets.of(12));
        modal.gap(6);

        // Title
        Component titleComp = demolition
                ? Component.literal("Причина сноса хранилища").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                : Component.literal("Причина списания:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        modal.child(Components.label(titleComp));

        // Divider
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(demolition ? 0xFF4C2226 : 0xFF38311B)).margins(Insets.bottom(2)));

        // Subtitle / Amount info
        String formattedAmt = String.format("%,d", amount).replace(',', ' ') + " $PR";
        if (demolition) {
            LabelComponent coordsLbl = Components.label(Component.literal("Хранилище на координатах: [" + coords + "]").withStyle(ChatFormatting.GRAY));
            coordsLbl.maxWidth(236);
            modal.child(coordsLbl);
            if (amount > 0) {
                LabelComponent amtLbl = Components.label(Component.literal("Изъято содержимого: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("-" + formattedAmt).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                amtLbl.maxWidth(236);
                modal.child(amtLbl);
            }
        } else {
            LabelComponent amtLbl = Components.label(Component.literal("Снято из казны: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("-" + formattedAmt).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            amtLbl.maxWidth(236);
            modal.child(amtLbl);
        }

        // Input Box
        reasonBox = new EditBoxComponent(this.font, 236, 20, Component.literal("Причина"));
        reasonBox.setMaxLength(100);
        reasonBox.setHint(Component.literal(demolition ? "Укажите причину сноса..." : "Укажите причину списания...").withStyle(ChatFormatting.DARK_GRAY));
        modal.child(reasonBox);

        errorLabel = Components.label(Component.empty());
        modal.child(errorLabel);

        // Confirm Button
        FlowLayout btnRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        btnRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int btnBg = demolition ? 0xFF3D1B1B : 0xFF2A2312;
        int btnHover = demolition ? 0xFF542727 : 0xFF3D3219;
        int btnBorder = demolition ? 0xFF8E3838 : 0xFFD4AF37;

        FlowLayout confirmBtn = Containers.horizontalFlow(Sizing.fixed(236), Sizing.fixed(22));
        confirmBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        confirmBtn.surface(Surface.flat(btnBg).and(Surface.outline(btnBorder)));
        confirmBtn.cursorStyle(CursorStyle.HAND);
        confirmBtn.child(Components.label(Component.literal("Подтвердить").withStyle(ChatFormatting.WHITE)));

        confirmBtn.mouseEnter().subscribe(() -> confirmBtn.surface(Surface.flat(btnHover).and(Surface.outline(btnBorder))));
        confirmBtn.mouseLeave().subscribe(() -> confirmBtn.surface(Surface.flat(btnBg).and(Surface.outline(btnBorder))));
        confirmBtn.mouseDown().subscribe((mx, my, button) -> {
            if (button == 0) {
                UiSoundHelper.playButtonClick();
                trySubmit();
                return true;
            }
            return false;
        });

        btnRow.child(confirmBtn);
        modal.child(btnRow);

        rootComponent.child(modal);

        if (this.uiAdapter != null && this.uiAdapter.rootComponent != null && this.uiAdapter.rootComponent.focusHandler() != null) {
            this.uiAdapter.rootComponent.focusHandler().focus(reasonBox, io.wispforest.owo.ui.core.Component.FocusSource.MOUSE_CLICK);
        }
    }

    private void trySubmit() {
        if (submitted) return;
        String text = reasonBox != null ? reasonBox.getValue().trim() : "";
        if (text.isEmpty()) {
            if (errorLabel != null) {
                errorLabel.text(Component.literal("⚠ Пожалуйста, укажите причину!").withStyle(ChatFormatting.RED));
            }
            return;
        }
        submitted = true;
        NetworkHandler.sendToServer(new SubmitTreasuryReasonC2SPacket(text));
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            trySubmit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (!submitted) {
            submitted = true;
            String text = (reasonBox != null && !reasonBox.getValue().trim().isEmpty())
                    ? reasonBox.getValue().trim()
                    : "Не указана";
            NetworkHandler.sendToServer(new SubmitTreasuryReasonC2SPacket(text));
        }
        super.removed();
    }
}
