package com.pcmanager.client.gui.components;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.inject.GreedyInputComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * An owo-ui component wrapping Minecraft's MultiLineEditBox.
 */
public class MultiLineEditBoxComponent extends BaseComponent implements GreedyInputComponent {
    private final MultiLineEditBox editBox;

    public MultiLineEditBoxComponent(Font font, int width, int height, Component placeholder, Component title) {
        this.editBox = new MultiLineEditBox(font, 0, 0, width, height, placeholder, title);
        this.sizing(Sizing.fixed(width), Sizing.fixed(height));
        this.cursorStyle(CursorStyle.TEXT);
    }

    public MultiLineEditBox getEditBox() {
        return editBox;
    }

    public String getValue() {
        return editBox.getValue();
    }

    public void setValue(String value) {
        editBox.setValue(value);
    }

    public void setCharacterLimit(int limit) {
        editBox.setCharacterLimit(limit);
    }

    public void setValueListener(Consumer<String> listener) {
        editBox.setValueListener(listener);
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return true;
    }

    @Override
    public void onFocusGained(FocusSource source) {
        super.onFocusGained(source);
        editBox.setFocused(true);
    }

    @Override
    public void onFocusLost() {
        super.onFocusLost();
        editBox.setFocused(false);
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        editBox.setX(this.x);
        editBox.setY(this.y);
        editBox.setWidth(this.width);
        editBox.setHeight(this.height);
        if (this.focusHandler() != null) {
            this.focusHandler().focus(this, FocusSource.MOUSE_CLICK);
        }
        editBox.setFocused(true);
        return editBox.mouseClicked(this.x + mouseX, this.y + mouseY, button);
    }

    @Override
    public boolean onMouseDrag(double mouseX, double mouseY, double deltaX, double deltaY, int button) {
        editBox.setX(this.x);
        editBox.setY(this.y);
        return editBox.mouseDragged(this.x + mouseX, this.y + mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        editBox.setX(this.x);
        editBox.setY(this.y);
        return editBox.mouseScrolled(this.x + mouseX, this.y + mouseY, 0.0, amount);
    }

    @Override
    public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
        return editBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean onCharTyped(char chr, int modifiers) {
        return editBox.charTyped(chr, modifiers);
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        editBox.setX(this.x);
        editBox.setY(this.y);
        editBox.setWidth(this.width);
        editBox.setHeight(this.height);
        editBox.render(context, mouseX, mouseY, partialTicks);
    }
}
