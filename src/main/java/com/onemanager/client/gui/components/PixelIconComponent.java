package com.onemanager.client.gui.components;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;

/**
 * Renders pixel-art sprites defined by ascii bitmasks.
 * '#' or '*' -> primaryColor
 * 'O' or 'o' -> secondaryColor
 * '.' or ' ' -> transparent
 */
public class PixelIconComponent extends BaseComponent {
    private final String[] pattern;
    private int primaryColor;
    private int secondaryColor;
    private final int pixelSize;
    private final int spriteWidth;
    private final int spriteHeight;

    public PixelIconComponent(String[] pattern, int pixelSize, int primaryColor, int secondaryColor) {
        this.pattern = pattern != null ? pattern : new String[0];
        this.pixelSize = Math.max(1, pixelSize);
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;

        int maxLen = 0;
        for (String row : this.pattern) {
            if (row != null && row.length() > maxLen) {
                maxLen = row.length();
            }
        }
        this.spriteWidth = maxLen * this.pixelSize;
        this.spriteHeight = this.pattern.length * this.pixelSize;

        this.sizing(Sizing.fixed(this.spriteWidth), Sizing.fixed(this.spriteHeight));
    }

    public PixelIconComponent(String[] pattern, int pixelSize, int primaryColor) {
        this(pattern, pixelSize, primaryColor, 0);
    }

    public PixelIconComponent color(int primaryColor) {
        this.primaryColor = primaryColor;
        return this;
    }

    public PixelIconComponent secondaryColor(int secondaryColor) {
        this.secondaryColor = secondaryColor;
        return this;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        int startX = this.x;
        int startY = this.y;

        for (int r = 0; r < pattern.length; r++) {
            String row = pattern[r];
            if (row == null) continue;
            int y1 = startY + r * pixelSize;
            int y2 = y1 + pixelSize;

            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                int color = 0;
                if (ch == '#' || ch == '*') {
                    color = primaryColor;
                } else if (ch == 'O' || ch == 'o') {
                    color = secondaryColor;
                }
                if ((color & 0xFF000000) != 0) {
                    int x1 = startX + c * pixelSize;
                    int x2 = x1 + pixelSize;
                    context.fill(x1, y1, x2, y2, color);
                }
            }
        }
    }

    // =========================================================================
    // BUILT-IN ICON PRESETS
    // =========================================================================

    public static final String[] SKULL = new String[] {
            ".......########.......",
            "....##############....",
            "...################...",
            "..##################..",
            ".####################.",
            ".####################.",
            "######################",
            "######################",
            "###..####..####..#####",
            "##....##....##....####",
            "##....##....##....####",
            "###..####..####..#####",
            "######################",
            "#########..###########",
            "##########.###########",
            ".########..##########.",
            "..##################..",
            "...####.######.####...",
            "....###.######.###....",
            "....###.######.###....",
            "....##############....",
            ".....############....."
    };

    public static final String[] SUN = new String[] {
            "..........##..........",
            ".........####.........",
            "#.......######.......#",
            "##.....########.....##",
            ".##...##########...##.",
            "..##.############.##..",
            "...################...",
            "...################...",
            "..##################..",
            ".####################.",
            "######################",
            "######################",
            ".####################.",
            "..##################..",
            "...################...",
            "...################...",
            "..##.############.##..",
            ".##...##########...##.",
            "##.....########.....##",
            "#.......######.......#",
            ".........####.........",
            "..........##.........."
    };

    public static final String[] THUMB_UP = new String[] {
            ".......##.......",
            "......####......",
            "......####......",
            "......####......",
            ".....######.....",
            ".....######.....",
            "..#############.",
            ".###############",
            "################",
            "################",
            "################",
            ".###############",
            "..#############.",
            "....#########...",
            ".....#######...."
    };

    public static final String[] THUMB_DOWN = new String[] {
            ".....#######....",
            "....#########...",
            "..#############.",
            ".###############",
            "################",
            "################",
            "################",
            ".###############",
            "..#############.",
            ".....######.....",
            ".....######.....",
            "......####......",
            "......####......",
            "......####......",
            ".......##......."
    };

    public static final String[] ARROW_UP = new String[] {
            "...#...",
            "..###..",
            ".#####.",
            "#######",
            "...#...",
            "...#..."
    };

    public static final String[] ARROW_DOWN = new String[] {
            "...#...",
            "...#...",
            "#######",
            ".#####.",
            "..###..",
            "...#..."
    };

    public static final String[] EQUAL = new String[] {
            "#######",
            "#######",
            ".......",
            "#######",
            "#######"
    };
}
