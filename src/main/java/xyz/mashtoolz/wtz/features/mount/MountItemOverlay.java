package xyz.mashtoolz.wtz.features.mount;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.InputUtil;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import xyz.mashtoolz.wtz.util.ColorUtils;
import xyz.mashtoolz.wtz.util.ScreenUtils;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.config.WTZConfig.MountItemOverlayModifierKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MountItemOverlay {

    private static final String MOUNT_SKIN_WINDOW_TITLE = "\uDAFF\uDFE8\uE015\uDAFF\uDF951\uDB00\uDC65";

    private static final int SHADOW_COLOR = 0xFF000000;
    private static final int SKIN_COLOR = 0xFFFFFFFF;
    private static final Pattern SKIN_PART_SEPARATOR = Pattern.compile("\\s*-\\s*");
    private static final int BAR_HEIGHT = 2;
    private static final int BAR_GAP = 1;
    private static final int BAR_BG_COLOR = 0xFF000000;
    private static final int BAR_BORDER_COLOR = 0xFF000000;
    private static final int MAXED_BAR_COLOR = 0xFFDD55DD;

    public static void renderSkinOverlay(DrawContext context, Slot slot) {
        if (!WTZClient.CONFIG.mountItemOverlaySkinColorsEnabled) return;

        HandledScreen<?> handled = ScreenUtils.currentHandledScreenOrNull();
        if (handled == null) return;
        if (!isMountSkinWindow(handled)) return;
        if (slot.id != 22) return;

        String skin = extractMountSkin(slot.id);
        if (skin == null) return;

        TextRenderer textRenderer = WTZClient.client().textRenderer;
        String mount = extractMountType(slot.id);
        List<SkinNameSegment> segments = skinNameSegments(mount, skin);
        int textWidth = skinNameWidth(textRenderer, segments);
        float scale = 0.8f;
        float scaledWidth = textWidth * scale;
        float scaledHeight = 7 * scale;

        float x = slot.x + (16 - scaledWidth) / 2f;
        float y = slot.y - 12 + (16 - scaledHeight) / 2f;

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.scale(scale, scale);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                drawSkinName(context, textRenderer, segments, dx, dy, true);
            }
        }

        drawSkinName(context, textRenderer, segments, 0, 0, false);
        matrices.popMatrix();
    }

    private record SkinNameSegment(String text, int color) {
    }

    private static List<SkinNameSegment> skinNameSegments(String mount, String skin) {
        List<SkinNameSegment> segments = new ArrayList<>();
        Matcher matcher = SKIN_PART_SEPARATOR.matcher(skin);
        int last = 0;
        int partIndex = 0;
        while (matcher.find()) {
            addSkinPart(segments, mount, roleForPart(partIndex++), skin.substring(last, matcher.start()));
            segments.add(new SkinNameSegment(matcher.group(), SKIN_COLOR));
            last = matcher.end();
        }
        addSkinPart(segments, mount, roleForPart(partIndex), skin.substring(last));

        return segments.isEmpty() ? List.of(new SkinNameSegment(skin, SKIN_COLOR)) : segments;
    }

    private static void addSkinPart(List<SkinNameSegment> segments, String mount, String role, String text) {
        if (text.isEmpty()) return;
        segments.add(new SkinNameSegment(text, MountSkinColors.colorFor(mount, role, text, SKIN_COLOR)));
    }

    private static String roleForPart(int index) {
        return index == 0 ? "primary" : "secondary";
    }

    private static int skinNameWidth(TextRenderer textRenderer, List<SkinNameSegment> segments) {
        int width = 0;
        for (SkinNameSegment segment : segments) {
            width += textRenderer.getWidth(segment.text);
        }
        return width;
    }

    private static void drawSkinName(DrawContext context, TextRenderer textRenderer, List<SkinNameSegment> segments, int x, int y, boolean shadow) {
        int currentX = x;
        for (SkinNameSegment segment : segments) {
            int color = shadow ? SHADOW_COLOR : segment.color;
            context.drawText(textRenderer, segment.text, currentX, y, color, false);
            currentX += textRenderer.getWidth(segment.text);
        }
    }

    public static void renderSlotOverlay(DrawContext context, Slot slot) {
        if (!slot.hasStack()) return;
        renderOverlayAt(context, slot.x, slot.y, slot.getStack(), true);
    }

    public static void renderHotbarItemOverlay(DrawContext context, int x, int y, ItemStack stack) {
        renderOverlayAt(context, x, y, stack, false);
    }

    private static void renderOverlayAt(DrawContext context, int x, int y, ItemStack stack, boolean inventorySlot) {
        if (!WTZClient.CONFIG.mountItemOverlayEnabled) return;
        if (stack == null || stack.isEmpty()) return;

        String name = stack.getName().getString();
        if (!name.contains("Reins") && !name.contains("Saddle") && !name.contains("Harness")
                && !name.contains("Flute") && !name.contains("Ocarina") && !name.contains("Whistle")) return;

        Map<String, int[]> mountStats = MountUtils.parseFullStats(stack);

        int totalMax = 0;
        boolean hasMax = false;
        for (int[] val : mountStats.values()) {
            if (val[2] > 0) {
                totalMax += val[2];
                hasMax = true;
            } else {
                totalMax += val[1];
            }
        }

        String potential;
        double potentialValue;
        if (hasMax && totalMax > 0) {
            potential = String.valueOf(totalMax);
            potentialValue = totalMax;
        } else {
            PotentialInfo info = extractPotentialInfo(stack);
            if (info == null) return;
            potential = info.formatted;
            potentialValue = info.value;
        }

        boolean showBars = WTZClient.CONFIG.mountItemOverlayBarsEnabled && shouldShowStatBars(inventorySlot);
        boolean showPotential = WTZClient.CONFIG.mountItemOverlayPotentialEnabled;

        if (showBars) {
            renderStatBars(context, x, y, mountStats);
        }

        if (showPotential) {
            TextRenderer textRenderer = WTZClient.client().textRenderer;
            int textWidth = textRenderer.getWidth(potential);
            int barsBottom = 2 + BAR_HEIGHT * 2 + BAR_GAP + 2;
            float maxHeight = 16 - barsBottom;
            float scaleByWidth = 16f / textWidth;
            float scaleByHeight = maxHeight / 7f;
            float scale = Math.min(0.7f, Math.min(scaleByWidth, scaleByHeight));
            float scaledWidth = textWidth * scale;
            float scaledHeight = 7 * scale;

            float px = x + (16 - scaledWidth) / 2f;
            float availableHeight = 16 - barsBottom;
            float py = y + barsBottom + (availableHeight - scaledHeight) / 2f;

            Matrix3x2fStack matrices = context.getMatrices();
            matrices.pushMatrix();
            matrices.translate(px, py);
            matrices.scale(scale, scale);
            int color = potentialColor(potentialValue);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    context.drawText(textRenderer, potential, dx, dy, SHADOW_COLOR, false);
                }
            }
            context.drawText(textRenderer, potential, 0, 0, color, false);
            matrices.popMatrix();
        }
    }

    private static boolean shouldShowStatBars(boolean inventorySlot) {
        MountItemOverlayModifierKey key = WTZClient.CONFIG.mountItemOverlayBarsModifierKey;
        if (!inventorySlot && WTZClient.CONFIG.mountItemOverlayBarsAlwaysShowInHotbar) return true;
        return switch (key) {
            case NONE -> true;
            case CTRL -> inventorySlot && isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
            case SHIFT -> inventorySlot && isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
            case ALT -> inventorySlot && isKeyPressed(GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT);
        };
    }

    private static boolean isKeyPressed(int leftKey, int rightKey) {
        return InputUtil.isKeyPressed(WTZClient.client().getWindow(), leftKey)
                || InputUtil.isKeyPressed(WTZClient.client().getWindow(), rightKey);
    }

    private record PotentialInfo(String formatted, double value) {
    }

    private static PotentialInfo extractPotentialInfo(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        for (Text line : lore.lines()) {
            String str = line.getString();
            Matcher m = MountPatterns.POTENTIAL.matcher(str);
            if (m.find()) {
                String raw = m.group(1);
                String lower = raw.toLowerCase();
                double value;
                if (lower.endsWith("k")) {
                    value = Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1000;
                } else {
                    value = Double.parseDouble(lower.replace(",", "."));
                }
                return new PotentialInfo(formatPotential(raw), value);
            }
        }
        return null;
    }

    private static final int[][] COLOR_STOPS = {
            {240, 0xFF, 0x44, 0x44},  
            {480, 0xFF, 0xAA, 0x22},  
            {720, 0xFF, 0xFF, 0x33},  
            {960, 0x55, 0xFF, 0x55},  
            {1200, 0x20, 0xB3, 0xB3},  
            {1800, 0x77, 0x55, 0xFF},  
            {2500, 0xFF, 0x55, 0xDD},  
    };

    private static int potentialColor(double value) {
        if (value <= COLOR_STOPS[0][0]) {
            return colorStop(COLOR_STOPS[0]);
        }
        for (int i = 1; i < COLOR_STOPS.length; i++) {
            if (value <= COLOR_STOPS[i][0]) {
                float t = (float) (value - COLOR_STOPS[i - 1][0]) / (COLOR_STOPS[i][0] - COLOR_STOPS[i - 1][0]);
                int r = (int) (COLOR_STOPS[i - 1][1] + t * (COLOR_STOPS[i][1] - COLOR_STOPS[i - 1][1]));
                int g = (int) (COLOR_STOPS[i - 1][2] + t * (COLOR_STOPS[i][2] - COLOR_STOPS[i - 1][2]));
                int b = (int) (COLOR_STOPS[i - 1][3] + t * (COLOR_STOPS[i][3] - COLOR_STOPS[i - 1][3]));
                return 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return colorStop(COLOR_STOPS[COLOR_STOPS.length - 1]);
    }

    private static int colorStop(int[] stop) {
        return 0xFF000000 | (stop[1] << 16) | (stop[2] << 8) | stop[3];
    }

    private static void renderStatBars(DrawContext context, int slotX, int slotY, Map<String, int[]> stats) {
        if (stats.isEmpty()) return;

        int totalLevel = 0, totalLimit = 0, totalMax = 0;
        boolean hasMax = false;

        for (int[] val : stats.values()) {
            totalLevel += val[0];
            totalLimit += val[1];
            if (val[2] > 0) {
                totalMax += val[2];
                hasMax = true;
            }
        }

        if (totalLimit == 0) return;

        int barWidth = 14;
        int barX = slotX + 1;

        if (hasMax && totalMax > 0) {
            float levelRatio = Math.min(1f, (float) totalLevel / totalMax);
            float limitRatio = Math.min(1f, (float) totalLimit / totalMax);
            boolean levelMaxed = totalLevel >= totalMax;
            boolean limitMaxed = totalLimit >= totalMax;

            int barY = slotY + 2;

            int levelColor = levelMaxed ? MAXED_BAR_COLOR : 0xFF000000 | ColorUtils.energyGradient(levelRatio);
            drawBar(context, barX, barY, barWidth, levelRatio, levelColor);

            barY += BAR_HEIGHT + BAR_GAP;

            int limitColor = limitMaxed ? MAXED_BAR_COLOR : 0xFF000000 | ColorUtils.energyGradient(limitRatio);
            drawBar(context, barX, barY, barWidth, limitRatio, limitColor);
        } else {
            float ratio = Math.min(1f, (float) totalLevel / totalLimit);
            boolean maxed = totalLevel >= totalLimit;

            int barY = slotY + 2;
            int color = maxed ? MAXED_BAR_COLOR : 0xFF000000 | ColorUtils.energyGradient(ratio);
            drawBar(context, barX, barY, barWidth, ratio, color);
        }
    }

    private static void drawBar(DrawContext context, int x, int y, int width, float ratio, int color) {
        context.fill(x - 1, y - 1, x + width + 1, y + BAR_HEIGHT + 1, BAR_BORDER_COLOR);
        context.fill(x, y, x + width, y + BAR_HEIGHT, BAR_BG_COLOR);
        int fill = Math.max(1, Math.round(width * ratio));
        context.fill(x, y, x + fill, y + BAR_HEIGHT, color);
    }

    public static String extractMountSkin(int slotId) {
        HandledScreen<?> handled = ScreenUtils.currentHandledScreenOrNull();
        if (handled == null) return null;

        ScreenHandler handler = handled.getScreenHandler();
        if (slotId < 0 || slotId >= handler.slots.size()) return null;

        ItemStack stack = handler.getSlot(slotId).getStack();
        if (stack.isEmpty()) return null;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;
        return MountUtils.extractSkin(lore.lines());
    }

    private static String extractMountType(int slotId) {
        HandledScreen<?> handled = ScreenUtils.currentHandledScreenOrNull();
        if (handled == null) return "";

        ScreenHandler handler = handled.getScreenHandler();
        if (slotId < 0 || slotId >= handler.slots.size()) return "";

        ItemStack stack = handler.getSlot(slotId).getStack();
        if (stack.isEmpty()) return "";

        String name = stack.getName().getString();
        if (name.contains("Saddle")) return "Horse";
        if (name.contains("Reins")) return "Wyvern";
        if (name.contains("Harness")) return "Adasaur";
        return "";
    }

    private static String formatPotential(String raw) {
        String lower = raw.toLowerCase();
        if (lower.endsWith("k")) {
            try {
                double val = Double.parseDouble(lower.substring(0, lower.length() - 1));
                long whole = Math.round(val * 1000);
                if (whole >= 1000 && whole % 1000 == 0) {
                    return (whole / 1000) + "k";
                } else if (whole >= 100 && whole % 100 == 0) {
                    return String.format("%.1fk", whole / 1000.0);
                }
                return raw;
            } catch (NumberFormatException e) {
                return raw;
            }
        }

        try {
            int val = Integer.parseInt(raw);
            if (val >= 1000) {
                double k = val / 1000.0;
                if (val % 1000 == 0) {
                    return (val / 1000) + "k";
                }
                return String.format("%.1fk", k);
            }
            return raw;
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    public static boolean isMountSkinWindow(HandledScreen<?> handled) {
        return ScreenUtils.handledScreenHasTitle(handled, MOUNT_SKIN_WINDOW_TITLE);
    }
}
