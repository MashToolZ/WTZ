package xyz.mashtoolz.wtz.features.shoppinglist;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import xyz.mashtoolz.wtz.client.WTZClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListUi.*;

final class ShoppingListTooltip {

    static final int TEXT_COLOR = TITLE_TEXT;
    static final int KEY_COLOR = 0xFFFFC766;

    private static final int BG = 0xFF060606;
    private static final int BORDER = 0x2EFFFFFF;
    private static final int KEY_BG = 0x12FFFFFF;
    private static final int KEY_BORDER = 0x2EFFFFFF;
    private static final int PADDING_X = 7;
    private static final int PADDING_Y = 6;
    private static final int VIEWPORT_PADDING = 12;
    private static final int LINE_GAP = 5;
    private static final float KEY_TEXT_SCALE = 0.72f;
    private static final int KEY_PAD_X = 3;
    private static final int KEY_PAD_Y = 2;
    private static final int KEY_Y_OFFSET = -1;

    private ShoppingListTooltip() {
    }

    static void render(DrawContext context, TextRenderer textRenderer, String text, int mouseX, int mouseY, int originX, int originY, float scale) {
        render(context, textRenderer, parse(text), mouseX, mouseY, originX, originY, scale);
    }

    static void render(DrawContext context, TextRenderer textRenderer, List<Line> lines, int mouseX, int mouseY, int originX, int originY, float scale) {
        int width = 0;
        for (Line line : lines) {
            int lineWidth = 0;
            for (Segment segment : line.segments()) {
                lineWidth += getSegmentAdvance(textRenderer, segment);
            }
            width = Math.max(width, lineWidth);
        }

        int textHeight = getScaledFontHeight(textRenderer);
        int height = lines.size() * textHeight + Math.max(0, lines.size() - 1) * LINE_GAP;
        int boxW = width + PADDING_X * 2;
        int boxH = height + PADDING_Y * 2;
        int minX = toUnscaled(originX, scale, VIEWPORT_PADDING);
        int minY = toUnscaled(originY, scale, VIEWPORT_PADDING);
        int maxX = Math.max(minX, toUnscaled(originX, scale, WTZClient.client().getWindow().getScaledWidth() - VIEWPORT_PADDING) - boxW);
        int maxY = Math.max(minY, toUnscaled(originY, scale, WTZClient.client().getWindow().getScaledHeight() - VIEWPORT_PADDING) - boxH);
        int x = Math.clamp(mouseX + 10, minX, maxX);
        int y = Math.clamp(mouseY + 10, minY, maxY);

        drawOutlinedRect(context, x, y, boxW, boxH, BG, BORDER);

        for (int i = 0; i < lines.size(); i++) {
            int segmentX = x + PADDING_X;
            int lineY = y + PADDING_Y + i * (textHeight + LINE_GAP);
            for (Segment segment : lines.get(i).segments()) {
                drawSegment(context, textRenderer, segment, segmentX, lineY, textHeight);
                segmentX += getSegmentAdvance(textRenderer, segment);
            }
        }
    }

    static List<Line> parse(String text) {
        return Arrays.stream(text.split("\\R"))
                .map(ShoppingListTooltip::parseLine)
                .toList();
    }

    static Line line(Segment... segments) {
        return new Line(List.of(segments));
    }

    static Segment text(String text) {
        return text(text, TEXT_COLOR);
    }

    static Segment text(String text, int color) {
        return new Segment(text, color, false);
    }

    static Segment key(String text) {
        return new Segment(text, KEY_COLOR, true);
    }

    private static Line parseLine(String line) {
        List<String> keys = List.of("Shift+Click", "Ctrl+Click", "Alt+Click", "Right-click", "Middle-click", "Shift", "Click");
        List<Segment> segments = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            String matched = null;
            for (String key : keys) {
                if (line.regionMatches(true, index, key, 0, key.length())) {
                    matched = line.substring(index, index + key.length());
                    break;
                }
            }
            if (matched != null) {
                segments.add(key(matched));
                index += matched.length();
                continue;
            }

            int next = line.length();
            String lowerLine = line.toLowerCase();
            for (String key : keys) {
                int found = lowerLine.indexOf(key.toLowerCase(), index + 1);
                if (found >= 0) next = Math.min(next, found);
            }
            segments.add(text(line.substring(index, next)));
            index = next;
        }
        return new Line(segments);
    }

    private static int getSegmentWidth(TextRenderer textRenderer, Segment segment) {
        int textWidth = segment.key() ? getKeyTextWidth(textRenderer, segment.text()) : getScaledTextWidth(textRenderer, segment.text());
        return segment.key() ? textWidth + KEY_PAD_X * 2 : textWidth;
    }

    private static int getSegmentAdvance(TextRenderer textRenderer, Segment segment) {
        return getSegmentWidth(textRenderer, segment);
    }

    private static void drawSegment(DrawContext context, TextRenderer textRenderer, Segment segment, int x, int y, int textHeight) {
        if (!segment.key()) {
            drawTextScaled(context, textRenderer, segment.text(), x, y, segment.color());
            return;
        }

        int w = getSegmentWidth(textRenderer, segment);
        int keyTextHeight = getKeyTextHeight(textRenderer);
        int h = keyTextHeight + KEY_PAD_Y * 2;
        int top = y + (textHeight - h) / 2 + KEY_Y_OFFSET;
        drawOutlinedRect(context, x, top, w, h, KEY_BG, KEY_BORDER);
        drawKeyText(context, textRenderer, segment.text(), x + KEY_PAD_X, top + KEY_PAD_Y, segment.color());
    }

    private static int getKeyTextWidth(TextRenderer textRenderer, String text) {
        return Math.round(textRenderer.getWidth(text) * KEY_TEXT_SCALE);
    }

    private static int getKeyTextHeight(TextRenderer textRenderer) {
        return Math.round(textRenderer.fontHeight * KEY_TEXT_SCALE);
    }

    private static void drawKeyText(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(KEY_TEXT_SCALE, KEY_TEXT_SCALE);
        context.drawText(textRenderer, text, 0, 0, color, false);
        context.getMatrices().popMatrix();
    }

    private static void drawOutlinedRect(DrawContext context, int x, int y, int w, int h, int fill, int border) {
        context.fill(x, y, x + w, y + h, fill);
        context.fill(x, y, x + w, y + 1, border);
        context.fill(x, y + h - 1, x + w, y + h, border);
        context.fill(x, y + 1, x + 1, y + h - 1, border);
        context.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
    }

    private static int toUnscaled(int origin, float scale, int screenCoordinate) {
        return Math.round(origin + (screenCoordinate - origin) / scale);
    }

    record Line(List<Segment> segments) {
    }

    record Segment(String text, int color, boolean key) {
    }
}
