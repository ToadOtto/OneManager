package com.pcmanager.client.gui;

import com.pcmanager.client.gui.components.EditBoxComponent;
import com.pcmanager.network.NetworkHandler;
import com.pcmanager.network.RebirthC2SPacket;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
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

/**
 * Screen for character creation, rebirth upon death, or rewrite via New Life Structure.
 */
public class RebirthScreen extends BaseOwoScreen<FlowLayout> {
    private final boolean isRewrite;
    private final String initialName;
    private final int initialAge;
    private final String initialRace;
    private final String initialAppearance;
    private final String initialDescription;
    private final String defaultCause;

    private EditBoxComponent nameBox;
    private EditBoxComponent ageBox;
    private EditBoxComponent raceBox;
    private EditBoxComponent appearanceBox;
    private EditBoxComponent descBox;
    private EditBoxComponent causeBox;
    private LabelComponent errorLabel;

    public RebirthScreen(String defaultCause) {
        this(false, "", 18, "", "", "", defaultCause);
    }

    public RebirthScreen(boolean isRewrite, String initialName, int initialAge, String initialRace, String initialAppearance, String initialDescription, String defaultCause) {
        super(Component.literal(isRewrite ? "Изменить личность" : "Смерть и Новая Жизнь"));
        this.isRewrite = isRewrite;
        this.initialName = initialName != null ? initialName : "";
        this.initialAge = initialAge > 0 ? initialAge : 18;
        this.initialRace = initialRace != null ? initialRace : "";
        this.initialAppearance = initialAppearance != null ? initialAppearance : "";
        this.initialDescription = initialDescription != null ? initialDescription : "";
        this.defaultCause = defaultCause != null && !defaultCause.trim().isEmpty() ? defaultCause : "Неизвестная причина";
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.horizontalAlignment(HorizontalAlignment.CENTER);
        rootComponent.verticalAlignment(VerticalAlignment.CENTER);

        FlowLayout panel = Containers.verticalFlow(Sizing.fixed(320), Sizing.content());
        panel.surface(Surface.flat(0xFF101216).and(Surface.outline(0xFF2E333D)));
        panel.padding(Insets.of(14));
        panel.horizontalAlignment(HorizontalAlignment.CENTER);

        // Header Title (NO BOLD, standard white)
        String titleText = isRewrite ? "Изменить личность" : "Смерть и Новая Жизнь";
        panel.child(
                Components.label(Component.literal(titleText).withStyle(ChatFormatting.WHITE))
                        .shadow(true)
                        .margins(Insets.bottom(10))
        );

        // Form Fields (Scrollable to comfortably fit on any screen scale)
        FlowLayout form = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        form.gap(2);

        // 1. Name input
        form.child(Components.label(Component.literal("Имя персонажа:").withStyle(ChatFormatting.WHITE)).margins(Insets.bottom(2)));
        nameBox = new EditBoxComponent(Minecraft.getInstance().font, 280, 20, Component.literal("Имя"));
        nameBox.setMaxLength(32);
        nameBox.setValue(initialName);
        nameBox.setHint(Component.literal("Введите имя персонажа...").withStyle(ChatFormatting.DARK_GRAY));
        form.child(nameBox.margins(Insets.bottom(6)));

        // 2. Age input
        form.child(Components.label(Component.literal("Возраст персонажа:").withStyle(ChatFormatting.WHITE)).margins(Insets.bottom(2)));
        ageBox = new EditBoxComponent(Minecraft.getInstance().font, 280, 20, Component.literal("Возраст"));
        ageBox.setMaxLength(3);
        ageBox.setValue(String.valueOf(initialAge));
        ageBox.setFilter(s -> s.matches("\\d*"));
        form.child(ageBox.margins(Insets.bottom(6)));

        // 3. Race input
        form.child(Components.label(Component.literal("Раса:").withStyle(ChatFormatting.WHITE)).margins(Insets.bottom(2)));
        raceBox = new EditBoxComponent(Minecraft.getInstance().font, 280, 20, Component.literal("Раса"));
        raceBox.setMaxLength(32);
        raceBox.setValue(initialRace);
        raceBox.setHint(Component.literal("Например: Человек, Эльф, Дворф...").withStyle(ChatFormatting.DARK_GRAY));
        form.child(raceBox.margins(Insets.bottom(6)));

        // 4. Appearance input
        form.child(Components.label(Component.literal("Внешность:").withStyle(ChatFormatting.WHITE)).margins(Insets.bottom(2)));
        appearanceBox = new EditBoxComponent(Minecraft.getInstance().font, 280, 20, Component.literal("Внешность"));
        appearanceBox.setMaxLength(100);
        appearanceBox.setValue(initialAppearance);
        appearanceBox.setHint(Component.literal("Рост, цвет глаз, телосложение, приметы...").withStyle(ChatFormatting.DARK_GRAY));
        form.child(appearanceBox.margins(Insets.bottom(6)));

        // 5. Description (history/backstory) input
        form.child(Components.label(Component.literal("Описание (история):").withStyle(ChatFormatting.WHITE)).margins(Insets.bottom(2)));
        descBox = new EditBoxComponent(Minecraft.getInstance().font, 280, 20, Component.literal("Описание"));
        descBox.setMaxLength(250);
        descBox.setValue(initialDescription);
        descBox.setHint(Component.literal("Биография, происхождение, предыстория...").withStyle(ChatFormatting.DARK_GRAY));
        form.child(descBox.margins(Insets.bottom(6)));

        // 6. Cause of death (only for normal death rebirth)
        if (!isRewrite) {
            form.child(Components.label(Component.literal("Причина гибели предыдущего:").withStyle(ChatFormatting.GRAY)).margins(Insets.bottom(2)));
            causeBox = new EditBoxComponent(Minecraft.getInstance().font, 280, 20, Component.literal("Причина"));
            causeBox.setMaxLength(80);
            causeBox.setValue(defaultCause);
            form.child(causeBox.margins(Insets.bottom(6)));
        }

        int scrollH = Math.min(230, Math.max(140, this.height - 120));
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(scrollH), form);
        scroll.padding(Insets.right(4));
        panel.child(scroll);

        // Error Label
        errorLabel = Components.label(Component.empty());
        errorLabel.color(Color.ofRgb(0xFFFF5555));
        errorLabel.margins(Insets.vertical(6));
        panel.child(errorLabel);

        // Buttons Row
        FlowLayout buttonsRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        buttonsRow.horizontalAlignment(HorizontalAlignment.CENTER);
        buttonsRow.gap(12);

        String confirmBtnText = isRewrite ? "Сохранить" : "Возродиться";
        FlowLayout submitBtn = createFlatButton(
                Component.literal(confirmBtnText).withStyle(ChatFormatting.WHITE),
                134, 22,
                0xFF22262E, 0xFF2F3540, 0xFF454B58,
                this::onConfirm
        );
        buttonsRow.child(submitBtn);

        FlowLayout cancelBtn = createFlatButton(
                Component.literal("Отмена").withStyle(ChatFormatting.GRAY),
                134, 22,
                0xFF1A1D24, 0xFF252932, 0xFF353B46,
                this::onClose
        );
        buttonsRow.child(cancelBtn);

        panel.child(buttonsRow);
        rootComponent.child(panel);
    }

    private FlowLayout createFlatButton(Component text, int width, int height, int bgColor, int hoverColor, int borderColor, Runnable onClick) {
        FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(height));
        btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor)));
        btn.cursorStyle(CursorStyle.HAND);
        btn.child(Components.label(text));

        btn.mouseEnter().subscribe(() -> btn.surface(Surface.flat(hoverColor).and(Surface.outline(borderColor))));
        btn.mouseLeave().subscribe(() -> btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor))));
        btn.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onClick.run();
                return true;
            }
            return false;
        });
        return btn;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.uiAdapter != null && this.uiAdapter.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.uiAdapter != null && this.uiAdapter.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onConfirm() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            errorLabel.text(Component.literal("Укажите имя персонажа!"));
            return;
        }

        int age = 18;
        try {
            age = Integer.parseInt(ageBox.getValue().trim());
        } catch (NumberFormatException ignored) {
        }
        if (age <= 0) {
            age = 18;
        }

        String race = raceBox.getValue().trim();
        String appearance = appearanceBox.getValue().trim();
        String desc = descBox.getValue().trim();

        String cause = "";
        if (!isRewrite && causeBox != null) {
            cause = causeBox.getValue().trim();
            if (cause.isEmpty()) {
                cause = defaultCause;
            }
        }

        NetworkHandler.sendToServer(new RebirthC2SPacket(name, age, race, appearance, desc, cause, isRewrite));

        if (!isRewrite && Minecraft.getInstance().player != null && Minecraft.getInstance().player.isDeadOrDying()) {
            Minecraft.getInstance().player.respawn();
        }

        onClose();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}

