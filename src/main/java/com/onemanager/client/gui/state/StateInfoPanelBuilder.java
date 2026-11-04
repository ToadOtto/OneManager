package com.onemanager.client.gui.state;

import com.onemanager.data.ActiveCharacter;
import com.onemanager.data.CharacterWorldData;
import com.onemanager.data.DeadCharacter;
import com.onemanager.data.PlayerCharacterData;
import com.onemanager.profession.ProfessionRegistry;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * Builds the Information page for the State tab with settlement and server statistics.
 */
public final class StateInfoPanelBuilder {

    private StateInfoPanelBuilder() {}

    public static FlowLayout build(CharacterWorldData worldData, ScrollContainer.Scrollbar scrollbar) {
        FlowLayout root = Containers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
        root.horizontalAlignment(HorizontalAlignment.CENTER);

        if (worldData == null) {
            root.child(Components.label(Component.literal("Данные мира недоступны").withStyle(ChatFormatting.GRAY)));
            return root;
        }

        FlowLayout content = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.gap(6);
        content.padding(Insets.horizontal(8));

        // Compute demographic statistics
        int citizens = 0;
        int buried = 0;
        int unburied = 0;
        int occupiedProfessions = 0;
        for (PlayerCharacterData pd : worldData.getAllPlayerData().values()) {
            if (pd.hasActiveCharacter()) {
                citizens++;
                occupiedProfessions += pd.getProfessions().size();
            }
            for (DeadCharacter dead : pd.getCharacterHistory()) {
                if (dead.isBuried()) buried++;
                else unburied++;
            }
        }
        int totalPopulationHistory = citizens + buried + unburied;

        // Compute runtime hours and calendar
        long totalElapsedTicks = worldData.getTotalElapsedTicks();
        long totalSeconds = Math.max(0, totalElapsedTicks / 20L);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        String playedTimeStr = hours + " ч. " + minutes + " мин.";

        UUID knyazUuid = worldData.getKnyazUuid();
        PlayerCharacterData knyazPd = knyazUuid != null ? worldData.getPlayerData(knyazUuid) : null;
        ActiveCharacter knyazChar = knyazPd != null ? knyazPd.getActiveCharacter() : null;
        String rulerName = (knyazChar != null && !knyazChar.getName().isEmpty())
                ? knyazChar.getName() + " [" + worldData.getKnyazName() + "]"
                : (worldData.hasKnyaz() ? worldData.getKnyazName() : "Титул свободен");

        long treasury = worldData.getTreasury();
        long capacity = worldData.getTreasuryCapacity();
        long totalDeposited = worldData.getTotalTreasuryDeposited();

        // =====================================================================
        // SECTION 1: ЛЕТОПИСЬ И ПРАВЛЕНИЕ
        // =====================================================================
        content.child(buildSectionHeader("Летопись и Государство", Items.WRITABLE_BOOK));

        FlowLayout group1 = createCardGroup();
        group1.child(createStatRow("Дата основания поселения", worldData.getFoundationDate(), "Дата старта летописи и основания государства", ChatFormatting.WHITE));
        group1.child(createStatRow("Часов реального времени наиграно", playedTimeStr, "Общее время работы сервера и жизни поселения", ChatFormatting.YELLOW));
        group1.child(createStatRow("Игровой календарь", "Неделя " + worldData.getCurrentWeek() + ", День " + worldData.getCurrentDay() + " (" + worldData.getTotalDays() + "-й день)", "Текущий день и неделя в календаре OneHouse3", ChatFormatting.AQUA));
        group1.child(createStatRow("Правящий Князь", rulerName, "Текущий монарх и глава княжества", worldData.hasKnyaz() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        group1.child(createStatRow("Количество Князей за всё время", worldData.getKnyazCount() + " чел.", "Число правителей, когда-либо носивших Корону Монарха", ChatFormatting.WHITE));
        group1.child(createStatRow("Репутация правящего Князя", (worldData.getKnyazReputation() > 0 ? "+" : "") + worldData.getKnyazReputation() + " / 100", "Отношение граждан и фракций к текущему Князю", worldData.getKnyazReputation() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        content.child(group1);

        // =====================================================================
        // SECTION 2: ДЕМОГРАФИЯ И НАСЕЛЕНИЕ
        // =====================================================================
        content.child(buildSectionHeader("Демография и Население", Items.PLAYER_HEAD));

        FlowLayout group2 = createCardGroup();
        group2.child(createStatRow("Жителей за всю историю", totalPopulationHistory + " чел.", "Суммарное количество когда-либо живших граждан", ChatFormatting.WHITE));
        group2.child(createStatRow("Ныне живущих граждан", citizens + " чел.", "Количество персонажей, активно проживающих в мире", ChatFormatting.GREEN));
        group2.child(createStatRow("Упокоенных предков", buried + " чел.", "Усопшие жители, обретающие покой в могилах и урнах", ChatFormatting.GRAY));
        group2.child(createStatRow("Неупокоенных душ", unburied + " чел.", "Погибшие граждане, требующие проведения обряда упокоения", unburied > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        group2.child(createStatRow("Занятых рабочих мест", occupiedProfessions + " мест", "Суммарное количество профессий, занятых гражданами", ChatFormatting.WHITE));
        group2.child(createStatRow("Профессий в государственном реестре", ProfessionRegistry.getAll().size() + " шт.", "Всего доступных ремесел и специальностей в каталоге", ChatFormatting.WHITE));
        content.child(group2);

        // =====================================================================
        // SECTION 3: КАЗНА И ЭКОНОМИКА
        // =====================================================================
        content.child(buildSectionHeader("Казна и Экономика", Items.GOLD_INGOT));

        FlowLayout group3 = createCardGroup();
        String totalDepFormatted = String.format("%,d", totalDeposited).replace(',', ' ') + " $PR";
        String treasuryFormatted = String.format("%,d", treasury).replace(',', ' ') + " / " + String.format("%,d", capacity).replace(',', ' ') + " $PR";

        group3.child(createStatRow("Всего денег внесено в казну", totalDepFormatted, "Все добровольные взносы и налоговые сборы за всё время", ChatFormatting.GOLD));
        group3.child(createStatRow("Текущий баланс и вместимость", treasuryFormatted, "Физическое количество монет Sprota во всех хранилищах", ChatFormatting.YELLOW));
        group3.child(createStatRow("Физических хранилищ в мире", worldData.getTreasuryChests().size() + " шт.", "Количество малых и больших сундуков казны, установленных Князем", ChatFormatting.WHITE));
        group3.child(createStatRow("Базовая ставка налога", worldData.getTaxRate() + " $PR", "Сумма налога, автоматически списываемая каждые 20 минут", ChatFormatting.WHITE));
        content.child(group3);

        // =====================================================================
        // SECTION 4: ЗАКОНОДАТЕЛЬСТВО И МИР
        // =====================================================================
        content.child(buildSectionHeader("Законы и Развитие Мира", Items.COMPASS));

        FlowLayout group4 = createCardGroup();
        group4.child(createStatRow("Действующих законов государства", worldData.getLaws().size() + " актов", "Число утвержденных Князем законов в государственном реестре", ChatFormatting.WHITE));
        group4.child(createStatRow("Уровень развития мира (World Level)", "Уровень " + worldData.getWorldLevel(), "Глобальный технологический и культурный прогресс поселения", ChatFormatting.AQUA));
        group4.child(createStatRow("Завершённых мировых вех", worldData.getCompletedAdvancements().size() + " вех", "Глобальные достижения, открытые всеми жителями", ChatFormatting.WHITE));
        content.child(group4);

        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), content);
        if (scrollbar != null) {
            scroll.scrollbar(scrollbar);
        }
        scroll.scrollbarThiccness(4);

        root.child(scroll);
        return root;
    }

    private static FlowLayout buildSectionHeader(String title, net.minecraft.world.item.Item iconItem) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(6);
        row.margins(Insets.top(4).withBottom(2));

        ItemComponent icon = Components.item(new ItemStack(iconItem));
        icon.sizing(Sizing.fixed(14), Sizing.fixed(14));
        row.child(icon);

        LabelComponent lbl = Components.label(Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        row.child(lbl);

        row.child(Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofRgb(0xFF262C38)).margins(Insets.left(6)));

        return row;
    }

    private static FlowLayout createCardGroup() {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(Surface.flat(0xFF13151B).and(Surface.outline(0xFF242A36)));
        card.padding(Insets.of(6));
        card.gap(3);
        return card;
    }

    private static FlowLayout createStatRow(String label, String value, String description, ChatFormatting valueColor) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.padding(Insets.horizontal(4));

        LabelComponent lbl = Components.label(Component.literal("• " + label).withStyle(ChatFormatting.GRAY));
        lbl.maxWidth(220);
        row.child(lbl);

        row.child(Components.spacer());

        LabelComponent val = Components.label(Component.literal(value).withStyle(valueColor, ChatFormatting.BOLD));
        row.child(val);

        row.mouseEnter().subscribe(() -> row.surface(Surface.flat(0xFF1A1E26)));
        row.mouseLeave().subscribe(() -> row.surface(Surface.BLANK));

        return row;
    }
}
