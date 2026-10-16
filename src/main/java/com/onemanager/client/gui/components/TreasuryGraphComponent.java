package com.onemanager.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import com.onemanager.data.TreasuryLogEntry;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Interactive left-to-right line graph component for Treasury income & expenses over the last in-game month
 * (30 in-game days = 600 minutes = 10 hours).
 * Displays 3 marks on the left (Highest, Average, Lowest), colored action dots (green/red/yellow),
 * and per-dot hover tooltips showing the exact TreasuryLogEntry.
 */
public class TreasuryGraphComponent extends BaseComponent {
    public enum ActionType {
        INCOME(0xFF42F56C, 0xFF10451D, 0xFF38C958, ChatFormatting.GREEN),
        EXPENSE(0xFFFF4D4D, 0xFF521616, 0xFFD94343, ChatFormatting.RED),
        INSTALLATION(0xFFFFD000, 0xFF5C4700, 0xFFFFCC00, ChatFormatting.YELLOW);

        public final int dotColor;
        public final int borderColor;
        public final int lineColor;
        public final ChatFormatting chatColor;

        ActionType(int dotColor, int borderColor, int lineColor, ChatFormatting chatColor) {
            this.dotColor = dotColor;
            this.borderColor = borderColor;
            this.lineColor = lineColor;
            this.chatColor = chatColor;
        }
    }

    private static final List<ClientTooltipComponent> EMPTY_TOOLTIP = Collections.emptyList();

    private final List<TreasuryLogEntry> entries;
    private final long maxVal;
    private final long avgVal;
    private final long minVal;
    private final int leftAxisWidth;
    private final int recommendedWidth;
    private final int recommendedHeight;
    private int hoveredIndex = -1;

    public TreasuryGraphComponent(List<TreasuryLogEntry> chronologicalEntries, long currentTreasury, int maxAllowedWidth, int maxAllowedHeight) {
        this.entries = chronologicalEntries != null ? chronologicalEntries : Collections.emptyList();

        if (this.entries.isEmpty()) {
            this.maxVal = currentTreasury;
            this.avgVal = currentTreasury;
            this.minVal = currentTreasury;
        } else {
            long hi = Long.MIN_VALUE;
            long lo = Long.MAX_VALUE;
            long sum = 0L;
            for (TreasuryLogEntry e : this.entries) {
                long v = e.treasuryAfter();
                if (v > hi) hi = v;
                if (v < lo) lo = v;
                sum += v;
            }
            this.maxVal = hi;
            this.minVal = lo;
            this.avgVal = Math.round((double) sum / this.entries.size());
        }

        Font font = Minecraft.getInstance().font;
        String maxLabel = formatVal(maxVal) + " $PR";
        String avgLabel = formatVal(avgVal) + " $PR";
        String minLabel = formatVal(minVal) + " $PR";

        int maxTextW = Math.max(
                Math.max(font.width("Высшее:"), font.width("Среднее:")),
                Math.max(font.width(maxLabel), Math.max(font.width(avgLabel), font.width(minLabel)))
        );
        this.leftAxisWidth = Math.max(76, maxTextW + 14);

        // Dynamically size width & height based on point count and how strongly the graph swung in one direction
        int n = this.entries.size();
        int desiredPlotW = Math.max(200, 160 + n * 18);
        int clampedPlotW = Math.min(Math.max(200, maxAllowedWidth - leftAxisWidth - 16), desiredPlotW);

        long spread = Math.max(0L, maxVal - minVal);
        long upperDelta = Math.max(0L, maxVal - avgVal);
        long lowerDelta = Math.max(0L, avgVal - minVal);
        double skew = spread > 0 ? (double) Math.abs(upperDelta - lowerDelta) / spread : 0.0;

        int spreadBoost = spread > 0 ? (int) Math.min(68, Math.log10(Math.max(1, spread)) * 18.0) : 0;
        int skewBoost = (int) Math.round(skew * 36.0);
        int desiredPlotH = 115 + spreadBoost + skewBoost;
        int clampedPlotH = Math.min(Math.max(115, maxAllowedHeight), Math.max(115, desiredPlotH));

        this.recommendedWidth = this.leftAxisWidth + clampedPlotW + 10;
        this.recommendedHeight = clampedPlotH;

        this.sizing(Sizing.fixed(this.recommendedWidth), Sizing.fixed(this.recommendedHeight));
        this.tooltip(EMPTY_TOOLTIP);
    }

    public int getRecommendedWidth() {
        return recommendedWidth;
    }

    public int getRecommendedHeight() {
        return recommendedHeight;
    }

    public long getMaxVal() {
        return maxVal;
    }

    public long getAvgVal() {
        return avgVal;
    }

    public long getMinVal() {
        return minVal;
    }

    private static String formatVal(long v) {
        return String.format("%,d", v).replace(',', ' ');
    }

    public ActionType getPointActionType(int index) {
        if (index < 0 || index >= entries.size()) {
            return ActionType.INCOME;
        }
        TreasuryLogEntry entry = entries.get(index);
        String act = entry.action() != null ? entry.action().trim() : "";
        if (act.startsWith("-") || act.startsWith("Снос")) {
            return ActionType.EXPENSE;
        }
        if (act.startsWith("Установка")) {
            return ActionType.INSTALLATION;
        }
        if (act.startsWith("+")) {
            return ActionType.INCOME;
        }
        if (index > 0) {
            long prev = entries.get(index - 1).treasuryAfter();
            if (entry.treasuryAfter() < prev) return ActionType.EXPENSE;
            if (entry.treasuryAfter() > prev) return ActionType.INCOME;
        }
        return ActionType.INSTALLATION;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        Font font = Minecraft.getInstance().font;

        int baseX = this.x();
        int baseY = this.y();
        int totalW = this.width();
        int totalH = this.height();

        int plotLeft = baseX + leftAxisWidth;
        int plotRight = baseX + totalW - 8;
        int plotTop = baseY + 12;
        int plotBottom = baseY + totalH - 14;
        int plotW = Math.max(40, plotRight - plotLeft);
        int plotH = Math.max(40, plotBottom - plotTop);

        // Plot box background and outline
        context.fill(plotLeft - 6, baseY + 2, plotRight + 6, baseY + totalH - 2, 0xFF0A0D12);
        drawBoxOutline(context, plotLeft - 6, baseY + 2, plotRight + 6, baseY + totalH - 2, 0xFF262E3D);

        // Calculate Y coordinates for the 3 marks: Highest (yTop), Average (yMid), Lowest (yBottom)
        int yTop = plotTop;
        int yBottom = plotBottom;
        int yMid;
        if (maxVal > minVal) {
            double normAvg = (double) (avgVal - minVal) / (maxVal - minVal);
            // Clamp so the 3 left labels never collide vertically even if the graph went strongly to one side
            double clampedNorm = Math.max(0.24, Math.min(0.76, normAvg));
            yMid = yBottom - (int) Math.round(clampedNorm * plotH);
        } else {
            yMid = (yTop + yBottom) / 2;
        }

        // Draw horizontal dashed reference lines for the 3 marks (Average is yellow like chest installation)
        drawDashedHorizontalLine(context, plotLeft, plotRight, yTop, 0x4455FF55);
        drawDashedHorizontalLine(context, plotLeft, plotRight, yMid, 0x55FFD000);
        drawDashedHorizontalLine(context, plotLeft, plotRight, yBottom, 0x44FF5555);

        // Draw left axis tick notches
        context.fill(plotLeft - 9, yTop, plotLeft - 5, yTop + 1, 0xFF55FF55);
        context.fill(plotLeft - 9, yMid, plotLeft - 5, yMid + 1, 0xFFFFD000);
        context.fill(plotLeft - 9, yBottom, plotLeft - 5, yBottom + 1, 0xFFFF5555);

        // Draw the 3 marks on the left: Высшее (green/gold), Среднее (yellow), Низшее (red)
        drawAxisMark(context, font, baseX + 2, yTop, "Высшее:", formatVal(maxVal) + " $PR", 0xFF55FF55);
        drawAxisMark(context, font, baseX + 2, yMid, "Среднее:", formatVal(avgVal) + " $PR", 0xFFFFD000);
        drawAxisMark(context, font, baseX + 2, yBottom, "Низшее:", formatVal(minVal) + " $PR", 0xFFFF5555);

        if (entries.isEmpty()) {
            String emptyMsg = "Нет операций за последний месяц";
            int msgW = font.width(emptyMsg);
            context.drawString(font, emptyMsg, plotLeft + (plotW - msgW) / 2, yMid - 4, 0xFF6E7685, false);
            this.hoveredIndex = -1;
            this.tooltip(EMPTY_TOOLTIP);
            return;
        }

        int n = entries.size();
        int[] px = new int[n];
        int[] py = new int[n];
        ActionType[] types = new ActionType[n];

        for (int i = 0; i < n; i++) {
            TreasuryLogEntry entry = entries.get(i);
            types[i] = getPointActionType(i);

            if (n == 1) {
                px[i] = plotLeft + plotW / 2;
            } else {
                px[i] = plotLeft + (int) Math.round(((double) i / (n - 1)) * plotW);
            }
            py[i] = valueToY(entry.treasuryAfter(), yTop, yMid, yBottom);
        }

        // If only 1 point exists, draw a baseline from the left edge to the point so the left-to-right line is visible
        if (n == 1) {
            drawLine(context, plotLeft, yMid, px[0], py[0], types[0].lineColor);
        } else {
            // Draw connecting line segments from left to right
            for (int i = 1; i < n; i++) {
                drawLine(context, px[i - 1], py[i - 1], px[i], py[i], types[i].lineColor);
            }
        }

        // Detect hovered point
        int hoveredIdx = -1;
        double bestDistSq = 64.0; // 8px hover radius
        for (int i = 0; i < n; i++) {
            double dx = mouseX - px[i];
            double dy = mouseY - py[i];
            double dSq = dx * dx + dy * dy;
            if (dSq <= bestDistSq) {
                bestDistSq = dSq;
                hoveredIdx = i;
            }
        }
        this.hoveredIndex = hoveredIdx;

        // Draw crosshair if a point is hovered
        if (hoveredIdx >= 0) {
            int hx = px[hoveredIdx];
            int hy = py[hoveredIdx];
            context.fill(hx, plotTop, hx + 1, plotBottom, 0x44FFFFFF);
            context.fill(plotLeft, hy, plotRight, hy + 1, 0x44FFFFFF);
        }

        // Draw action dots (green for income, red for expense, yellow for installation)
        for (int i = 0; i < n; i++) {
            int x = px[i];
            int y = py[i];
            ActionType type = types[i];

            if (i == hoveredIdx) {
                // Highlighted dot on hover
                context.fill(x - 4, y - 4, x + 5, y + 5, 0xFFFFFFFF);
                context.fill(x - 3, y - 3, x + 4, y + 4, type.borderColor);
                context.fill(x - 2, y - 2, x + 3, y + 3, type.dotColor);
            } else {
                // Standard small dot
                context.fill(x - 2, y - 2, x + 3, y + 3, type.borderColor);
                context.fill(x - 1, y - 1, x + 2, y + 2, type.dotColor);
            }
        }

        // Update tooltip components cache so OwO-lib shouldDrawTooltip succeeds
        if (hoveredIdx >= 0 && hoveredIdx < entries.size()) {
            TreasuryLogEntry entry = entries.get(hoveredIdx);
            ActionType type = types[hoveredIdx];
            MutableComponent tip = Component.literal(entry.getDisplayName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                    .append(Component.literal("  •  " + entry.timestamp()).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal("\n" + entry.action()).withStyle(type.chatColor, ChatFormatting.BOLD))
                    .append(Component.literal("\nКазна после: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(formatVal(entry.treasuryAfter()) + " $PR").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            List<ClientTooltipComponent> tipList = new ArrayList<>();
            for (FormattedCharSequence seq : font.split(tip, 280)) {
                tipList.add(ClientTooltipComponent.create(seq));
            }
            this.tooltip(tipList);
        } else {
            this.tooltip(EMPTY_TOOLTIP);
        }
    }

    @Override
    public boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return this.hoveredIndex >= 0 && this.hoveredIndex < entries.size();
    }

    @Override
    public void drawTooltip(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.hoveredIndex < 0 || this.hoveredIndex >= entries.size()) {
            return;
        }

        TreasuryLogEntry entry = entries.get(this.hoveredIndex);
        ActionType type = getPointActionType(this.hoveredIndex);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(entry.getDisplayName()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(Component.literal("  •  " + entry.timestamp()).withStyle(ChatFormatting.DARK_GRAY)));
        lines.add(Component.literal(entry.action()).withStyle(type.chatColor, ChatFormatting.BOLD));
        lines.add(Component.literal("Казна после: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatVal(entry.treasuryAfter()) + " $PR").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));

        Font font = Minecraft.getInstance().font;
        context.pose().pushPose();
        context.pose().translate(0.0F, 0.0F, 800.0F);
        RenderSystem.disableDepthTest();
        context.renderComponentTooltip(font, lines, mouseX, mouseY);
        context.flush();
        RenderSystem.enableDepthTest();
        context.pose().popPose();
    }

    private int valueToY(long v, int yTop, int yMid, int yBottom) {
        if (maxVal <= minVal) {
            return yMid;
        }
        if (v >= avgVal) {
            if (maxVal <= avgVal) return yTop;
            double t = (double) (v - avgVal) / (maxVal - avgVal);
            return yMid - (int) Math.round(t * (yMid - yTop));
        } else {
            if (avgVal <= minVal) return yBottom;
            double t = (double) (v - minVal) / (avgVal - minVal);
            return yBottom - (int) Math.round(t * (yBottom - yMid));
        }
    }

    private void drawAxisMark(OwoUIDrawContext context, Font font, int x, int centerY, String title, String valueStr, int valueColor) {
        context.pose().pushPose();
        float scale = 0.78f;
        context.pose().scale(scale, scale, 1.0f);
        int scaledX = (int) (x / scale);
        int topY = (int) ((centerY - 7) / scale);
        int botY = (int) ((centerY + 1) / scale);
        context.drawString(font, title, scaledX, topY, 0xFF9EA6B4, false);
        context.drawString(font, valueStr, scaledX, botY, valueColor, false);
        context.pose().popPose();
    }

    private void drawDashedHorizontalLine(OwoUIDrawContext context, int x1, int x2, int y, int color) {
        for (int x = x1; x < x2; x += 6) {
            int end = Math.min(x + 3, x2);
            context.fill(x, y, end, y + 1, color);
        }
    }

    private void drawBoxOutline(OwoUIDrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1, x1 + 1, y2, color);
        context.fill(x2 - 1, y1, x2, y2, color);
    }

    private void drawLine(OwoUIDrawContext context, int x0, int y0, int x1, int y1, int color) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            context.fill(x0, y0, x0 + 2, y0 + 2, color);
            return;
        }
        for (int s = 0; s <= steps; s++) {
            int px = x0 + (dx * s) / steps;
            int py = y0 + (dy * s) / steps;
            context.fill(px, py, px + 2, py + 2, color);
        }
    }
}
