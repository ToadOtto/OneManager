package com.onemanager.client.gui.state;

import com.onemanager.OneManager;
import com.onemanager.client.gui.UiSoundHelper;
import com.onemanager.client.gui.components.PixelIconComponent;
import com.onemanager.client.gui.components.PlayerAvatarComponent;
import com.onemanager.client.gui.components.ReputationScaleComponent;
import com.onemanager.client.gui.components.TextureIconComponent;
import com.onemanager.data.ActiveCharacter;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.ReputationActionC2SPacket;
import com.onemanager.reputation.ReputationAbility;
import com.onemanager.reputation.ReputationAction;
import com.onemanager.reputation.ReputationEvent;
import com.onemanager.reputation.ReputationManager;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * Builds the complete Knyaz Reputation UI according to template (assets/tab_state_ui/reputation_ui.png).
 */
public final class ReputationPanelBuilder {

    private static final ResourceLocation TEXTURE_RIBBON = ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "textures/gui/state/red_ribbon.png");

    private ReputationPanelBuilder() {}

    public static FlowLayout build(CharacterWorldData worldData, Runnable onActionTriggered) {
        FlowLayout root = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        root.horizontalAlignment(HorizontalAlignment.CENTER);
        root.gap(6);
        root.padding(Insets.of(4));

        if (worldData == null) {
            root.child(Components.label(Component.literal("Данные мира недоступны").withStyle(ChatFormatting.GRAY)));
            return root;
        }

        Minecraft mc = Minecraft.getInstance();
        UUID myUuid = mc.player != null ? mc.player.getUUID() : null;
        boolean amIKnyaz = myUuid != null && worldData.isKnyaz(myUuid);
        PlayerCharacterData myPd = myUuid != null ? worldData.getPlayerData(myUuid) : null;
        long now = worldData.getTotalElapsedTicks();

        int rep = worldData.getKnyazReputation();
        int trend = worldData.getReputationTrend();

        // =====================================================================
        // SECTION 1: HEADER WITH RED RIBBON & KNYAZ AVATAR / TITLE
        // =====================================================================
        root.child(buildRibbonHeader(worldData));

        // =====================================================================
        // SECTION 2: ACTIONS ROW & BIG REPUTATION DISPLAY
        // =====================================================================
        root.child(buildActionsAndCenterRow(worldData, rep, trend, myPd, amIKnyaz, now, onActionTriggered));

        // =====================================================================
        // SECTION 3: REPUTATION HORIZONTAL SCALE (-100 .. +100)
        // =====================================================================
        ReputationScaleComponent scale = new ReputationScaleComponent(rep, 320, 22);
        root.child(scale);

        // =====================================================================
        // SECTION 4: ABILITY COLUMNS & UNIQUE EVENTS (SKULL & SUN)
        // =====================================================================
        root.child(buildAbilitiesAndEventsRow(rep));

        return root;
    }

    // =========================================================================
    // 1. RIBBON & KNYAZ AVATAR
    // =========================================================================

    private static FlowLayout buildRibbonHeader(CharacterWorldData worldData) {
        FlowLayout container = Containers.verticalFlow(Sizing.content(), Sizing.content());
        container.horizontalAlignment(HorizontalAlignment.CENTER);

        StackLayout ribbonStack = Containers.stack(Sizing.fixed(240), Sizing.fixed(60));
        ribbonStack.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        // Background red ribbon texture (native size 626x201)
        TextureIconComponent ribbon = new TextureIconComponent(TEXTURE_RIBBON, 0, 0, 626, 201, 626, 201, 240, 60);
        ribbonStack.child(ribbon);

        // Central overlay box for Knyaz portrait / title
        UUID knyazUuid = worldData.getKnyazUuid();
        String knyazName = worldData.getKnyazName();
        boolean hasKnyaz = worldData.hasKnyaz();

        FlowLayout centerBox = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(38));
        centerBox.verticalAlignment(VerticalAlignment.CENTER);
        centerBox.horizontalAlignment(HorizontalAlignment.CENTER);
        centerBox.surface(Surface.flat(0xEE12141B).and(Surface.outline(hasKnyaz ? 0xFF8A7242 : 0xFF353B47)));
        centerBox.padding(Insets.horizontal(8));
        centerBox.gap(6);

        if (hasKnyaz) {
            centerBox.child(new PlayerAvatarComponent(knyazUuid, 26));

            FlowLayout nameColumn = Containers.verticalFlow(Sizing.content(), Sizing.content());
            nameColumn.gap(1);

            PlayerCharacterData knyazPd = knyazUuid != null ? worldData.getPlayerData(knyazUuid) : null;
            ActiveCharacter knyazChar = knyazPd != null ? knyazPd.getActiveCharacter() : null;
            String charName = knyazChar != null && !knyazChar.getName().isEmpty() ? knyazChar.getName() : knyazName;

            LabelComponent titleLbl = Components.label(Component.literal("Князь").withStyle(ChatFormatting.GOLD));
            titleLbl.maxWidth(120);
            nameColumn.child(titleLbl);

            LabelComponent nameLbl = Components.label(Component.literal(charName).withStyle(ChatFormatting.WHITE));
            nameLbl.maxWidth(120);
            nameColumn.child(nameLbl);

            centerBox.child(nameColumn);
        } else {
            LabelComponent vacant = Components.label(Component.literal("Титул свободен").withStyle(ChatFormatting.GRAY));
            centerBox.child(vacant);
        }

        ribbonStack.child(centerBox);
        container.child(ribbonStack);

        return container;
    }

    // =========================================================================
    // 2. ACTIONS ROW (LEFT NEGATIVE, CENTER BIG DISPLAY, RIGHT POSITIVE)
    // =========================================================================

    private static FlowLayout buildActionsAndCenterRow(CharacterWorldData worldData, int rep, int trend,
                                                      PlayerCharacterData myPd, boolean amIKnyaz, long now,
                                                      Runnable onActionTriggered) {
        FlowLayout row = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.horizontalAlignment(HorizontalAlignment.CENTER);
        row.gap(10);

        // --- LEFT (Negative Actions) ---
        // Top: [-5] [-1] [-2]
        // Bottom: [-10] [-3] [-4]
        FlowLayout leftCol = Containers.verticalFlow(Sizing.content(), Sizing.content());
        leftCol.gap(3);

        FlowLayout leftTop = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        leftTop.gap(3);
        leftTop.child(createActionButton(ReputationAction.PROPAGANDA, false, myPd, amIKnyaz, now, onActionTriggered));
        leftTop.child(createActionButton(ReputationAction.SUPPORT, false, myPd, amIKnyaz, now, onActionTriggered));
        leftTop.child(createActionButton(ReputationAction.RUMORS, false, myPd, amIKnyaz, now, onActionTriggered));
        leftCol.child(leftTop);

        FlowLayout leftBottom = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        leftBottom.gap(3);
        leftBottom.child(createActionButton(ReputationAction.REPRESSION, false, myPd, amIKnyaz, now, onActionTriggered));
        leftBottom.child(createActionButton(ReputationAction.CELEBRATION, false, myPd, amIKnyaz, now, onActionTriggered));
        leftBottom.child(createActionButton(ReputationAction.PARADE, false, myPd, amIKnyaz, now, onActionTriggered));
        leftCol.child(leftBottom);

        row.child(leftCol);

        // --- CENTER: "Репутация:" + [ThumbDown] [Big Number] [Trend] [ThumbUp] ---
        FlowLayout centerCol = Containers.verticalFlow(Sizing.fixed(120), Sizing.content());
        centerCol.horizontalAlignment(HorizontalAlignment.CENTER);
        centerCol.gap(2);

        LabelComponent title = Components.label(Component.literal("Репутация:").withStyle(ChatFormatting.GRAY));
        centerCol.child(title);

        FlowLayout displayRow = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(26));
        displayRow.verticalAlignment(VerticalAlignment.CENTER);
        displayRow.horizontalAlignment(HorizontalAlignment.CENTER);
        displayRow.gap(5);

        // Thumbs Down (Red)
        displayRow.child(new PixelIconComponent(PixelIconComponent.THUMB_DOWN, 1, 0xFFFF5252));

        // Big signed number (custom color interpolation)
        int numColor = getReputationColor(rep);
        String repStr = (rep > 0 ? "+" : "") + rep;
        LabelComponent numLabel = Components.label(Component.literal(repStr).withStyle(style -> style.withColor(numColor).withBold(true)));
        displayRow.child(numLabel);

        // Trend arrows / equal
        displayRow.child(buildTrendIndicator(trend));

        // Thumbs Up (Green)
        displayRow.child(new PixelIconComponent(PixelIconComponent.THUMB_UP, 1, 0xFF4CAF50));

        centerCol.child(displayRow);
        row.child(centerCol);

        // --- RIGHT (Positive Actions) ---
        // Top: [+1] [+2] [+5]
        // Bottom: [+3] [+4] [+10]
        FlowLayout rightCol = Containers.verticalFlow(Sizing.content(), Sizing.content());
        rightCol.gap(3);

        FlowLayout rightTop = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        rightTop.gap(3);
        rightTop.child(createActionButton(ReputationAction.SUPPORT, true, myPd, amIKnyaz, now, onActionTriggered));
        rightTop.child(createActionButton(ReputationAction.RUMORS, true, myPd, amIKnyaz, now, onActionTriggered));
        rightTop.child(createActionButton(ReputationAction.PROPAGANDA, true, myPd, amIKnyaz, now, onActionTriggered));
        rightCol.child(rightTop);

        FlowLayout rightBottom = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        rightBottom.gap(3);
        rightBottom.child(createActionButton(ReputationAction.CELEBRATION, true, myPd, amIKnyaz, now, onActionTriggered));
        rightBottom.child(createActionButton(ReputationAction.PARADE, true, myPd, amIKnyaz, now, onActionTriggered));
        rightBottom.child(createActionButton(ReputationAction.REPRESSION, true, myPd, amIKnyaz, now, onActionTriggered));
        rightCol.child(rightBottom);

        row.child(rightCol);

        return row;
    }

    private static FlowLayout buildTrendIndicator(int trend) {
        FlowLayout box = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        box.verticalAlignment(VerticalAlignment.CENTER);
        box.gap(1);

        switch (trend) {
            case 2 -> { // Strong positive
                box.child(new PixelIconComponent(PixelIconComponent.ARROW_UP, 1, 0xFF00E676));
                box.child(new PixelIconComponent(PixelIconComponent.ARROW_UP, 1, 0xFF00E676));
                box.tooltip(Component.literal("Тенденция: сильный рост репутации").withStyle(ChatFormatting.GREEN));
            }
            case 1 -> { // Weak positive
                box.child(new PixelIconComponent(PixelIconComponent.ARROW_UP, 1, 0xFF66BB6A));
                box.tooltip(Component.literal("Тенденция: умеренный рост репутации").withStyle(ChatFormatting.GREEN));
            }
            case -1 -> { // Weak negative
                box.child(new PixelIconComponent(PixelIconComponent.ARROW_DOWN, 1, 0xFFEF5350));
                box.tooltip(Component.literal("Тенденция: умеренное падение репутации").withStyle(ChatFormatting.RED));
            }
            case -2 -> { // Strong negative
                box.child(new PixelIconComponent(PixelIconComponent.ARROW_DOWN, 1, 0xFFFF1744));
                box.child(new PixelIconComponent(PixelIconComponent.ARROW_DOWN, 1, 0xFFFF1744));
                box.tooltip(Component.literal("Тенденция: сильное падение репутации").withStyle(ChatFormatting.RED));
            }
            default -> { // Stable
                box.child(new PixelIconComponent(PixelIconComponent.EQUAL, 1, 0xFF8A93A6));
                box.tooltip(Component.literal("Тенденция: стабильно (изменений не было)").withStyle(ChatFormatting.GRAY));
            }
        }
        return box;
    }

    private static FlowLayout createActionButton(ReputationAction action, boolean positive,
                                                 PlayerCharacterData myPd, boolean amIKnyaz, long now,
                                                 Runnable onActionTriggered) {
        String labelText = (positive ? "+" : "-") + action.getMagnitude();
        int width = action.getMagnitude() >= 5 ? 30 : 26;
        int height = 20;

        FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(height));
        btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        long lastUsed = myPd != null ? myPd.getReputationActionLastUsed(action.getId()) : -1L;
        long cdRem = ReputationManager.getCooldownRemaining(lastUsed, action, now);
        boolean onCooldown = cdRem > 0;
        boolean isFaction = action.isFactionAction();
        boolean canClick = !amIKnyaz && !onCooldown && !isFaction;

        int bgColor;
        int borderColor;
        int hoverColor;

        if (canClick) {
            bgColor = positive ? 0xFF14241B : 0xFF2A1518;
            borderColor = positive ? 0xFF2C633E : 0xFF6B262F;
            hoverColor = positive ? 0xFF1C3A29 : 0xFF3D1C22;
            btn.cursorStyle(CursorStyle.HAND);
        } else {
            bgColor = 0xFF12141A;
            borderColor = 0xFF222630;
            hoverColor = 0xFF12141A;
            btn.cursorStyle(CursorStyle.POINTER);
        }

        btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor)));

        ChatFormatting txtColor = canClick ? (positive ? ChatFormatting.GREEN : ChatFormatting.RED) : ChatFormatting.DARK_GRAY;
        btn.child(Components.label(Component.literal(labelText).withStyle(txtColor)));

        // Tooltip
        StringBuilder sb = new StringBuilder();
        sb.append(action.getName(positive)).append(" (").append(labelText).append(" к репутации)\n");
        sb.append("§7").append(action.getDescription(positive)).append("§r\n\n");
        sb.append("• Цена: §f").append(action.getCostText()).append("§r\n");
        sb.append("• Периодичность: §f").append(action.getCooldownText()).append("§r\n");

        if (amIKnyaz) {
            sb.append("\n§cКнязь не может влиять на собственную репутацию.§r");
        } else if (isFaction) {
            sb.append("\n§eТребуется от ").append(action.getRequiredFactionInfluence())
                    .append("% влияния фракции (в разработке).§r");
        } else if (onCooldown) {
            sb.append("\n§cКулдаун: осталось ").append(ReputationManager.formatTicks(cdRem)).append("§r");
        } else {
            sb.append("\n§aДоступно для использования§r");
        }
        btn.tooltip(Component.literal(sb.toString()));

        final int fBg = bgColor;
        final int fHover = hoverColor;
        final int fBorder = borderColor;
        btn.mouseEnter().subscribe(() -> {
            if (canClick) btn.surface(Surface.flat(fHover).and(Surface.outline(fBorder)));
        });
        btn.mouseLeave().subscribe(() -> btn.surface(Surface.flat(fBg).and(Surface.outline(fBorder))));

        btn.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                UiSoundHelper.playButtonClick();
                if (canClick) {
                    NetworkHandler.sendToServer(new ReputationActionC2SPacket(action.getId(), positive));
                    if (onActionTriggered != null) {
                        onActionTriggered.run();
                    }
                } else if (amIKnyaz) {
                    sendLocalActionbar("Князь не может влиять на собственную репутацию.");
                } else if (isFaction) {
                    sendLocalActionbar("Действия фракций находятся в разработке.");
                } else if (onCooldown) {
                    sendLocalActionbar("Действие на кулдауне: осталось " + ReputationManager.formatTicks(cdRem));
                }
                return true;
            }
            return false;
        });

        return btn;
    }

    // =========================================================================
    // 3. ABILITY TIERS & SPECIAL EVENTS (SKULL & SUN)
    // =========================================================================

    private static FlowLayout buildAbilitiesAndEventsRow(int rep) {
        FlowLayout container = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        container.verticalAlignment(VerticalAlignment.CENTER);
        container.horizontalAlignment(HorizontalAlignment.CENTER);
        container.gap(8);

        // --- SKULL (Королевская Охота, от -90) ---
        container.child(buildSkullEvent(rep));

        // --- NEGATIVE TIERS (Left: от -1, от -25, от -50, от -75) ---
        FlowLayout negCol = Containers.verticalFlow(Sizing.content(), Sizing.content());
        negCol.gap(3);
        negCol.child(buildAbilityTierRow(1, false, rep));
        negCol.child(buildAbilityTierRow(2, false, rep));
        negCol.child(buildAbilityTierRow(3, false, rep));
        negCol.child(buildAbilityTierRow(4, false, rep));
        container.child(negCol);

        // --- POSITIVE TIERS (Right: от +1, от +25, от +50, от +75) ---
        FlowLayout posCol = Containers.verticalFlow(Sizing.content(), Sizing.content());
        posCol.gap(3);
        posCol.child(buildAbilityTierRow(1, true, rep));
        posCol.child(buildAbilityTierRow(2, true, rep));
        posCol.child(buildAbilityTierRow(3, true, rep));
        posCol.child(buildAbilityTierRow(4, true, rep));
        container.child(posCol);

        // --- SUN (Монумент Правителя, от +90) ---
        container.child(buildSunEvent(rep));

        return container;
    }

    private static FlowLayout buildAbilityTierRow(int tier, boolean positive, int reputation) {
        boolean unlocked = ReputationAbility.isTierUnlocked(tier, positive, reputation);

        FlowLayout row = Containers.horizontalFlow(Sizing.fixed(144), Sizing.fixed(24));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.padding(Insets.horizontal(4));
        row.gap(4);

        int bgColor;
        int borderColor;
        if (!unlocked || reputation == 0) {
            bgColor = 0xFF111318;
            borderColor = 0xFF222630;
        } else if (positive) {
            // Progressive green contrast: tiers 1..4
            bgColor = switch (tier) {
                case 1 -> 0xFF111E16;
                case 2 -> 0xFF14291D;
                case 3 -> 0xFF173524;
                default -> 0xFF1B422D;
            };
            borderColor = switch (tier) {
                case 1 -> 0xFF244832;
                case 2 -> 0xFF2E6342;
                case 3 -> 0xFF388052;
                default -> 0xFF43A065;
            };
        } else {
            // Progressive red contrast: tiers 1..4
            bgColor = switch (tier) {
                case 1 -> 0xFF201316;
                case 2 -> 0xFF2D161B;
                case 3 -> 0xFF3A1920;
                default -> 0xFF4A1C25;
            };
            borderColor = switch (tier) {
                case 1 -> 0xFF482229;
                case 2 -> 0xFF632832;
                case 3 -> 0xFF802E3B;
                default -> 0xFF9E3342;
            };
        }

        row.surface(Surface.flat(bgColor).and(Surface.outline(borderColor)));

        // Threshold label on the left: "от +25" or "от -25"
        int threshold = ReputationAbility.signedThreshold(tier, positive);
        String thLabel = "от " + (threshold > 0 ? "+" : "") + threshold;
        LabelComponent lbl = Components.label(Component.literal(thLabel).withStyle(unlocked && reputation != 0 ? (positive ? ChatFormatting.GREEN : ChatFormatting.RED) : ChatFormatting.DARK_GRAY));
        lbl.maxWidth(42);
        row.child(lbl);

        // 4 Ability button stubs
        List<ReputationAbility> abilities = ReputationAbility.getTier(tier, positive);
        for (int i = 0; i < 4; i++) {
            ReputationAbility a = i < abilities.size() ? abilities.get(i) : null;
            row.child(createAbilitySlot(a, unlocked && reputation != 0, positive));
        }

        return row;
    }

    private static FlowLayout createAbilitySlot(ReputationAbility ability, boolean unlocked, boolean positive) {
        FlowLayout slot = Containers.horizontalFlow(Sizing.fixed(18), Sizing.fixed(18));
        slot.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int slotBg = unlocked ? (positive ? 0xFF1A3324 : 0xFF35191E) : 0xFF14161E;
        int slotBorder = unlocked ? (positive ? 0xFF357049 : 0xFF702D37) : 0xFF252934;
        slot.surface(Surface.flat(slotBg).and(Surface.outline(slotBorder)));
        slot.cursorStyle(unlocked ? CursorStyle.HAND : CursorStyle.POINTER);

        if (ability != null) {
            String star = unlocked ? "★" : "·";
            ChatFormatting starColor = unlocked ? (positive ? ChatFormatting.GREEN : ChatFormatting.RED) : ChatFormatting.DARK_GRAY;
            slot.child(Components.label(Component.literal(star).withStyle(starColor)));

            String statusText = unlocked ? "§aРазблокировано" : "§cЗаблокировано (требуется репутация " + ability.threshold() + ")";
            slot.tooltip(Component.literal(
                    "§f" + ability.name() + " §7[ID: " + ability.id() + "]§r\n"
                            + "§7" + ability.description() + "§r\n\n"
                            + statusText + "§r\n§8(Кнопка-заглушка для будущих способностей)§r"
            ));

            slot.mouseDown().subscribe((mouseX, mouseY, button) -> {
                if (button == 0) {
                    UiSoundHelper.playButtonClick();
                    sendLocalActionbar(ability.name() + " [ID: " + ability.id() + "]: механика в разработке.");
                    return true;
                }
                return false;
            });
        }

        return slot;
    }

    private static FlowLayout buildSkullEvent(int rep) {
        FlowLayout box = Containers.verticalFlow(Sizing.fixed(48), Sizing.content());
        box.horizontalAlignment(HorizontalAlignment.CENTER);
        box.gap(3);

        boolean unlocked = rep <= -90;
        LabelComponent label = Components.label(Component.literal("от -90").withStyle(unlocked ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        box.child(label);

        int skullColor = unlocked ? 0xFFFF5252 : 0xFF3D4352;
        PixelIconComponent skull = new PixelIconComponent(PixelIconComponent.SKULL, 1, skullColor);
        skull.cursorStyle(unlocked ? CursorStyle.HAND : CursorStyle.POINTER);

        ReputationEvent event = ReputationEvent.ROYAL_HUNT;
        skull.tooltip(Component.literal(
                "§c§l" + event.getTitle() + "§r §7(от -90)§r\n\n"
                        + "§7" + event.getDescription() + "§r\n\n"
                        + (unlocked ? "§aСобытие доступно для запуска!§r\n§8(Заглушка)§r" : "§cНедоступно (требуется репутация ≤ -90)§r")
        ));

        skull.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                UiSoundHelper.playButtonClick();
                sendLocalActionbar(event.getTitle() + ": событие находится в разработке.");
                return true;
            }
            return false;
        });

        box.child(skull);
        return box;
    }

    private static FlowLayout buildSunEvent(int rep) {
        FlowLayout box = Containers.verticalFlow(Sizing.fixed(48), Sizing.content());
        box.horizontalAlignment(HorizontalAlignment.CENTER);
        box.gap(3);

        boolean unlocked = rep >= 90;
        LabelComponent label = Components.label(Component.literal("от +90").withStyle(unlocked ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        box.child(label);

        int sunColor = unlocked ? 0xFFFFD700 : 0xFF3D4352;
        PixelIconComponent sun = new PixelIconComponent(PixelIconComponent.SUN, 1, sunColor);
        sun.cursorStyle(unlocked ? CursorStyle.HAND : CursorStyle.POINTER);

        ReputationEvent event = ReputationEvent.RULER_MONUMENT;
        sun.tooltip(Component.literal(
                "§6§l" + event.getTitle() + "§r §7(от +90)§r\n\n"
                        + "§7" + event.getDescription() + "§r\n\n"
                        + (unlocked ? "§aДоступно добровольно для Князя!§r\n§8(Заглушка)§r" : "§cНедоступно (требуется репутация ≥ +90)§r")
        ));

        sun.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                UiSoundHelper.playButtonClick();
                sendLocalActionbar(event.getTitle() + ": событие находится в разработке.");
                return true;
            }
            return false;
        });

        box.child(sun);
        return box;
    }

    private static int getReputationColor(int rep) {
        if (rep == 0) return 0xFF8A93A6;
        if (rep > 0) {
            float factor = Math.min(1.0f, rep / 100.0f);
            return interpolateRgb(0xFF81C784, 0xFF00E676, factor);
        } else {
            float factor = Math.min(1.0f, Math.abs(rep) / 100.0f);
            return interpolateRgb(0xFFE57373, 0xFFFF1744, factor);
        }
    }

    private static int interpolateRgb(int col1, int col2, float factor) {
        int r1 = (col1 >> 16) & 0xFF, g1 = (col1 >> 8) & 0xFF, b1 = col1 & 0xFF;
        int r2 = (col2 >> 16) & 0xFF, g2 = (col2 >> 8) & 0xFF, b2 = col2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void sendLocalActionbar(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.YELLOW), true);
        }
    }
}
