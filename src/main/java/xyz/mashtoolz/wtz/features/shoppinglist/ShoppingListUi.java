package xyz.mashtoolz.wtz.features.shoppinglist;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

final class ShoppingListUi {

    static final int PANEL_WIDTH = 230;
    static final int MAX_VISIBLE_ROWS = 12;

    static final int OUTLINE_DARK = 0xFF080808;
    static final int OUTLINE_MID = 0xFF2A2A2A;
    static final int OUTLINE_LIGHT = 0xFF3A3A3A;
    static final int PRIMARY_ORANGE = 0xFFFF4800;
    static final int PANEL_BG = 0xFF171717;
    static final int HEADER_BG = 0xFF1E1E1E;
    static final int BODY_ROW = 0xFF171717;
    static final int BODY_ROW_HOVER = 0x33FF4800;
    static final int DROPDOWN_BG = 0xF0121212;
    static final int DROPDOWN_HOVER = 0x33FF4800;
    static final int FIELD_BG = 0xFF101010;
    static final int FIELD_FILL = 0xFF171717;
    static final int INPUT_BG = 0xFF101010;
    static final int INPUT_FILL = 0xFF202020;
    static final int BUTTON_BG = 0xFF1F1F1F;
    static final int BUTTON_FILL = 0xFF1F1F1F;
    static final int BUTTON_HOVER_FILL = 0xFF2A2A2A;
    static final int BUTTON_ACTIVE_FILL = 0xFF332415;
    static final float UI_TEXT_SCALE = 0.78f;

    static final int TITLE_TEXT = 0xFFECECEC;
    static final int LABEL_TEXT = 0xFF9A9A9A;
    static final int ITEM_TEXT = 0xFFE5E5E5;
    static final int MUTED_TEXT = 0xFF8A8A8A;
    static final int HOVER_TEXT = PRIMARY_ORANGE;
    static final int SUCCESS_TEXT = 0xFF55FF55;
    static final int PARTIAL_TEXT = 0xFFFFFF55;
    static final int MISSING_TEXT = 0xFFFF5555;

    static final int PADDING = 4;
    static final int DRAG_BAR_HEIGHT = 0;
    static final int TITLE_HEIGHT = 22;
    static final int HEADER_HEIGHT = 12;
    static final int ROW_HEIGHT = 16;
    static final int FOOTER_HEIGHT = 22;
    static final int BUTTON_HEIGHT = 14;
    static final int TITLE_BUTTON_GAP = 3;
    static final int FOOTER_BUTTON_GAP = 4;
    static final int DROPDOWN_ROW_HEIGHT = 12;

    static final int ITEM_NAME_X = PADDING + 6;
    static final int ITEM_NAME_WIDTH = 108;
    static final int QTY_X = 126;
    static final int QTY_WIDTH = 34;
    static final int HAVE_X = 170;
    static final int HAVE_WIDTH = 50;

    static final String[] TITLE_BUTTON_LABELS = {"Ren", "+", "Del", "X"};
    static final String[] TITLE_BUTTON_TOOLTIPS = {
            "Rename this list",
            "Create a new list",
            "Delete this list",
            "Close this panel"
    };
    static final String[] FOOTER_BUTTON_LABELS = {"Clear", "Export", "Import"};

    static final int SLIDER_HANDLE_WIDTH = 4;
    static final float MIN_SCALE = 0.5f;
    static final float MAX_SCALE = 1.0f;

    private ShoppingListUi() {
    }

    static void drawPanelFrame(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, OUTLINE_DARK);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, OUTLINE_MID);
        context.fill(x + 2, y + 2, x + w - 2, y + h - 2, PANEL_BG);
    }

    static void drawBevelBox(DrawContext context, int x, int y, int w, int h, int fill) {
        context.fill(x, y, x + w, y + h, OUTLINE_DARK);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, OUTLINE_LIGHT);
        context.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
    }

    static void drawCenteredText(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int w, int h, int color) {
        drawCenteredText(context, textRenderer, text, x, y, w, h, color, 0);
    }

    static void drawCenteredText(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int w, int h, int color, int xOffset) {
        int tw = getScaledTextWidth(textRenderer, text);
        int th = getScaledFontHeight(textRenderer);
        int tx = x + (w - tw) / 2 + xOffset;
        int ty = y + (h - th) / 2 + 1;
        drawTextScaled(context, textRenderer, text, tx, ty, color);
    }

    static int getScaledTextWidth(TextRenderer textRenderer, String text) {
        return Math.round(textRenderer.getWidth(text) * UI_TEXT_SCALE);
    }

    static int getScaledFontHeight(TextRenderer textRenderer) {
        return Math.round(textRenderer.fontHeight * UI_TEXT_SCALE);
    }

    static void drawTextScaled(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int color) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(UI_TEXT_SCALE, UI_TEXT_SCALE);
        context.drawText(textRenderer, text, 0, 0, color, false);
        context.getMatrices().popMatrix();
    }

    static String trimToWidth(TextRenderer textRenderer, String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "..";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (textRenderer.getWidth(builder.toString() + text.charAt(i)) + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(text.charAt(i));
        }
        return builder + ellipsis;
    }

    static String trimToScaledWidth(TextRenderer textRenderer, String text, int maxWidth) {
        int unscaledWidth = Math.max(1, (int) Math.floor(maxWidth / UI_TEXT_SCALE));
        return trimToWidth(textRenderer, text, unscaledWidth);
    }

    static boolean isWithin(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
