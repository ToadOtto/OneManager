package com.onemanager.client.gui.state;

import com.onemanager.client.KeyBindings;
import com.onemanager.client.gui.UiSoundHelper;
import com.onemanager.client.gui.components.PlayerAvatarComponent;
import com.onemanager.client.gui.components.ScaledLabelComponent;
import com.onemanager.data.ActiveCharacter;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * Builds the Knyaz Management UI (assets/tab_state_ui/knyaz_ui.png).
 * Accessible only to the ruling Knyaz.
 */
public final class KnyazPanelBuilder {

    private KnyazPanelBuilder() {}

    public static FlowLayout build(CharacterWorldData worldData, Runnable onActionTriggered) {
        FlowLayout root = Containers.verticalFlow(Sizing.content(), Sizing.content());
        root.horizontalAlignment(HorizontalAlignment.CENTER);
        root.gap(10);
        root.padding(Insets.of(4));

        if (worldData == null) {
            root.child(Components.label(Component.literal("Данные мира недоступны").withStyle(ChatFormatting.GRAY)));
            return root;
        }

        UUID knyazUuid = worldData.getKnyazUuid();
        String knyazName = worldData.getKnyazName();
        int rep = worldData.getKnyazReputation();
        int trend = worldData.getReputationTrend();

        // =====================================================================
        // SECTION 1: KNYAZ AVATAR & REPUTATION SUMMARY
        // =====================================================================
        FlowLayout topBox = Containers.verticalFlow(Sizing.content(), Sizing.content());
        topBox.horizontalAlignment(HorizontalAlignment.CENTER);
        topBox.gap(4);

        FlowLayout rulerCard = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(42));
        rulerCard.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        rulerCard.surface(Surface.flat(0xEE12141B).and(Surface.outline(0xFF8A7242)));
        rulerCard.padding(Insets.horizontal(10));
        rulerCard.gap(8);

        rulerCard.child(new PlayerAvatarComponent(knyazUuid, 32));

        FlowLayout rulerNames = Containers.verticalFlow(Sizing.content(), Sizing.content());
        rulerNames.gap(2);

        PlayerCharacterData knyazPd = knyazUuid != null ? worldData.getPlayerData(knyazUuid) : null;
        ActiveCharacter knyazChar = knyazPd != null ? knyazPd.getActiveCharacter() : null;
        String charName = knyazChar != null && !knyazChar.getName().isEmpty() ? knyazChar.getName() : knyazName;

        LabelComponent titleLbl = Components.label(Component.literal("Князь").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        titleLbl.maxWidth(140);
        rulerNames.child(titleLbl);

        LabelComponent nameLbl = Components.label(Component.literal(charName).withStyle(ChatFormatting.WHITE));
        nameLbl.maxWidth(140);
        rulerNames.child(nameLbl);

        rulerCard.child(rulerNames);
        topBox.child(rulerCard);

        // "Ваша репутация:"
        topBox.child(Components.label(Component.literal("Ваша репутация:").withStyle(ChatFormatting.GRAY)));

        // Big signed number + trend
        FlowLayout repRow = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(24));
        repRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        repRow.gap(4);

        int numColor = ReputationPanelBuilder.getReputationColor(rep);
        String repStr = (rep > 0 ? "+" : "") + rep;
        ScaledLabelComponent numLabel = new ScaledLabelComponent(
                Component.literal(repStr).withStyle(style -> style.withColor(numColor).withBold(true)),
                1.6f, numColor, true
        );
        numLabel.sizing(Sizing.fixed(36), Sizing.fixed(16));
        repRow.child(numLabel);
        repRow.child(ReputationPanelBuilder.buildTrendIndicator(trend));
        topBox.child(repRow);

        root.child(topBox);

        // =====================================================================
        // SECTION 2: СИЛЫ МОНАРХА (CARD WITH BUTTONS 1-6 ACTIVE, 7 PASSIVE)
        // =====================================================================
        FlowLayout powersCard = Containers.verticalFlow(Sizing.fixed(360), Sizing.content());
        powersCard.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF2E3544)));
        powersCard.padding(Insets.of(8));
        powersCard.gap(6);
        powersCard.horizontalAlignment(HorizontalAlignment.CENTER);

        FlowLayout powersTitleRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        powersTitleRow.verticalAlignment(VerticalAlignment.CENTER);
        powersTitleRow.gap(4);
        ItemComponent crownIcon = Components.item(new ItemStack(Items.GOLDEN_HELMET));
        crownIcon.sizing(Sizing.fixed(14), Sizing.fixed(14));
        powersTitleRow.child(crownIcon);
        powersTitleRow.child(Components.label(Component.literal("Силы Монарха").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        powersCard.child(powersTitleRow);

        FlowLayout buttonsRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        buttonsRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        buttonsRow.gap(8);

        // Buttons 1-6 (Active abilities)
        for (int i = 1; i <= 6; i++) {
            buttonsRow.child(createMonarchActionButton(i));
        }

        // Button 7 (Passive ability - Crown buffs/effects)
        buttonsRow.child(createMonarchPassiveButton());

        powersCard.child(buttonsRow);
        root.child(powersCard);

        // =====================================================================
        // SECTION 3: ABILITY TIERS (<0, <-25.. and >0, >25..)
        // =====================================================================
        root.child(ReputationPanelBuilder.buildAbilityColumnsOnly(rep));

        return root;
    }

    private static FlowLayout createMonarchActionButton(int slot) {
        FlowLayout col = Containers.verticalFlow(Sizing.content(), Sizing.content());
        col.horizontalAlignment(HorizontalAlignment.CENTER);
        col.gap(2);

        // Number above button
        col.child(Components.label(Component.literal(String.valueOf(slot)).withStyle(ChatFormatting.GRAY)));

        FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(32), Sizing.fixed(32));
        btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        btn.surface(Surface.flat(0xFF181B23).and(Surface.outline(0xFF323846)));
        btn.cursorStyle(CursorStyle.HAND);

        // Icon inside button
        String roman = switch (slot) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> String.valueOf(slot);
        };
        btn.child(Components.label(Component.literal(roman).withStyle(ChatFormatting.GOLD)));

        // Keybind description
        String keyName = "Не назначена";
        if (slot >= 1 && slot <= KeyBindings.KNYAZ_ABILITIES.length) {
            keyName = KeyBindings.KNYAZ_ABILITIES[slot - 1].getTranslatedKeyMessage().getString();
        }

        btn.tooltip(Component.literal(
                "§6§lСила Монарха " + slot + " (Активная)§r\n\n"
                        + "§7Активная способность Князя, доступная всегда.§r\n"
                        + "§8Назначьте горячую клавишу в настройках управления.§r\n\n"
                        + "§eГорячая клавиша: §f" + keyName + "§r\n"
                        + "§8(Нажмите для вызова или используйте клавишу)§r"
        ));

        btn.mouseEnter().subscribe(() -> btn.surface(Surface.flat(0xFF222734).and(Surface.outline(0xFF4C566A))));
        btn.mouseLeave().subscribe(() -> btn.surface(Surface.flat(0xFF181B23).and(Surface.outline(0xFF323846))));
        btn.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                UiSoundHelper.playButtonClick();
                KeyBindings.triggerKnyazAbility(slot);
                return true;
            }
            return false;
        });

        col.child(btn);
        return col;
    }

    private static FlowLayout createMonarchPassiveButton() {
        FlowLayout col = Containers.verticalFlow(Sizing.content(), Sizing.content());
        col.horizontalAlignment(HorizontalAlignment.CENTER);
        col.gap(2);

        // Number above button in Gold
        col.child(Components.label(Component.literal("7").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));

        FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(32), Sizing.fixed(32));
        btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        btn.surface(Surface.flat(0xFF282214).and(Surface.outline(0xFFB5933C)));
        btn.cursorStyle(CursorStyle.HAND);

        ItemComponent crown = Components.item(new ItemStack(Items.GOLDEN_HELMET));
        crown.sizing(Sizing.fixed(20), Sizing.fixed(20));
        btn.child(crown);

        btn.tooltip(Component.literal(
                "§6§lБлагословение Короны (Пассивная)§r\n\n"
                        + "§7Постоянные бафы и эффекты Монарха от ношения Короны:§r\n"
                        + "§a• Регенерация I§r\n"
                        + "§a• Сопротивление I§r\n"
                        + "§a• Укрепление и защита правителя§r\n\n"
                        + "§eДействует автоматически, пока Корона надета на Князя.§r"
        ));

        btn.mouseEnter().subscribe(() -> btn.surface(Surface.flat(0xFF382F1C).and(Surface.outline(0xFFFFD700))));
        btn.mouseLeave().subscribe(() -> btn.surface(Surface.flat(0xFF282214).and(Surface.outline(0xFFB5933C))));
        btn.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                UiSoundHelper.playButtonClick();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("👑 Благословение Короны активно: защита и статус правителя.").withStyle(ChatFormatting.GOLD),
                            true
                    );
                }
                return true;
            }
            return false;
        });

        col.child(btn);
        return col;
    }
}
