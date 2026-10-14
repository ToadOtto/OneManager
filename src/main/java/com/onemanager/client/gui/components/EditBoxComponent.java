package com.onemanager.client.gui.components;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.inject.GreedyInputComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An owo-ui component wrapping Minecraft's EditBox.
 * Fully supports focus, keyboard typing, cursor navigation, and GreedyInputComponent.
 */
public class EditBoxComponent extends BaseComponent implements GreedyInputComponent {
    private final EditBox editBox;

    public EditBoxComponent(Font font, int width, int height, Component title) {
        this.editBox = new EditBox(font, 0, 0, width, height, title);
        this.editBox.setBordered(true);
        this.sizing(Sizing.fixed(width), Sizing.fixed(height));
        this.cursorStyle(CursorStyle.TEXT);
    }

    public EditBox getEditBox() {
        return editBox;
    }

    public String getValue() {
        return editBox.getValue();
    }

    public void setValue(String value) {
        editBox.setValue(value);
    }

    public void setMaxLength(int length) {
        editBox.setMaxLength(length);
    }

    public void setHint(Component hint) {
        editBox.setHint(hint);
    }

    public void setFilter(Predicate<String> filter) {
        editBox.setFilter(filter);
    }

    public void setResponder(Consumer<String> responder) {
        editBox.setResponder(responder);
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
        if (this.focusHandler() != null) {
            this.focusHandler().focus(this, FocusSource.MOUSE_CLICK);
        }
        editBox.setFocused(true);
        return editBox.mouseClicked(this.x + mouseX, this.y + mouseY, button);
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
