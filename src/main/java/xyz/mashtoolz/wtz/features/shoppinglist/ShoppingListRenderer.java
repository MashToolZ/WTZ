package xyz.mashtoolz.wtz.features.shoppinglist;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.enums.GUI;

import java.util.ArrayList;
import java.util.List;

public class ShoppingListRenderer {

    private static final ShoppingListRenderer INSTANCE = new ShoppingListRenderer();
    private static final int PANEL_OFFSET = 20;
    private static final long NPC_OPEN_GRACE_MS = 2_000L;

    private final List<ShoppingListPanel> panels = new ArrayList<>();
    private boolean globalVisible = false;
    private HandledScreen<?> lastAutoOpenedTradeMarketScreen = null;
    private boolean autoOpenedTradeMarket = false;
    private long pendingNpcTradeMarketOpenUntilMs = 0L;

    private ShoppingListRenderer() {
    }

    public static ShoppingListRenderer getInstance() {
        return INSTANCE;
    }

    public void init() {
        ShoppingListManager mgr = ShoppingListManager.getInstance();
        ShoppingListData active = mgr.getActiveList();
        if (active != null && panels.isEmpty()) {
            addPanel(active.getId());
        }
    }

    public void toggleVisibility() {
        panels.removeIf(ShoppingListPanel::isClosed);
        if (globalVisible && !panels.isEmpty()) {
            globalVisible = false;
        } else {
            globalVisible = true;
            ensureAtLeastOnePanel();
        }
    }

    public void openNewPanelForList(String listId) {
        ShoppingListManager mgr = ShoppingListManager.getInstance();
        if (mgr.getList(listId) == null) return;

        addPanel(listId);
    }

    public void ensureAtLeastOnePanel() {
        panels.removeIf(ShoppingListPanel::isClosed);
        if (panels.isEmpty()) {
            ShoppingListManager mgr = ShoppingListManager.getInstance();
            ShoppingListData active = mgr.getActiveList();
            if (active != null) {
                addPanel(active.getId());
            }
        }
    }

    public void switchToList(String listId) {
        for (ShoppingListPanel panel : panels) {
            panel.setPinnedListId(listId);
        }
    }

    public void showIfHidden() {
        globalVisible = true;
        ensureAtLeastOnePanel();
    }

    public void autoOpenForScreen(HandledScreen<?> screen) {
        if (!WTZClient.CONFIG.shoppingListEnabled || !WTZClient.CONFIG.shoppingListAutoOpenTradeMarket) return;
        if (!GUI.TRADE_MARKET.is(screen)) {
            closeAutoOpenedTradeMarketPanel();
            return;
        }
        if (lastAutoOpenedTradeMarketScreen == screen) return;
        if (!consumePendingNpcTradeMarketOpen()) return;

        boolean wasVisible = globalVisible;
        lastAutoOpenedTradeMarketScreen = screen;
        showIfHidden();
        autoOpenedTradeMarket = !wasVisible;
    }

    public void onScreenClosed(HandledScreen<?> screen) {
        if (screen == lastAutoOpenedTradeMarketScreen && WTZClient.client().currentScreen == null) {
            onScreenChanged(null);
        }
    }

    public void onScreenChanged(Screen screen) {
        if (screen instanceof HandledScreen<?> handledScreen && GUI.TRADE_MARKET.is(handledScreen)) return;

        pendingNpcTradeMarketOpenUntilMs = 0L;
        closeAutoOpenedTradeMarketPanel();
    }

    public void onNpcInteraction() {
        pendingNpcTradeMarketOpenUntilMs = System.currentTimeMillis() + NPC_OPEN_GRACE_MS;
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        if (cannotUsePanel()) return;

        panels.removeIf(ShoppingListPanel::isClosed);

        for (ShoppingListPanel panel : panels) {
            panel.render(context, mouseX, mouseY);
        }
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (cannotUsePanel()) return false;

        for (int i = panels.size() - 1; i >= 0; i--) {
            ShoppingListPanel panel = panels.get(i);
            if (panel.isMouseOver(mouseX, mouseY)) {
                boolean consumed = panel.onMouseClicked(mouseX, mouseY, button);
                if (consumed) {
                    bringToFront(i);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (cannotUsePanel()) return false;
 
        for (int i = panels.size() - 1; i >= 0; i--) {
            ShoppingListPanel panel = panels.get(i);
            if (panel.isMouseOver(mouseX, mouseY)) {
                return panel.onMouseScrolled(mouseX, mouseY, amount);
            }
        }
        return false;
    }

    public boolean isMouseOverPanel(double mouseX, double mouseY) {
        if (cannotUsePanel()) return false;

        panels.removeIf(ShoppingListPanel::isClosed);
        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    public boolean onKeyPressed(int keyCode) {
        if (cannotUsePanel()) return false;

        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).onKeyPressed(keyCode)) {
                return true;
            }
        }
        return false;
    }

    public boolean onCharTyped(int codepoint) {
        if (cannotUsePanel()) return false;

        for (int i = panels.size() - 1; i >= 0; i--) {
            if (panels.get(i).onCharTyped(codepoint)) {
                return true;
            }
        }
        return false;
    }

    public void addItemToActiveList(String name) {
        name = name.trim();
        ShoppingListManager mgr = ShoppingListManager.getInstance();

        ShoppingListData active = mgr.getActiveList();
        if (active == null) return;

        if (active.hasItem(name)) return;

        mgr.addItem(name);
        showIfHidden();
    }

    private void bringToFront(int index) {
        if (index < 0 || index >= panels.size() - 1) return;
        ShoppingListPanel panel = panels.remove(index);
        panels.add(panel);
    }

    private void addPanel(String listId) {
        int offset = 10 + panels.size() * PANEL_OFFSET;
        panels.add(new ShoppingListPanel(listId, offset, offset));
    }

    private void closeAutoOpenedTradeMarketPanel() {
        if (autoOpenedTradeMarket) {
            globalVisible = false;
        }
        lastAutoOpenedTradeMarketScreen = null;
        autoOpenedTradeMarket = false;
    }

    private boolean consumePendingNpcTradeMarketOpen() {
        if (System.currentTimeMillis() > pendingNpcTradeMarketOpenUntilMs) {
            pendingNpcTradeMarketOpenUntilMs = 0L;
            return false;
        }
        pendingNpcTradeMarketOpenUntilMs = 0L;
        return true;
    }

    private boolean cannotUsePanel() {
        return !globalVisible || !WTZClient.CONFIG.shoppingListEnabled;
    }
}
