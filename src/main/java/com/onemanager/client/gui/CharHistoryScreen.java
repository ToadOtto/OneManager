package com.onemanager.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.onemanager.OneManager;
import com.onemanager.client.gui.components.BurdenLampComponent;
import com.onemanager.client.gui.components.EditBoxComponent;
import com.onemanager.client.gui.components.PlayerAvatarComponent;
import com.onemanager.client.gui.components.ProfessionProgressBarComponent;
import com.onemanager.client.gui.components.ScaledLabelComponent;
import com.onemanager.client.gui.components.SlotLampComponent;
import com.onemanager.client.gui.components.TextureIconComponent;
import com.onemanager.data.ActiveCharacter;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.DeadCharacter;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.data.MailEntry;
import com.onemanager.data.LawEntry;
import com.onemanager.data.TreasuryLogEntry;
import com.onemanager.item.ModItems;
import com.onemanager.client.gui.law.LawEditScreen;
import com.onemanager.client.gui.note.NoteViewScreen;
import com.onemanager.network.ClaimLetterPacket;
import com.onemanager.network.RepealLawPacket;
import com.onemanager.network.DepositTreasuryPacket;
import com.oneshop.client.ClientBankCache;
import com.oneshop.item.SprotaItem;
import com.onemanager.network.DepositWithdrawStateBrandPacket;
import com.onemanager.network.ExpandWorldBorderPacket;
import com.onemanager.network.LeaveProfessionPacket;
import com.onemanager.network.NetworkHandler;
import com.onemanager.network.PayTaxPacket;
import com.onemanager.network.SelectProfessionPacket;
import com.onemanager.network.SetTaxRatePacket;
import com.onemanager.network.WithdrawTreasuryPacket;
import com.onemanager.profession.Profession;
import com.onemanager.profession.ProfessionCategory;
import com.onemanager.profession.ProfessionRegistry;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.OverlayContainer;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Modern hub screen powered by owo-lib (owo-ui).
 * Contains State, Population, and Profession management.
 */
public class CharHistoryScreen extends BaseOwoScreen<StackLayout> {

    public enum Tab {
        POPULATION("Население"),
        STATE("Государство"),
        PROFESSIONS("Профессии"),
        TREASURY("Казна"),
        LETTERS("Письма"),
        LAWS("Законы"),
        FACTIONS("Фракции"),
        PROGRESS("Мир");

        private final String title;

        Tab(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public ItemStack getIconStack() {
            return switch (this) {
                case POPULATION -> new ItemStack(Items.PLAYER_HEAD);
                case STATE -> new ItemStack(Items.GOLDEN_HELMET);
                case PROFESSIONS -> new ItemStack(Items.IRON_PICKAXE);
                case TREASURY -> new ItemStack(com.oneshop.item.ModItems.SPROTA.get());
                case LETTERS -> new ItemStack(ModItems.NOTE.get());
                case LAWS -> new ItemStack(Items.BOOK);
                case FACTIONS -> new ItemStack(Items.SHIELD);
                case PROGRESS -> new ItemStack(Items.GRASS_BLOCK);
            };
        }
    }

    public static final ResourceLocation ICON_BURDEN = ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "textures/mob_effect/ancestor_burden.png");
    public static final ResourceLocation ICON_REMAINS = ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "textures/item/remains.png");
    public static final ResourceLocation ICON_STATE_BRAND = ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "textures/item/state_brand.png");
    public static final ResourceLocation ICON_MONARCH_CROWN = ResourceLocation.fromNamespaceAndPath(OneManager.MOD_ID, "textures/item/monarch_crown.png");

    private static final ScrollContainer.Scrollbar DARK_SCROLLBAR = (context, x, y, width, height, trackX, trackY, trackWidth, trackHeight, lastInteractTime, direction, isHovered) -> {
        context.fill(trackX, trackY, trackX + trackWidth, trackY + trackHeight, 0x33000000);
        int thumbColor = isHovered ? 0xFF4A5568 : 0xFF2A2F3A;
        context.fill(x, y, x + width, y + height, thumbColor);
    };

    private static final float UI_SCALE = 0.70f;

    private CharacterWorldData worldData;
    private Tab currentTab = Tab.POPULATION;

    public enum ProfessionSubCategory {
        MY_PROFESSIONS("Ваши профессии"),
        CATALOG("Каталог профессий");

        private final String title;
        ProfessionSubCategory(String title) {
            this.title = title;
        }
        public String getTitle() { return title; }
    }

    private ProfessionSubCategory currentProfessionSubCategory = ProfessionSubCategory.MY_PROFESSIONS;

    public static int interpolateColor(int color1, int color2, float factor) {
        factor = Math.max(0.0f, Math.min(1.0f, factor));
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int clientTaxTicksRemaining = 0;
    private int clientWeekTicksRemaining = 0;
    private int clientWeek = 1;
    private int clientDay = 1;
    private LabelComponent taxTimerLabel = null;
    private LabelComponent cycleTimerLabel = null;

    public static final java.util.Set<UUID> SEEN_LETTER_IDS = new java.util.HashSet<>();
    public static final Map<String, Long> ABILITY_COOLDOWNS = new HashMap<>();

    public boolean hasUnreadLetters() {
        PlayerCharacterData pd = getMyPlayerData();
        if (pd == null) return false;
        for (MailEntry mail : pd.getMailbox()) {
            if (!mail.isClaimed() && !SEEN_LETTER_IDS.contains(mail.getId())) {
                return true;
            }
        }
        return false;
    }

    public void markLettersAsSeen() {
        PlayerCharacterData pd = getMyPlayerData();
        if (pd == null) return;
        for (MailEntry mail : pd.getMailbox()) {
            SEEN_LETTER_IDS.add(mail.getId());
        }
    }

    public record LivingEntry(ActiveCharacter active, PlayerCharacterData playerData) {}
    public record DeadEntry(DeadCharacter dead, String playerName, UUID playerUuid) {}

    public CharHistoryScreen(CharacterWorldData worldData) {
        super(Component.literal("OneHouse3"));
        this.worldData = worldData != null ? worldData : new CharacterWorldData();
        this.clientTaxTicksRemaining = this.worldData.getTaxTicksRemaining();
        this.clientWeekTicksRemaining = this.worldData.getWeekTicksRemaining();
        this.clientWeek = this.worldData.getCurrentWeek();
        this.clientDay = this.worldData.getCurrentDay();
        if (this.currentTab == Tab.LETTERS) {
            markLettersAsSeen();
        }
    }

    public void updateData(CharacterWorldData newWorldData) {
        this.worldData = newWorldData != null ? newWorldData : new CharacterWorldData();
        this.clientTaxTicksRemaining = this.worldData.getTaxTicksRemaining();
        this.clientWeekTicksRemaining = this.worldData.getWeekTicksRemaining();
        this.clientWeek = this.worldData.getCurrentWeek();
        this.clientDay = this.worldData.getCurrentDay();
        if (this.currentTab == Tab.LETTERS) {
            markLettersAsSeen();
        }
        rebuildUI();
    }

    public void setTab(Tab tab) {
        this.currentTab = tab != null ? tab : Tab.POPULATION;
        if (this.currentTab == Tab.LETTERS) {
            markLettersAsSeen();
        }
        rebuildUI();
    }

    public void rebuildUI() {
        if (this.uiAdapter != null && this.uiAdapter.rootComponent != null) {
            try {
                java.lang.reflect.Field taskQueueField = io.wispforest.owo.ui.base.BaseParentComponent.class.getDeclaredField("taskQueue");
                taskQueueField.setAccessible(true);
                Object queue = taskQueueField.get(this.uiAdapter.rootComponent);
                if (queue instanceof java.util.List<?> list) {
                    list.clear();
                }
            } catch (Throwable ignored) {
            }
            this.uiAdapter.rootComponent.clearChildren();
            build(this.uiAdapter.rootComponent);
        }
    }

    @Override
    protected OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::stack);
    }

    @Override
    protected void init() {
        super.init();
        if (this.uiAdapter != null) {
            this.uiAdapter.moveAndResize(0, 0, (int) (this.width / UI_SCALE), (int) (this.height / UI_SCALE));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.invalid) {
            this.onClose();
            return;
        }
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(UI_SCALE, UI_SCALE, 1.0f);
        int scaledMouseX = (int) (mouseX / UI_SCALE);
        int scaledMouseY = (int) (mouseY / UI_SCALE);
        try {
            this.uiAdapter.render(guiGraphics, scaledMouseX, scaledMouseY, partialTick);
        } catch (Throwable t) {
            OneManager.LOGGER.error("Error during CharHistoryScreen render", t);
        }
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        try {
            return this.uiAdapter != null && this.uiAdapter.mouseClicked(mouseX / UI_SCALE, mouseY / UI_SCALE, button);
        } catch (Throwable t) {
            OneManager.LOGGER.error("Error during mouseClicked in CharHistoryScreen", t);
            return false;
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        try {
            return this.uiAdapter != null && this.uiAdapter.mouseReleased(mouseX / UI_SCALE, mouseY / UI_SCALE, button);
        } catch (Throwable t) {
            OneManager.LOGGER.error("Error during mouseReleased in CharHistoryScreen", t);
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        try {
            return this.uiAdapter != null && this.uiAdapter.mouseScrolled(mouseX / UI_SCALE, mouseY / UI_SCALE, scrollX, scrollY);
        } catch (Throwable t) {
            OneManager.LOGGER.error("Error during mouseScrolled in CharHistoryScreen", t);
            return false;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        try {
            return this.uiAdapter != null && this.uiAdapter.mouseDragged(mouseX / UI_SCALE, mouseY / UI_SCALE, button, dragX / UI_SCALE, dragY / UI_SCALE);
        } catch (Throwable t) {
            OneManager.LOGGER.error("Error during mouseDragged in CharHistoryScreen", t);
            return false;
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        try {
            if (this.uiAdapter != null && this.uiAdapter.charTyped(codePoint, modifiers)) {
                return true;
            }
        } catch (Throwable t) {
            OneManager.LOGGER.error("Error during charTyped in CharHistoryScreen", t);
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
            OneManager.LOGGER.error("Error during keyPressed in CharHistoryScreen", t);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void build(StackLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT);

        FlowLayout baseWrapper = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        baseWrapper.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        // Main Window Panel: very dark gray, almost black
        FlowLayout mainPanel = Containers.verticalFlow(Sizing.fill(92), Sizing.fill(90));
        mainPanel.surface(Surface.flat(0xFF0D0F13).and(Surface.outline(0xFF1E222A)));
        mainPanel.padding(Insets.of(8));

        // 1. TOP HEADER BAR: pops out with elevated dark gray surface
        StackLayout header = Containers.stack(Sizing.fill(100), Sizing.fixed(26));
        header.surface(Surface.flat(0xFF161920).and(Surface.outline(0xFF262C36)));
        header.padding(Insets.horizontal(8));

        // Center: Character name of the player who opened the menu (subtle, non-contrasting blue)
        PlayerCharacterData myData = getMyPlayerData();
        String myCharName = "";
        if (myData != null && myData.hasActiveCharacter() && myData.getActiveCharacter().getName() != null && !myData.getActiveCharacter().getName().isEmpty()) {
            myCharName = myData.getActiveCharacter().getName();
        } else if (Minecraft.getInstance().player != null) {
            myCharName = Minecraft.getInstance().player.getGameProfile().getName();
        }

        if (!myCharName.isEmpty()) {
            FlowLayout centerRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
            centerRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            LabelComponent charLabel = Components.label(Component.literal(myCharName));
            charLabel.color(Color.ofRgb(0xFF6B8A9E));
            centerRow.child(charLabel);
            header.child(centerRow);
        }

        // Header controls (Title on left, State Brands & Close on right)
        FlowLayout headerBar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
        headerBar.verticalAlignment(VerticalAlignment.CENTER);

        // Logo / Title - Standard White, no bold
        LabelComponent titleLabel = Components.label(Component.literal("OneHouse3").withStyle(ChatFormatting.WHITE));
        titleLabel.margins(Insets.right(12));
        headerBar.child(titleLabel);

        // Cycle Timers (Week & Day)
        cycleTimerLabel = Components.label(Component.literal(formatCycleTimers()).withColor(0xFFB0B7C3));
        cycleTimerLabel.tooltip(Component.literal("Игровой календарь OneHouse3:\n• Неделя: 140 минут (сброс профессий и недельные грамоты)\n• День: 20 минут (налоговый цикл списания $PR)").withStyle(ChatFormatting.GRAY));
        headerBar.child(cycleTimerLabel);

        // Spacer to push right-hand widgets
        headerBar.child(Components.spacer());

        // Player Money / Sprota Pill (Currency display without deposit/withdraw buttons)
        double myBalance = ClientBankCache.getBalance();
        int inventorySprota = getInventorySprota();
        String formattedMoney = (myBalance % 1 == 0 ? String.format(java.util.Locale.ROOT, "%,d", (long) myBalance).replace(',', ' ') : String.format(java.util.Locale.ROOT, "%,.2f", myBalance)) + " " + SprotaItem.CURRENCY_SYMBOL;

        FlowLayout balancePill = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(20));
        balancePill.surface(Surface.flat(0xFF1F232D).and(Surface.outline(0xFF323946)));
        balancePill.padding(Insets.horizontal(8));
        balancePill.verticalAlignment(VerticalAlignment.CENTER);
        balancePill.gap(4);
        balancePill.margins(Insets.right(6));
        balancePill.tooltip(Component.literal("Банковский счёт: " + formattedMoney + "\nВ инвентаре: " + inventorySprota + " шт. " + SprotaItem.CURRENCY_SYMBOL).withStyle(ChatFormatting.GRAY));

        ItemComponent currencyIcon = Components.item(new ItemStack(com.oneshop.item.ModItems.SPROTA.get()));
        currencyIcon.sizing(Sizing.fixed(12), Sizing.fixed(12));
        balancePill.child(currencyIcon);

        balancePill.child(Components.label(Component.literal(formattedMoney).withStyle(ChatFormatting.WHITE)));

        headerBar.child(balancePill);

        // State Brands Pill (Currency display with neutral gray + / - buttons)
        int balance = getMyStateBrands();
        int inventoryBrands = getInventoryStateBrands();

        FlowLayout brandsPill = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(20));
        brandsPill.surface(Surface.flat(0xFF1F232D).and(Surface.outline(0xFF323946)));
        brandsPill.padding(Insets.horizontal(6));
        brandsPill.verticalAlignment(VerticalAlignment.CENTER);
        brandsPill.gap(4);
        brandsPill.tooltip(Component.literal("Гос. Марки на счету: " + balance + " шт.\nВ инвентаре: " + inventoryBrands + " шт.").withStyle(ChatFormatting.GRAY));

        brandsPill.child(new TextureIconComponent(ICON_STATE_BRAND, 12));
        brandsPill.child(Components.label(Component.literal(String.valueOf(balance)).withStyle(ChatFormatting.WHITE)));

        // Minus button (Withdraw)
        FlowLayout minusBtn = createIconButton("-", 14, 14, 0, 0xFF2A2E38, 0xFF3A404E, 0xFF4A5263, () -> openBrandModal(false));
        minusBtn.tooltip(Component.literal("Снять Гос. Марки со счета"));
        brandsPill.child(minusBtn);

        // Plus button (Deposit)
        FlowLayout plusBtn = createIconButton("+", 14, 14, 0, 0xFF2A2E38, 0xFF3A404E, 0xFF4A5263, () -> openBrandModal(true));
        plusBtn.tooltip(Component.literal("Внести Гос. Марки на счет"));
        brandsPill.child(plusBtn);

        headerBar.child(brandsPill);

        // Close Button [✕] - shifted right by 2px for centering
        FlowLayout closeBtn = createIconButton("✕", 16, 16, 2, 0xFF2A2E38, 0xFF3E2226, 0xFF5A3036, this::onClose);
        closeBtn.margins(Insets.left(6));
        headerBar.child(closeBtn);

        header.child(headerBar);
        mainPanel.child(header);

        // Header Divider
        mainPanel.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF1E222A)).margins(Insets.vertical(6)));

        // 2. MAIN BODY ROW (Left Sidebar + Vertical Divider + Right Content)
        FlowLayout bodyRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.expand());

        // LEFT SIDEBAR TABS: standing out on dark background
        FlowLayout sidebar = Containers.verticalFlow(Sizing.fixed(122), Sizing.fill(100));
        sidebar.surface(Surface.flat(0xFF13151A).and(Surface.outline(0xFF22262E)));
        sidebar.padding(Insets.of(4));
        sidebar.gap(4);

        for (Tab tab : Tab.values()) {
            boolean isActive = tab == this.currentTab;
            FlowLayout tabBtn = createTabButton(tab, isActive);
            sidebar.child(tabBtn);
        }
        bodyRow.child(sidebar);

        // Vertical Separator
        bodyRow.child(Components.box(Sizing.fixed(1), Sizing.fill(100)).color(Color.ofRgb(0xFF1E222A)).margins(Insets.horizontal(4)));

        // RIGHT CONTENT PANEL: expand to fit remaining horizontal space
        FlowLayout content = Containers.verticalFlow(Sizing.expand(), Sizing.fill(100));
        content.surface(Surface.flat(0xFF111317).and(Surface.outline(0xFF1E222A)));
        content.padding(Insets.of(6));

        switch (this.currentTab) {
            case POPULATION -> buildPopulationTab(content);
            case STATE -> buildStateTab(content);
            case PROFESSIONS -> buildProfessionsTab(content);
            case TREASURY -> buildTreasuryTab(content);
            case LETTERS -> buildLettersTab(content);
            case LAWS -> buildLawsTab(content);
            case PROGRESS -> buildWorldTab(content);
            default -> buildPlaceholderTab(content, this.currentTab.getTitle());
        }
        bodyRow.child(content);

        mainPanel.child(bodyRow);
        baseWrapper.child(mainPanel);
        rootComponent.child(baseWrapper);
    }

    private FlowLayout createTabButton(Tab tab, boolean isActive) {
        FlowLayout btn = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        btn.verticalAlignment(VerticalAlignment.CENTER);
        btn.padding(Insets.horizontal(4));
        btn.gap(5);
        btn.cursorStyle(CursorStyle.HAND);

        int bgColor = isActive ? 0xFF282D37 : 0xFF181A20;
        int hoverColor = isActive ? 0xFF303642 : 0xFF222630;
        int borderColor = isActive ? 0xFF505A6D : 0xFF22262E;

        btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor)));

        // Square icon placeholder reflecting tab theme
        FlowLayout iconBox = Containers.horizontalFlow(Sizing.fixed(18), Sizing.fixed(18));
        iconBox.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        iconBox.surface(Surface.flat(isActive ? 0xFF1E232B : 0xFF13151A).and(Surface.outline(isActive ? 0xFF3D4658 : 0xFF22262E)));

        ItemComponent icon = Components.item(tab.getIconStack());
        icon.sizing(Sizing.fixed(16), Sizing.fixed(16));
        iconBox.child(icon);
        btn.child(iconBox);

        ChatFormatting textColor = isActive ? ChatFormatting.WHITE : ChatFormatting.GRAY;
        LabelComponent label = Components.label(Component.literal(tab.getTitle()).withStyle(textColor));
        btn.child(label);

        if (tab == Tab.LETTERS && hasUnreadLetters()) {
            FlowLayout badge = new FlowLayout(Sizing.fixed(10), Sizing.fixed(10), FlowLayout.Algorithm.HORIZONTAL) {
                @Override
                public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
                    RenderSystem.disableDepthTest();
                    context.pose().pushPose();
                    context.pose().translate(0, 0, 200);
                    super.draw(context, mouseX, mouseY, partialTicks, delta);
                    context.pose().popPose();
                    RenderSystem.enableDepthTest();
                }
            };
            badge.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            badge.surface(Surface.flat(0xFFB71C1C).and(Surface.outline(0xFFFF5252)));
            badge.positioning(Positioning.absolute(1, 1));
            badge.zIndex(300);
            LabelComponent mark = Components.label(Component.literal("!").withStyle(style -> style.withColor(0xFFFFFFFF)));
            badge.child(mark);
            badge.tooltip(Component.literal("Новые входящие письма").withStyle(ChatFormatting.RED));
            btn.child(badge);
        }

        btn.mouseEnter().subscribe(() -> {
            if (!isActive) {
                btn.surface(Surface.flat(hoverColor).and(Surface.outline(0xFF353B47)));
                iconBox.surface(Surface.flat(0xFF181C23).and(Surface.outline(0xFF353B47)));
            }
        });
        btn.mouseLeave().subscribe(() -> {
            btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor)));
            iconBox.surface(Surface.flat(isActive ? 0xFF1E232B : 0xFF13151A).and(Surface.outline(isActive ? 0xFF3D4658 : 0xFF22262E)));
        });
        btn.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                setTab(tab);
                return true;
            }
            return false;
        });

        return btn;
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

    private FlowLayout createIconButton(String symbol, int width, int height, int xOffset, int bgColor, int hoverColor, int borderColor, Runnable onClick) {
        FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(height));
        btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor)));
        btn.cursorStyle(CursorStyle.HAND);

        LabelComponent label = Components.label(Component.literal(symbol).withStyle(ChatFormatting.WHITE));
        label.horizontalTextAlignment(HorizontalAlignment.CENTER);
        label.verticalTextAlignment(VerticalAlignment.CENTER);
        if (xOffset != 0) {
            label.margins(Insets.left(xOffset));
        }
        btn.child(label);

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

    /**
     * Creates an overlay container with depth testing disabled so it guarantees rendering in front of all background elements.
     */
    private OverlayContainer<FlowLayout> createOverlay(FlowLayout modal) {
        OverlayContainer<FlowLayout> overlay = new OverlayContainer<>(modal) {
            private boolean isRemoving = false;

            @Override
            public void remove() {
                if (isRemoving) return;
                isRemoving = true;
                Minecraft.getInstance().tell(() -> {
                    if (hasParent()) {
                        ParentComponent p = parent();
                        if (p != null) {
                            p.removeChild(this);
                        }
                    }
                });
            }

            @Override
            public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
                RenderSystem.disableDepthTest();
                super.draw(context, mouseX, mouseY, partialTicks, delta);
                RenderSystem.enableDepthTest();
            }
        };
        overlay.closeOnClick(true);
        overlay.zIndex(500);
        return overlay;
    }

    // =========================================================================
    // TAB 1: POPULATION (НАСЕЛЕНИЕ)
    // =========================================================================
    private void buildPopulationTab(FlowLayout parent) {
        FlowLayout columns = Containers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
        columns.gap(6);

        List<LivingEntry> livingList = new ArrayList<>();
        List<DeadEntry> buriedList = new ArrayList<>();
        List<DeadEntry> unburiedList = new ArrayList<>();

        if (worldData != null) {
            for (PlayerCharacterData pd : worldData.getAllPlayerData().values()) {
                String playerName = pd.getLastKnownPlayerName();
                if (playerName == null || playerName.isEmpty()) {
                    playerName = "Игрок";
                }
                if (pd.hasActiveCharacter()) {
                    livingList.add(new LivingEntry(pd.getActiveCharacter(), pd));
                }
                for (DeadCharacter dead : pd.getCharacterHistory()) {
                    if (dead.isBuried()) {
                        buriedList.add(new DeadEntry(dead, playerName, pd.getPlayerUuid()));
                    } else {
                        unburiedList.add(new DeadEntry(dead, playerName, pd.getPlayerUuid()));
                    }
                }
            }
        }

        // Sort buried ancestors by newest death date first (descending)
        buriedList.sort((a, b) -> Long.compare(b.dead().getDeathDate(), a.dead().getDeathDate()));

        long currentGameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;

        // --- LEFT COLUMN: Living Residents + Buried Ancestors (58% width) ---
        FlowLayout leftFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        leftFlow.padding(Insets.of(0, 16, 0, 6));
        leftFlow.gap(6);

        // Section A: Жители
        leftFlow.child(Components.label(Component.literal("▪ Жители [" + livingList.size() + "]").withStyle(ChatFormatting.WHITE)));
        leftFlow.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF262C36)).margins(Insets.bottom(2)));

        if (livingList.isEmpty()) {
            leftFlow.child(Components.label(Component.literal("Нет живых персонажей").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            for (LivingEntry entry : livingList) {
                ActiveCharacter active = entry.active();
                PlayerCharacterData pd = entry.playerData();

                FlowLayout card = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(42));
                // Living cards always framed in subtle blue
                int borderColor = 0xFF2E485C;
                int hoverBorder = 0xFF3D607B;
                card.surface(Surface.flat(0xFF181B22).and(Surface.outline(borderColor)));
                card.padding(Insets.of(4));
                card.verticalAlignment(VerticalAlignment.CENTER);
                card.cursorStyle(CursorStyle.HAND);
                card.tooltip(Component.literal("Нажмите, чтобы открыть досье персонажа").withStyle(ChatFormatting.GRAY));

                // Hover styling
                card.mouseEnter().subscribe(() -> card.surface(Surface.flat(0xFF222630).and(Surface.outline(hoverBorder))));
                card.mouseLeave().subscribe(() -> card.surface(Surface.flat(0xFF181B22).and(Surface.outline(borderColor))));

                // Click opens dossier modal
                card.mouseDown().subscribe((mouseX, mouseY, button) -> {
                    if (button == 0) {
                        openCharacterDetailModal(entry);
                        return true;
                    }
                    return false;
                });

                // Left: Head Avatar (clean, NO badge lamp on head)
                card.child(new PlayerAvatarComponent(pd.getPlayerUuid(), 32));

                // Middle: Info (content sizing, does not push out the lamp!)
                FlowLayout info = Containers.verticalFlow(Sizing.content(), Sizing.content());
                info.margins(Insets.left(6));
                info.gap(1);

                FlowLayout nameRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
                nameRow.verticalAlignment(VerticalAlignment.CENTER);
                nameRow.gap(4);

                Component nameComp = Component.literal(active.getName()).withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(" [" + pd.getLastKnownPlayerName() + "]").withStyle(ChatFormatting.GRAY));
                nameRow.child(Components.label(nameComp));

                if (worldData != null && worldData.isKnyaz(pd.getPlayerUuid())) {
                    TextureIconComponent crownIcon = new TextureIconComponent(ICON_MONARCH_CROWN, 12);
                    crownIcon.tooltip(Component.literal("Князь").withStyle(ChatFormatting.GOLD));
                    nameRow.child(crownIcon);
                }
                info.child(nameRow);

                String fact = pd.getFaction().isEmpty() ? "Не состоит" : pd.getFaction();
                info.child(Components.label(Component.literal("Фракция: " + fact).withStyle(ChatFormatting.GRAY)));

                long days = active.getDaysAlive(currentGameTime);
                String lifeStr = "Время жизни: " + days + " дн. (" + active.getAge() + " л.)";
                if (!active.getRace().isEmpty()) {
                    lifeStr += " • " + active.getRace();
                }
                info.child(Components.label(Component.literal(lifeStr).withStyle(ChatFormatting.DARK_GRAY)));

                card.child(info);

                // Spacer pushing right lamp
                card.child(Components.spacer());

                // Right: 20x20 Burden Lamp (sprite only, dimmed when inactive)
                BurdenLampComponent burdenLamp = new BurdenLampComponent(active.isStatusBurden(), 20);
                burdenLamp.margins(Insets.right(8));
                card.child(burdenLamp);

                leftFlow.child(card);
            }
        }

        // Section B: Упокоенные предки (Аналогичные кнопки с открытием досье!)
        leftFlow.child(Components.label(Component.literal("▪ Упокоенные предки [" + buriedList.size() + "]").withStyle(ChatFormatting.GRAY)).margins(Insets.top(6)));
        leftFlow.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF22262E)).margins(Insets.bottom(2)));

        if (buriedList.isEmpty()) {
            leftFlow.child(Components.label(Component.literal("Нет упокоенных предков").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            for (DeadEntry entry : buriedList) {
                DeadCharacter dead = entry.dead();

                FlowLayout card = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(42));
                card.surface(Surface.flat(0xFF15171C).and(Surface.outline(0xFF242832)));
                card.padding(Insets.of(4));
                card.verticalAlignment(VerticalAlignment.CENTER);
                card.cursorStyle(CursorStyle.HAND);
                card.tooltip(Component.literal("Нажмите, чтобы открыть досье персонажа").withStyle(ChatFormatting.GRAY));

                card.mouseEnter().subscribe(() -> card.surface(Surface.flat(0xFF1C2028).and(Surface.outline(0xFF383F4E))));
                card.mouseLeave().subscribe(() -> card.surface(Surface.flat(0xFF15171C).and(Surface.outline(0xFF242832))));

                card.mouseDown().subscribe((mouseX, mouseY, button) -> {
                    if (button == 0) {
                        openDeadCharacterDetailModal(entry);
                        return true;
                    }
                    return false;
                });

                // Avatar (dimmed 30% for buried ancestors)
                card.child(new PlayerAvatarComponent(entry.playerUuid(), 32, 0.70f));

                // Middle Info
                FlowLayout info = Containers.verticalFlow(Sizing.content(), Sizing.content());
                info.margins(Insets.left(6));
                info.gap(1);

                Component titleComp = Component.literal(dead.getName() + " (" + dead.getAge() + " л.)").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(" [" + entry.playerName() + "]").withStyle(ChatFormatting.DARK_GRAY));
                if (!dead.getRace().isEmpty()) {
                    titleComp = titleComp.copy().append(Component.literal(" • " + dead.getRace()).withStyle(ChatFormatting.DARK_GRAY));
                }
                info.child(Components.label(titleComp));

                String factStr = dead.getFaction().isEmpty() ? "Не указана" : dead.getFaction();
                info.child(Components.label(Component.literal("Фракция: " + factStr).withStyle(ChatFormatting.DARK_GRAY)));
                info.child(Components.label(Component.literal("Причина: " + dead.getDeathCause()).withStyle(ChatFormatting.DARK_GRAY)));

                card.child(info);

                card.child(Components.spacer());

                // Status indicator
                card.child(Components.label(Component.literal("✔").withStyle(ChatFormatting.GRAY)).margins(Insets.right(6)));

                leftFlow.child(card);
            }
        }

        ScrollContainer<FlowLayout> leftScroll = Containers.verticalScroll(Sizing.fill(58), Sizing.fill(100), leftFlow);
        leftScroll.scrollbar(DARK_SCROLLBAR);
        leftScroll.scrollbarThiccness(4);
        columns.child(leftScroll);

        // --- RIGHT COLUMN: Неупокоенные (takes remaining space <= 1/3 of menu) ---
        FlowLayout rightFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rightFlow.padding(Insets.of(0, 16, 4, 10));
        rightFlow.gap(6);

        // Header with remains icon
        FlowLayout unburiedHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(16));
        unburiedHeader.verticalAlignment(VerticalAlignment.CENTER);
        unburiedHeader.gap(4);
        unburiedHeader.child(new TextureIconComponent(ICON_REMAINS, 14));
        unburiedHeader.child(Components.label(Component.literal("Неупокоенные").withStyle(ChatFormatting.WHITE)));
        unburiedHeader.child(Components.label(Component.literal("[" + unburiedList.size() + "]").withStyle(ChatFormatting.GRAY)));

        rightFlow.child(unburiedHeader);
        // Long dividing line aligned with cards
        rightFlow.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF382428)).margins(Insets.bottom(2)));

        if (unburiedList.isEmpty()) {
            rightFlow.child(Components.label(Component.literal("✔ Все предки упокоены").withStyle(ChatFormatting.GRAY)));
        } else {
            int uiWidth = (int) (this.width / UI_SCALE);
            int totalColumnsWidth = Math.max(200, (int) (uiWidth * 0.92) - 159);
            int rightColWidth = Math.max(120, (int) (totalColumnsWidth * 0.42) - 6);
            int unburiedCauseMaxWidth = Math.max(80, rightColWidth - 78);

            for (DeadEntry entry : unburiedList) {
                DeadCharacter dead = entry.dead();

                FlowLayout card = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
                int borderColor = 0xFF382428;
                int hoverBorder = 0xFF4C3036;
                card.surface(Surface.flat(0xFF1E1719).and(Surface.outline(borderColor)));
                card.padding(Insets.of(5));
                card.verticalAlignment(VerticalAlignment.CENTER);
                card.cursorStyle(CursorStyle.HAND);
                card.tooltip(Component.literal("Нажмите, чтобы открыть досье персонажа").withStyle(ChatFormatting.GRAY));

                card.mouseEnter().subscribe(() -> card.surface(Surface.flat(0xFF281E21).and(Surface.outline(hoverBorder))));
                card.mouseLeave().subscribe(() -> card.surface(Surface.flat(0xFF1E1719).and(Surface.outline(borderColor))));

                card.mouseDown().subscribe((mouseX, mouseY, button) -> {
                    if (button == 0) {
                        openDeadCharacterDetailModal(entry);
                        return true;
                    }
                    return false;
                });

                // Avatar
                card.child(new PlayerAvatarComponent(entry.playerUuid(), 32));

                // Middle Info: strictly only name, death date, and death cause
                FlowLayout info = Containers.verticalFlow(Sizing.expand(), Sizing.content());
                info.margins(Insets.left(6));
                info.gap(1);

                info.child(Components.label(Component.literal(dead.getName()).withStyle(ChatFormatting.WHITE)));
                info.child(Components.label(Component.literal("Дата: " + dead.getFormattedDeathDate()).withStyle(ChatFormatting.GRAY)));

                LabelComponent causeLabel = Components.label(Component.literal("Причина: " + dead.getDeathCause()).withStyle(ChatFormatting.DARK_GRAY));
                causeLabel.maxWidth(unburiedCauseMaxWidth);
                info.child(causeLabel);

                card.child(info);

                rightFlow.child(card);
            }
        }

        ScrollContainer<FlowLayout> rightScroll = Containers.verticalScroll(Sizing.expand(), Sizing.fill(100), rightFlow);
        rightScroll.scrollbar(DARK_SCROLLBAR);
        rightScroll.scrollbarThiccness(4);
        columns.child(rightScroll);

        parent.child(columns);
    }

    // =========================================================================
    // TAB 2: PROFESSIONS (ПРОФЕССИИ)
    // =========================================================================
    private void buildProfessionsTab(FlowLayout parent) {
        // Top navigation bar for categories: centered, neutral buttons
        FlowLayout topNav = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(26));
        topNav.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        topNav.gap(8);
        topNav.margins(Insets.bottom(4));

        boolean isMyProfessions = this.currentProfessionSubCategory == ProfessionSubCategory.MY_PROFESSIONS;
        topNav.child(createProfessionSubTabButton(
                "Ваши профессии",
                isMyProfessions,
                () -> {
                    this.currentProfessionSubCategory = ProfessionSubCategory.MY_PROFESSIONS;
                    rebuildUI();
                }
        ));

        topNav.child(createProfessionSubTabButton(
                "Каталог профессий",
                !isMyProfessions,
                () -> {
                    this.currentProfessionSubCategory = ProfessionSubCategory.CATALOG;
                    rebuildUI();
                }
        ));

        parent.child(topNav);
        parent.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF1E222A)).margins(Insets.bottom(6)));

        if (isMyProfessions) {
            buildMyProfessionsCategory(parent);
        } else {
            buildProfessionCatalogCategory(parent);
        }
    }

    private FlowLayout createProfessionSubTabButton(String title, boolean isActive, Runnable onClick) {
        FlowLayout btn = Containers.horizontalFlow(Sizing.fixed(135), Sizing.fixed(22));
        btn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        btn.cursorStyle(CursorStyle.HAND);

        int bgColor = isActive ? 0xFF2A2F3A : 0xFF161820;
        int hoverColor = isActive ? 0xFF303642 : 0xFF222630;
        int borderColor = isActive ? 0xFF505A6D : 0xFF262A34;
        int hoverBorder = isActive ? 0xFF657288 : 0xFF3D4658;

        btn.surface(Surface.flat(bgColor).and(Surface.outline(borderColor)));

        Component textComp = isActive
                ? Component.literal(title).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                : Component.literal(title).withStyle(ChatFormatting.GRAY);
        btn.child(Components.label(textComp));

        btn.mouseEnter().subscribe(() -> btn.surface(Surface.flat(hoverColor).and(Surface.outline(hoverBorder))));
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

    private void buildMyProfessionsCategory(FlowLayout parent) {
        List<Profession> myOccupied = getMyOccupiedProfessions();

        if (myOccupied.isEmpty()) {
            FlowLayout emptyBox = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
            emptyBox.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            emptyBox.gap(8);

            ItemComponent emptyIcon = Components.item(new ItemStack(Items.IRON_PICKAXE));
            emptyIcon.sizing(Sizing.fixed(32), Sizing.fixed(32));
            emptyBox.child(emptyIcon);

            emptyBox.child(Components.label(Component.literal("У вашего персонажа пока нет выбранных профессий").withStyle(ChatFormatting.WHITE)));
            emptyBox.child(Components.label(Component.literal("Выберите специальность в каталоге, чтобы развивать мастерство и ремесло").withStyle(ChatFormatting.DARK_GRAY)));

            FlowLayout goCatalogBtn = createFlatButton(
                    Component.literal("Перейти в каталог профессий").withStyle(ChatFormatting.WHITE),
                    180, 22,
                    0xFF222630, 0xFF2C3240, 0xFF3D4658,
                    () -> {
                        this.currentProfessionSubCategory = ProfessionSubCategory.CATALOG;
                        rebuildUI();
                    }
            );
            goCatalogBtn.margins(Insets.top(4));
            emptyBox.child(goCatalogBtn);

            parent.child(emptyBox);
            return;
        }

        // Horizontal row: cards are centered up to 4
        FlowLayout cardsRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        cardsRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        cardsRow.gap(12);
        cardsRow.padding(Insets.horizontal(8));

        for (Profession prof : myOccupied) {
            cardsRow.child(createOccupiedProfessionCard(prof));
        }

        parent.child(cardsRow);
    }

    private void buildProfessionCatalogCategory(FlowLayout parent) {
        // Top status sub-header with profession slot indicators and weekly grants
        FlowLayout subHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(26));
        subHeader.verticalAlignment(VerticalAlignment.CENTER);
        subHeader.surface(Surface.flat(0xFF161920).and(Surface.outline(0xFF262C36)));
        subHeader.padding(Insets.horizontal(8));
        subHeader.gap(6);

        subHeader.child(Components.label(Component.literal("Каталог профессий").withStyle(ChatFormatting.WHITE)));
        subHeader.child(Components.spacer());

        // Slot indicators panel (Main = Green, Secondary = Orange) styled as a distinct widget pill
        FlowLayout slotIndicators = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(20));
        slotIndicators.surface(Surface.flat(0xFF1F232D).and(Surface.outline(0xFF323946)));
        slotIndicators.padding(Insets.horizontal(6));
        slotIndicators.verticalAlignment(VerticalAlignment.CENTER);
        slotIndicators.gap(5);

        PlayerCharacterData myData = getMyPlayerData();
        int totalMainSlots = myData != null ? myData.getMainProfessionSlots() : 1;
        int totalSecSlots = myData != null ? myData.getSecondaryProfessionSlots() : 2;

        int mainUsed = 0;
        int secUsed = 0;
        if (myData != null) {
            for (String profTitle : myData.getProfessions()) {
                Profession registered = ProfessionRegistry.getByIdOrTitle(profTitle);
                if (registered != null) {
                    if (registered.getType() == ProfessionCategory.MAIN) {
                        mainUsed++;
                    } else if (registered.getType() == ProfessionCategory.SECONDARY) {
                        secUsed++;
                    }
                }
            }
        }
        int availableMain = Math.max(0, totalMainSlots - mainUsed);
        int availableSec = Math.max(0, totalSecSlots - secUsed);

        Component mainTip = Component.literal("Количество доступных слотов основных профессий: " + availableMain);
        Component secTip = Component.literal("Количество доступных слотов дополнительных профессий: " + availableSec);

        LabelComponent mainLabel = Components.label(Component.literal("Осн:").withStyle(ChatFormatting.GREEN));
        mainLabel.tooltip(mainTip);
        slotIndicators.child(mainLabel);
        for (int i = 0; i < totalMainSlots; i++) {
            boolean free = i < availableMain;
            slotIndicators.child(SlotLampComponent.primaryFree(free, 8, mainTip));
        }

        slotIndicators.child(Components.box(Sizing.fixed(1), Sizing.fixed(12)).color(Color.ofRgb(0xFF323946)).margins(Insets.horizontal(2)));

        LabelComponent secLabel = Components.label(Component.literal("Доп:").withStyle(style -> style.withColor(0xFFFFA500)));
        secLabel.tooltip(secTip);
        slotIndicators.child(secLabel);
        for (int i = 0; i < totalSecSlots; i++) {
            boolean free = i < availableSec;
            slotIndicators.child(SlotLampComponent.secondaryFree(free, 8, secTip));
        }

        subHeader.child(slotIndicators);

        // Weekly grants pill to the right of slot indicators
        int weeklyGrantsCount = getMyOccupiedProfessions().size();
        FlowLayout weeklyPill = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(20));
        weeklyPill.surface(Surface.flat(0xFF1F232D).and(Surface.outline(0xFF323946)));
        weeklyPill.padding(Insets.horizontal(6));
        weeklyPill.verticalAlignment(VerticalAlignment.CENTER);
        weeklyPill.gap(4);

        weeklyPill.child(Components.label(Component.literal("Недельные грамоты:").withStyle(ChatFormatting.GRAY)));
        weeklyPill.child(Components.label(Component.literal(String.valueOf(weeklyGrantsCount)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        weeklyPill.child(new TextureIconComponent(ICON_STATE_BRAND, 12));
        weeklyPill.tooltip(Component.literal("Недельные грамоты: раз в неделю (140 мин.) на ваш счёт приходят Гос. Марки ровно в размере числа занятых профессий (" + weeklyGrantsCount + " шт.).").withStyle(ChatFormatting.GRAY));

        subHeader.child(weeklyPill);
        parent.child(subHeader);

        // Scrollable content with 3 separated categories
        FlowLayout scrollContent = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        scrollContent.padding(Insets.of(6, 16, 16, 8));
        scrollContent.gap(10);

        List<Profession> stateProfs = new ArrayList<>();
        List<Profession> mainProfs = new ArrayList<>();
        List<Profession> secProfs = new ArrayList<>();

        for (Profession prof : ProfessionRegistry.getAll()) {
            if (prof.getType() == ProfessionCategory.STATE) {
                stateProfs.add(prof);
            } else if (prof.getType() == ProfessionCategory.MAIN) {
                mainProfs.add(prof);
            } else {
                secProfs.add(prof);
            }
        }

        // 1) Государственные должности - красный (0xFFE53935)
        buildProfessionCategorySection(scrollContent, "Государственные должности", 0xFFE53935, stateProfs);

        // 2) Основные профессии - зелёный (0xFF4CAF50)
        buildProfessionCategorySection(scrollContent, "Основные профессии", 0xFF4CAF50, mainProfs);

        // 3) Дополнительные профессии - оранжевый (0xFFFFA500)
        buildProfessionCategorySection(scrollContent, "Дополнительные профессии", 0xFFFFA500, secProfs);

        ScrollContainer<FlowLayout> profScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), scrollContent);
        profScroll.scrollbar(DARK_SCROLLBAR);
        profScroll.scrollbarThiccness(4);
        profScroll.margins(Insets.top(4));
        parent.child(profScroll);
    }

    private void buildProfessionCategorySection(FlowLayout parent, String title, int colorRgb, List<Profession> profs) {
        FlowLayout section = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        section.gap(4);
        section.padding(Insets.bottom(8));

        // Category Header
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(14));
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.child(Components.label(Component.literal("▪ " + title + " [" + profs.size() + "]").withStyle(style -> style.withColor(colorRgb))));
        section.child(header);

        // Long horizontal dividing line spanning 100% width with category color
        section.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(colorRgb)).margins(Insets.bottom(4)));

        // Square cards grid (strictly max 5 per row)
        if (profs.isEmpty()) {
            section.child(Components.label(Component.literal("Нет доступных профессий").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            FlowLayout grid = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            grid.gap(6);
            for (int i = 0; i < profs.size(); i += 6) {
                FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
                row.gap(6);
                row.padding(Insets.of(1, 2, 2, 0));
                int end = Math.min(i + 6, profs.size());
                for (int j = i; j < end; j++) {
                    Profession prof = profs.get(j);
                    row.child(createProfessionCard(prof));
                }
                grid.child(row);
            }
            section.child(grid);
        }

        parent.child(section);
    }

    private FlowLayout createProfessionCard(Profession prof) {
        List<LivingEntry> occupants = getProfessionOccupants(prof);
        int maxOccupants = prof.getMaxOccupants();
        int occupiedCount = occupants.size();
        int freeSlots = Math.max(0, maxOccupants - occupiedCount);
        boolean isFull = occupiedCount >= maxOccupants;

        ProfessionCategory cat = prof.getType();
        int catColor = cat.getColor();

        int borderColor = isFull ? 0xFF353B46 : (cat == ProfessionCategory.STATE ? 0xFF8B2525 : (cat == ProfessionCategory.MAIN ? 0xFF2E7D32 : 0xFFB36B00));
        int hoverBorder = isFull ? 0xFF4A5260 : catColor;
        int bgColor = isFull ? 0xFF14171E : (cat == ProfessionCategory.STATE ? 0xFF241416 : (cat == ProfessionCategory.MAIN ? 0xFF122216 : 0xFF241A12));
        int hoverBg = isFull ? 0xFF1A1E26 : (cat == ProfessionCategory.STATE ? 0xFF331C1F : (cat == ProfessionCategory.MAIN ? 0xFF182E1E : 0xFF362416));

        // Square card: 60px wide, 66px high to fit larger icon and scaled font cleanly
        FlowLayout card = Containers.verticalFlow(Sizing.fixed(60), Sizing.fixed(66));
        Surface normalSurface = (ctx, comp) -> {
            int x = comp.x();
            int y = comp.y();
            int w = comp.width();
            int h = comp.height();
            ctx.fill(x, y, x + w, y + h, bgColor);
            ctx.fill(x, y, x + w, y + 1, borderColor);
            ctx.fill(x, y + h - 1, x + w, y + h, borderColor);
            ctx.fill(x, y + 1, x + 1, y + h - 1, borderColor);
            ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, borderColor);
        };
        Surface hoverSurface = (ctx, comp) -> {
            int x = comp.x();
            int y = comp.y();
            int w = comp.width();
            int h = comp.height();
            ctx.fill(x, y, x + w, y + h, hoverBg);
            ctx.fill(x, y, x + w, y + 1, hoverBorder);
            ctx.fill(x, y + h - 1, x + w, y + h, hoverBorder);
            ctx.fill(x, y + 1, x + 1, y + h - 1, hoverBorder);
            ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, hoverBorder);
        };
        card.surface(normalSurface);
        card.padding(Insets.of(2, 2, 2, 2));
        card.horizontalAlignment(HorizontalAlignment.CENTER);
        card.cursorStyle(CursorStyle.HAND);

        // Tooltip
        card.tooltip(Component.literal(prof.getTitle())
                .append(Component.literal("\nКатегория: " + cat.getDisplayName()).withStyle(style -> style.withColor(catColor)))
                .append(Component.literal("\nСвободных мест: " + freeSlots + "/" + maxOccupants).withStyle(freeSlots > 0 ? ChatFormatting.WHITE : ChatFormatting.RED))
                .append(Component.literal("\nНажмите, чтобы открыть подробности").withStyle(ChatFormatting.DARK_GRAY)));

        // Hover
        card.mouseEnter().subscribe(() -> card.surface(hoverSurface));
        card.mouseLeave().subscribe(() -> card.surface(normalSurface));

        // Click opens profession dialog
        card.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                openProfessionModal(prof);
                return true;
            }
            return false;
        });

        // 1. Icon (19x19, +15% larger)
        ItemComponent icon = Components.item(new ItemStack(prof.getIconItem()));
        icon.sizing(Sizing.fixed(19), Sizing.fixed(19));
        icon.margins(Insets.bottom(1));
        card.child(icon);

        // 2. Title: split multi-word or hyphenated titles into strictly 2 lines if needed, scaled to 0.83x (+15% larger)
        String rawTitle = prof.getTitle();
        FlowLayout titleCol = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        titleCol.horizontalAlignment(HorizontalAlignment.CENTER);
        titleCol.gap(1);
        int titleColor = isFull ? 0xFFAAAAAA : 0xFFFFFFFF;

        if (rawTitle.contains("-") && rawTitle.length() > 8) {
            String[] parts = rawTitle.split("-", 2);
            titleCol.child(new ScaledLabelComponent(Component.literal(parts[0] + "-"), 0.83f, titleColor, true));
            titleCol.child(new ScaledLabelComponent(Component.literal(parts[1]), 0.83f, titleColor, true));
        } else if (rawTitle.contains(" ") && rawTitle.length() > 8) {
            String[] parts = rawTitle.split(" ", 2);
            titleCol.child(new ScaledLabelComponent(Component.literal(parts[0]), 0.83f, titleColor, true));
            titleCol.child(new ScaledLabelComponent(Component.literal(parts[1]), 0.83f, titleColor, true));
        } else {
            titleCol.child(new ScaledLabelComponent(Component.literal(rawTitle), 0.83f, titleColor, true));
        }
        card.child(titleCol);

        card.child(Components.spacer());

        // 3. Bottom bar: indicators of free slots on left, numeric ID on right
        FlowLayout cardBottom = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(8));
        cardBottom.verticalAlignment(VerticalAlignment.CENTER);
        cardBottom.margins(Insets.bottom(1));

        FlowLayout occLamps = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        occLamps.verticalAlignment(VerticalAlignment.CENTER);
        occLamps.gap(2);
        occLamps.tooltip(Component.literal("Свободных мест: " + freeSlots + " из " + maxOccupants));

        for (int i = 0; i < maxOccupants; i++) {
            boolean isFree = i < freeSlots;
            int litBg = catColor;
            int litBorder = cat == ProfessionCategory.MAIN ? 0xFF66BB6A : (cat == ProfessionCategory.SECONDARY ? 0xFFFFB74D : 0xFFEF5350);
            int unlitBg = 0xFF181B22;
            int unlitBorder = 0xFF353B47;
            Component lampTip = Component.literal(isFree ? "Свободное место (" + freeSlots + " из " + maxOccupants + ")" : "Место занято");
            occLamps.child(new SlotLampComponent(isFree, litBg, unlitBg, isFree ? litBorder : unlitBorder, 5, lampTip));
        }
        cardBottom.child(occLamps);

        cardBottom.child(Components.spacer());

        LabelComponent idLabel = Components.label(Component.literal("#" + prof.getId()).withStyle(ChatFormatting.DARK_GRAY));
        idLabel.tooltip(Component.literal("ID профессии: #" + prof.getId()));
        cardBottom.child(idLabel);

        card.child(cardBottom);
        return card;
    }

    public record ProfessionAbility(ItemStack icon, String name, boolean isActive, String description) {}

    private List<ProfessionAbility> getProfessionAbilities(Profession prof) {
        if (prof == null) return Collections.emptyList();
        String title = prof.getTitle().toLowerCase();
        if (title.contains("фермер")) {
            return List.of(
                    new ProfessionAbility(new ItemStack(Items.WHEAT_SEEDS), "Песнь Плодородия", true, "Ускоренный рост зерновых и повышенный урожай."),
                    new ProfessionAbility(new ItemStack(Items.GOLDEN_CARROT), "Благодать Пастбищ", false, "Увеличенный приплод и здоровье животных.")
            );
        } else if (title.contains("повар")) {
            return List.of(
                    new ProfessionAbility(new ItemStack(Items.COOKED_BEEF), "Кулинарный Шедевр", false, "Приготовление питательных блюд с усиленным насыщением."),
                    new ProfessionAbility(new ItemStack(Items.GOLDEN_APPLE), "Праздничный Пир", true, "Блюда с эффектами регенерации и восстановления сил.")
            );
        } else if (title.contains("охотник")) {
            return List.of(
                    new ProfessionAbility(new ItemStack(Items.BOW), "Зоркий Стрелок", false, "Повышенный урон и точность критических выстрелов."),
                    new ProfessionAbility(new ItemStack(Items.FEATHER), "Следопыт", true, "Маскировка и быстрое выслеживание добычи.")
            );
        } else if (title.contains("казначей")) {
            return List.of(
                    new ProfessionAbility(new ItemStack(com.oneshop.item.ModItems.SPROTA.get()), "Государственная Пошлина", false, "Сбор налогов и контроль государственной казны."),
                    new ProfessionAbility(new ItemStack(Items.EMERALD), "Финансовый Аудит", true, "Регулирование торговых сделок и оборот Гос. Марок.")
            );
        } else if (title.contains("флорист")) {
            return List.of(
                    new ProfessionAbility(new ItemStack(Items.POPPY), "Цветущий Оазис", false, "Селекция и размножение редких декоративных цветов."),
                    new ProfessionAbility(new ItemStack(Items.BONE_MEAL), "Природный Экстракт", true, "Производство ярких натуральных красителей.")
            );
        } else if (title.contains("зельевар")) {
            return List.of(
                    new ProfessionAbility(new ItemStack(Items.BREWING_STAND), "Мастер Дистилляции", false, "Варка сложных зелий и целебных эликсиров."),
                    new ProfessionAbility(new ItemStack(Items.GLOWSTONE_DUST), "Катализатор Судьбы", true, "Увеличение концентрации и продолжительности зелий.")
            );
        }
        return List.of(
                new ProfessionAbility(new ItemStack(Items.EXPERIENCE_BOTTLE), "Мастерство Ремесла", false, "Особые профессиональные навыки.")
        );
    }

    private List<Profession> getMyOccupiedProfessions() {
        List<Profession> list = new ArrayList<>();
        PlayerCharacterData pd = getMyPlayerData();
        if (pd != null && pd.hasActiveCharacter()) {
            for (String userProf : pd.getProfessions()) {
                Profession registered = ProfessionRegistry.getByIdOrTitle(userProf);
                if (registered != null && !list.contains(registered)) {
                    list.add(registered);
                }
            }
        }
        list.sort(Comparator.comparingInt(p -> switch (p.getType()) {
            case STATE -> 0;
            case MAIN -> 1;
            case SECONDARY -> 2;
        }));
        return list;
    }

    private FlowLayout createOccupiedProfessionCard(Profession prof) {
        ProfessionCategory cat = prof.getType();
        int catColor = cat.getColor();

        // Color definitions for darkened base and smooth bright hover
        int baseBorder;
        int hoverBorder = catColor;
        int baseBg;
        int hoverBg;

        if (cat == ProfessionCategory.MAIN) {
            baseBorder = 0xFF1C4222;
            baseBg = 0xFF101712;
            hoverBg = 0xFF152618;
        } else if (cat == ProfessionCategory.SECONDARY) {
            baseBorder = 0xFF5C3B14;
            baseBg = 0xFF17130F;
            hoverBg = 0xFF281E13;
        } else {
            baseBorder = 0xFF581D21;
            baseBg = 0xFF161012;
            hoverBg = 0xFF261417;
        }

        // Rectangular vertical card: ~23% width (max 4 per row, centered), fixed 205px height
        FlowLayout card = Containers.verticalFlow(Sizing.fill(23), Sizing.fixed(205));
        card.padding(Insets.of(6, 6, 6, 6));
        card.horizontalAlignment(HorizontalAlignment.CENTER);
        card.cursorStyle(CursorStyle.HAND);

        // Smooth fade hover animation state
        float[] hoverProgress = new float[]{0.0f};
        float[] targetHover = new float[]{0.0f};
        long[] lastTime = new long[]{System.currentTimeMillis()};

        card.mouseEnter().subscribe(() -> targetHover[0] = 1.0f);
        card.mouseLeave().subscribe(() -> targetHover[0] = 0.0f);

        Surface smoothSurface = (ctx, comp) -> {
            long now = System.currentTimeMillis();
            float dt = Math.min(0.05f, (now - lastTime[0]) / 1000.0f);
            lastTime[0] = now;

            float speed = 8.0f;
            if (hoverProgress[0] < targetHover[0]) {
                hoverProgress[0] = Math.min(targetHover[0], hoverProgress[0] + speed * dt);
            } else if (hoverProgress[0] > targetHover[0]) {
                hoverProgress[0] = Math.max(targetHover[0], hoverProgress[0] - speed * dt);
            }

            float factor = hoverProgress[0];
            int curBg = interpolateColor(baseBg, hoverBg, factor);
            int curBorder = interpolateColor(baseBorder, hoverBorder, factor);

            int x = comp.x();
            int y = comp.y();
            int w = comp.width();
            int h = comp.height();

            ctx.fill(x, y, x + w, y + h, curBg);
            ctx.fill(x, y, x + w, y + 1, curBorder);
            ctx.fill(x, y + h - 1, x + w, y + h, curBorder);
            ctx.fill(x, y + 1, x + 1, y + h - 1, curBorder);
            ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, curBorder);
        };
        card.surface(smoothSurface);

        // Click card to open profession modal details
        card.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0) {
                openProfessionModal(prof);
                return true;
            }
            return false;
        });

        // 1. Top row: Leave button on top-right, title centered
        StackLayout topStack = Containers.stack(Sizing.fill(100), Sizing.content());

        // Centered Title Column
        FlowLayout titleCol = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        titleCol.horizontalAlignment(HorizontalAlignment.CENTER);
        titleCol.gap(1);

        String rawTitle = prof.getTitle();
        if (rawTitle.length() > 14 && rawTitle.contains("-")) {
            String[] parts = rawTitle.split("-", 2);
            titleCol.child(new ScaledLabelComponent(Component.literal(parts[0] + "-"), 0.85f, 0xFFFFFFFF, true));
            titleCol.child(new ScaledLabelComponent(Component.literal(parts[1]), 0.85f, 0xFFFFFFFF, true));
        } else if (rawTitle.length() > 14 && rawTitle.contains(" ")) {
            String[] parts = rawTitle.split(" ", 2);
            titleCol.child(new ScaledLabelComponent(Component.literal(parts[0]), 0.85f, 0xFFFFFFFF, true));
            titleCol.child(new ScaledLabelComponent(Component.literal(parts[1]), 0.85f, 0xFFFFFFFF, true));
        } else {
            titleCol.child(new ScaledLabelComponent(Component.literal(rawTitle), 0.90f, 0xFFFFFFFF, true));
        }

        // Category subtitle
        titleCol.child(new ScaledLabelComponent(Component.literal(cat.getDisplayName()), 0.70f, catColor, false));
        topStack.child(titleCol);

        // Top-right Leave Button (✕)
        PlayerCharacterData pd = getMyPlayerData();
        boolean onCooldown = false;
        String costTip = "Бесплатно";
        if (cat == ProfessionCategory.MAIN) {
            costTip = "500 $PR + 2 марки";
            onCooldown = pd != null && pd.hasLeftMainThisCycle();
        } else if (cat == ProfessionCategory.SECONDARY) {
            costTip = "250 $PR + 1 марка";
            onCooldown = pd != null && pd.hasLeftSecondaryThisCycle();
        }
        String fullTip = "Покинуть профессию «" + prof.getTitle() + "»\nЦена: " + costTip;
        if (onCooldown && pd != null) {
            long remMs = clientWeekTicksRemaining > 0 ? (clientWeekTicksRemaining * 50L) : pd.getLongCycleRemainingMs();
            long hrs = remMs / 3600000L;
            long mins = (remMs % 3600000L) / 60000L;
            fullTip += "\n⏳ Кулдаун длинного цикла: " + hrs + "ч " + mins + "м";
        } else if (cat != ProfessionCategory.STATE) {
            fullTip += "\nЛимит: 1 раз в неделю (140 мин.)";
        }

        FlowLayout leaveBtn = createIconButton("✕", 14, 14, 1, 0xFF2A1C1E, 0xFF3D2226, 0xFF8A3030, () -> openLeaveProfessionModal(prof));
        leaveBtn.tooltip(Component.literal(fullTip).withStyle(ChatFormatting.RED));
        leaveBtn.positioning(Positioning.relative(100, 0));
        topStack.child(leaveBtn);

        card.child(topStack);

        // 2. Middle: Large profession icon (38x38)
        ItemComponent profIcon = Components.item(new ItemStack(prof.getIconItem()));
        profIcon.sizing(Sizing.fixed(38), Sizing.fixed(38));
        profIcon.margins(Insets.vertical(6));
        card.child(profIcon);

        // 3. Progress bar: Gray track with white line, notch marker, and percent label (0 - 100%)
        int progress = pd != null ? pd.getProfessionProgress(prof.getTitle()) : 25;
        ProfessionProgressBarComponent progressBar = new ProfessionProgressBarComponent(progress);
        card.child(progressBar);

        // 4. Abilities section
        FlowLayout abilitiesRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        abilitiesRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        abilitiesRow.gap(5);
        abilitiesRow.margins(Insets.top(8));

        long now = System.currentTimeMillis();
        List<ProfessionAbility> abilities = getProfessionAbilities(prof);
        for (ProfessionAbility ab : abilities) {
            String abilityKey = prof.getId() + "_" + ab.name();
            Long cdExpire = ABILITY_COOLDOWNS.get(abilityKey);
            boolean onCd = ab.isActive() && cdExpire != null && cdExpire > now;
            long remainingSec = onCd ? Math.max(1, (cdExpire - now + 999) / 1000) : 0L;

            StackLayout abSlot = Containers.stack(Sizing.fixed(28), Sizing.fixed(28));
            abSlot.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

            int slotBorder;
            int slotBg;
            if (!ab.isActive()) {
                slotBorder = 0xFF505A6B;
                slotBg = 0xFF141720;
            } else if (onCd) {
                slotBorder = 0xFF22303C;
                slotBg = 0xFF0E1217;
            } else {
                slotBorder = 0xFF2E6B9E;
                slotBg = 0xFF12222E;
            }

            int fSlotBorder = slotBorder;
            int fSlotBg = slotBg;
            boolean fOnCd = onCd;

            abSlot.surface((ctx, comp) -> {
                int x = comp.x();
                int y = comp.y();
                int w = comp.width();
                int h = comp.height();
                ctx.fill(x + 1, y, x + w - 1, y + h, fSlotBg);
                ctx.fill(x, y + 1, x + w, y + h - 1, fSlotBg);

                ctx.fill(x + 1, y, x + w - 1, y + 1, fSlotBorder);
                ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, fSlotBorder);
                ctx.fill(x, y + 1, x + 1, y + h - 1, fSlotBorder);
                ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, fSlotBorder);

                if (fOnCd) {
                    ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xAF080B10);
                }
            });

            ItemComponent abIcon = Components.item(ab.icon());
            abIcon.sizing(Sizing.fixed(20), Sizing.fixed(20));
            abSlot.child(abIcon);

            if (onCd) {
                LabelComponent cdLabel = Components.label(Component.literal(remainingSec + "с").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                abSlot.child(cdLabel);
            }

            Component tip;
            if (ab.isActive()) {
                if (onCd) {
                    tip = Component.literal(ab.name() + " (активная)").withStyle(ChatFormatting.DARK_AQUA)
                            .append(Component.literal("\n" + ab.description()).withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("\n⏳ Перезарядка: " + remainingSec + " сек.").withStyle(ChatFormatting.RED));
                } else {
                    tip = Component.literal(ab.name() + " (активная)").withStyle(ChatFormatting.AQUA)
                            .append(Component.literal("\n" + ab.description()).withStyle(ChatFormatting.GRAY))
                            .append(Component.literal("\n[ЛКМ: Активировать способность]").withStyle(ChatFormatting.YELLOW));
                    abSlot.cursorStyle(CursorStyle.HAND);
                }

                abSlot.mouseDown().subscribe((mouseX, mouseY, button) -> {
                    if (button == 0) {
                        long cur = System.currentTimeMillis();
                        Long exp = ABILITY_COOLDOWNS.get(abilityKey);
                        if (exp != null && exp > cur) {
                            long sec = Math.max(1, (exp - cur + 999) / 1000);
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal("⏳ Способность «" + ab.name() + "» восстанавливается! Осталось " + sec + " сек.").withStyle(ChatFormatting.RED), false);
                            }
                        } else {
                            ABILITY_COOLDOWNS.put(abilityKey, cur + 30_000L);
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
                            if (Minecraft.getInstance().player != null) {
                                Minecraft.getInstance().player.displayClientMessage(
                                        Component.literal("✨ Использована способность: ").withStyle(ChatFormatting.AQUA)
                                                .append(Component.literal(ab.name()).withStyle(ChatFormatting.YELLOW)), false);
                            }
                            rebuildUI();
                        }
                        return true;
                    }
                    return false;
                });
            } else {
                tip = Component.literal(ab.name() + " (пассивная)").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("\n" + ab.description()).withStyle(ChatFormatting.DARK_GRAY));
            }
            abSlot.tooltip(tip);
            abilitiesRow.child(abSlot);
        }
        card.child(abilitiesRow);

        return card;
    }

    private void openLeaveProfessionModal(Profession prof) {
        if (prof == null) return;
        PlayerCharacterData pd = getMyPlayerData();
        long balance = (long) ClientBankCache.getBalance();
        int brands = pd != null ? pd.getStateBrands() : 0;
        ProfessionCategory cat = prof.getType();

        boolean onCooldown = false;
        boolean canAfford = true;
        String costStr = "Бесплатно";
        ChatFormatting costColor = ChatFormatting.GREEN;
        String cantAffordMsg = "";

        if (cat == ProfessionCategory.MAIN) {
            costStr = "500 $PR + 2 марки";
            costColor = ChatFormatting.YELLOW;
            onCooldown = pd != null && pd.hasLeftMainThisCycle();
            canAfford = balance >= 500 && brands >= 2;
            if (balance < 500) {
                cantAffordMsg = "Недостаточно $PR (нужно 500)";
            } else if (brands < 2) {
                cantAffordMsg = "Недостаточно марок (нужно 2 шт.)";
            }
        } else if (cat == ProfessionCategory.SECONDARY) {
            costStr = "250 $PR + 1 марка";
            costColor = ChatFormatting.AQUA;
            onCooldown = pd != null && pd.hasLeftSecondaryThisCycle();
            canAfford = balance >= 250 && brands >= 1;
            if (balance < 250) {
                cantAffordMsg = "Недостаточно $PR (нужно 250)";
            } else if (brands < 1) {
                cantAffordMsg = "Недостаточно марок (нужна 1 шт.)";
            }
        }

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(270), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF5A2A2E)));
        modal.padding(Insets.of(12));
        modal.gap(6);
        modal.zIndex(10);

        OverlayContainer<FlowLayout> overlay = createOverlay(modal);

        // Header
        FlowLayout modalHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        modalHeader.verticalAlignment(VerticalAlignment.CENTER);
        modalHeader.gap(5);

        ItemComponent profIcon = Components.item(new ItemStack(prof.getIconItem()));
        profIcon.sizing(Sizing.fixed(14), Sizing.fixed(14));
        modalHeader.child(profIcon);

        modalHeader.child(Components.label(Component.literal("Покинуть профессию").withStyle(ChatFormatting.RED)));
        modalHeader.child(Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0)));

        FlowLayout xBtn = createIconButton("✕", 16, 16, 2, 0xFF22262E, 0xFF353B47, 0xFF4A5263, overlay::remove);
        modalHeader.child(xBtn);
        modal.child(modalHeader);
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF381F22)).margins(Insets.bottom(4)));

        modal.child(Components.label(Component.literal("Профессия: " + prof.getTitle()).withStyle(ChatFormatting.WHITE)));
        modal.child(Components.label(Component.literal("Категория: " + cat.getDisplayName()).withStyle(style -> style.withColor(cat.getColor()))));

        FlowLayout costRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        costRow.child(Components.label(Component.literal("Цена ухода: ").withStyle(ChatFormatting.GRAY)));
        costRow.child(Components.label(Component.literal(costStr).withStyle(costColor)));
        modal.child(costRow);

        if (cat == ProfessionCategory.MAIN || cat == ProfessionCategory.SECONDARY) {
            FlowLayout balRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            balRow.child(Components.label(Component.literal("Ваш баланс: ").withStyle(ChatFormatting.GRAY)));
            balRow.child(Components.label(Component.literal(String.format("%,d", balance).replace(',', ' ') + " $PR").withStyle(ChatFormatting.GOLD)));
            modal.child(balRow);

            FlowLayout brandsRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            brandsRow.child(Components.label(Component.literal("Ваши марки: ").withStyle(ChatFormatting.GRAY)));
            brandsRow.child(Components.label(Component.literal(String.format("%,d", brands).replace(',', ' ') + " шт.").withStyle(ChatFormatting.AQUA)));
            modal.child(brandsRow);
        }

        if (cat != ProfessionCategory.STATE) {
            FlowLayout limitRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            limitRow.child(Components.label(Component.literal("Лимит цикла: ").withStyle(ChatFormatting.DARK_GRAY)));
            limitRow.child(Components.label(Component.literal("1 смена в неделю (140 мин.)").withStyle(ChatFormatting.GRAY)));
            modal.child(limitRow);
        }

        if (onCooldown && pd != null) {
            long remMs = pd.getLongCycleRemainingMs();
            long hrs = remMs / 3600000L;
            long mins = (remMs % 3600000L) / 60000L;
            FlowLayout cdBox = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            cdBox.padding(Insets.of(4));
            cdBox.surface(Surface.flat(0xFF261517).and(Surface.outline(0xFF5A2528)));
            cdBox.child(Components.label(Component.literal("⏳ Лимит исчерпан! Сброс через: " + hrs + "ч " + mins + "м").withStyle(ChatFormatting.RED)));
            modal.child(cdBox);
        }

        FlowLayout confirmBtn;
        if (onCooldown) {
            confirmBtn = Containers.horizontalFlow(Sizing.fixed(246), Sizing.fixed(22));
            confirmBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            confirmBtn.surface(Surface.flat(0xFF181B22).and(Surface.outline(0xFF282D38)));
            confirmBtn.child(Components.label(Component.literal("Лимит на цикл исчерпан").withStyle(ChatFormatting.DARK_GRAY)));
        } else if (!canAfford) {
            confirmBtn = Containers.horizontalFlow(Sizing.fixed(246), Sizing.fixed(22));
            confirmBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            confirmBtn.surface(Surface.flat(0xFF181B22).and(Surface.outline(0xFF282D38)));
            confirmBtn.child(Components.label(Component.literal(cantAffordMsg).withStyle(ChatFormatting.DARK_RED)));
        } else {
            confirmBtn = createFlatButton(
                    Component.literal("Покинуть профессию").withStyle(ChatFormatting.WHITE),
                    246, 22,
                    0xFF2E1919, 0xFF422323, 0xFF8A3030,
                    () -> {
                        overlay.remove();
                        NetworkHandler.sendToServer(new LeaveProfessionPacket(prof.getId()));
                    }
            );
        }
        confirmBtn.margins(Insets.top(6));
        modal.child(confirmBtn);

        this.uiAdapter.rootComponent.child(overlay);
    }

    // =========================================================================
    // TAB 0: STATE (ГОСУДАРСТВО)
    // =========================================================================
    private void buildStateTab(FlowLayout parent) {
        FlowLayout mainLayout = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        mainLayout.gap(6);

        // Subheader bar
        FlowLayout subHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(26));
        subHeader.verticalAlignment(VerticalAlignment.CENTER);
        subHeader.surface(Surface.flat(0xFF161920).and(Surface.outline(0xFF262C36)));
        subHeader.padding(Insets.horizontal(8));
        subHeader.child(Components.label(Component.literal("Государство OneHouse3").withStyle(ChatFormatting.WHITE)));
        mainLayout.child(subHeader);

        // Scrollable content area
        FlowLayout content = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.gap(6);
        content.padding(Insets.of(4));

        UUID knyazUuid = worldData != null ? worldData.getKnyazUuid() : null;
        String knyazName = worldData != null ? worldData.getKnyazName() : "";
        boolean hasKnyaz = worldData != null && worldData.hasKnyaz();
        PlayerCharacterData knyazPd = (worldData != null && knyazUuid != null) ? worldData.getPlayerData(knyazUuid) : null;
        ActiveCharacter knyazChar = knyazPd != null ? knyazPd.getActiveCharacter() : null;

        // SECTION 1: Ruler Card (Правящий Князь) with LARGE PORTRAIT (64x64)
        FlowLayout rulerCard = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        rulerCard.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF383F4E)));
        rulerCard.padding(Insets.of(10));
        rulerCard.verticalAlignment(VerticalAlignment.CENTER);
        rulerCard.gap(12);

        // Large portrait (64x64)
        FlowLayout avatarBox = Containers.horizontalFlow(Sizing.fixed(66), Sizing.fixed(66));
        avatarBox.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        avatarBox.surface(Surface.flat(0xFF0F1115).and(Surface.outline(hasKnyaz ? 0xFF5C5236 : 0xFF2A2E38)));
        avatarBox.child(new PlayerAvatarComponent(knyazUuid, 64));
        rulerCard.child(avatarBox);

        // Ruler details
        FlowLayout rulerInfo = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rulerInfo.gap(3);

        FlowLayout titleRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        titleRow.verticalAlignment(VerticalAlignment.CENTER);
        titleRow.gap(5);
        titleRow.child(new TextureIconComponent(ICON_MONARCH_CROWN, 14));
        titleRow.child(Components.label(Component.literal("Правящий Князь").withStyle(ChatFormatting.WHITE)));
        rulerInfo.child(titleRow);

        if (hasKnyaz) {
            String charName = knyazChar != null && !knyazChar.getName().isEmpty() ? knyazChar.getName() : knyazName;
            Component nameComp = Component.literal(charName).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" [" + knyazName + "]").withStyle(ChatFormatting.GRAY));
            rulerInfo.child(Components.label(nameComp));

            if (knyazChar != null) {
                if (!knyazChar.getRace().isEmpty()) {
                    rulerInfo.child(Components.label(Component.literal("Раса: ").withStyle(ChatFormatting.GRAY).append(Component.literal(knyazChar.getRace()).withStyle(ChatFormatting.WHITE))));
                }
                rulerInfo.child(Components.label(Component.literal("Возраст: ").withStyle(ChatFormatting.GRAY).append(Component.literal(knyazChar.getAge() + " лет").withStyle(ChatFormatting.WHITE))));
            }
            if (knyazPd != null && !knyazPd.getFaction().isEmpty()) {
                rulerInfo.child(Components.label(Component.literal("Фракция: ").withStyle(ChatFormatting.GRAY).append(Component.literal(knyazPd.getFaction()).withStyle(ChatFormatting.WHITE))));
            }
        } else {
            rulerInfo.child(Components.label(Component.literal("Титул Князя свободен").withStyle(ChatFormatting.GRAY)));
            rulerInfo.child(Components.label(Component.literal("Корона еще не обрела своего правителя.").withStyle(ChatFormatting.DARK_GRAY)));
        }

        rulerCard.child(rulerInfo);
        content.child(rulerCard);

        // SECTION 2: Settlement Statistics
        FlowLayout statsCard = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        statsCard.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF262C38)));
        statsCard.padding(Insets.of(10));
        statsCard.gap(4);

        statsCard.child(Components.label(Component.literal("Статистика поселения").withStyle(ChatFormatting.WHITE)));
        statsCard.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF262C38)).margins(Insets.bottom(4)));

        int citizens = 0;
        int buried = 0;
        int unburied = 0;
        if (worldData != null) {
            for (PlayerCharacterData pd : worldData.getAllPlayerData().values()) {
                if (pd.hasActiveCharacter()) citizens++;
                for (DeadCharacter dead : pd.getCharacterHistory()) {
                    if (dead.isBuried()) buried++;
                    else unburied++;
                }
            }
        }

        statsCard.child(Components.label(Component.literal("Жителей в мире: " + citizens).withStyle(ChatFormatting.GRAY).append(Component.literal(" чел.").withStyle(ChatFormatting.DARK_GRAY))));
        statsCard.child(Components.label(Component.literal("Упокоенных предков: " + buried).withStyle(ChatFormatting.GRAY).append(Component.literal(" чел.").withStyle(ChatFormatting.DARK_GRAY))));
        statsCard.child(Components.label(Component.literal("Неупокоенных душ: " + unburied).withStyle(ChatFormatting.GRAY).append(Component.literal(" чел.").withStyle(ChatFormatting.DARK_GRAY))));

        PlayerCharacterData myData = getMyPlayerData();
        String myFaction = myData != null && !myData.getFaction().isEmpty() ? myData.getFaction() : "Не состоит";
        statsCard.child(Components.label(Component.literal("Ваша фракция: ").withStyle(ChatFormatting.GRAY).append(Component.literal(myFaction).withStyle(ChatFormatting.WHITE))));
        statsCard.child(Components.label(Component.literal("Баланс Гос. Марок: ").withStyle(ChatFormatting.GRAY).append(Component.literal(getMyStateBrands() + " шт.").withStyle(ChatFormatting.WHITE))));

        content.child(statsCard);

        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), content);
        scroll.scrollbar(DARK_SCROLLBAR);
        scroll.scrollbarThiccness(4);
        mainLayout.child(scroll);

        parent.child(mainLayout);
    }

    private void buildPlaceholderTab(FlowLayout parent, String tabName) {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        card.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF262C38)));
        card.padding(Insets.of(16));
        card.horizontalAlignment(HorizontalAlignment.CENTER);
        card.verticalAlignment(VerticalAlignment.CENTER);
        card.gap(6);

        card.child(Components.label(Component.literal("Вкладка «" + tabName + "»").withStyle(ChatFormatting.WHITE)));
        card.child(Components.label(Component.literal("Раздел находится в активной разработке.").withStyle(ChatFormatting.GRAY)));

        parent.child(card);
    }

    // =========================================================================
    // TAB 4: TREASURY (КАЗНА)
    // =========================================================================

    private void buildTreasuryTab(FlowLayout parent) {
        FlowLayout treasuryFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        treasuryFlow.padding(Insets.of(4, 12, 10, 8));
        treasuryFlow.gap(10);

        // --- 1. TOP CARD: Treasury status ---
        FlowLayout treasuryCard = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        treasuryCard.surface(Surface.flat(0xFF13171F).and(Surface.outline(0xFF4A3E18)));
        treasuryCard.padding(Insets.of(14, 16, 16, 16));
        treasuryCard.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        treasuryCard.gap(8);

        // Subtitle row with small font text in the middle
        FlowLayout subTitleRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        subTitleRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        subTitleRow.gap(5);

        LabelComponent subTitle = Components.label(Component.literal("На данный момент в казне находится:"));
        subTitle.color(Color.ofRgb(0xFFAAAAAA));
        subTitle.shadow(true);
        subTitleRow.child(subTitle);

        treasuryCard.child(subTitleRow);

        // Current treasury amount
        long treasury = worldData != null ? worldData.getTreasury() : 0L;
        String formattedTreasury = String.format("%,d", treasury).replace(',', ' ') + " $PR";
        Component amountComp = Component.literal(formattedTreasury).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        float bigScale = 2.4f;
        Font font = Minecraft.getInstance().font;
        int bigWidth = (int) (font.width(amountComp) * bigScale);
        int bigHeight = (int) (font.lineHeight * bigScale) + 2;

        FlowLayout amountRow = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(bigHeight));
        amountRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        amountRow.gap(8);

        ItemComponent bigIngot = Components.item(new ItemStack(com.oneshop.item.ModItems.SPROTA.get()));
        bigIngot.sizing(Sizing.fixed(20), Sizing.fixed(20));
        amountRow.child(bigIngot);

        FlowLayout textBox = Containers.horizontalFlow(Sizing.fixed(bigWidth), Sizing.fixed(bigHeight));
        textBox.surface((ctx, p) -> {
            ctx.drawText(amountComp, p.x(), p.y() + 1, bigScale, 0xFFFFAA00);
        });
        amountRow.child(textBox);

        treasuryCard.child(amountRow);
        treasuryFlow.child(treasuryCard);

        // --- 2. TAXES CATEGORY (НАЛОГИ) ---
        FlowLayout taxCard = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        taxCard.surface(Surface.flat(0xFF13171F).and(Surface.outline(0xFF4A3E18)));
        taxCard.padding(Insets.of(12, 14, 12, 14));
        taxCard.gap(8);

        // Header: Orange Dot + "Налоги"
        FlowLayout taxTitleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        taxTitleRow.verticalAlignment(VerticalAlignment.CENTER);
        taxTitleRow.gap(6);

        taxTitleRow.child(Components.label(Component.literal("• ").withStyle(ChatFormatting.GOLD)));
        taxTitleRow.child(Components.label(Component.literal("Налоги").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));

        boolean canManage = false;
        if (Minecraft.getInstance().player != null && worldData != null) {
            canManage = worldData.canManageTaxes(Minecraft.getInstance().player.getUUID());
        }

        taxTitleRow.child(Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0)));
        if (canManage) {
            FlowLayout changeRateBtn = createFlatButton(
                    Component.literal("Изменить ставку").withStyle(ChatFormatting.GOLD),
                    110, 18,
                    0xFF262112, 0xFF3D3219, 0xFFD4AF37,
                    this::openSetTaxRateModal
            );
            changeRateBtn.tooltip(Component.literal("Установить новую ставку налога (только для Князя)").withStyle(ChatFormatting.GRAY));
            taxTitleRow.child(changeRateBtn);
        }

        taxCard.child(taxTitleRow);
        taxCard.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF38311B)).margins(Insets.bottom(2)));

        // 1. Отображение актуального налога и таймера
        int currentTaxRate = worldData != null ? worldData.getTaxRate() : 10;
        FlowLayout rateRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        rateRow.verticalAlignment(VerticalAlignment.CENTER);
        rateRow.gap(6);

        rateRow.child(Components.label(Component.literal("• ").withStyle(ChatFormatting.GOLD)));
        rateRow.child(Components.label(Component.literal("Актуальный налог: ").withStyle(ChatFormatting.GRAY)));
        rateRow.child(Components.label(Component.literal(currentTaxRate + " $PR").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));

        int totalSecs = Math.max(0, clientTaxTicksRemaining / 20);
        int mins = totalSecs / 60;
        int secs = totalSecs % 60;
        taxTimerLabel = Components.label(Component.literal(String.format("(до следующего снятия: %02d:%02d)", mins, secs)).withStyle(ChatFormatting.GRAY));
        rateRow.child(taxTimerLabel);
        taxCard.child(rateRow);

        // 2. Отображение налогового статуса игрока - "Налоговый статус"
        PlayerCharacterData myData = getMyPlayerData();
        long myStatus = myData != null ? myData.getTaxStatus() : 0L;
        FlowLayout statusRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        statusRow.verticalAlignment(VerticalAlignment.CENTER);
        statusRow.gap(6);

        statusRow.child(Components.label(Component.literal("• ").withStyle(ChatFormatting.GOLD)));
        statusRow.child(Components.label(Component.literal("Налоговый статус: ").withStyle(ChatFormatting.GRAY)));

        String formattedVal;
        if (myStatus > 0) {
            formattedVal = "+" + String.format("%,d", myStatus).replace(',', ' ') + " $PR";
        } else if (myStatus < 0) {
            formattedVal = String.format("%,d", myStatus).replace(',', ' ') + " $PR";
        } else {
            formattedVal = "0 $PR";
        }

        ChatFormatting statusColor;
        if (myStatus < 0) {
            statusColor = ChatFormatting.RED;
        } else if (myStatus > 0) {
            statusColor = ChatFormatting.GREEN;
        } else {
            statusColor = ChatFormatting.WHITE;
        }

        long maxStatus = currentTaxRate * 3L;
        MutableComponent statusComp = Component.literal(formattedVal).withStyle(statusColor, ChatFormatting.BOLD);

        if (myStatus < 0) {
            statusRow.child(Components.label(statusComp));
            statusRow.child(Components.label(Component.literal("(задолженность)").withStyle(ChatFormatting.DARK_RED)));
        } else if (currentTaxRate > 0) {
            statusComp.append(Component.literal(" / " + String.format("%,d", maxStatus).replace(',', ' ') + " $PR")
                    .withStyle(ChatFormatting.GRAY)
                    .withStyle(style -> style.withBold(false)));
            statusRow.child(Components.label(statusComp));
        } else {
            statusRow.child(Components.label(statusComp));
            statusRow.child(Components.label(Component.literal("(нет задолженности)").withStyle(ChatFormatting.GRAY)));
        }
        taxCard.child(statusRow);

        // 3. Баланс игрока
        double myBalance = ClientBankCache.getBalance();
        String formattedMyBal = (myBalance % 1 == 0 ? String.format(java.util.Locale.ROOT, "%,d", (long) myBalance).replace(',', ' ') : String.format(java.util.Locale.ROOT, "%,.2f", myBalance)) + " " + SprotaItem.CURRENCY_SYMBOL;
        FlowLayout balanceRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        balanceRow.verticalAlignment(VerticalAlignment.CENTER);
        balanceRow.gap(6);

        balanceRow.child(Components.label(Component.literal("• ").withStyle(ChatFormatting.GOLD)));
        balanceRow.child(Components.label(Component.literal("Ваш баланс: ").withStyle(ChatFormatting.GRAY)));
        balanceRow.child(Components.label(Component.literal(formattedMyBal).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        taxCard.child(balanceRow);

        // 4. Кнопки действий казны - друг под другом
        FlowLayout btnCol = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        btnCol.gap(6);
        btnCol.margins(Insets.top(4));

        FlowLayout payTaxBtn = createFlatButton(
                Component.literal("Оплатить налог").withStyle(ChatFormatting.WHITE),
                200, 22,
                0xFF1C2B1D, 0xFF273D29, 0xFF437547,
                this::openPayTaxModal
        );
        payTaxBtn.tooltip(Component.literal("Оплатить налог (погасить задолженность или оплатить наперед до максимума)").withStyle(ChatFormatting.GRAY));
        btnCol.child(payTaxBtn);

        FlowLayout depositBtn = createFlatButton(
                Component.literal("Внести деньги в казну").withStyle(ChatFormatting.WHITE),
                200, 22,
                0xFF2A2312, 0xFF3D3219, 0xFFD4AF37,
                this::openDepositTreasuryModal
        );
        depositBtn.tooltip(Component.literal("Внести деньги в казну и получить Гос. Марки на счёт").withStyle(ChatFormatting.GRAY));
        btnCol.child(depositBtn);

        taxCard.child(btnCol);
        treasuryFlow.child(taxCard);

        // --- 3. CATEGORY "ЛОГ КАЗНЫ" ---
        FlowLayout logCard = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        logCard.surface(Surface.flat(0xFF13171F).and(Surface.outline(0xFF4A3E18)));
        logCard.padding(Insets.of(12, 14, 12, 14));
        logCard.gap(6);

        // Header
        FlowLayout logHeaderRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        logHeaderRow.verticalAlignment(VerticalAlignment.CENTER);
        logHeaderRow.gap(6);

        logHeaderRow.child(Components.label(Component.literal("• ").withStyle(ChatFormatting.GOLD)));
        logHeaderRow.child(Components.label(Component.literal("Лог Казны").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));

        List<TreasuryLogEntry> logEntries = worldData != null ? worldData.getTreasuryLog() : Collections.emptyList();
        logHeaderRow.child(Components.spacer());
        logHeaderRow.child(Components.label(Component.literal("(" + logEntries.size() + " операций)").withStyle(ChatFormatting.DARK_GRAY)));
        logCard.child(logHeaderRow);

        logCard.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF38311B)).margins(Insets.bottom(2)));

        // List Flow
        FlowLayout logListFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        logListFlow.gap(4);

        if (logEntries.isEmpty()) {
            FlowLayout emptyRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
            emptyRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            emptyRow.child(Components.label(Component.literal("Записей в логе казны пока нет.").withStyle(ChatFormatting.DARK_GRAY)));
            logListFlow.child(emptyRow);
        } else {
            for (TreasuryLogEntry entry : logEntries) {
                FlowLayout entryCard = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
                entryCard.surface(Surface.flat(0xFF0E1116).and(Surface.outline(0xFF222834)));
                entryCard.padding(Insets.of(4, 8, 4, 8));
                entryCard.gap(2);

                entryCard.mouseEnter().subscribe(() -> entryCard.surface(Surface.flat(0xFF151922).and(Surface.outline(0xFF363F52))));
                entryCard.mouseLeave().subscribe(() -> entryCard.surface(Surface.flat(0xFF0E1116).and(Surface.outline(0xFF222834))));

                // Row 1: Who + Action
                FlowLayout r1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(12));
                r1.verticalAlignment(VerticalAlignment.CENTER);
                r1.gap(6);

                String displayName = entry.getDisplayName();
                r1.child(Components.label(Component.literal(displayName).withStyle(ChatFormatting.WHITE)));

                r1.child(Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0)));

                String act = entry.action();
                ChatFormatting actColor = ChatFormatting.YELLOW;
                if (act.startsWith("+")) {
                    actColor = ChatFormatting.GREEN;
                } else if (act.startsWith("-")) {
                    actColor = ChatFormatting.RED;
                }
                r1.child(Components.label(Component.literal(act).withStyle(actColor, ChatFormatting.BOLD)));
                entryCard.child(r1);

                // Row 2: Time + Treasury state after
                FlowLayout r2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(12));
                r2.verticalAlignment(VerticalAlignment.CENTER);
                r2.gap(6);

                r2.child(Components.label(Component.literal(entry.timestamp()).withStyle(ChatFormatting.DARK_GRAY)));

                r2.child(Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0)));

                FlowLayout afterRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
                afterRow.gap(4);
                afterRow.child(Components.label(Component.literal("Казна после: ").withStyle(ChatFormatting.GRAY)));
                afterRow.child(Components.label(Component.literal(String.format("%,d", entry.treasuryAfter()).replace(',', ' ') + " $PR").withStyle(ChatFormatting.GOLD)));
                r2.child(afterRow);

                entryCard.child(r2);

                logListFlow.child(entryCard);
            }
        }

        int logScrollH = Math.min(180, Math.max(80, Math.min(logEntries.size() * 34, 180)));
        if (logEntries.isEmpty()) {
            logScrollH = 32;
        }
        ScrollContainer<FlowLayout> logScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(logScrollH), logListFlow);
        logScroll.scrollbar(DARK_SCROLLBAR);
        logScroll.scrollbarThiccness(4);
        logScroll.padding(Insets.right(2));
        logCard.child(logScroll);

        treasuryFlow.child(logCard);

        // --- 3. Information Card ---
        FlowLayout infoCard = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        infoCard.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF262C38)));
        infoCard.padding(Insets.of(12, 14, 12, 14));
        infoCard.gap(6);

        FlowLayout infoTitleRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        infoTitleRow.verticalAlignment(VerticalAlignment.CENTER);
        infoTitleRow.gap(6);
        infoTitleRow.child(Components.label(Component.literal("• ").withStyle(ChatFormatting.GOLD)));
        infoTitleRow.child(Components.label(Component.literal("Государственная Казна").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        infoCard.child(infoTitleRow);

        infoCard.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF262C38)).margins(Insets.vertical(2)));

        FlowLayout descRow1 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        descRow1.gap(4);
        descRow1.child(Components.label(Component.literal("• ").withStyle(ChatFormatting.GOLD)));
        descRow1.child(Components.label(Component.literal("Игроки платят налоги и пополняют государственную казну.").withStyle(ChatFormatting.GRAY)));
        infoCard.child(descRow1);

        FlowLayout descRow2 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        descRow2.gap(4);
        descRow2.child(Components.label(Component.literal("• ").withStyle(ChatFormatting.GOLD)));
        descRow2.child(Components.label(Component.literal("Казначей и Правящий Князь единолично распоряжаются казной.").withStyle(ChatFormatting.GRAY)));
        infoCard.child(descRow2);

        FlowLayout descRow3 = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        descRow3.gap(4);
        descRow3.child(Components.label(Component.literal("• ").withStyle(ChatFormatting.GOLD)));
        descRow3.child(Components.label(Component.literal("Средства казны используются для финансирования проектов и нужд государства.").withStyle(ChatFormatting.GRAY)));
        infoCard.child(descRow3);

        treasuryFlow.child(infoCard);

        ScrollContainer<FlowLayout> treasuryScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), treasuryFlow);
        treasuryScroll.scrollbar(DARK_SCROLLBAR);
        treasuryScroll.scrollbarThiccness(4);
        parent.child(treasuryScroll);
    }

    // =========================================================================
    // TAB 6: WORLD (МИР) & PROGRESSION
    // =========================================================================

    public record WorldMilestone(int level, String title, String description, ItemStack icon) {}

    private static final List<WorldMilestone> WORLD_MILESTONES = List.of(
            new WorldMilestone(25, "Врата Незера (Разблокировка Ада)", "Открытие древних порталов и доступ к Нижнему миру.", new ItemStack(Items.OBSIDIAN)),
            new WorldMilestone(50, "Эпоха Алхимии и Пламени", "Доступ к ифритам, варочным стойкам и сложным зельям.", new ItemStack(Items.BREWING_STAND)),
            new WorldMilestone(75, "Поиск Подземных Цитаделей", "Око Края указывает путь к затерянным крепостям предков.", new ItemStack(Items.ENDER_EYE)),
            new WorldMilestone(100, "Врата Пустоты (Разблокировка Энда)", "Активация портала в Край и битва с Драконом Пустоты.", new ItemStack(Items.END_PORTAL_FRAME)),
            new WorldMilestone(125, "Освоение Дальнего Энда", "Доступ к внешним островам, городам Края и Элитрам.", new ItemStack(Items.ELYTRA)),
            new WorldMilestone(150, "Культ Иссушителя и Маяки", "Возможность победить Иссушителя и освятить великие маяки.", new ItemStack(Items.NETHER_STAR)),
            new WorldMilestone(175, "Незеритовый Расцвет", "Освоение древних обломков и ковка высшего снаряжения.", new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)),
            new WorldMilestone(200, "Владычество над Миром", "Абсолютный триумф цивилизации. Максимальный потенциал мира.", new ItemStack(Items.TOTEM_OF_UNDYING))
    );

    // =========================================================================
    // TAB: LETTERS (ПИСЬМА)
    // =========================================================================
    private void buildLettersTab(FlowLayout parent) {
        FlowLayout mainLayout = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        mainLayout.gap(6);

        PlayerCharacterData pd = getMyPlayerData();
        List<MailEntry> mailList = pd != null ? pd.getMailbox() : Collections.emptyList();

        // Subheader bar
        FlowLayout subHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(26));
        subHeader.verticalAlignment(VerticalAlignment.CENTER);
        subHeader.surface(Surface.flat(0xFF161920).and(Surface.outline(0xFF262C36)));
        subHeader.padding(Insets.horizontal(8));
        subHeader.gap(6);

        ItemComponent noteIcon = Components.item(new ItemStack(ModItems.NOTE.get()));
        noteIcon.sizing(Sizing.fixed(16), Sizing.fixed(16));
        subHeader.child(noteIcon);

        subHeader.child(Components.label(Component.literal("Почтовый ящик (Входящие письма)").withStyle(ChatFormatting.WHITE)));
        subHeader.child(Components.spacer());
        subHeader.child(Components.label(Component.literal("Всего писем: " + mailList.size()).withStyle(ChatFormatting.DARK_GRAY)));
        mainLayout.child(subHeader);

        // Content area
        FlowLayout content = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.gap(6);
        content.padding(Insets.of(4));

        if (mailList.isEmpty()) {
            FlowLayout emptyBox = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(80));
            emptyBox.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            emptyBox.surface(Surface.flat(0xFF13151A).and(Surface.outline(0xFF22262E)));
            emptyBox.gap(4);
            emptyBox.child(Components.label(Component.literal("Ваш почтовый ящик пуст").withStyle(ChatFormatting.GRAY)));
            emptyBox.child(Components.label(Component.literal("Сюда приходят анонимные записки от других персонажей.").withStyle(ChatFormatting.DARK_GRAY)));
            content.child(emptyBox);
        } else {
            for (MailEntry entry : mailList) {
                FlowLayout card = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(32));
                card.surface(Surface.flat(0xFF13171F).and(Surface.outline(0xFF262C38)));
                card.padding(Insets.of(4, 8, 4, 8));
                card.verticalAlignment(VerticalAlignment.CENTER);
                card.gap(8);

                card.mouseEnter().subscribe(() -> card.surface(Surface.flat(0xFF171B24).and(Surface.outline(0xFF353D4E))));
                card.mouseLeave().subscribe(() -> card.surface(Surface.flat(0xFF13171F).and(Surface.outline(0xFF262C38))));

                ItemComponent icon = Components.item(new ItemStack(ModItems.NOTE.get()));
                icon.sizing(Sizing.fixed(16), Sizing.fixed(16));
                card.child(icon);

                FlowLayout textCol = Containers.verticalFlow(Sizing.content(), Sizing.content());
                textCol.gap(1);
                String title = entry.getTitle().isEmpty() ? "Записка (без названия)" : entry.getTitle();
                textCol.child(Components.label(Component.literal(title).withStyle(ChatFormatting.WHITE)));
                String meta = "Дата: " + entry.getDate();
                if (entry.getAuthor() != null && !entry.getAuthor().isBlank()) {
                    meta += "  •  Автор: " + entry.getAuthor();
                }
                textCol.child(Components.label(Component.literal(meta).withStyle(ChatFormatting.DARK_GRAY)));
                card.child(textCol);

                card.child(Components.spacer());

                if (!entry.isClaimed()) {
                    FlowLayout claimBtn = createFlatButton(
                            Component.literal("Получить").withStyle(ChatFormatting.WHITE),
                            72, 20,
                            0xFF1E3823, 0xFF28542E, 0xFF3E8046,
                            () -> NetworkHandler.sendToServer(new ClaimLetterPacket(entry.getId()))
                    );
                    claimBtn.tooltip(Component.literal("Получить копию записки в инвентарь"));
                    card.child(claimBtn);
                } else {
                    FlowLayout claimedBadge = Containers.horizontalFlow(Sizing.fixed(76), Sizing.fixed(20));
                    claimedBadge.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                    claimedBadge.surface(Surface.flat(0xFF161922).and(Surface.outline(0xFF262B36)));
                    claimedBadge.child(Components.label(Component.literal("✔ Получено").withStyle(ChatFormatting.GRAY)));
                    claimedBadge.tooltip(Component.literal("Копия записки уже получена в инвентарь"));
                    card.child(claimedBadge);
                }

                content.child(card);
            }
        }

        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), content);
        scroll.scrollbar(DARK_SCROLLBAR);
        scroll.scrollbarThiccness(4);
        mainLayout.child(scroll);

        parent.child(mainLayout);
    }

    private void buildLawsTab(FlowLayout parent) {
        FlowLayout mainLayout = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        mainLayout.gap(6);

        PlayerCharacterData pd = getMyPlayerData();
        boolean isKnyaz = worldData != null && pd != null && worldData.isKnyaz(pd.getPlayerUuid());
        List<LawEntry> lawsList = worldData != null ? worldData.getLaws() : Collections.emptyList();

        // Subheader bar
        FlowLayout subHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(26));
        subHeader.verticalAlignment(VerticalAlignment.CENTER);
        subHeader.surface(Surface.flat(0xFF161920).and(Surface.outline(0xFF262C36)));
        subHeader.padding(Insets.horizontal(8));
        subHeader.gap(6);

        subHeader.child(new TextureIconComponent(ICON_MONARCH_CROWN, 14));
        subHeader.child(Components.label(Component.literal("Законы государства").withStyle(ChatFormatting.GOLD)));
        subHeader.child(Components.spacer());
        subHeader.child(Components.label(Component.literal("Всего законов: " + lawsList.size()).withStyle(ChatFormatting.DARK_GRAY)));
        mainLayout.child(subHeader);

        // Content area
        FlowLayout content = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.gap(6);
        content.padding(Insets.of(4));

        if (lawsList.isEmpty()) {
            FlowLayout emptyBox = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(80));
            emptyBox.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            emptyBox.surface(Surface.flat(0xFF13151A).and(Surface.outline(0xFF22262E)));
            emptyBox.gap(4);
            emptyBox.child(Components.label(Component.literal("В государстве пока нет изданных законов").withStyle(ChatFormatting.GRAY)));
            emptyBox.child(Components.label(Component.literal("Действующий Князь может издать закон при завершении записки.").withStyle(ChatFormatting.DARK_GRAY)));
            content.child(emptyBox);
        } else {
            for (LawEntry law : lawsList) {
                FlowLayout card = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
                card.surface(Surface.flat(0xFF13171F).and(Surface.outline(0xFF262C38)));
                card.padding(Insets.of(5, 8, 5, 8));
                card.verticalAlignment(VerticalAlignment.CENTER);
                card.gap(8);

                card.mouseEnter().subscribe(() -> card.surface(Surface.flat(0xFF171B24).and(Surface.outline(0xFF353D4E))));
                card.mouseLeave().subscribe(() -> card.surface(Surface.flat(0xFF13171F).and(Surface.outline(0xFF262C38))));

                // Left: Book icon
                ItemComponent bookIcon = Components.item(new ItemStack(Items.BOOK));
                bookIcon.sizing(Sizing.fixed(16), Sizing.fixed(16));
                card.child(bookIcon);

                // Middle: Info Column
                FlowLayout infoCol = Containers.verticalFlow(Sizing.expand(), Sizing.content());
                infoCol.gap(1);

                // Title
                LabelComponent titleLbl = Components.label(Component.literal(law.getTitle()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                infoCol.child(titleLbl);

                // Author & Editors
                String authorName = law.getAuthor().startsWith("Князь ") ? law.getAuthor() : "Князь " + law.getAuthor();
                Component metaComp = Component.literal("Автор: " + authorName).withStyle(ChatFormatting.WHITE);
                if (!law.getEditors().isEmpty()) {
                    metaComp = metaComp.copy().append(Component.literal("  •  Редакторы: " + String.join(", ", law.getEditors())).withStyle(ChatFormatting.GRAY));
                }
                infoCol.child(Components.label(metaComp));

                // Dates
                String dateStr = "Создан: " + law.getCreatedDate();
                if (law.getLastModifiedDate() != null && !law.getLastModifiedDate().isEmpty() && !law.getLastModifiedDate().equals(law.getCreatedDate())) {
                    dateStr += "  •  Изменен: " + law.getLastModifiedDate();
                }
                infoCol.child(Components.label(Component.literal(dateStr).withStyle(ChatFormatting.DARK_GRAY)));

                card.child(infoCol);

                // Right: Action buttons
                FlowLayout btnsRow = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(20));
                btnsRow.verticalAlignment(VerticalAlignment.CENTER);
                btnsRow.gap(4);

                FlowLayout showBtn = createFlatButton(
                        Component.literal("Показать").withStyle(ChatFormatting.WHITE),
                        68, 20,
                        0xFF1E222A, 0xFF2A2E38, 0xFF353B47,
                        () -> Minecraft.getInstance().setScreen(new NoteViewScreen(law.getTitle(), law.getPages(), authorName, this))
                );
                showBtn.tooltip(Component.literal("Показать полный текст закона"));
                btnsRow.child(showBtn);

                if (isKnyaz) {
                    FlowLayout editBtn = createFlatButton(
                            Component.literal("Изменить").withStyle(ChatFormatting.WHITE),
                            68, 20,
                            0xFF1E222A, 0xFF2A2E38, 0xFF353B47,
                            () -> Minecraft.getInstance().setScreen(new LawEditScreen(law.getId(), law.getTitle(), law.getPages(), this))
                    );
                    editBtn.tooltip(Component.literal("Внести поправки в закон"));
                    btnsRow.child(editBtn);

                    FlowLayout repealBtn = createIconButton("✕", 20, 20, 2, 0xFF2A1C1E, 0xFF3D2226, 0xFF7A3036, () -> openRepealConfirmationModal(law));
                    repealBtn.tooltip(Component.literal("Отменить закон").withStyle(ChatFormatting.RED));
                    btnsRow.child(repealBtn);
                }

                card.child(btnsRow);
                content.child(card);
            }
        }

        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), content);
        scroll.scrollbar(DARK_SCROLLBAR);
        scroll.scrollbarThiccness(4);
        mainLayout.child(scroll);

        parent.child(mainLayout);
    }

    private void openRepealConfirmationModal(LawEntry law) {
        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(260), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF5A2222)));
        modal.padding(Insets.of(12));
        modal.gap(8);
        modal.zIndex(20);

        OverlayContainer<FlowLayout> overlay = createOverlay(modal);

        modal.child(Components.label(Component.literal("Отменить закон?").withStyle(ChatFormatting.RED)));
        LabelComponent warnLabel = Components.label(Component.literal("Вы уверены, что хотите отменить закон «" + law.getTitle() + "»? Это действие необратимо.").withStyle(ChatFormatting.GRAY));
        warnLabel.maxWidth(236);
        modal.child(warnLabel);

        FlowLayout btnRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        btnRow.gap(6);

        FlowLayout cancelBtn = createFlatButton(Component.literal("Отмена").withStyle(ChatFormatting.WHITE), 110, 22, 0xFF1E222A, 0xFF2A2E38, 0xFF353B47, overlay::remove);
        FlowLayout confirmBtn = createFlatButton(
                Component.literal("Отменить закон").withStyle(ChatFormatting.RED),
                120, 22,
                0xFF3D1B1B, 0xFF542727, 0xFF8E3838,
                () -> {
                    overlay.remove();
                    NetworkHandler.sendToServer(new RepealLawPacket(law.getId()));
                }
        );

        btnRow.child(cancelBtn);
        btnRow.child(confirmBtn);
        modal.child(btnRow);

        this.uiAdapter.rootComponent.child(overlay);
    }

    private void buildWorldTab(FlowLayout parent) {
        FlowLayout worldFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        worldFlow.padding(Insets.of(4, 12, 10, 8));
        worldFlow.gap(10);

        // --- 1. TOP CARD: World Border & "Мир" in lime green ---
        FlowLayout borderCard = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        borderCard.surface(Surface.flat(0xFF13171F).and(Surface.outline(0xFF263A2C)));
        borderCard.padding(Insets.of(10, 12, 10, 12));
        borderCard.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        borderCard.gap(4);

        // "Мир" in center in vibrant lime color (#72E060)
        LabelComponent worldTitle = Components.label(Component.literal("Мир"));
        worldTitle.color(Color.ofRgb(0xFF72E060));
        worldTitle.shadow(true);
        borderCard.child(worldTitle);

        // Current world border size
        double currentBorder = 1000.0;
        if (Minecraft.getInstance().level != null) {
            currentBorder = Minecraft.getInstance().level.getWorldBorder().getSize();
        }
        long sizeLong = Math.round(currentBorder);

        FlowLayout sizeRow = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(18));
        sizeRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        sizeRow.gap(5);

        ItemComponent compassIcon = Components.item(new ItemStack(Items.COMPASS));
        compassIcon.sizing(Sizing.fixed(16), Sizing.fixed(16));
        sizeRow.child(compassIcon);

        LabelComponent sizeText = Components.label(Component.literal(String.format("Текущий размер: %,d × %,d блоков", sizeLong, sizeLong)).withStyle(ChatFormatting.WHITE));
        sizeRow.child(sizeText);
        borderCard.child(sizeRow);

        LabelComponent radiusText = Components.label(Component.literal("Радиус границы: ±" + String.format("%,d", sizeLong / 2) + " блоков от центра (0, 0)").withStyle(ChatFormatting.DARK_GRAY));
        borderCard.child(radiusText);

        // Button "Расширить мир" directly under world size
        FlowLayout expandBtn = createFlatButton(
                Component.literal("Расширить мир").withStyle(ChatFormatting.WHITE),
                128, 22,
                0xFF1D2E22, 0xFF284432, 0xFF3E6E4C,
                this::openExpandWorldModal
        );
        expandBtn.margins(Insets.top(4));
        expandBtn.tooltip(Component.literal("Расширить границы мира за Гос. Марки (+50 блоков за марку)").withStyle(ChatFormatting.GRAY));
        borderCard.child(expandBtn);

        worldFlow.child(borderCard);

        // --- 2. SECTION DIVIDER: Line through header badge ("Уровень Мира") ---
        FlowLayout dividerRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        dividerRow.verticalAlignment(VerticalAlignment.CENTER);
        dividerRow.margins(Insets.vertical(4));

        dividerRow.child(Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofRgb(0xFF262E3B)));

        FlowLayout badge = Containers.horizontalFlow(Sizing.content(), Sizing.fixed(22));
        badge.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        badge.surface(Surface.flat(0xFF13161C).and(Surface.outline(0xFF333E4D)));
        badge.padding(Insets.horizontal(8));
        badge.gap(5);

        ItemComponent starIcon = Components.item(new ItemStack(Items.EXPERIENCE_BOTTLE));
        starIcon.sizing(Sizing.fixed(14), Sizing.fixed(14));
        badge.child(starIcon);

        LabelComponent headerTitle = Components.label(Component.literal("Уровень Мира").withStyle(ChatFormatting.WHITE));
        badge.child(headerTitle);

        int currentWorldLevel = worldData != null ? worldData.getWorldLevel() : 0;
        LabelComponent levelNum = Components.label(Component.literal("[" + currentWorldLevel + "/200]").withStyle(ChatFormatting.GREEN));
        badge.child(levelNum);

        dividerRow.child(badge);

        dividerRow.child(Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofRgb(0xFF262E3B)));

        worldFlow.child(dividerRow);

        // --- 3. MILESTONES LIST (Every 25 levels with connecting downward arrow) ---
        FlowLayout milestoneList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        milestoneList.gap(4);

        for (int i = 0; i < WORLD_MILESTONES.size(); i++) {
            WorldMilestone m = WORLD_MILESTONES.get(i);
            boolean reached = currentWorldLevel >= m.level();

            int cardBg = reached ? 0xFF142418 : 0xFF0E1116;
            int cardBorder = reached ? 0xFF2E7D32 : 0xFF1D222A;
            int hoverBg = reached ? 0xFF1B3020 : 0xFF151922;
            int hoverBorder = reached ? 0xFF3D9E42 : 0xFF293240;

            ChatFormatting levelColor = reached ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
            ChatFormatting titleColor = reached ? ChatFormatting.WHITE : ChatFormatting.GRAY;
            ChatFormatting descColor = reached ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY;

            FlowLayout card = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(42));
            card.surface(Surface.flat(cardBg).and(Surface.outline(cardBorder)));
            card.padding(Insets.horizontal(8));
            card.verticalAlignment(VerticalAlignment.CENTER);
            card.gap(8);

            card.mouseEnter().subscribe(() -> card.surface(Surface.flat(hoverBg).and(Surface.outline(hoverBorder))));
            card.mouseLeave().subscribe(() -> card.surface(Surface.flat(cardBg).and(Surface.outline(cardBorder))));

            // Left circle containing icon
            FlowLayout circle = Containers.horizontalFlow(Sizing.fixed(30), Sizing.fixed(30));
            circle.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            circle.surface((ctx, p) -> {
                int cx = p.x() + p.width() / 2;
                int cy = p.y() + p.height() / 2;
                int r = Math.min(p.width(), p.height()) / 2;
                ctx.drawCircle(cx, cy, r, 1.0, Color.ofRgb(reached ? 0xFF4CAF50 : 0xFF242A35));
                ctx.drawCircle(cx, cy, Math.max(1, r - 2), 1.0, Color.ofRgb(reached ? 0xFF1B3D23 : 0xFF12151B));
            });

            ItemComponent mIcon = Components.item(m.icon());
            mIcon.sizing(Sizing.fixed(16), Sizing.fixed(16));
            circle.child(mIcon);
            card.child(circle);

            // Center details
            FlowLayout info = Containers.verticalFlow(Sizing.expand(), Sizing.content());
            info.gap(2);

            FlowLayout titleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            titleRow.verticalAlignment(VerticalAlignment.CENTER);
            titleRow.gap(5);
            titleRow.child(Components.label(Component.literal("Уровень " + m.level() + ":").withStyle(levelColor)));
            titleRow.child(Components.label(Component.literal(m.title()).withStyle(titleColor)));
            info.child(titleRow);

            info.child(Components.label(Component.literal(m.description()).withStyle(descColor)));
            card.child(info);

            // Right status indicator
            card.child(Components.spacer());
            if (reached) {
                LabelComponent status = Components.label(Component.literal("✔ Достигнуто").withStyle(ChatFormatting.GREEN));
                card.child(status);
            } else {
                LabelComponent status = Components.label(Component.literal("🔒 Требуется " + m.level()).withStyle(ChatFormatting.DARK_GRAY));
                card.child(status);
            }

            milestoneList.child(card);

            // Connecting downward arrow between milestones
            if (i < WORLD_MILESTONES.size() - 1) {
                boolean nextReached = currentWorldLevel >= WORLD_MILESTONES.get(i + 1).level();
                FlowLayout arrowRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(14));
                arrowRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                LabelComponent arrow = Components.label(Component.literal("↓"));
                arrow.color(Color.ofRgb(nextReached ? 0xFF4CAF50 : 0xFF2A3442));
                arrowRow.child(arrow);
                milestoneList.child(arrowRow);
            }
        }

        worldFlow.child(milestoneList);

        ScrollContainer<FlowLayout> worldScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), worldFlow);
        worldScroll.scrollbar(DARK_SCROLLBAR);
        worldScroll.scrollbarThiccness(4);
        parent.child(worldScroll);
    }

    private int expandBrandAmount = 1;

    private void openExpandWorldModal() {
        int balance = getMyStateBrands();
        int inventoryBrands = getInventoryStateBrands();
        int totalAvailable = balance + inventoryBrands;
        expandBrandAmount = totalAvailable > 0 ? 1 : 0;

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(270), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF454B58)));
        modal.padding(Insets.of(12));
        modal.gap(6);
        modal.zIndex(10);

        OverlayContainer<FlowLayout> overlay = createOverlay(modal);

        // Header
        FlowLayout modalHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        modalHeader.verticalAlignment(VerticalAlignment.CENTER);
        modalHeader.child(Components.label(Component.literal("Расширение Мира").withStyle(ChatFormatting.WHITE)));
        modalHeader.child(Components.spacer());

        FlowLayout xBtn = createIconButton("✕", 16, 16, 2, 0xFF22262E, 0xFF353B47, 0xFF4A5263, overlay::remove);
        modalHeader.child(xBtn);
        modal.child(modalHeader);
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF2E3442)).margins(Insets.bottom(4)));

        // Available marks
        String availStr = "Доступно: " + totalAvailable + " шт. (счёт: " + balance + ", инв.: " + inventoryBrands + ")";
        modal.child(Components.label(Component.literal(availStr).withStyle(ChatFormatting.GRAY)));

        // Currency converter row: 1 [icon] -> +50 Мир with lime arrow
        FlowLayout converterRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        converterRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        converterRow.surface(Surface.flat(0xFF191F26).and(Surface.outline(0xFF2A3D2F)));
        converterRow.padding(Insets.horizontal(8));
        converterRow.gap(5);

        converterRow.child(Components.label(Component.literal("1 ").withStyle(ChatFormatting.WHITE)));
        converterRow.child(new TextureIconComponent(ICON_STATE_BRAND, 12));

        LabelComponent limeArrow = Components.label(Component.literal(" ➔ "));
        limeArrow.color(Color.ofRgb(0xFF72E060));
        converterRow.child(limeArrow);

        LabelComponent rateLabel = Components.label(Component.literal("+50 Мир"));
        rateLabel.color(Color.ofRgb(0xFF72E060));
        converterRow.child(rateLabel);

        modal.child(converterRow);

        // Dynamic result calculation label
        LabelComponent calcLabel = Components.label(Component.literal("Расширение: +" + (expandBrandAmount * 50) + " блоков").withStyle(ChatFormatting.GRAY));
        calcLabel.horizontalTextAlignment(HorizontalAlignment.CENTER);

        // Amount Input & Stepper Controls Row
        FlowLayout amountRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        amountRow.verticalAlignment(VerticalAlignment.CENTER);
        amountRow.horizontalAlignment(HorizontalAlignment.CENTER);
        amountRow.gap(4);

        EditBoxComponent amtBox = new EditBoxComponent(Minecraft.getInstance().font, 56, 18, Component.literal("РљРѕР»РёС‡РµСЃС‚РІРѕ"));
        amtBox.setMaxLength(6);
        amtBox.setValue(String.valueOf(expandBrandAmount));
        amtBox.setFilter(s -> s.matches("\\d*"));

        Runnable updateCalc = () -> {
            calcLabel.text(Component.literal("Расширение: +" + (expandBrandAmount * 50) + " блоков").withStyle(ChatFormatting.GREEN));
        };

        amtBox.setResponder(val -> {
            try {
                int parsed = Integer.parseInt(val.trim());
                expandBrandAmount = Math.max(1, Math.min(parsed, Math.max(1, totalAvailable)));
                updateCalc.run();
            } catch (NumberFormatException ignored) {}
        });

        // Steppers
        FlowLayout minus10 = createFlatButton(Component.literal("-10").withStyle(ChatFormatting.GRAY), 26, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            expandBrandAmount = Math.max(1, expandBrandAmount - 10);
            amtBox.setValue(String.valueOf(expandBrandAmount));
            updateCalc.run();
        });

        FlowLayout minus1 = createFlatButton(Component.literal("-1").withStyle(ChatFormatting.GRAY), 22, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            expandBrandAmount = Math.max(1, expandBrandAmount - 1);
            amtBox.setValue(String.valueOf(expandBrandAmount));
            updateCalc.run();
        });

        FlowLayout plus1 = createFlatButton(Component.literal("+1").withStyle(ChatFormatting.GRAY), 22, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            expandBrandAmount = Math.min(Math.max(1, totalAvailable), expandBrandAmount + 1);
            amtBox.setValue(String.valueOf(expandBrandAmount));
            updateCalc.run();
        });

        FlowLayout plus10 = createFlatButton(Component.literal("+10").withStyle(ChatFormatting.GRAY), 26, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            expandBrandAmount = Math.min(Math.max(1, totalAvailable), expandBrandAmount + 10);
            amtBox.setValue(String.valueOf(expandBrandAmount));
            updateCalc.run();
        });

        FlowLayout maxBtn = createFlatButton(Component.literal("MAX").withStyle(ChatFormatting.WHITE), 30, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            expandBrandAmount = Math.max(1, totalAvailable);
            amtBox.setValue(String.valueOf(expandBrandAmount));
            updateCalc.run();
        });

        amountRow.child(minus10);
        amountRow.child(minus1);
        amountRow.child(amtBox);
        amountRow.child(plus1);
        amountRow.child(plus10);
        amountRow.child(maxBtn);

        modal.child(amountRow);
        modal.child(calcLabel);

        // Confirm button
        FlowLayout confirmBtn;
        if (totalAvailable <= 0) {
            confirmBtn = Containers.horizontalFlow(Sizing.fixed(246), Sizing.fixed(22));
            confirmBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            confirmBtn.surface(Surface.flat(0xFF181B22).and(Surface.outline(0xFF282D38)));
            confirmBtn.child(Components.label(Component.literal("Недостаточно Гос. Марок").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            confirmBtn = createFlatButton(
                    Component.literal("Купить расширение").withStyle(ChatFormatting.WHITE),
                    246, 22,
                    0xFF1D2E22, 0xFF274230, 0xFF3E6B4B,
                    () -> {
                        if (expandBrandAmount > 0 && expandBrandAmount <= totalAvailable) {
                            overlay.remove();
                            NetworkHandler.sendToServer(new ExpandWorldBorderPacket(expandBrandAmount));
                        }
                    }
            );
        }
        confirmBtn.margins(Insets.top(6));
        modal.child(confirmBtn);

        this.uiAdapter.rootComponent.child(overlay);
    }

    // =========================================================================
    // MODAL DIALOGS (OVERLAYS)
    // =========================================================================

    /**
     * Living Character Detail Dossier Modal
     */
    private void openCharacterDetailModal(LivingEntry entry) {
        ActiveCharacter active = entry.active();
        PlayerCharacterData pd = entry.playerData();

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(320), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF454B58)));
        modal.padding(Insets.of(12));
        modal.gap(6);
        modal.zIndex(10);

        OverlayContainer<FlowLayout> overlay = createOverlay(modal);

        // Header with close button
        FlowLayout modalHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        modalHeader.verticalAlignment(VerticalAlignment.CENTER);
        modalHeader.child(Components.label(Component.literal("Досье персонажа").withStyle(ChatFormatting.WHITE)));
        modalHeader.child(Components.spacer());

        FlowLayout xBtn = createIconButton("✕", 16, 16, 2, 0xFF22262E, 0xFF353B47, 0xFF4A5263, overlay::remove);
        modalHeader.child(xBtn);
        modal.child(modalHeader);
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF2E3442)).margins(Insets.bottom(4)));

        // Profile row: Avatar + Name (with Monarch Crown if Knyaz)
        FlowLayout profileRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        profileRow.verticalAlignment(VerticalAlignment.CENTER);
        profileRow.gap(8);
        profileRow.child(new PlayerAvatarComponent(pd.getPlayerUuid(), 36));

        FlowLayout nameCol = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        FlowLayout nameRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        nameRow.verticalAlignment(VerticalAlignment.CENTER);
        nameRow.gap(4);
        nameRow.child(Components.label(Component.literal(active.getName()).withStyle(ChatFormatting.WHITE)));
        if (worldData != null && worldData.isKnyaz(pd.getPlayerUuid())) {
            TextureIconComponent crown = new TextureIconComponent(ICON_MONARCH_CROWN, 12);
            crown.tooltip(Component.literal("Князь").withStyle(ChatFormatting.GOLD));
            nameRow.child(crown);
        }
        nameCol.child(nameRow);
        nameCol.child(Components.label(Component.literal("Игрок: " + pd.getLastKnownPlayerName()).withStyle(ChatFormatting.GRAY)));
        profileRow.child(nameCol);
        modal.child(profileRow);

        // Details Container (Scrollable to prevent overflow on long appearance/backstory)
        FlowLayout detailsFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        detailsFlow.gap(3);

        long currentGameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        long daysAlive = active.getDaysAlive(currentGameTime);

        detailsFlow.child(createWrappedLabel(Component.literal("Возраст: ").withStyle(ChatFormatting.GRAY).append(Component.literal(active.getAge() + " лет").withStyle(ChatFormatting.WHITE))));

        String raceStr = active.getRace().isEmpty() ? "Не указана" : active.getRace();
        detailsFlow.child(createWrappedLabel(Component.literal("Раса: ").withStyle(ChatFormatting.GRAY).append(Component.literal(raceStr).withStyle(ChatFormatting.WHITE))));

        String factStr = pd.getFaction().isEmpty() ? "Не состоит" : pd.getFaction();
        detailsFlow.child(createWrappedLabel(Component.literal("Фракция: ").withStyle(ChatFormatting.GRAY).append(Component.literal(factStr).withStyle(ChatFormatting.WHITE))));
        detailsFlow.child(createWrappedLabel(Component.literal("Жив: ").withStyle(ChatFormatting.GRAY).append(Component.literal(daysAlive + " полных дней (в мире)").withStyle(ChatFormatting.WHITE))));

        // Professions
        String profs = pd.getProfessions().isEmpty() ? "Без профессии" : String.join(", ", pd.getProfessions());
        detailsFlow.child(createWrappedLabel(Component.literal("Профессии: ").withStyle(ChatFormatting.GRAY).append(Component.literal(profs).withStyle(ChatFormatting.WHITE))));

        // Licenses
        String licenses = pd.getLicenses().isEmpty() ? "Отсутствуют (заводы и предприятия)" : String.join(", ", pd.getLicenses());
        detailsFlow.child(createWrappedLabel(Component.literal("Лицензии: ").withStyle(ChatFormatting.GRAY).append(Component.literal(licenses).withStyle(ChatFormatting.DARK_GRAY))));

        // Appearance
        if (!active.getAppearance().isEmpty()) {
            detailsFlow.child(Components.label(Component.literal("Внешность:").withStyle(ChatFormatting.GRAY)).margins(Insets.top(2)));
            LabelComponent appLabel = Components.label(Component.literal(active.getAppearance()).withStyle(ChatFormatting.WHITE));
            appLabel.maxWidth(280);
            detailsFlow.child(appLabel);
        }

        // Description / Backstory
        if (!active.getDescription().isEmpty()) {
            detailsFlow.child(Components.label(Component.literal("Описание (история):").withStyle(ChatFormatting.GRAY)).margins(Insets.top(2)));
            LabelComponent descLabel = Components.label(Component.literal(active.getDescription()).withStyle(ChatFormatting.WHITE));
            descLabel.maxWidth(280);
            detailsFlow.child(descLabel);
        }

        int scrollH = Math.min(200, Math.max(120, (int) (this.height / UI_SCALE) - 150));
        ScrollContainer<FlowLayout> detailsScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(scrollH), detailsFlow);
        detailsScroll.scrollbar(DARK_SCROLLBAR);
        detailsScroll.scrollbarThiccness(4);
        detailsScroll.padding(Insets.right(4));
        modal.child(detailsScroll);

        // Close button
        FlowLayout closeBtn = createFlatButton(Component.literal("Закрыть").withStyle(ChatFormatting.GRAY), 100, 20, 0xFF1E222A, 0xFF2A303C, 0xFF3E4656, overlay::remove);
        closeBtn.margins(Insets.top(6));
        modal.child(closeBtn);

        this.uiAdapter.rootComponent.child(overlay);
    }

    /**
     * Dead Character Detail Dossier Modal ("Досье персонажа (мертв)")
     */
    private void openDeadCharacterDetailModal(DeadEntry entry) {
        DeadCharacter dead = entry.dead();

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(320), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF454B58)));
        modal.padding(Insets.of(12));
        modal.gap(6);
        modal.zIndex(10);

        OverlayContainer<FlowLayout> overlay = createOverlay(modal);

        // Header with close button: "Досье персонажа (мертв)"
        FlowLayout modalHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        modalHeader.verticalAlignment(VerticalAlignment.CENTER);
        modalHeader.child(Components.label(Component.literal("Досье персонажа (мертв)").withStyle(ChatFormatting.WHITE)));
        modalHeader.child(Components.spacer());

        FlowLayout xBtn = createIconButton("✕", 16, 16, 2, 0xFF22262E, 0xFF353B47, 0xFF4A5263, overlay::remove);
        modalHeader.child(xBtn);
        modal.child(modalHeader);
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF2E3442)).margins(Insets.bottom(4)));

        // Profile row: Avatar + Name (dimmed 30% if buried)
        FlowLayout profileRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        profileRow.verticalAlignment(VerticalAlignment.CENTER);
        profileRow.gap(8);
        profileRow.child(new PlayerAvatarComponent(entry.playerUuid(), 36, dead.isBuried() ? 0.70f : 1.0f));

        FlowLayout nameCol = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        nameCol.child(Components.label(Component.literal(dead.getName()).withStyle(ChatFormatting.WHITE)));
        nameCol.child(Components.label(Component.literal("Игрок: " + entry.playerName()).withStyle(ChatFormatting.GRAY)));
        profileRow.child(nameCol);
        modal.child(profileRow);

        // Details Container (Scrollable)
        FlowLayout detailsFlow = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        detailsFlow.gap(3);

        detailsFlow.child(createWrappedLabel(Component.literal("Возраст (при гибели): ").withStyle(ChatFormatting.GRAY).append(Component.literal(dead.getAge() + " лет").withStyle(ChatFormatting.WHITE))));

        String raceStr = dead.getRace().isEmpty() ? "Не указана" : dead.getRace();
        detailsFlow.child(createWrappedLabel(Component.literal("Раса: ").withStyle(ChatFormatting.GRAY).append(Component.literal(raceStr).withStyle(ChatFormatting.WHITE))));

        String factStr = dead.getFaction().isEmpty() ? "Не указана" : dead.getFaction();
        detailsFlow.child(createWrappedLabel(Component.literal("Фракция: ").withStyle(ChatFormatting.GRAY).append(Component.literal(factStr).withStyle(ChatFormatting.WHITE))));
        detailsFlow.child(createWrappedLabel(Component.literal("Причина гибели: ").withStyle(ChatFormatting.GRAY).append(Component.literal(dead.getDeathCause()).withStyle(ChatFormatting.WHITE))));
        detailsFlow.child(createWrappedLabel(Component.literal("Дата гибели: ").withStyle(ChatFormatting.GRAY).append(Component.literal(dead.getFormattedDeathDate()).withStyle(ChatFormatting.DARK_GRAY))));

        // For buried characters: lifespan (Прожил: [n] дней)
        if (dead.isBuried()) {
            long days = dead.getDaysAlive() >= 0 ? dead.getDaysAlive() : 0L;
            detailsFlow.child(createWrappedLabel(Component.literal("Прожил: ").withStyle(ChatFormatting.GRAY).append(Component.literal(days + " дней").withStyle(ChatFormatting.WHITE))));
        }

        String profs = dead.getProfessions().isEmpty() ? "Без профессии" : String.join(", ", dead.getProfessions());
        detailsFlow.child(createWrappedLabel(Component.literal("Профессии: ").withStyle(ChatFormatting.GRAY).append(Component.literal(profs).withStyle(ChatFormatting.WHITE))));

        detailsFlow.child(createWrappedLabel(Component.literal("Лицензии: ").withStyle(ChatFormatting.GRAY).append(Component.literal("Отсутствуют").withStyle(ChatFormatting.DARK_GRAY))));

        // Appearance
        if (!dead.getAppearance().isEmpty()) {
            detailsFlow.child(Components.label(Component.literal("Внешность:").withStyle(ChatFormatting.GRAY)).margins(Insets.top(2)));
            LabelComponent appLabel = Components.label(Component.literal(dead.getAppearance()).withStyle(ChatFormatting.WHITE));
            appLabel.maxWidth(280);
            detailsFlow.child(appLabel);
        }

        // Description / Backstory
        if (!dead.getDescription().isEmpty()) {
            detailsFlow.child(Components.label(Component.literal("Описание (история):").withStyle(ChatFormatting.GRAY)).margins(Insets.top(2)));
            LabelComponent descLabel = Components.label(Component.literal(dead.getDescription()).withStyle(ChatFormatting.WHITE));
            descLabel.maxWidth(280);
            detailsFlow.child(descLabel);
        }

        int scrollH = Math.min(200, Math.max(120, (int) (this.height / UI_SCALE) - 150));
        ScrollContainer<FlowLayout> detailsScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(scrollH), detailsFlow);
        detailsScroll.scrollbar(DARK_SCROLLBAR);
        detailsScroll.scrollbarThiccness(4);
        detailsScroll.padding(Insets.right(4));
        modal.child(detailsScroll);

        // Close button
        FlowLayout closeBtn = createFlatButton(Component.literal("Закрыть").withStyle(ChatFormatting.GRAY), 100, 20, 0xFF1E222A, 0xFF2A303C, 0xFF3E4656, overlay::remove);
        closeBtn.margins(Insets.top(6));
        modal.child(closeBtn);

        this.uiAdapter.rootComponent.child(overlay);
    }

    private LabelComponent createWrappedLabel(Component text) {
        LabelComponent label = Components.label(text);
        label.maxWidth(280);
        return label;
    }

    /**
     * Profession Selection Modal
     */
    private void openProfessionModal(Profession prof) {
        ProfessionCategory cat = prof.getType();
        int modalBorder = cat.getColor();
        int dividerColor = cat == ProfessionCategory.STATE ? 0xFF5A1E20 : (cat == ProfessionCategory.MAIN ? 0xFF1E3824 : 0xFF4A2B18);

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(320), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(modalBorder)));
        modal.padding(Insets.of(12));
        modal.gap(6);
        modal.zIndex(10);

        OverlayContainer<FlowLayout> overlay = createOverlay(modal);

        // Header
        FlowLayout modalHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        modalHeader.verticalAlignment(VerticalAlignment.CENTER);
        modalHeader.child(Components.label(Component.literal(prof.getTitle()).withStyle(style -> style.withColor(modalBorder))));
        modalHeader.child(Components.spacer());

        FlowLayout xBtn = createIconButton("✕", 16, 16, 2, 0xFF22262E, 0xFF353B47, 0xFF4A5263, overlay::remove);
        modalHeader.child(xBtn);
        modal.child(modalHeader);
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(dividerColor)).margins(Insets.bottom(4)));

        // Occupants section
        List<LivingEntry> occupants = getProfessionOccupants(prof);
        String occHeader = cat == ProfessionCategory.STATE ? "Занимают должность (" : "Занимаются профессией (";
        modal.child(Components.label(Component.literal(occHeader + occupants.size() + "/" + prof.getMaxOccupants() + "):").withStyle(ChatFormatting.WHITE)));

        if (occupants.isEmpty()) {
            modal.child(Components.label(Component.literal("Пока никто не занимает эту должность.").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            for (LivingEntry occ : occupants) {
                FlowLayout occRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
                occRow.verticalAlignment(VerticalAlignment.CENTER);
                occRow.gap(6);
                occRow.child(new PlayerAvatarComponent(occ.playerData().getPlayerUuid(), 20));

                boolean isMe = Minecraft.getInstance().player != null && Minecraft.getInstance().player.getUUID().equals(occ.playerData().getPlayerUuid());
                Component occTitle = Component.literal(occ.active().getName()).withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(" [" + occ.playerData().getLastKnownPlayerName() + "]").withStyle(ChatFormatting.GRAY));
                if (isMe) {
                    occTitle = occTitle.copy().append(Component.literal(" ✔").withStyle(style -> style.withColor(modalBorder)));
                }
                occRow.child(Components.label(occTitle));
                modal.child(occRow);
            }
        }

        // Description section
        modal.child(Components.label(Component.literal("Описание:").withStyle(ChatFormatting.GRAY)).margins(Insets.top(4)));
        LabelComponent descLabel = Components.label(Component.literal(prof.getDescription()).withStyle(ChatFormatting.WHITE));
        descLabel.maxWidth(290);
        modal.child(descLabel);

        // Action button or State notice
        if (cat == ProfessionCategory.STATE) {
            FlowLayout noteBox = Containers.horizontalFlow(Sizing.fixed(290), Sizing.fixed(22));
            noteBox.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            noteBox.surface(Surface.flat(0xFF1B1E26).and(Surface.outline(0xFF2C323E)));
            noteBox.child(Components.label(Component.literal("Государственная должность назначается правителем").withStyle(ChatFormatting.GRAY)));
            noteBox.margins(Insets.top(8));
            modal.child(noteBox);
        } else {
            boolean isFull = occupants.size() >= prof.getMaxOccupants();
            boolean alreadyHave = isClientInProfession(prof);

            FlowLayout selectBtn;
            if (alreadyHave) {
                selectBtn = Containers.horizontalFlow(Sizing.fixed(290), Sizing.fixed(22));
                selectBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                selectBtn.surface(Surface.flat(0xFF1B1E26).and(Surface.outline(0xFF2C323E)));
                selectBtn.child(Components.label(Component.literal("Вы уже выбрали эту профессию").withStyle(ChatFormatting.GRAY)));
            } else if (isFull) {
                selectBtn = Containers.horizontalFlow(Sizing.fixed(290), Sizing.fixed(22));
                selectBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                selectBtn.surface(Surface.flat(0xFF1B1E26).and(Surface.outline(0xFF2C323E)));
                selectBtn.child(Components.label(Component.literal("Все места заняты").withStyle(ChatFormatting.GRAY)));
            } else {
                boolean isMain = cat == ProfessionCategory.MAIN;
                int btnBg = isMain ? 0xFF142B1A : 0xFF2E1B12;
                int btnHover = isMain ? 0xFF1E3D25 : 0xFF402619;
                int btnBorder = isMain ? 0xFF2E7D32 : 0xFFFFA500;
                selectBtn = createFlatButton(
                        Component.literal("Выбрать профессию").withStyle(ChatFormatting.WHITE),
                        290, 22,
                        btnBg, btnHover, btnBorder,
                        () -> {
                            overlay.remove();
                            NetworkHandler.sendToServer(new SelectProfessionPacket(prof.getId()));
                        }
                );
            }
            selectBtn.margins(Insets.top(8));
            modal.child(selectBtn);
        }

        this.uiAdapter.rootComponent.child(overlay);
    }

    /**
     * State Brands Deposit / Withdraw Modal (Standard White / Gray, NO bold, with Input Field)
     */
    private int transferAmount = 1;
    private void openBrandModal(boolean isDeposit) {
        int maxAvailable = isDeposit ? getInventoryStateBrands() : getMyStateBrands();
        transferAmount = Math.min(1, maxAvailable);

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(260), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF454B58)));
        modal.padding(Insets.of(12));
        modal.gap(6);
        modal.zIndex(10);

        OverlayContainer<FlowLayout> overlay = createOverlay(modal);

        // Header: Standard white/gray, no bold
        FlowLayout modalHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        modalHeader.verticalAlignment(VerticalAlignment.CENTER);
        String title = isDeposit ? "Внесение Гос. Марок" : "Снятие Гос. Марок";
        modalHeader.child(Components.label(Component.literal(title).withStyle(ChatFormatting.WHITE)));
        modalHeader.child(Components.spacer());

        FlowLayout xBtn = createIconButton("✕", 16, 16, 2, 0xFF22262E, 0xFF353B47, 0xFF4A5263, overlay::remove);
        modalHeader.child(xBtn);
        modal.child(modalHeader);
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF2E3442)).margins(Insets.bottom(4)));

        String availStr = isDeposit ? "В инвентаре: " + maxAvailable + " шт." : "На счету: " + maxAvailable + " шт.";
        modal.child(Components.label(Component.literal(availStr).withStyle(ChatFormatting.GRAY)));

        // Amount Input & Stepper Controls Row
        FlowLayout amountRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        amountRow.verticalAlignment(VerticalAlignment.CENTER);
        amountRow.horizontalAlignment(HorizontalAlignment.CENTER);
        amountRow.gap(4);

        // Input Field for amount
        EditBoxComponent amtBox = new EditBoxComponent(Minecraft.getInstance().font, 56, 18, Component.literal("РљРѕР»РёС‡РµСЃС‚РІРѕ"));
        amtBox.setMaxLength(6);
        amtBox.setValue(String.valueOf(transferAmount));
        amtBox.setFilter(s -> s.matches("\\d*"));
        amtBox.setResponder(val -> {
            try {
                int parsed = Integer.parseInt(val.trim());
                transferAmount = Math.max(1, Math.min(parsed, maxAvailable));
            } catch (NumberFormatException ignored) {}
        });

        // Steppers
        FlowLayout minus10 = createFlatButton(Component.literal("-10").withStyle(ChatFormatting.GRAY), 26, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            transferAmount = Math.max(1, transferAmount - 10);
            amtBox.setValue(String.valueOf(transferAmount));
        });

        FlowLayout minus1 = createFlatButton(Component.literal("-1").withStyle(ChatFormatting.GRAY), 22, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            transferAmount = Math.max(1, transferAmount - 1);
            amtBox.setValue(String.valueOf(transferAmount));
        });

        FlowLayout plus1 = createFlatButton(Component.literal("+1").withStyle(ChatFormatting.GRAY), 22, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            transferAmount = Math.min(maxAvailable, transferAmount + 1);
            amtBox.setValue(String.valueOf(transferAmount));
        });

        FlowLayout plus10 = createFlatButton(Component.literal("+10").withStyle(ChatFormatting.GRAY), 26, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            transferAmount = Math.min(maxAvailable, transferAmount + 10);
            amtBox.setValue(String.valueOf(transferAmount));
        });

        FlowLayout maxBtn = createFlatButton(Component.literal("MAX").withStyle(ChatFormatting.WHITE), 30, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            transferAmount = maxAvailable;
            amtBox.setValue(String.valueOf(transferAmount));
        });

        amountRow.child(minus10);
        amountRow.child(minus1);
        amountRow.child(amtBox);
        amountRow.child(plus1);
        amountRow.child(plus10);
        amountRow.child(maxBtn);

        modal.child(amountRow);

        // Action confirm button (standard white/gray, no bold)
        String actionText = isDeposit ? "Внести на счет" : "Снять в инвентарь";
        FlowLayout confirmBtn;
        if (maxAvailable <= 0) {
            confirmBtn = Containers.horizontalFlow(Sizing.fixed(236), Sizing.fixed(22));
            confirmBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            confirmBtn.surface(Surface.flat(0xFF181B22).and(Surface.outline(0xFF282D38)));
            confirmBtn.child(Components.label(Component.literal(actionText).withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            confirmBtn = createFlatButton(
                    Component.literal(actionText).withStyle(ChatFormatting.WHITE),
                    236, 22,
                    0xFF222630, 0xFF2F3542, 0xFF485164,
                    () -> {
                        if (transferAmount > 0 && transferAmount <= maxAvailable) {
                            overlay.remove();
                            NetworkHandler.sendToServer(new DepositWithdrawStateBrandPacket(isDeposit, transferAmount));
                        }
                    }
            );
        }
        confirmBtn.margins(Insets.top(8));
        modal.child(confirmBtn);

        this.uiAdapter.rootComponent.child(overlay);
    }

    private int getInventorySprota() {
        if (Minecraft.getInstance().player != null) {
            int count = 0;
            for (ItemStack stack : Minecraft.getInstance().player.getInventory().items) {
                if (stack.is(com.oneshop.item.ModItems.SPROTA.get())) {
                    count += stack.getCount();
                }
            }
            return count;
        }
        return 0;
    }

    /**
     * Pay Tax Modal ("Оплатить налог")
     * Allows paying tax debt and prepaying up to taxRate * 3.
     */
    private long payTaxAmount = 10;

    private void openPayTaxModal() {
        PlayerCharacterData pd = getMyPlayerData();
        long balance = (long) ClientBankCache.getBalance();
        long currentStatus = pd != null ? pd.getTaxStatus() : 0L;
        int taxRate = worldData != null ? worldData.getTaxRate() : 10;
        long maxStatus = taxRate * 3L;
        long neededToMax = Math.max(0, maxStatus - currentStatus);

        long debt = Math.max(0, -currentStatus);
        if (debt > 0 && balance >= debt) {
            payTaxAmount = debt;
        } else if (debt > 0) {
            payTaxAmount = balance;
        } else {
            payTaxAmount = Math.min(Math.min(taxRate, balance), Math.max(1, neededToMax));
        }
        if (payTaxAmount <= 0 && balance > 0 && neededToMax > 0) {
            payTaxAmount = 1;
        }

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(280), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF437547)));
        modal.padding(Insets.of(12));
        modal.gap(6);
        modal.zIndex(10);

        OverlayContainer<FlowLayout> overlay = createOverlay(modal);

        // Header
        FlowLayout modalHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        modalHeader.verticalAlignment(VerticalAlignment.CENTER);
        modalHeader.gap(5);

        ItemComponent headerIngot = Components.item(new ItemStack(com.oneshop.item.ModItems.SPROTA.get()));
        headerIngot.sizing(Sizing.fixed(14), Sizing.fixed(14));
        modalHeader.child(headerIngot);

        modalHeader.child(Components.label(Component.literal("Оплата Налога").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
        modalHeader.child(Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0)));

        FlowLayout xBtn = createIconButton("✕", 16, 16, 2, 0xFF22262E, 0xFF353B47, 0xFF4A5263, overlay::remove);
        modalHeader.child(xBtn);
        modal.child(modalHeader);
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF263D2B)).margins(Insets.bottom(4)));

        // Status lines
        FlowLayout balRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        balRow.child(Components.label(Component.literal("Ваш баланс: ").withStyle(ChatFormatting.GRAY)));
        balRow.child(Components.label(Component.literal(String.format("%,d", balance).replace(',', ' ') + " $PR").withStyle(ChatFormatting.GOLD)));
        modal.child(balRow);

        FlowLayout statusRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        statusRow.child(Components.label(Component.literal("Текущий статус: ").withStyle(ChatFormatting.GRAY)));
        String curStatusStr = (currentStatus > 0 ? "+" : "") + String.format("%,d", currentStatus).replace(',', ' ') + " $PR";
        ChatFormatting stCol = currentStatus < 0 ? ChatFormatting.RED : (currentStatus >= taxRate ? ChatFormatting.GREEN : ChatFormatting.WHITE);
        statusRow.child(Components.label(Component.literal(curStatusStr).withStyle(stCol, ChatFormatting.BOLD)));
        modal.child(statusRow);

        FlowLayout maxRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        maxRow.child(Components.label(Component.literal("Максимум статуса: ").withStyle(ChatFormatting.GRAY)));
        maxRow.child(Components.label(Component.literal("+" + String.format("%,d", maxStatus).replace(',', ' ') + " $PR (" + taxRate + " × 3)").withStyle(ChatFormatting.DARK_GREEN)));
        modal.child(maxRow);

        // Dynamic result calculation label
        LabelComponent calcLabel = Components.label(Component.literal("").withStyle(ChatFormatting.GRAY));
        calcLabel.horizontalTextAlignment(HorizontalAlignment.CENTER);

        long maxPayable = Math.min(balance, neededToMax);

        Runnable updateCalc = () -> {
            if (neededToMax <= 0) {
                calcLabel.text(Component.literal("✔ Налоговый статус уже на максимуме").withStyle(ChatFormatting.GREEN));
            } else if (payTaxAmount <= 0) {
                calcLabel.text(Component.literal("Введите сумму оплаты").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                long afterStatus = Math.min(maxStatus, currentStatus + payTaxAmount);
                String afterStr = (afterStatus > 0 ? "+" : "") + String.format("%,d", afterStatus).replace(',', ' ') + " $PR";
                ChatFormatting aftCol = afterStatus < 0 ? ChatFormatting.RED : (afterStatus >= taxRate ? ChatFormatting.GREEN : ChatFormatting.WHITE);
                calcLabel.text(Component.literal("Статус после оплаты: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(afterStr).withStyle(aftCol, ChatFormatting.BOLD)));
            }
        };

        // Amount Input & Stepper Controls Row
        FlowLayout amountRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        amountRow.verticalAlignment(VerticalAlignment.CENTER);
        amountRow.horizontalAlignment(HorizontalAlignment.CENTER);
        amountRow.gap(4);

        EditBoxComponent amtBox = new EditBoxComponent(Minecraft.getInstance().font, 64, 18, Component.literal("РЎСѓРјРјР°"));
        amtBox.setMaxLength(10);
        amtBox.setValue(String.valueOf(payTaxAmount));
        amtBox.setFilter(s -> s.matches("\\d*"));

        amtBox.setResponder(val -> {
            try {
                long parsed = Long.parseLong(val.trim());
                payTaxAmount = Math.max(1, Math.min(parsed, Math.max(1, maxPayable)));
                updateCalc.run();
            } catch (NumberFormatException ignored) {}
        });

        FlowLayout minus100 = createFlatButton(Component.literal("-100").withStyle(ChatFormatting.GRAY), 32, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            payTaxAmount = Math.max(1, payTaxAmount - 100);
            amtBox.setValue(String.valueOf(payTaxAmount));
            updateCalc.run();
        });

        FlowLayout minus10 = createFlatButton(Component.literal("-10").withStyle(ChatFormatting.GRAY), 26, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            payTaxAmount = Math.max(1, payTaxAmount - 10);
            amtBox.setValue(String.valueOf(payTaxAmount));
            updateCalc.run();
        });

        FlowLayout plus10 = createFlatButton(Component.literal("+10").withStyle(ChatFormatting.GRAY), 26, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            payTaxAmount = Math.min(Math.max(1, maxPayable), payTaxAmount + 10);
            amtBox.setValue(String.valueOf(payTaxAmount));
            updateCalc.run();
        });

        FlowLayout plus100 = createFlatButton(Component.literal("+100").withStyle(ChatFormatting.GRAY), 32, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            payTaxAmount = Math.min(Math.max(1, maxPayable), payTaxAmount + 100);
            amtBox.setValue(String.valueOf(payTaxAmount));
            updateCalc.run();
        });

        amountRow.child(minus100);
        amountRow.child(minus10);
        amountRow.child(amtBox);
        amountRow.child(plus10);
        amountRow.child(plus100);
        modal.child(amountRow);

        // Quick buttons row
        FlowLayout quickRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        quickRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        quickRow.gap(6);

        if (debt > 0) {
            FlowLayout payDebtBtn = createFlatButton(Component.literal("Погасить долг").withStyle(ChatFormatting.YELLOW), 95, 18, 0xFF262112, 0xFF3A3018, 0xFF8A6D24, () -> {
                payTaxAmount = Math.max(1, Math.min(debt, Math.max(1, maxPayable)));
                amtBox.setValue(String.valueOf(payTaxAmount));
                updateCalc.run();
            });
            quickRow.child(payDebtBtn);
        }

        FlowLayout toMaxBtn = createFlatButton(Component.literal("До максимума").withStyle(ChatFormatting.WHITE), 90, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            payTaxAmount = Math.max(1, maxPayable);
            amtBox.setValue(String.valueOf(payTaxAmount));
            updateCalc.run();
        });
        quickRow.child(toMaxBtn);

        modal.child(quickRow);
        modal.child(calcLabel);
        updateCalc.run();

        // Confirm button
        FlowLayout confirmBtn;
        if (neededToMax <= 0) {
            confirmBtn = Containers.horizontalFlow(Sizing.fixed(256), Sizing.fixed(22));
            confirmBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            confirmBtn.surface(Surface.flat(0xFF181B22).and(Surface.outline(0xFF282D38)));
            confirmBtn.child(Components.label(Component.literal("Статус уже на максимуме").withStyle(ChatFormatting.DARK_GRAY)));
        } else if (balance <= 0) {
            confirmBtn = Containers.horizontalFlow(Sizing.fixed(256), Sizing.fixed(22));
            confirmBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            confirmBtn.surface(Surface.flat(0xFF181B22).and(Surface.outline(0xFF282D38)));
            confirmBtn.child(Components.label(Component.literal("Недостаточно средств").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            confirmBtn = createFlatButton(
                    Component.literal("Оплатить налог").withStyle(ChatFormatting.WHITE),
                    256, 22,
                    0xFF1C2B1D, 0xFF273D29, 0xFF437547,
                    () -> {
                        if (payTaxAmount > 0 && payTaxAmount <= balance) {
                            overlay.remove();
                            NetworkHandler.sendToServer(new PayTaxPacket(payTaxAmount));
                        }
                    }
            );
        }
        confirmBtn.margins(Insets.top(4));
        modal.child(confirmBtn);

        this.uiAdapter.rootComponent.child(overlay);
    }

    /**
     * Treasury Deposit Modal ("Внести деньги в казну")
     * Allows depositing custom money into treasury.
     * Rewards State Brands based on progression (250, 500, 1000, 2000, 4000, 8000, 16000 max).
     * Surcharge resets every 3 hours of real time.
     */
    private long depositTreasuryAmount = 250;

    private void openDepositTreasuryModal() {
        PlayerCharacterData pd = getMyPlayerData();
        long balance = (long) ClientBankCache.getBalance();
        if (pd != null) {
            pd.checkBrandReset();
        }

        int curTier = pd != null ? pd.getBrandTier() : 0;
        long curProg = pd != null ? pd.getBrandProgress() : 0L;
        int curCost = pd != null ? pd.getCurrentBrandCost() : 250;

        depositTreasuryAmount = Math.min(Math.max(1, curCost - curProg), Math.max(1, balance));

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(280), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF4A3E18)));
        modal.padding(Insets.of(12));
        modal.gap(5);
        modal.zIndex(10);

        OverlayContainer<FlowLayout> overlay = createOverlay(modal);

        // Header
        FlowLayout modalHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        modalHeader.verticalAlignment(VerticalAlignment.CENTER);
        modalHeader.gap(5);

        ItemComponent headerIngot = Components.item(new ItemStack(com.oneshop.item.ModItems.SPROTA.get()));
        headerIngot.sizing(Sizing.fixed(14), Sizing.fixed(14));
        modalHeader.child(headerIngot);

        modalHeader.child(Components.label(Component.literal("Внесение в Казну").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        modalHeader.child(Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0)));

        FlowLayout xBtn = createIconButton("✕", 16, 16, 2, 0xFF22262E, 0xFF353B47, 0xFF4A5263, overlay::remove);
        modalHeader.child(xBtn);
        modal.child(modalHeader);
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF38311B)).margins(Insets.bottom(4)));

        // Balance & Weekly deposited row
        FlowLayout balRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        balRow.verticalAlignment(VerticalAlignment.CENTER);
        balRow.child(Components.label(Component.literal("Ваш баланс: ").withStyle(ChatFormatting.GRAY)));
        balRow.child(Components.label(Component.literal(String.format("%,d", balance).replace(',', ' ') + " $PR").withStyle(ChatFormatting.GOLD)));

        balRow.child(Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0)));

        long weeklyDep = pd != null ? pd.getWeeklyDeposit() : 0L;
        String formattedWeeklyDep = String.format("%,d", weeklyDep).replace(',', ' ') + " $PR";
        balRow.child(Components.label(Component.literal("Внесено: " + formattedWeeklyDep).withStyle(ChatFormatting.GRAY)));

        modal.child(balRow);

        // Converter banner: curCost $PR -> 1 [icon] Гос. Марка
        FlowLayout converterRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        converterRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        converterRow.surface(Surface.flat(0xFF191F26).and(Surface.outline(0xFF3D3219)));
        converterRow.padding(Insets.horizontal(8));
        converterRow.gap(5);

        converterRow.child(Components.label(Component.literal(curCost + " $PR").withStyle(ChatFormatting.GOLD)));

        LabelComponent limeArrow = Components.label(Component.literal(" ➔ "));
        limeArrow.color(Color.ofRgb(0xFF72E060));
        converterRow.child(limeArrow);

        converterRow.child(Components.label(Component.literal("+1 ").withStyle(ChatFormatting.WHITE)));
        converterRow.child(new TextureIconComponent(ICON_STATE_BRAND, 12));
        converterRow.child(Components.label(Component.literal("Гос. Марка").withStyle(ChatFormatting.WHITE)));
        modal.child(converterRow);

        // Amount Input (declared before tiersRow so tier cells can set its value)
        EditBoxComponent amtBox = new EditBoxComponent(Minecraft.getInstance().font, 56, 18, Component.literal("Сумма"));
        amtBox.setMaxLength(10);
        amtBox.setValue(String.valueOf(depositTreasuryAmount));
        amtBox.setFilter(s -> s.matches("\\d*"));

        amtBox.setResponder(val -> {
            try {
                long parsed = Long.parseLong(val.trim());
                depositTreasuryAmount = Math.max(1, Math.min(parsed, Math.max(1, balance)));
            } catch (NumberFormatException ignored) {}
        });

        // 5 Tiers under converter row: 250, 500, 1000, 2000, 4000
        FlowLayout tiersRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        tiersRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        tiersRow.gap(4);

        int[] brandTiers = {250, 500, 1000, 2000, 4000};
        for (int i = 0; i < brandTiers.length; i++) {
            int tierCost = brandTiers[i];
            boolean isReceived = curTier > i;
            boolean isCurrent = curTier == i;

            FlowLayout cell = Containers.horizontalFlow(Sizing.fixed(48), Sizing.fixed(20));
            cell.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

            if (isReceived) {
                // Dimmed / faded gray when brand has been received
                cell.surface(Surface.flat(0xFF14161C).and(Surface.outline(0xFF22252C)));
                LabelComponent lbl = Components.label(Component.literal(String.valueOf(tierCost)));
                lbl.color(Color.ofRgb(0xFF525966));
                cell.child(lbl);
                cell.tooltip(Component.literal("Гос. марка получена (" + tierCost + " $PR)").withStyle(ChatFormatting.DARK_GRAY));
            } else if (isCurrent) {
                // Current active tier - neutral active styling
                cell.surface(Surface.flat(0xFF222733).and(Surface.outline(0xFF3E485C)));
                cell.child(Components.label(Component.literal(String.valueOf(tierCost)).withStyle(ChatFormatting.WHITE)));
                cell.cursorStyle(CursorStyle.HAND);
                cell.tooltip(Component.literal("Текущая стоимость: " + tierCost + " $PR\nНажмите, чтобы выбрать").withStyle(ChatFormatting.GRAY));
                cell.mouseEnter().subscribe(() -> cell.surface(Surface.flat(0xFF2A3140).and(Surface.outline(0xFF4F5B73))));
                cell.mouseLeave().subscribe(() -> cell.surface(Surface.flat(0xFF222733).and(Surface.outline(0xFF3E485C))));
                cell.mouseDown().subscribe((mx, my, btn) -> {
                    if (btn == 0) {
                        depositTreasuryAmount = Math.min(tierCost, Math.max(1, balance));
                        amtBox.setValue(String.valueOf(depositTreasuryAmount));
                        return true;
                    }
                    return false;
                });
            } else {
                // Upcoming tier - neutral waiting styling
                cell.surface(Surface.flat(0xFF1B1E26).and(Surface.outline(0xFF2D3442)));
                cell.child(Components.label(Component.literal(String.valueOf(tierCost)).withStyle(ChatFormatting.GRAY)));
                cell.cursorStyle(CursorStyle.HAND);
                cell.tooltip(Component.literal("Следующая стоимость: " + tierCost + " $PR\nНажмите, чтобы выбрать").withStyle(ChatFormatting.GRAY));
                cell.mouseEnter().subscribe(() -> cell.surface(Surface.flat(0xFF242833).and(Surface.outline(0xFF3B4456))));
                cell.mouseLeave().subscribe(() -> cell.surface(Surface.flat(0xFF1B1E26).and(Surface.outline(0xFF2D3442))));
                cell.mouseDown().subscribe((mx, my, btn) -> {
                    if (btn == 0) {
                        depositTreasuryAmount = Math.min(tierCost, Math.max(1, balance));
                        amtBox.setValue(String.valueOf(depositTreasuryAmount));
                        return true;
                    }
                    return false;
                });
            }
            tiersRow.child(cell);
        }
        modal.child(tiersRow);

        long remainingResetMs = clientWeekTicksRemaining > 0 ? (clientWeekTicksRemaining * 50L) : (pd != null ? pd.getLongCycleRemainingMs() : PlayerCharacterData.BRAND_RESET_INTERVAL_MS);
        int remHours = (int) (remainingResetMs / 3600000L);
        int remMins = (int) ((remainingResetMs % 3600000L) / 60000L);
        int remSecs = (int) ((remainingResetMs % 60000L) / 1000L);
        String timeStr = remHours > 0 ? String.format("%d:%02d:%02d", remHours, remMins, remSecs) : String.format("%02d:%02d", remMins, remSecs);

        // Reset markup timer - centered
        FlowLayout resetRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        resetRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        resetRow.gap(4);
        resetRow.child(Components.label(Component.literal("Сброс наценки через: ").withStyle(ChatFormatting.DARK_GRAY)));
        resetRow.child(Components.label(Component.literal(timeStr).withStyle(ChatFormatting.GRAY)));
        modal.child(resetRow);

        // Amount Input & Stepper Controls Row (with "Все" button)
        FlowLayout amountRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        amountRow.verticalAlignment(VerticalAlignment.CENTER);
        amountRow.horizontalAlignment(HorizontalAlignment.CENTER);
        amountRow.gap(4);

        FlowLayout minus100 = createFlatButton(Component.literal("-100").withStyle(ChatFormatting.GRAY), 32, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            depositTreasuryAmount = Math.max(1, depositTreasuryAmount - 100);
            amtBox.setValue(String.valueOf(depositTreasuryAmount));
        });

        FlowLayout minus10 = createFlatButton(Component.literal("-10").withStyle(ChatFormatting.GRAY), 26, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            depositTreasuryAmount = Math.max(1, depositTreasuryAmount - 10);
            amtBox.setValue(String.valueOf(depositTreasuryAmount));
        });

        FlowLayout plus10 = createFlatButton(Component.literal("+10").withStyle(ChatFormatting.GRAY), 26, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            depositTreasuryAmount = Math.min(Math.max(1, balance), depositTreasuryAmount + 10);
            amtBox.setValue(String.valueOf(depositTreasuryAmount));
        });

        FlowLayout plus100 = createFlatButton(Component.literal("+100").withStyle(ChatFormatting.GRAY), 32, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            depositTreasuryAmount = Math.min(Math.max(1, balance), depositTreasuryAmount + 100);
            amtBox.setValue(String.valueOf(depositTreasuryAmount));
        });

        FlowLayout allBtn = createFlatButton(Component.literal("Все").withStyle(ChatFormatting.WHITE), 32, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            depositTreasuryAmount = Math.max(1, balance);
            amtBox.setValue(String.valueOf(depositTreasuryAmount));
        });

        amountRow.child(minus100);
        amountRow.child(minus10);
        amountRow.child(amtBox);
        amountRow.child(plus10);
        amountRow.child(plus100);
        amountRow.child(allBtn);
        modal.child(amountRow);

        // Confirm button
        FlowLayout confirmBtn;
        if (balance <= 0) {
            confirmBtn = Containers.horizontalFlow(Sizing.fixed(256), Sizing.fixed(22));
            confirmBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            confirmBtn.surface(Surface.flat(0xFF181B22).and(Surface.outline(0xFF282D38)));
            confirmBtn.child(Components.label(Component.literal("Недостаточно средств на балансе").withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            confirmBtn = createFlatButton(
                    Component.literal("Внести в казну").withStyle(ChatFormatting.WHITE),
                    256, 22,
                    0xFF2A2312, 0xFF3D3219, 0xFFD4AF37,
                    () -> {
                        if (depositTreasuryAmount > 0 && depositTreasuryAmount <= balance) {
                            overlay.remove();
                            NetworkHandler.sendToServer(new DepositTreasuryPacket(depositTreasuryAmount));
                        }
                    }
            );
        }
        confirmBtn.margins(Insets.top(4));
        modal.child(confirmBtn);

        this.uiAdapter.rootComponent.child(overlay);
    }



    /**
     * Set Tax Rate Modal (Knyaz / Treasurer only)
     */
    private int newTaxRateSetting = 10;

    private void openSetTaxRateModal() {
        int currentRate = worldData != null ? worldData.getTaxRate() : 10;
        newTaxRateSetting = currentRate;

        FlowLayout modal = Containers.verticalFlow(Sizing.fixed(260), Sizing.content());
        modal.surface(Surface.flat(0xFF14171E).and(Surface.outline(0xFF4A3E18)));
        modal.padding(Insets.of(12));
        modal.gap(6);
        modal.zIndex(10);

        OverlayContainer<FlowLayout> overlay = createOverlay(modal);

        // Header
        FlowLayout modalHeader = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        modalHeader.verticalAlignment(VerticalAlignment.CENTER);
        modalHeader.gap(5);

        ItemComponent headerIngot = Components.item(new ItemStack(com.oneshop.item.ModItems.SPROTA.get()));
        headerIngot.sizing(Sizing.fixed(14), Sizing.fixed(14));
        modalHeader.child(headerIngot);

        modalHeader.child(Components.label(Component.literal("Установка Налога").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        modalHeader.child(Components.spacer());

        FlowLayout xBtn = createIconButton("✕", 16, 16, 2, 0xFF22262E, 0xFF353B47, 0xFF4A5263, overlay::remove);
        modalHeader.child(xBtn);
        modal.child(modalHeader);
        modal.child(Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofRgb(0xFF38311B)).margins(Insets.bottom(4)));

        modal.child(Components.label(Component.literal("Ставка налога начисляется всем игрокам в задолженность каждые 20 минут.").withStyle(ChatFormatting.GRAY)));

        // Amount Input & Stepper Controls Row
        FlowLayout amountRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        amountRow.verticalAlignment(VerticalAlignment.CENTER);
        amountRow.horizontalAlignment(HorizontalAlignment.CENTER);
        amountRow.gap(4);

        EditBoxComponent rateBox = new EditBoxComponent(Minecraft.getInstance().font, 56, 18, Component.literal("РЎС‚Р°РІРєР°"));
        rateBox.setMaxLength(6);
        rateBox.setValue(String.valueOf(newTaxRateSetting));
        rateBox.setFilter(s -> s.matches("\\d*"));

        rateBox.setResponder(val -> {
            try {
                int parsed = Integer.parseInt(val.trim());
                newTaxRateSetting = Math.max(0, parsed);
            } catch (NumberFormatException ignored) {}
        });

        FlowLayout minus10 = createFlatButton(Component.literal("-10").withStyle(ChatFormatting.GRAY), 26, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            newTaxRateSetting = Math.max(0, newTaxRateSetting - 10);
            rateBox.setValue(String.valueOf(newTaxRateSetting));
        });

        FlowLayout minus1 = createFlatButton(Component.literal("-1").withStyle(ChatFormatting.GRAY), 22, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            newTaxRateSetting = Math.max(0, newTaxRateSetting - 1);
            rateBox.setValue(String.valueOf(newTaxRateSetting));
        });

        FlowLayout plus1 = createFlatButton(Component.literal("+1").withStyle(ChatFormatting.GRAY), 22, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            newTaxRateSetting = newTaxRateSetting + 1;
            rateBox.setValue(String.valueOf(newTaxRateSetting));
        });

        FlowLayout plus10 = createFlatButton(Component.literal("+10").withStyle(ChatFormatting.GRAY), 26, 18, 0xFF1C1F26, 0xFF292E38, 0xFF3C4352, () -> {
            newTaxRateSetting = newTaxRateSetting + 10;
            rateBox.setValue(String.valueOf(newTaxRateSetting));
        });

        amountRow.child(minus10);
        amountRow.child(minus1);
        amountRow.child(rateBox);
        amountRow.child(plus1);
        amountRow.child(plus10);

        modal.child(amountRow);

        // Confirm button
        FlowLayout confirmBtn = createFlatButton(
                Component.literal("Сохранить ставку").withStyle(ChatFormatting.WHITE),
                236, 22,
                0xFF2A2312, 0xFF3D3219, 0xFFD4AF37,
                () -> {
                    overlay.remove();
                    NetworkHandler.sendToServer(new SetTaxRatePacket(newTaxRateSetting));
                }
        );
        confirmBtn.margins(Insets.top(6));
        modal.child(confirmBtn);

        this.uiAdapter.rootComponent.child(overlay);
    }

    // =========================================================================
    // DATA HELPERS
    // =========================================================================
    private PlayerCharacterData getMyPlayerData() {
        if (Minecraft.getInstance().player != null && worldData != null) {
            return worldData.getPlayerData(Minecraft.getInstance().player.getUUID());
        }
        return null;
    }

    private int getMyStateBrands() {
        PlayerCharacterData pd = getMyPlayerData();
        return pd != null ? pd.getStateBrands() : 0;
    }

    private int getInventoryStateBrands() {
        if (Minecraft.getInstance().player != null) {
            int count = 0;
            for (ItemStack stack : Minecraft.getInstance().player.getInventory().items) {
                if (stack.is(ModItems.STATE_BRAND.get())) {
                    count += stack.getCount();
                }
            }
            return count;
        }
        return 0;
    }

    private int getMyMainProfSlots() {
        PlayerCharacterData pd = getMyPlayerData();
        return pd != null ? pd.getMainProfessionSlots() : 1;
    }

    private int getMySecondaryProfSlots() {
        PlayerCharacterData pd = getMyPlayerData();
        return pd != null ? pd.getSecondaryProfessionSlots() : 2;
    }

    private List<LivingEntry> getProfessionOccupants(Profession prof) {
        List<LivingEntry> list = new ArrayList<>();
        if (worldData != null) {
            for (PlayerCharacterData pd : worldData.getAllPlayerData().values()) {
                if (pd.hasActiveCharacter() && (pd.hasProfession(prof.getTitle()) || pd.hasProfession(String.valueOf(prof.getId())))) {
                    list.add(new LivingEntry(pd.getActiveCharacter(), pd));
                }
            }
        }
        return list;
    }

    private boolean isClientInProfession(Profession prof) {
        PlayerCharacterData pd = getMyPlayerData();
        return pd != null && (pd.hasProfession(prof.getTitle()) || pd.hasProfession(String.valueOf(prof.getId())));
    }

    @Override
    public void tick() {
        super.tick();
        if (clientTaxTicksRemaining > 0) {
            clientTaxTicksRemaining--;
        } else {
            clientTaxTicksRemaining = CharacterWorldData.TAX_CYCLE_TICKS;
            clientDay++;
            if (clientDay > 7) {
                clientDay = 1;
            }
        }

        if (clientWeekTicksRemaining > 0) {
            clientWeekTicksRemaining--;
        } else {
            clientWeekTicksRemaining = CharacterWorldData.WEEK_CYCLE_TICKS;
            clientWeek++;
            clientDay = 1;
        }

        if (cycleTimerLabel != null) {
            cycleTimerLabel.text(Component.literal(formatCycleTimers()).withColor(0xFFB0B7C3));
        }

        if (taxTimerLabel != null && currentTab == Tab.TREASURY) {
            int totalSecs = clientTaxTicksRemaining / 20;
            int mins = totalSecs / 60;
            int secs = totalSecs % 60;
            taxTimerLabel.text(Component.literal(String.format("(до следующего снятия: %02d:%02d)", mins, secs)).withStyle(ChatFormatting.GRAY));
        }
    }

    private String formatCycleTimers() {
        int weekSecsTotal = Math.max(0, clientWeekTicksRemaining / 20);
        int wHrs = weekSecsTotal / 3600;
        int wMins = (weekSecsTotal % 3600) / 60;
        int wSecs = weekSecsTotal % 60;
        String weekTimeStr = String.format("%02d:%02d:%02d", wHrs, wMins, wSecs);

        int daySecsTotal = Math.max(0, clientTaxTicksRemaining / 20);
        int dMins = daySecsTotal / 60;
        int dSecs = daySecsTotal % 60;
        String dayTimeStr = String.format("%02d:%02d", dMins, dSecs);

        return String.format("Неделя %d (%s)   День %d (%s)", clientWeek, weekTimeStr, clientDay, dayTimeStr);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
