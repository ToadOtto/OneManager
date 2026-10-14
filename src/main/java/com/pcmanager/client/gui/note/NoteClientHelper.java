package com.pcmanager.client.gui.note;

import com.pcmanager.item.NoteItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class NoteClientHelper {
    public static void handleNoteUse(InteractionHand hand, ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty()) return;

        if (NoteItem.isOpened(stack)) {
            // Вскрытое письмо нельзя отправить — открывает чтение напрямую
            Minecraft.getInstance().setScreen(new NoteViewScreen(NoteItem.getTitle(stack), NoteItem.getPages(stack), NoteItem.getAuthor(stack)));
        } else if (NoteItem.isSealed(stack)) {
            if (player != null && player.isShiftKeyDown()) {
                Minecraft.getInstance().setScreen(new NoteViewScreen(NoteItem.getTitle(stack), NoteItem.getPages(stack), NoteItem.getAuthor(stack)));
            } else {
                Minecraft.getInstance().setScreen(new SendNoteScreen(hand, stack));
            }
        } else {
            Minecraft.getInstance().setScreen(new NoteEditScreen(hand, stack));
        }
    }
}
