package xyz.mashtoolz.wtz.features.mount.bank;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.lwjgl.glfw.GLFW;
import xyz.mashtoolz.wtz.client.WTZClient;
import java.util.ArrayList;
import java.util.List;

public final class MountBankIndexPanel {
    private static final int WIDTH = 152;
    private static final int HEIGHT = 66;
    private static final int PADDING = 7;
    private static final int TITLE_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BUTTON_GAP = 4;
    private static final int RESIZE_HANDLE_SIZE = 9;
    private static final float MIN_SCALE = 0.75f;
    private static final float MAX_SCALE = 1.5f;
    private static final int TOOLTIP_PADDING = 5;
    private static final int PANEL_BG = 0xE6121212;
    private static final int PANEL_BORDER = 0xFF2F2F2F;
    private static final int BUTTON_BG = 0xFF2A2A2A;
    private static final int BUTTON_HOVER = 0xFF3A3A3A;
    private static final int BUTTON_DISABLED = 0xFF1E1E1E;
    private static final int TEXT = 0xFFECECEC;
    private static final int MUTED = 0xFF9A9A9A;
    private static final int ACCENT = 0xFFFF8A2A;

    private static final boolean VISIBLE = false;
    private static String status = "Ready";
    private static int panelX;
    private static int panelY;
    private static boolean dragging = false;
    private static boolean resizing = false;
    private static int dragOffsetX;
    private static int dragOffsetY;

    private MountBankIndexPanel() {
    }

    public static void render(DrawContext context, HandledScreen<?> screen, int mouseX, int mouseY) {
        if (MountBankIndexer.isDisabled()) return;
        if (!VISIBLE) return;

        TextRenderer textRenderer = WTZClient.client().textRenderer;
        int screenW = WTZClient.client().getWindow().getScaledWidth();
        int screenH = WTZClient.client().getWindow().getScaledHeight();
        float scale = WTZClient.CONFIG.mountBankIndexerScale;
        int scaledW = Math.round(WIDTH * scale);
        int scaledH = Math.round(HEIGHT * scale);
        panelX = Math.round((float) (WTZClient.CONFIG.mountBankIndexerPctX / 100.0 * Math.max(0, screenW - scaledW)));
        panelY = Math.round((float) (WTZClient.CONFIG.mountBankIndexerPctY / 100.0 * Math.max(0, screenH - scaledH)));

        boolean mouseDown = GLFW.glfwGetMouseButton(WTZClient.client().getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (!mouseDown) {
            dragging = false;
            resizing = false;
        }
        if (resizing) {
            scale = Math.clamp((float) (mouseX - panelX) / WIDTH, MIN_SCALE, MAX_SCALE);
            WTZClient.CONFIG.mountBankIndexerScale = scale;
            scaledW = Math.round(WIDTH * scale);
            scaledH = Math.round(HEIGHT * scale);
        }
        if (dragging) {
            panelX = mouseX - dragOffsetX;
            panelY = mouseY - dragOffsetY;
        }
        panelX = Math.clamp(panelX, 0, Math.max(0, screenW - scaledW));
        panelY = Math.clamp(panelY, 0, Math.max(0, screenH - scaledH));
        savePosition(screenW, screenH, scaledW, scaledH);

        int umx = unscaleMouseX(mouseX, scale);
        int umy = unscaleMouseY(mouseY, scale);

        boolean bank = isBankScreen(screen);
        List<String> tooltip = null;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(panelX, panelY);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-panelX, -panelY);

        context.fill(panelX, panelY, panelX + WIDTH, panelY + HEIGHT, PANEL_BORDER);
        context.fill(panelX + 1, panelY + 1, panelX + WIDTH - 1, panelY + HEIGHT - 1, PANEL_BG);
        context.drawText(textRenderer, "Mount Bank Indexer", panelX + PADDING, panelY + 6, ACCENT, false);

        String count = MountBankIndexer.lastEntryCount() + " indexed";
        context.drawText(textRenderer, count, panelX + PADDING, panelY + 19, MUTED, false);
        if (in(umx, umy, panelX + PADDING, panelY + 18, WIDTH - PADDING * 2, 11)) {
            tooltip = List.of(
                    "Loaded index entries.",
                    "A completed Start scan saves these to disk.",
                    "The website receives the full dataset after scanning."
            );
        }

        int buttonY = panelY + 34;
        int startX = buttonX(0);
        int cancelX = buttonX(1);
        int buttonWidth = buttonW();
        drawButton(context, textRenderer, buttonX(0), buttonY, buttonW(),
                MountBankIndexer.isRunning() ? "Running" : "Start", bank && !MountBankIndexer.isRunning(), umx, umy);
        drawButton(context, textRenderer, buttonX(1), buttonY, buttonW(),
                "Cancel", MountBankIndexer.isRunning(), umx, umy);
        if (in(umx, umy, startX, buttonY, buttonWidth, BUTTON_HEIGHT)) {
            tooltip = startTooltip(bank);
        } else if (in(umx, umy, cancelX, buttonY, buttonWidth, BUTTON_HEIGHT)) {
            tooltip = MountBankIndexer.isRunning()
                    ? List.of("Stop the active full-bank scan.", "Already indexed pages stay in memory.")
                    : List.of("No scan is currently running.");
        }

        int statusY = panelY + HEIGHT - 13;
        context.drawText(textRenderer, trimStatus(textRenderer, bank ? status : "Open bank page"),
                panelX + PADDING, statusY, bank ? MUTED : 0xFFFFCC55, false);
        drawResizeHandle(context, umx, umy);
        context.getMatrices().popMatrix();

        if (in(umx, umy, panelX + PADDING, statusY - 1, WIDTH - PADDING * 2, 11)) {
            tooltip = List.of("Latest panel status.", bank ? status : "Open a bank page to enable scanning.");
        }
        if (isOverResizeHandle(umx, umy)) {
            tooltip = List.of("Drag to resize this panel.");
        } else if (in(umx, umy, panelX, panelY, WIDTH, TITLE_HEIGHT)) {
            tooltip = List.of("Drag to move this panel.");
        }

        if (tooltip != null) {
            drawTooltip(context, textRenderer, tooltip, mouseX, mouseY);
        }
    }

    public static boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (MountBankIndexer.isDisabled()) return false;
        if (!VISIBLE || button != 0) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;

        if (!(WTZClient.client().currentScreen instanceof HandledScreen<?> screen)) return true;
        boolean bank = isBankScreen(screen);
        float scale = WTZClient.CONFIG.mountBankIndexerScale;
        int umx = unscaleMouseX(mouseX, scale);
        int umy = unscaleMouseY(mouseY, scale);

        int row1 = panelY + 34;
        if (isOverResizeHandle(umx, umy)) {
            resizing = true;
            return true;
        }
        if (in(umx, umy, buttonX(0), row1, buttonW(), BUTTON_HEIGHT)) {
            if (bank && !MountBankIndexer.isRunning()) {
                MountBankIndexer.start();
                status = "Indexing...";
            } else if (!bank) {
                status = "Open bank page";
            }
            return true;
        }
        if (in(umx, umy, buttonX(1), row1, buttonW(), BUTTON_HEIGHT)) {
            if (MountBankIndexer.isRunning()) {
                MountBankIndexer.cancel();
                status = "Cancelled";
            }
            return true;
        }
        if (in(umx, umy, panelX, panelY, WIDTH, HEIGHT)) {
            dragging = true;
            dragOffsetX = (int) mouseX - panelX;
            dragOffsetY = (int) mouseY - panelY;
            return true;
        }
        return true;
    }

    public static boolean isMouseOver(double mouseX, double mouseY) {
        if (MountBankIndexer.isDisabled() || !VISIBLE) return false;
        float scale = WTZClient.CONFIG.mountBankIndexerScale;
        return in(mouseX, mouseY, panelX, panelY, Math.round(WIDTH * scale), Math.round(HEIGHT * scale));
    }

    private static boolean isBankScreen(HandledScreen<?> screen) {
        return MountBankScanner.isBankScreen(screen);
    }

    private static List<String> startTooltip(boolean bank) {
        if (MountBankIndexer.isRunning()) {
            return List.of("A full-bank scan is already running.", "Wait for it to finish or press Cancel.");
        }
        if (!bank) {
            return List.of("Open a bank page first.", "Start scans all pages from the current page forward.");
        }
        return List.of(
                "Scan every bank page from here to the end.",
                "Clicks the next-page button automatically.",
                "Sends the completed mount dataset to the website."
        );
    }

    private static void drawButton(DrawContext context, TextRenderer textRenderer, int x, int y, int w,
                                   String label, boolean enabled, int mouseX, int mouseY) {
        int h = BUTTON_HEIGHT;
        boolean hovered = enabled && in(mouseX, mouseY, x, y, w, h);
        int fill = enabled ? hovered ? BUTTON_HOVER : BUTTON_BG : BUTTON_DISABLED;
        context.fill(x, y, x + w, y + h, 0xFF0A0A0A);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);

        int color = enabled ? TEXT : MUTED;
        int textX = x + (w - textRenderer.getWidth(label)) / 2;
        int textY = y + (h - textRenderer.fontHeight) / 2 + 1;
        context.drawText(textRenderer, label, textX, textY, color, false);
    }

    private static int buttonX(int column) {
        return panelX + PADDING + column * (buttonW() + BUTTON_GAP);
    }

    private static int buttonW() {
        return (WIDTH - PADDING * 2 - BUTTON_GAP) / 2;
    }

    private static int unscaleMouseX(double mouseX, float scale) {
        return Math.round(panelX + (float) (mouseX - panelX) / scale);
    }

    private static int unscaleMouseY(double mouseY, float scale) {
        return Math.round(panelY + (float) (mouseY - panelY) / scale);
    }

    private static void savePosition(int screenW, int screenH, int scaledW, int scaledH) {
        WTZClient.CONFIG.mountBankIndexerPctX = screenW > scaledW ? (double) panelX / (screenW - scaledW) * 100.0 : 0.0;
        WTZClient.CONFIG.mountBankIndexerPctY = screenH > scaledH ? (double) panelY / (screenH - scaledH) * 100.0 : 0.0;
    }

    private static boolean isOverResizeHandle(double mouseX, double mouseY) {
        int x = panelX + WIDTH - RESIZE_HANDLE_SIZE - 3;
        int y = panelY + HEIGHT - RESIZE_HANDLE_SIZE - 3;
        return in(mouseX, mouseY, x, y, RESIZE_HANDLE_SIZE + 2, RESIZE_HANDLE_SIZE + 2);
    }

    private static void drawResizeHandle(DrawContext context, int mouseX, int mouseY) {
        int x = panelX + WIDTH - RESIZE_HANDLE_SIZE - 3;
        int y = panelY + HEIGHT - RESIZE_HANDLE_SIZE - 3;
        int color = isOverResizeHandle(mouseX, mouseY) || resizing ? ACCENT : MUTED;
        context.fill(x + RESIZE_HANDLE_SIZE - 1, y, x + RESIZE_HANDLE_SIZE + 1, y + RESIZE_HANDLE_SIZE + 1, color);
        context.fill(x, y + RESIZE_HANDLE_SIZE - 1, x + RESIZE_HANDLE_SIZE + 1, y + RESIZE_HANDLE_SIZE + 1, color);
    }

    private static boolean in(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void drawTooltip(DrawContext context, TextRenderer textRenderer, List<String> rawLines, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            if (line != null && !line.isBlank()) lines.add(line);
        }
        if (lines.isEmpty()) return;

        int width = 0;
        for (String line : lines) {
            width = Math.max(width, textRenderer.getWidth(line));
        }
        int boxW = width + TOOLTIP_PADDING * 2;
        int boxH = lines.size() * (textRenderer.fontHeight + 2) + TOOLTIP_PADDING * 2 - 2;

        int screenW = WTZClient.client().getWindow().getScaledWidth();
        int screenH = WTZClient.client().getWindow().getScaledHeight();
        int x = mouseX + 10;
        int y = mouseY + 10;
        if (x + boxW > screenW - 4) x = mouseX - boxW - 10;
        if (y + boxH > screenH - 4) y = screenH - boxH - 4;
        x = Math.max(4, x);
        y = Math.max(4, y);

        context.fill(x, y, x + boxW, y + boxH, 0xF0101010);
        context.fill(x, y, x + boxW, y + 1, PANEL_BORDER);
        context.fill(x, y + boxH - 1, x + boxW, y + boxH, PANEL_BORDER);
        context.fill(x, y, x + 1, y + boxH, PANEL_BORDER);
        context.fill(x + boxW - 1, y, x + boxW, y + boxH, PANEL_BORDER);

        int lineY = y + TOOLTIP_PADDING;
        for (int i = 0; i < lines.size(); i++) {
            context.drawText(textRenderer, lines.get(i), x + TOOLTIP_PADDING, lineY,
                    i == 0 ? TEXT : MUTED, false);
            lineY += textRenderer.fontHeight + 2;
        }
    }

    private static String trimStatus(TextRenderer textRenderer, String text) {
        int maxWidth = WIDTH - PADDING * 2;
        if (textRenderer.getWidth(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = textRenderer.getWidth(ellipsis);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (textRenderer.getWidth(builder.toString() + text.charAt(i)) + ellipsisWidth > maxWidth) break;
            builder.append(text.charAt(i));
        }
        return builder + ellipsis;
    }
}
