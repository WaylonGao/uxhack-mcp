package net.minecraft.client.pose;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/**
 * Renders the pose skeleton overlay and semaphore letter HUD.
 *
 * Layout (top-right corner):
 * ┌──────────────────────────────┐
 * │  skeleton panel (192×108)    │  ← top-right, 10px margin
 * │                              │
 * └──────────────────────────────┘
 *     [ A ]  HOLD  CAPS           ← letter badge directly below panel
 *
 * Node index map (17 nodes, matches main.py target_indices):
 *  0=nose  1=leftEye  2=rightEye  3=leftEar  4=rightEar
 *  5=leftShoulder  6=rightShoulder  7=leftElbow  8=rightElbow
 *  9=leftWrist  10=rightWrist  11=leftHip  12=rightHip
 *  13=leftKnee  14=rightKnee  15=leftAnkle  16=rightAnkle
 */
public class PoseOverlay {

    // ── Skeleton panel ─────────────────────────────────────────────────────────
    private static final int PANEL_W      = 192;
    private static final int PANEL_H      = 108;
    private static final int PANEL_MARGIN = 10;

    private static final int DOT_RADIUS = 3;

    // ── Colours (ARGB) ─────────────────────────────────────────────────────────
    private static final int COL_PANEL_BG  = 0x99000000;
    private static final int COL_BORDER    = 0xFFFFFFFF;
    private static final int COL_BONE      = 0xFF00FFFF;
    private static final int COL_DOT       = 0xFFFF4444;
    private static final int COL_DOT_FACE  = 0xFF44FF44;

    // Letter badge colours
    private static final int COL_BADGE_BG       = 0xCC1A1A2E; // dark navy
    private static final int COL_BADGE_ACTIVE   = 0xFFFFD700; // gold — letter detected
    private static final int COL_BADGE_INACTIVE = 0xFF888888; // grey — "None"
    private static final int COL_HOLD_BG        = 0xCC0A4A0A; // dark green
    private static final int COL_HOLD_TEXT      = 0xFF00FF44;
    private static final int COL_CAPS_BG        = 0xCC4A2000; // dark orange
    private static final int COL_CAPS_TEXT      = 0xFFFF8800;

    // ── Skeleton bone connections ──────────────────────────────────────────────
    private static final int[][] BONES = {
            // Face
            {0, 1}, {0, 2}, {1, 3}, {2, 4},
            // Shoulders
            {5, 6},
            // Left arm
            {5, 7}, {7, 9},
            // Right arm
            {6, 8}, {8, 10},
            // Torso
            {5, 11}, {6, 12}, {11, 12},
            // Left leg
            {11, 13}, {13, 15},
            // Right leg
            {12, 14}, {14, 16},
    };

    private static final int[] FACE_NODES = {0, 1, 2, 3, 4};

    // ── ASCII pose diagrams keyed by letter ───────────────────────────────────
    // Each value is a String[] of lines to render, read from ASKI_pose_instructions.txt
    private static final java.util.Map<String, String[]> ASCII_POSES;
    static {
        java.util.Map<String, String[]> m = new java.util.LinkedHashMap<>();
        m.put("W", new String[]{
                " \\ O /  ",
                "    |    ",
                "   / \\   "
        });
        m.put("S", new String[]{
                "     O    ",
                "   / ] \\ ",
                "    / \\  "
        });
        m.put("A", new String[]{
                "   O /    ",
                " / ]     ",
                "  / \\    "
        });
        m.put("D", new String[]{
                "  \\ O   ",
                "     | \\ ",
                "    / \\  "
        });
        m.put("E", new String[]{
                " __O__   ",
                "   |     ",
                "  / \\    "
        });
        m.put("Q", new String[]{
                "  |O|    ",
                "   [     ",
                "  / \\    "
        });
        m.put("ESCAPE", new String[]{
                "  O__    ",
                " |[      ",
                "  / \\    "
        });
        ASCII_POSES = java.util.Collections.unmodifiableMap(m);
    }

    // ── Main render entry point ────────────────────────────────────────────────
    public static void render(GuiGraphics gui) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        // Panel top-right
        int panelX = screenW - PANEL_W - PANEL_MARGIN;
        int panelY = PANEL_MARGIN;

        renderSkeletonPanel(gui, panelX, panelY);
        renderAsciiPose(gui, panelX, panelY);
    }

    // ── Skeleton panel ─────────────────────────────────────────────────────────
    private static void renderSkeletonPanel(GuiGraphics gui, int panelX, int panelY) {
        float[] nodes = PoseReceiver.getNodes();

        // Badge in top-left of the panel area (no background box, no border)
        renderLetterBadge(gui, panelX, panelY);

        if (nodes.length < 2) return;

        int nodeCount = nodes.length / 2;

        // Bones
        for (int[] bone : BONES) {
            int a = bone[0], b = bone[1];
            if (a >= nodeCount || b >= nodeCount) continue;
            int x1 = nx(nodes[a * 2],     panelX);
            int y1 = ny(nodes[a * 2 + 1], panelY);
            int x2 = nx(nodes[b * 2],     panelX);
            int y2 = ny(nodes[b * 2 + 1], panelY);
            drawLine(gui, x1, y1, x2, y2, COL_BONE);
        }

        // Dots
        for (int i = 0; i < nodeCount; i++) {
            int px = nx(nodes[i * 2],     panelX);
            int py = ny(nodes[i * 2 + 1], panelY);
            int col = isFaceNode(i) ? COL_DOT_FACE : COL_DOT;
            gui.fill(px - DOT_RADIUS, py - DOT_RADIUS, px + DOT_RADIUS, py + DOT_RADIUS, col);
        }
    }

    // ── Letter badge — text with a tight box around it ────────────────────────
    private static void renderLetterBadge(GuiGraphics gui, int panelX, int panelY) {
        var font = Minecraft.getInstance().font;
        String key      = PoseReceiver.getKey();
        boolean active  = key != null && !key.equals("None");
        boolean holding = PoseReceiver.isHolding();
        boolean caps    = PoseReceiver.isCaps();

        // Build the full label to measure total width
        StringBuilder sb = new StringBuilder();
        String displayKey = active ? key : "-";
        sb.append(displayKey);
        if (holding) sb.append("  HOLD");
        if (caps)    sb.append("  CAPS");

        int pad  = 3;
        int boxW = font.width(sb.toString()) + pad * 2;
        int boxH = 8 + pad * 2; // font is always 8px tall

        // Semi-transparent dark background
        gui.fill(panelX, panelY, panelX + boxW, panelY + boxH, 0xAA000000);
        // Thin white border
        gui.fill(panelX,            panelY,            panelX + boxW, panelY + 1,           0xFFFFFFFF);
        gui.fill(panelX,            panelY + boxH - 1, panelX + boxW, panelY + boxH,        0xFFFFFFFF);
        gui.fill(panelX,            panelY,            panelX + 1,    panelY + boxH,         0xFFFFFFFF);
        gui.fill(panelX + boxW - 1, panelY,            panelX + boxW, panelY + boxH,         0xFFFFFFFF);

        // Draw each word in its own colour
        int x = panelX + pad;
        int y = panelY + pad;

        int keyCol = active ? COL_BADGE_ACTIVE : COL_BADGE_INACTIVE;
        gui.drawString(font, displayKey, x, y, keyCol);
        x += font.width(displayKey);

        if (holding) {
            gui.drawString(font, "  HOLD", x, y, COL_HOLD_TEXT);
            x += font.width("  HOLD");
        }
        if (caps) {
            gui.drawString(font, "  CAPS", x, y, COL_CAPS_TEXT);
        }
    }

    // ── ASCII pose diagram — shown to the LEFT of the badge box ─────────────────
    private static void renderAsciiPose(GuiGraphics gui, int panelX, int panelY) {
        var font = Minecraft.getInstance().font;
        String key = PoseReceiver.getKey();
        if (key == null || key.equals("None")) return;

        // Look up the ASCII art for this key (uppercase)
        String[] lines = ASCII_POSES.get(key.toUpperCase());
        if (lines == null) return;

        // Measure the widest line so we can size the box
        int maxLineW = 0;
        for (String line : lines) maxLineW = Math.max(maxLineW, font.width(line));

        int lineH   = 10; // 8px font + 2px gap
        int pad     = 3;
        int boxW    = maxLineW + pad * 2;
        int boxH    = lines.length * lineH + pad * 2 - 2; // -2: no gap after last line

        // Position: to the LEFT of the badge box, aligned to same Y
        int boxX = panelX - boxW - 6;
        int boxY = panelY;

        // Semi-transparent background + white border (same style as badge)
        gui.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xAA000000);
        gui.fill(boxX,            boxY,            boxX + boxW, boxY + 1,           0xFFFFFFFF);
        gui.fill(boxX,            boxY + boxH - 1, boxX + boxW, boxY + boxH,        0xFFFFFFFF);
        gui.fill(boxX,            boxY,            boxX + 1,    boxY + boxH,         0xFFFFFFFF);
        gui.fill(boxX + boxW - 1, boxY,            boxX + boxW, boxY + boxH,         0xFFFFFFFF);

        // Draw each line of ASCII art in cyan
        int textX = boxX + pad;
        int textY = boxY + pad;
        for (String line : lines) {
            gui.drawString(font, line, textX, textY, 0xFF00FFFF);
            textY += lineH;
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private static int nx(float normX, int panelX) {
        return panelX + 1 + (int)(normX * (PANEL_W - 2));
    }

    private static int ny(float normY, int panelY) {
        return panelY + 1 + (int)(normY * (PANEL_H - 2));
    }

    private static boolean isFaceNode(int i) {
        for (int f : FACE_NODES) if (f == i) return true;
        return false;
    }

    private static void drawLine(GuiGraphics gui, int x1, int y1, int x2, int y2, int colour) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy, x = x1, y = y1;
        while (true) {
            gui.fill(x, y, x + 1, y + 1, colour);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 <  dx) { err += dx; y += sy; }
        }
    }
}
