package com.onemanager.client.gui;

import com.onemanager.OneManager;
import com.onemanager.network.LockpickCancelC2SPacket;
import com.onemanager.network.LockpickQteAnswerC2SPacket;
import com.onemanager.network.NetworkHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.BoxComponent;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class LockpickScreen extends BaseOwoScreen<StackLayout> {
    private final int totalTicks;
    private final String toolName;

    private int elapsedTicks = 0;
    private int activeQteId = 0;
    private int activeTargetDigit = 0;
    private int qteTimeoutTicks = 0;
    private int qteElapsed = 0;

    // Green flash state on successful input
    private int flashDigit = 0;
    private int flashTicks = 0;
    private boolean completed = false;

    private final Map<Integer, FlowLayout> numButtons = new HashMap<>();
    private final Map<Integer, LabelComponent> numLabels = new HashMap<>();
    private LabelComponent cueLabel;
    private LabelComponent progressLabel;
    private BoxComponent progressBarFill;

    public LockpickScreen(int totalTicks, String toolName) {
        super(Component.literal("Взлом хранилища"));
        this.totalTicks = Math.max(1, totalTicks);
        this.toolName = toolName;
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::stack);
    }

    @Override
    protected void init() {
        super.init();
        if (this.uiAdapter != null) {
            float scale = CharHistoryScreen.getUiScale();
            this.uiAdapter.moveAndResize(0, 0, (int) (this.width / scale), (int) (this.height / scale));
        }
    }

    @Override
    public void resize(Minecraft client, int width, int height) {
        super.resize(client, width, height);
        if (this.uiAdapter != null) {
            float scale = CharHistoryScreen.getUiScale();
            this.uiAdapter.moveAndResize(0, 0, (int) (this.width / scale), (int) (this.height / scale));
        }
        rebuildUI();
    }

    @Override
    protected void build(StackLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(230), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF4A3E18)));
        modal.padding(Insets.of(12));
        modal.gap(8);
        modal.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        // Header
        LabelComponent title = Components.label(Component.literal("Взлом хранилища").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        title.maxWidth(206);
        modal.child(title);

        // Divider
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF38311B)));

        // Target Cue Label
        cueLabel = Components.label(Component.literal("Подбор штифтов...").withStyle(ChatFormatting.GRAY));
        cueLabel.maxWidth(206);
        modal.child(cueLabel);

        // Progress Bar Outer
        FlowLayout progressContainer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(10));
        progressContainer.surface(Surface.flat(0xFF0F1116).and(Surface.outline(0xFF2A2E3D)));
        progressContainer.padding(Insets.of(1));

        progressBarFill = Components.box(Sizing.fixed(0), Sizing.fill(100));
        progressBarFill.color(Color.ofRgb(0xFFF59E0B));
        progressContainer.child(progressBarFill);
        modal.child(progressContainer);

        // Progress Text
        progressLabel = Components.label(Component.literal("0%").withStyle(ChatFormatting.DARK_GRAY));
        modal.child(progressLabel);

        // NumPad 3x3
        FlowLayout numPad = Containers.verticalFlow(Sizing.content(), Sizing.content());
        numPad.gap(4);
        numPad.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        numButtons.clear();
        numLabels.clear();

        int digit = 1;
        for (int row = 0; row < 3; row++) {
            FlowLayout rowLayout = Containers.horizontalFlow(Sizing.content(), Sizing.content());
            rowLayout.gap(4);
            for (int col = 0; col < 3; col++) {
                final int d = digit++;
                FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(56), Sizing.fixed(32));
                btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                btn.cursorStyle(CursorStyle.HAND);

                LabelComponent lbl = Components.label(Component.literal(String.valueOf(d)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
                btn.child(lbl);

                btn.mouseDown().subscribe((mouseX, mouseY, button) -> {
                    if (button == 0) {
                        UiSoundHelper.playButtonClick();
                        pressDigit(d);
                        return true;
                    }
                    return false;
                });

                numButtons.put(d, btn);
                numLabels.put(d, lbl);
                rowLayout.child(btn);
            }
            numPad.child(rowLayout);
        }
        modal.child(numPad);

        // Footer helper note
        LabelComponent footer = Components.label(Component.literal("[1-9] Клавиши | [Esc] Отмена").withStyle(ChatFormatting.DARK_GRAY));
        footer.maxWidth(206);
        modal.child(footer);

        rootComponent.child(modal);
        updateButtonVisuals();
    }

    private void rebuildUI() {
        if (this.uiAdapter != null) {
            this.uiAdapter.rootComponent.clearChildren();
            build(this.uiAdapter.rootComponent);
        }
    }

    public void startQte(int qteId, int targetDigit, int timeoutTicks) {
        this.activeQteId = qteId;
        this.activeTargetDigit = targetDigit;
        this.qteTimeoutTicks = timeoutTicks;
        this.qteElapsed = 0;
        this.flashDigit = 0;
        this.flashTicks = 0;
        updateCue();
        updateButtonVisuals();
    }

    public void finish(boolean success, String message) {
        this.completed = true;
        this.onClose();
    }

    private void pressDigit(int digit) {
        if (activeQteId <= 0) {
            // No active QTE, ignore click or send cancel if desired
            return;
        }

        if (digit == activeTargetDigit) {
            // Correct digit pressed! Turn green flash
            this.flashDigit = digit;
            this.flashTicks = 6; // flash green for ~6 frames/ticks
            NetworkHandler.sendToServer(new LockpickQteAnswerC2SPacket(activeQteId, digit));
            this.activeQteId = 0;
            this.activeTargetDigit = 0;
            updateCue();
            updateButtonVisuals();
        } else {
            // Wrong digit pressed!
            NetworkHandler.sendToServer(new LockpickQteAnswerC2SPacket(activeQteId, digit));
            this.activeQteId = 0;
            this.activeTargetDigit = 0;
            updateCue();
            updateButtonVisuals();
        }
    }

    private void updateCue() {
        if (cueLabel == null) return;
        if (activeTargetDigit > 0) {
            cueLabel.text(Component.literal("НАЖМИТЕ: " + activeTargetDigit).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        } else if (flashTicks > 0) {
            cueLabel.text(Component.literal("Штифт зафиксирован!").withStyle(ChatFormatting.GREEN));
        } else {
            cueLabel.text(Component.literal("Подбор штифтов...").withStyle(ChatFormatting.GRAY));
        }
    }

    private void updateButtonVisuals() {
        for (int d = 1; d <= 9; d++) {
            FlowLayout btn = numButtons.get(d);
            LabelComponent lbl = numLabels.get(d);
            if (btn == null || lbl == null) continue;

            if (d == flashDigit && flashTicks > 0) {
                // Flashing green
                btn.surface(Surface.flat(0xFF2E7D32).and(Surface.outline(0xFF81C784)));
                lbl.text(Component.literal(String.valueOf(d)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
            } else if (d == activeTargetDigit) {
                // Highlighted yellow
                btn.surface(Surface.flat(0xFFF59E0B).and(Surface.outline(0xFFFFD54F)));
                lbl.text(Component.literal(String.valueOf(d)).withStyle(ChatFormatting.BLACK, ChatFormatting.BOLD));
            } else {
                // Default gray
                btn.surface(Surface.flat(0xFF232730).and(Surface.outline(0xFF3B404F)));
                lbl.text(Component.literal(String.valueOf(d)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        elapsedTicks++;

        if (flashTicks > 0) {
            flashTicks--;
            if (flashTicks == 0) {
                flashDigit = 0;
                updateCue();
                updateButtonVisuals();
            }
        }

        if (activeQteId > 0) {
            qteElapsed++;
            if (qteTimeoutTicks > 0 && qteElapsed >= qteTimeoutTicks) {
                // Timed out locally
                activeQteId = 0;
                activeTargetDigit = 0;
                updateCue();
                updateButtonVisuals();
            }
        }

        // Update progress bar
        if (progressBarFill != null && progressLabel != null) {
            float progress = Math.min(1.0F, (float) elapsedTicks / (float) totalTicks);
            int fillWidth = (int) (progress * 204.0F); // inner width of 206px container minus padding
            progressBarFill.sizing(Sizing.fixed(fillWidth), Sizing.fill(100));

            int pct = (int) (progress * 100);
            int secRemaining = Math.max(0, (totalTicks - elapsedTicks) / 20);
            progressLabel.text(Component.literal(pct + "% (" + secRemaining + " сек.)").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        int digit = getDigitFromKey(keyCode);
        if (digit >= 1 && digit <= 9) {
            UiSoundHelper.playButtonClick();
            pressDigit(digit);
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int getDigitFromKey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1 -> 1;
            case GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_KP_2 -> 2;
            case GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3 -> 3;
            case GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_KP_4 -> 4;
            case GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_KP_5 -> 5;
            case GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_KP_6 -> 6;
            case GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_KP_7 -> 7;
            case GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_KP_8 -> 8;
            case GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_KP_9 -> 9;
            default -> -1;
        };
    }

    @Override
    public void onClose() {
        if (!completed) {
            NetworkHandler.sendToServer(new LockpickCancelC2SPacket());
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        float scale = CharHistoryScreen.getUiScale();
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        int scaledMouseX = (int) (mouseX / scale);
        int scaledMouseY = (int) (mouseY / scale);
        try {
            if (this.uiAdapter != null) {
                this.uiAdapter.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);
            }
        } catch (Throwable t) {
            OneManager.LOGGER.error("Error during LockpickScreen render", t);
        }
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        try {
            float scale = CharHistoryScreen.getUiScale();
            return this.uiAdapter != null && this.uiAdapter.mouseClicked(mouseX / scale, mouseY / scale, button);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        try {
            float scale = CharHistoryScreen.getUiScale();
            return this.uiAdapter != null && this.uiAdapter.mouseReleased(mouseX / scale, mouseY / scale, button);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        try {
            float scale = CharHistoryScreen.getUiScale();
            return this.uiAdapter != null && this.uiAdapter.mouseScrolled(mouseX / scale, mouseY / scale, scrollX, scrollY);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        try {
            float scale = CharHistoryScreen.getUiScale();
            return this.uiAdapter != null && this.uiAdapter.mouseDragged(mouseX / scale, mouseY / scale, button, dragX / scale, dragY / scale);
        } catch (Throwable t) {
            return false;
        }
    }
}
