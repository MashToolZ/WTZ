package xyz.mashtoolz.wtz.features.shoppinglist;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.config.WTZConfig;
import xyz.mashtoolz.wtz.features.qol.QualityOfLife;

import java.util.ArrayList;
import java.util.List;

import static xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListUi.*;

public class ShoppingListPanel {

    private String pinnedListId;
    private int posX;
    private int posY;
    private int scrollOffset;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean dropdownOpen;
    private boolean renaming;
    private String renameBuffer = "";
    private boolean renameAllSelected;
    private int editingQtyIndex = -1;
    private String qtyBuffer = "";
    private boolean qtyAllSelected;
    private int editingNameIndex = -1;
    private String itemNameBuffer = "";
    private boolean itemNameAllSelected;
    private boolean addingItem;
    private String addItemBuffer = "";
    private boolean addItemAllSelected;
    private int selectedSuggestionIndex;
    private int suggestionScrollOffset;
    private boolean visible;
    private long clearConfirmUntil;
    private float previewScale = 1.0f;
    private boolean draggingSlider;
    private String pendingTooltipText;
    private List<ShoppingListTooltip.Line> pendingTooltipLines;
    private int pendingTooltipMouseX;
    private int pendingTooltipMouseY;
    private List<ShoppingListCache.CachedItem> itemSuggestions = List.of();

    private static final int SUGGESTION_ROW_HEIGHT = 16;
    private static final int MAX_VISIBLE_SUGGESTIONS = 10;
    private static final float ITEM_ICON_SCALE = 0.65f;
    private static final int QTY_MINUS_COLOR = 0xFFFD3434;
    private static final int QTY_PLUS_COLOR = 0xFF34FD34;

    public ShoppingListPanel(String pinnedListId, int posX, int posY) {
        this.pinnedListId = pinnedListId;
        this.posX = posX;
        this.posY = posY;
        this.visible = true;
    }

    public boolean isClosed() {
        return !visible;
    }

    public void close() {
        clearTransientState();
        visible = false;
    }

    private void clearTransientState() {
        renaming = false;
        renameBuffer = "";
        renameAllSelected = false;
        editingQtyIndex = -1;
        qtyBuffer = "";
        qtyAllSelected = false;
        editingNameIndex = -1;
        itemNameBuffer = "";
        itemNameAllSelected = false;
        addingItem = false;
        addItemBuffer = "";
        addItemAllSelected = false;
        selectedSuggestionIndex = 0;
        suggestionScrollOffset = 0;
        itemSuggestions = List.of();
        dropdownOpen = false;
        clearConfirmUntil = 0L;
    }

    public void setPinnedListId(String id) {
        this.pinnedListId = id;
        this.scrollOffset = 0;
    }

    public ShoppingListData getList() {
        return ShoppingListManager.getInstance().getList(pinnedListId);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!visible) return false;

        int w = getScaledPanelWidth();
        int h = getScaledPanelHeight();
        boolean overBase = mouseX >= posX && mouseX < posX + w && mouseY >= posY && mouseY < posY + h;

        if (!overBase && isOverScaleSlider(mouseX, mouseY)) return true;
        if (overBase) return true;

        if ((addingItem || editingNameIndex >= 0) && !itemSuggestions.isEmpty()) {
            double umx = unscaleMouseX(mouseX);
            double umy = unscaleMouseY(mouseY);
            int suggestionY = getSuggestionListY();
            int suggestionHeight = getVisibleSuggestionCount() * SUGGESTION_ROW_HEIGHT + 4;
            int suggestionX = posX + ITEM_NAME_X - 2;
            int suggestionW = ITEM_NAME_WIDTH + QTY_WIDTH + HAVE_WIDTH + 8;
            return umx >= suggestionX && umx < suggestionX + suggestionW
                    && umy >= suggestionY && umy < suggestionY + suggestionHeight;
        }

        if (dropdownOpen) {
            double umx = unscaleMouseX(mouseX);
            double umy = unscaleMouseY(mouseY);
            int ddY = getDropdownListY();
            int ddHeight = ShoppingListManager.getInstance().getAllLists().size() * DROPDOWN_ROW_HEIGHT + 4;
            return umx >= getDropdownX() && umx < getDropdownX() + getDropdownWidth()
                    && umy >= ddY && umy < ddY + ddHeight;
        }

        return false;
    }

    private float getScale() {
        return WTZClient.CONFIG.shoppingListScale;
    }

    private void setScale(float value) {
        WTZClient.CONFIG.shoppingListScale = value;
        WTZConfig.save();
    }

    private double unscaleMouseX(double mouseX) {
        return posX + (mouseX - posX) / getScale();
    }

    private double unscaleMouseY(double mouseY) {
        return posY + (mouseY - posY) / getScale();
    }

    private int getPanelWidth() {
        return PANEL_WIDTH;
    }

    private int getScaledPanelWidth() {
        return Math.round(PANEL_WIDTH * getScale());
    }

    private int getScaledPanelHeight() {
        return Math.round(getUnscaledPanelHeight() * getScale());
    }

    private int getVisibleRows() {
        ShoppingListData list = getList();
        int itemCount = list != null ? list.getItems().size() : 0;
        return Math.min(itemCount + 1, MAX_VISIBLE_ROWS);
    }

    private int getUnscaledPanelHeight() {
        return DRAG_BAR_HEIGHT + TITLE_HEIGHT + HEADER_HEIGHT + getVisibleRows() * ROW_HEIGHT + FOOTER_HEIGHT + PADDING - 2;
    }

    private int getPanelHeight() {
        return getUnscaledPanelHeight();
    }

    private int getTitleY() {
        return posY + DRAG_BAR_HEIGHT + 2;
    }

    private int getHeaderY() {
        return getTitleY() + TITLE_HEIGHT;
    }

    private int getItemAreaY() {
        return getHeaderY() + HEADER_HEIGHT;
    }

    private int getFooterY() {
        return getItemAreaY() + getVisibleRows() * ROW_HEIGHT;
    }

    private int getSuggestionListY() {
        if (editingNameIndex >= 0) {
            return getItemAreaY() + (editingNameIndex - scrollOffset + 1) * ROW_HEIGHT + 2;
        }
        return getAddRowY(getList()) + ROW_HEIGHT + 2;
    }

    private int getDropdownX() {
        return posX + 2 + PADDING;
    }

    private int getDropdownY() {
        return getTitleY() + (TITLE_HEIGHT - getDropdownHeight()) / 2;
    }

    private int getDropdownWidth() {
        return 100;
    }

    private int getDropdownHeight() {
        return TITLE_HEIGHT - 8;
    }

    private int getDropdownListY() {
        return getDropdownY() + getDropdownHeight() + 2;
    }

    private int getTitleButtonWidth(TextRenderer textRenderer, int index) {
        if ("X".equals(TITLE_BUTTON_LABELS[index])) {
            return getDropdownHeight();
        }
        return Math.max(getDropdownHeight(), getScaledTextWidth(textRenderer, TITLE_BUTTON_LABELS[index]) + 12);
    }

    private int getFooterButtonWidth(TextRenderer textRenderer, int index) {
        int minWidth = index == 0 ? 42 : 44;
        return Math.max(minWidth, getScaledTextWidth(textRenderer, getFooterButtonLabel(index)) + 12);
    }

    private String getFooterButtonLabel(int index) {
        if (index == 0 && clearConfirmUntil > System.currentTimeMillis()) {
            return "Confirm";
        }
        return FOOTER_BUTTON_LABELS[index];
    }

    public void render(DrawContext context, int mouseX, int mouseY) {
        if (!visible) return;

        ShoppingListData list = getList();
        if (list == null) {
            ShoppingListData active = ShoppingListManager.getInstance().getActiveList();
            if (active == null) return;
            pinnedListId = active.getId();
            list = active;
        }

        long window = WTZClient.client().getWindow().getHandle();
        boolean mouseDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (dragging && !mouseDown) {
            dragging = false;
        }

        int scaledW = getScaledPanelWidth();
        int scaledH = getScaledPanelHeight();
        int screenW = WTZClient.client().getWindow().getScaledWidth();
        int screenH = WTZClient.client().getWindow().getScaledHeight();

        if (draggingSlider) {
            previewScale = Math.clamp((float) (mouseX - posX) / PANEL_WIDTH, MIN_SCALE, MAX_SCALE);
            if (!mouseDown) {
                setScale(previewScale);
                draggingSlider = false;
                scaledW = getScaledPanelWidth();
                scaledH = getScaledPanelHeight();
            }
        }

        if (dragging) {
            posX = mouseX - dragOffsetX;
            posY = mouseY - dragOffsetY;
        }
        posX = Math.clamp(posX, 0, Math.max(0, screenW - scaledW));
        posY = Math.clamp(posY, 0, Math.max(0, screenH - scaledH));

        clampScroll(list);

        TextRenderer textRenderer = WTZClient.client().textRenderer;
        int w = getPanelWidth();
        int h = getPanelHeight();

        int umx = (int) unscaleMouseX(mouseX);
        int umy = (int) unscaleMouseY(mouseY);
        pendingTooltipText = null;
        pendingTooltipLines = null;

        float s = getScale();
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(posX, posY);
        context.getMatrices().scale(s, s);
        context.getMatrices().translate(-posX, -posY);

        drawPanelFrame(context, posX, posY, w, h);
        renderTitleBar(context, textRenderer, list, umx, umy);
        renderHeader(context, textRenderer);
        renderItems(context, textRenderer, list, umx, umy);
        renderFooter(context, textRenderer, umx, umy);
        renderAddItemSuggestions(context, textRenderer, umx, umy);
        renderScaleSlider(context, umx, umy);

        if (dropdownOpen) {
            renderDropdown(context, textRenderer, umx, umy);
        }
        renderPendingTooltip(context, textRenderer);

        context.getMatrices().popMatrix();

        if (draggingSlider) {
            int previewW = Math.round(PANEL_WIDTH * previewScale);
            int previewH = Math.round(getUnscaledPanelHeight() * previewScale);
            context.fill(posX, posY, posX + previewW, posY + 1, PRIMARY_ORANGE);
            context.fill(posX, posY + previewH - 1, posX + previewW, posY + previewH, PRIMARY_ORANGE);
            context.fill(posX, posY, posX + 1, posY + previewH, PRIMARY_ORANGE);
            context.fill(posX + previewW - 1, posY, posX + previewW, posY + previewH, PRIMARY_ORANGE);
        }
    }

    private void renderTitleBar(DrawContext context, TextRenderer textRenderer, ShoppingListData list, int mouseX, int mouseY) {

        int dropdownX = getDropdownX();
        int dropdownY = getDropdownY();
        int dropdownW = getDropdownWidth();
        int dropdownH = getDropdownHeight();
        int arrowW = Math.max(10, dropdownH - 2);
        drawBevelBox(context, dropdownX, dropdownY, dropdownW, dropdownH, FIELD_BG);
        context.fill(dropdownX + 2, dropdownY + 2, dropdownX + dropdownW - arrowW, dropdownY + dropdownH - 2, FIELD_FILL);
        drawBevelBox(context, dropdownX + dropdownW - arrowW - 3, dropdownY + 2, arrowW + 1, dropdownH - 4, BUTTON_BG);

        int dropdownTextY = dropdownY + (dropdownH - getScaledFontHeight(textRenderer)) / 2 + 1;
        if (renaming) {
            String text = renameBuffer + cursorSuffix();
            if (renameAllSelected) {
                drawSelectionHighlight(context, textRenderer, renameBuffer, dropdownX + 5, dropdownTextY, dropdownW - arrowW - 10);
            }
            drawTextScaled(context, textRenderer, trimToScaledWidth(textRenderer, text, dropdownW - arrowW - 10), dropdownX + 5, dropdownTextY, TITLE_TEXT);
        } else {
            String label = trimToScaledWidth(textRenderer, list.getName(), dropdownW - arrowW - 12);
            boolean hovered = isWithin(mouseX, mouseY, dropdownX, dropdownY, dropdownW, dropdownH);
            drawTextScaled(context, textRenderer, label, dropdownX + 4, dropdownTextY, hovered ? HOVER_TEXT : TITLE_TEXT);
            drawCenteredText(context, textRenderer, "v", dropdownX + dropdownW - arrowW - 2, dropdownY + 2, arrowW + 1, dropdownH - 4, hovered ? HOVER_TEXT : TITLE_TEXT, 0);
        }

        int closeIndex = TITLE_BUTTON_LABELS.length - 1;
        int closeW = getTitleButtonWidth(textRenderer, closeIndex);
        int closeX = posX + getPanelWidth() - 2 - PADDING - closeW;
        int buttonX = dropdownX + dropdownW + TITLE_BUTTON_GAP;
        int hoveredButton = -1;
        for (int i = 0; i < TITLE_BUTTON_LABELS.length; i++) {
            if (i == closeIndex) {
                continue;
            }
            int buttonW = getTitleButtonWidth(textRenderer, i);
            if (buttonX + buttonW > closeX - TITLE_BUTTON_GAP) {
                break;
            }
            boolean hovered = isWithin(mouseX, mouseY, buttonX, dropdownY, buttonW, dropdownH);
            if (hovered) hoveredButton = i;

            int fillColor = hovered ? BUTTON_HOVER_FILL : BUTTON_FILL;
            if (i == 1 && ShoppingListManager.getInstance().getAllLists().size() <= 1) {
                fillColor = 0xFF2A2A2A;
            }
            drawBevelBox(context, buttonX, dropdownY, buttonW, dropdownH, fillColor);

            int color = hovered ? HOVER_TEXT : TITLE_TEXT;
            if (i == 1 && ShoppingListManager.getInstance().getAllLists().size() <= 1) {
                color = MUTED_TEXT;
            }
            int symbolXOffset = getTitleSymbolXOffset(TITLE_BUTTON_LABELS[i]);
            drawCenteredText(context, textRenderer, TITLE_BUTTON_LABELS[i], buttonX, dropdownY, buttonW, dropdownH, color, symbolXOffset);
            buttonX += buttonW + TITLE_BUTTON_GAP;
        }

        boolean closeHovered = isWithin(mouseX, mouseY, closeX, dropdownY, closeW, dropdownH);
        if (closeHovered) hoveredButton = closeIndex;
        drawBevelBox(context, closeX, dropdownY, closeW, dropdownH, closeHovered ? BUTTON_HOVER_FILL : BUTTON_FILL);
        int closeSymbolXOffset = getTitleSymbolXOffset(TITLE_BUTTON_LABELS[closeIndex]);
        drawCenteredText(context, textRenderer, TITLE_BUTTON_LABELS[closeIndex], closeX, dropdownY, closeW, dropdownH, closeHovered ? HOVER_TEXT : TITLE_TEXT, closeSymbolXOffset);

        if (hoveredButton >= 0) {
            queueTooltip(TITLE_BUTTON_TOOLTIPS[hoveredButton], mouseX, mouseY);
        } else if (!renaming && isWithin(mouseX, mouseY, dropdownX, dropdownY, dropdownW, dropdownH)) {
            queueTooltip("Right-click to rename. Middle-click a list to pop it into a new window.", mouseX, mouseY);
        }
    }

    private void renderHeader(DrawContext context, TextRenderer textRenderer) {
        int headerY = getHeaderY();
        context.fill(posX + 2, headerY, posX + getPanelWidth() - 2, headerY + HEADER_HEIGHT, HEADER_BG);

        int textY = headerY + (HEADER_HEIGHT - getScaledFontHeight(textRenderer)) / 2 + 1;
        drawTextScaled(context, textRenderer, "Item Name", posX + ITEM_NAME_X, textY, LABEL_TEXT);
        drawCenteredText(context, textRenderer, "Qty", posX + QTY_X, headerY, QTY_WIDTH, HEADER_HEIGHT, LABEL_TEXT);
        drawCenteredText(context, textRenderer, "Have", posX + HAVE_X, headerY, HAVE_WIDTH, HEADER_HEIGHT, LABEL_TEXT);
    }

    private void renderItems(DrawContext context, TextRenderer textRenderer, ShoppingListData list, int mouseX, int mouseY) {
        List<ShoppingListData.ShoppingItem> items = list.getItems();
        ShoppingListManager mgr = ShoppingListManager.getInstance();
        int itemAreaY = getItemAreaY();
        int w = getPanelWidth();
        int visibleRows = getVisibleRows();

        for (int row = 0; row < visibleRows; row++) {
            int rowY = itemAreaY + row * ROW_HEIGHT;
            int itemIndex = scrollOffset + row;

            int bgColor = BODY_ROW;
            if (itemIndex == items.size()) {
                renderAddItemRow(context, textRenderer, mouseX, mouseY, rowY);
                continue;
            }
            if (itemIndex > items.size()) {
                context.fill(posX + 2, rowY, posX + w - 2, rowY + ROW_HEIGHT, bgColor);
                continue;
            }

            ShoppingListData.ShoppingItem item = items.get(itemIndex);
            int have = mgr.getHaveCount(item.getName());
            int need = item.getQuantity();
            boolean completed = have >= need;

            boolean rowHovered = isWithin(mouseX, mouseY, posX + 2, rowY, w - 4, ROW_HEIGHT);
            if (rowHovered) {
                bgColor = BODY_ROW_HOVER;
            }
            context.fill(posX + 2, rowY, posX + w - 2, rowY + ROW_HEIGHT, bgColor);
            context.fill(posX + 2, rowY + ROW_HEIGHT - 1, posX + w - 2, rowY + ROW_HEIGHT, PANEL_BG);

            int textY = rowY + (ROW_HEIGHT - getScaledFontHeight(textRenderer)) / 2 + 1;
            int itemColor = completed ? MUTED_TEXT : ITEM_TEXT;
            boolean nameHovered = isWithin(mouseX, mouseY, posX + ITEM_NAME_X, rowY, ITEM_NAME_WIDTH, ROW_HEIGHT);
            if (editingNameIndex != itemIndex && nameHovered) {
                itemColor = completed ? 0xFFFF915D : HOVER_TEXT;
            }

            String displayName = trimToScaledWidth(textRenderer, item.getName(), ITEM_NAME_WIDTH - 4);
            ItemStack iconStack = ShoppingListCache.getInstance().getIconStack(item.getName());
            if (!iconStack.isEmpty()) {
                drawScaledItem(context, iconStack, posX + ITEM_ICON_X, rowY);
            }
            if (editingNameIndex == itemIndex) {
                int inputX = posX + ITEM_NAME_X - 2;
                int inputY = rowY + 2;
                int inputW = ITEM_NAME_WIDTH;
                int inputH = ROW_HEIGHT - 4;
                drawBevelBox(context, inputX, inputY, inputW, inputH, 0xFF2A2A2A);
                context.fill(inputX + 2, inputY + 2, inputX + inputW - 2, inputY + inputH - 2, 0xFF383838);
                String editText = itemNameBuffer + cursorSuffix();
                if (itemNameAllSelected) {
                    drawSelectionHighlight(context, textRenderer, itemNameBuffer, inputX + 5, textY, inputW - 8);
                }
                drawTextScaled(context, textRenderer, trimToScaledWidth(textRenderer, editText, inputW - 8), inputX + 5, textY, ITEM_TEXT);
            } else {
                drawTextScaled(context, textRenderer, displayName, posX + ITEM_NAME_X, textY, itemColor);
            }
            if (completed && editingNameIndex != itemIndex) {
                int width = getScaledTextWidth(textRenderer, displayName);
                int strikeY = textY + getScaledFontHeight(textRenderer) / 2;
                context.fill(posX + ITEM_NAME_X, strikeY, posX + ITEM_NAME_X + width, strikeY + 1, itemColor);
            }

            int qtyBoxY = rowY + 2;
            int qtyBoxH = ROW_HEIGHT - 4;
            int qtyInputX = getQtyInputX();
            int minusX = getQtyMinusButtonX();
            int plusX = getQtyPlusButtonX();
            boolean minusHovered = isWithin(mouseX, mouseY, minusX, qtyBoxY, QTY_STEP_BUTTON_WIDTH, qtyBoxH);
            boolean plusHovered = isWithin(mouseX, mouseY, plusX, qtyBoxY, QTY_STEP_BUTTON_WIDTH, qtyBoxH);
            drawQtyStepButton(context, textRenderer, "-", minusX, qtyBoxY, qtyBoxH, QTY_MINUS_COLOR, minusHovered);
            drawBevelBox(context, qtyInputX, qtyBoxY, QTY_INPUT_WIDTH, qtyBoxH,
                    editingQtyIndex == itemIndex ? 0xFF2A2A2A : INPUT_BG);
            context.fill(qtyInputX + 2, qtyBoxY + 2, qtyInputX + QTY_INPUT_WIDTH - 2, qtyBoxY + qtyBoxH - 2,
                    editingQtyIndex == itemIndex ? 0xFF383838 : INPUT_FILL);
            drawQtyStepButton(context, textRenderer, "+", plusX, qtyBoxY, qtyBoxH, QTY_PLUS_COLOR, plusHovered);

            String qtyText = editingQtyIndex == itemIndex ? qtyBuffer + cursorSuffix() : String.valueOf(need);
            if (editingQtyIndex == itemIndex && qtyAllSelected) {
                drawQtySelectionHighlight(context, textRenderer, qtyBuffer, qtyInputX, qtyBoxY, qtyBoxH);
            }
            drawCenteredText(context, textRenderer, qtyText, qtyInputX, qtyBoxY, QTY_INPUT_WIDTH, qtyBoxH, completed ? MUTED_TEXT : ITEM_TEXT, 0);

            String haveText = have + "/" + need;
            int haveColor = have >= need ? SUCCESS_TEXT : (have > 0 ? PARTIAL_TEXT : MISSING_TEXT);
            drawCenteredText(context, textRenderer, haveText, posX + HAVE_X, rowY, HAVE_WIDTH, ROW_HEIGHT, haveColor, 0);
            if (minusHovered) {
                queueTooltip(getQtyStepTooltip(false, need), mouseX, mouseY);
            } else if (plusHovered) {
                queueTooltip(getQtyStepTooltip(true, need), mouseX, mouseY);
            } else if (nameHovered && editingNameIndex != itemIndex) {
                queueTooltip(getItemNameTooltip(), mouseX, mouseY);
            } else if (rowHovered) {
                queueTooltip("Right-click to remove this item.", mouseX, mouseY);
            }
        }
    }

    private void drawQtyStepButton(DrawContext context, TextRenderer textRenderer, String label, int x, int y, int h, int color, boolean hovered) {
        drawBevelBox(context, x, y, QTY_STEP_BUTTON_WIDTH, h, hovered ? BUTTON_HOVER_FILL : BUTTON_FILL);
        drawCenteredText(context, textRenderer, label, x, y, QTY_STEP_BUTTON_WIDTH, h, color, 1);
    }

    private void renderAddItemRow(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, int rowY) {
        int w = getPanelWidth();
        context.fill(posX + 2, rowY, posX + w - 2, rowY + ROW_HEIGHT, BODY_ROW);
        context.fill(posX + 2, rowY + ROW_HEIGHT - 1, posX + w - 2, rowY + ROW_HEIGHT, PANEL_BG);

        int inputX = posX + ITEM_NAME_X - 2;
        int inputY = rowY + 2;
        int inputW = ITEM_NAME_WIDTH + QTY_WIDTH + HAVE_WIDTH + 8;
        int inputH = ROW_HEIGHT - 4;
        boolean focused = addingItem;
        boolean inputHovered = isWithin(mouseX, mouseY, inputX, inputY, inputW, inputH);
        drawBevelBox(context, inputX, inputY, inputW, inputH, focused || inputHovered ? 0xFF2A2A2A : INPUT_BG);
        context.fill(inputX + 2, inputY + 2, inputX + inputW - 2, inputY + inputH - 2, focused ? 0xFF383838 : INPUT_FILL);

        String text = addItemBuffer.isEmpty() ? (focused ? cursorSuffix() : "Search for items") : addItemBuffer + (focused ? cursorSuffix() : "");
        int color = addItemBuffer.isEmpty() ? MUTED_TEXT : ITEM_TEXT;
        int textY = rowY + (ROW_HEIGHT - getScaledFontHeight(textRenderer)) / 2 + 1;
        if (addItemAllSelected) {
            drawSelectionHighlight(context, textRenderer, addItemBuffer, inputX + 5, textY, inputW - 8);
        }
        drawTextScaled(context, textRenderer, trimToScaledWidth(textRenderer, text, inputW - 8), inputX + 5, textY, focused ? ITEM_TEXT : (inputHovered ? HOVER_TEXT : color));
    }

    private void renderFooter(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        int footerY = getFooterY();

        int buttonX = posX + PADDING;
        int buttonY = footerY + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
        String hoveredTooltip = null;
        for (int i = 0; i < FOOTER_BUTTON_LABELS.length; i++) {
            String label = getFooterButtonLabel(i);
            int buttonW = getFooterButtonWidth(textRenderer, i);
            boolean hovered = isWithin(mouseX, mouseY, buttonX, buttonY, buttonW, BUTTON_HEIGHT);
            boolean active = i == 0 && clearConfirmUntil > System.currentTimeMillis();
            int fillColor = active ? BUTTON_ACTIVE_FILL : (hovered ? BUTTON_HOVER_FILL : BUTTON_FILL);
            drawBevelBox(context, buttonX, buttonY, buttonW, BUTTON_HEIGHT, fillColor);
            drawCenteredText(context, textRenderer, label, buttonX, buttonY, buttonW, BUTTON_HEIGHT, hovered ? HOVER_TEXT : TITLE_TEXT);
            if (hovered) {
                hoveredTooltip = switch (i) {
                    case 0 -> "Hold Shift to clear fulfilled quantities.";
                    case 1 -> "Copy this list to clipboard.";
                    case 2 -> "Import from clipboard. Hold Shift to merge into this list.";
                    default -> null;
                };
            }
            buttonX += buttonW + FOOTER_BUTTON_GAP;
        }

        if (hoveredTooltip != null) {
            queueTooltip(hoveredTooltip, mouseX, mouseY);
        }
    }

    private void queueTooltip(String text, int mouseX, int mouseY) {
        pendingTooltipText = text;
        pendingTooltipLines = null;
        pendingTooltipMouseX = mouseX;
        pendingTooltipMouseY = mouseY;
    }

    private void queueTooltip(List<ShoppingListTooltip.Line> lines, int mouseX, int mouseY) {
        pendingTooltipText = null;
        pendingTooltipLines = lines;
        pendingTooltipMouseX = mouseX;
        pendingTooltipMouseY = mouseY;
    }

    private void renderPendingTooltip(DrawContext context, TextRenderer textRenderer) {
        if (pendingTooltipLines != null) {
            ShoppingListTooltip.render(context, textRenderer, pendingTooltipLines, pendingTooltipMouseX, pendingTooltipMouseY, posX, posY, getScale());
        } else if (pendingTooltipText != null) {
            ShoppingListTooltip.render(context, textRenderer, pendingTooltipText, pendingTooltipMouseX, pendingTooltipMouseY, posX, posY, getScale());
        }
    }

    private void renderAddItemSuggestions(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!addingItem && editingNameIndex < 0) return;
        if (itemSuggestions.isEmpty()) return;

        int listX = posX + ITEM_NAME_X - 2;
        int listY = getSuggestionListY();
        int listW = ITEM_NAME_WIDTH + QTY_WIDTH + HAVE_WIDTH + 8;
        int visibleCount = getVisibleSuggestionCount();
        int listH = visibleCount * SUGGESTION_ROW_HEIGHT + 4;
        drawBevelBox(context, listX, listY, listW, listH, DROPDOWN_BG);

        for (int row = 0; row < visibleCount; row++) {
            int i = suggestionScrollOffset + row;
            ShoppingListCache.CachedItem suggestion = itemSuggestions.get(i);
            int rowY = listY + 2 + row * SUGGESTION_ROW_HEIGHT;
            boolean hovered = isWithin(mouseX, mouseY, listX + 2, rowY, listW - 4, SUGGESTION_ROW_HEIGHT);
            boolean selected = i == selectedSuggestionIndex;
            if (hovered || selected) {
                context.fill(listX + 2, rowY, listX + listW - 2, rowY + SUGGESTION_ROW_HEIGHT, DROPDOWN_HOVER);
            }

            ItemStack iconStack = suggestion.iconStack();
            if (!iconStack.isEmpty()) {
                drawScaledItem(context, iconStack, listX + 6, rowY);
            }

            int rowTextY = rowY + (SUGGESTION_ROW_HEIGHT - getScaledFontHeight(textRenderer)) / 2 + 1;
            int rowColor = hovered || selected ? HOVER_TEXT : ITEM_TEXT;
            drawTextScaled(context, textRenderer, trimToScaledWidth(textRenderer, suggestion.name(), listW - 28), listX + 24, rowTextY, rowColor);
        }

        if (itemSuggestions.size() > MAX_VISIBLE_SUGGESTIONS) {
            int barX = listX + listW - 5;
            int trackY = listY + 2;
            int trackH = visibleCount * SUGGESTION_ROW_HEIGHT;
            int maxScroll = getMaxSuggestionScroll();
            int thumbH = Math.max(8, trackH * visibleCount / itemSuggestions.size());
            int thumbY = trackY + Math.round((trackH - thumbH) * (suggestionScrollOffset / (float) maxScroll));
            context.fill(barX, trackY, barX + 2, trackY + trackH, 0x66000000);
            context.fill(barX, thumbY, barX + 2, thumbY + thumbH, PRIMARY_ORANGE);
        }
    }

    private void renderScaleSlider(DrawContext context, int mouseX, int mouseY) {
        int handleX = posX + getPanelWidth() - RESIZE_HANDLE_SIZE - 3;
        int handleY = posY + getPanelHeight() - RESIZE_HANDLE_SIZE - 3;
        boolean hovered = isWithin(mouseX, mouseY, handleX, handleY, RESIZE_HANDLE_SIZE + 2, RESIZE_HANDLE_SIZE + 2) || draggingSlider;
        int color = hovered ? PRIMARY_ORANGE : OUTLINE_LIGHT;
        context.fill(handleX + RESIZE_HANDLE_SIZE - 1, handleY, handleX + RESIZE_HANDLE_SIZE + 1, handleY + RESIZE_HANDLE_SIZE + 1, color);
        context.fill(handleX, handleY + RESIZE_HANDLE_SIZE - 1, handleX + RESIZE_HANDLE_SIZE + 1, handleY + RESIZE_HANDLE_SIZE + 1, color);
    }

    private boolean isOverScaleSlider(double mouseX, double mouseY) {
        double umx = unscaleMouseX(mouseX);
        double umy = unscaleMouseY(mouseY);
        int handleX = posX + getPanelWidth() - RESIZE_HANDLE_SIZE - 3;
        int handleY = posY + getPanelHeight() - RESIZE_HANDLE_SIZE - 3;
        return umx >= handleX && umx < handleX + RESIZE_HANDLE_SIZE + 2
                && umy >= handleY && umy < handleY + RESIZE_HANDLE_SIZE + 2;
    }

    private void renderDropdown(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        List<ShoppingListData> allLists = new ArrayList<>(ShoppingListManager.getInstance().getAllLists());
        int x = getDropdownX();
        int y = getDropdownListY();
        int w = getDropdownWidth();
        int h = allLists.size() * DROPDOWN_ROW_HEIGHT + 4;

        drawBevelBox(context, x, y, w, h, DROPDOWN_BG);

        for (int i = 0; i < allLists.size(); i++) {
            ShoppingListData entry = allLists.get(i);
            int rowY = y + 2 + i * DROPDOWN_ROW_HEIGHT;
            boolean hovered = isWithin(mouseX, mouseY, x + 2, rowY, w - 4, DROPDOWN_ROW_HEIGHT);
            if (hovered) {
                context.fill(x + 2, rowY, x + w - 2, rowY + DROPDOWN_ROW_HEIGHT, DROPDOWN_HOVER);
            }

            int textY = rowY + (DROPDOWN_ROW_HEIGHT - getScaledFontHeight(textRenderer)) / 2 + 1;
            boolean selected = entry.getId().equals(pinnedListId);
            int color = selected ? MUTED_TEXT : ITEM_TEXT;
            if (hovered && !selected) color = HOVER_TEXT;
            String display = trimToScaledWidth(textRenderer, entry.getName(), w - 12);
            drawTextScaled(context, textRenderer, display, x + 6, textY, color);
        }
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;

        ShoppingListData list = getList();
        if (list == null) return false;

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && isOverScaleSlider(mouseX, mouseY)) {
            draggingSlider = false;
            previewScale = 1.0f;
            setScale(1.0f);
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isOverScaleSlider(mouseX, mouseY)) {
            draggingSlider = true;
            previewScale = getScale();
            return true;
        }

        double umx = unscaleMouseX(mouseX);
        double umy = unscaleMouseY(mouseY);

        if ((addingItem || editingNameIndex >= 0) && handleAddItemClick(umx, umy, button, list)) {
            return true;
        }

        if (dropdownOpen) {
            boolean clickedDropdownList = handleDropdownClick(umx, umy, button);
            boolean clickedDropdownTrigger = isWithin(umx, umy, getDropdownX(), getDropdownY(), getDropdownWidth(), getDropdownHeight());
            dropdownOpen = false;
            if (clickedDropdownList || clickedDropdownTrigger) {
                return true;
            }
        }

        int w = getPanelWidth();
        int h = getPanelHeight();
        if (umx < posX || umx >= posX + w || umy < posY || umy >= posY + h) {
            if (editingQtyIndex >= 0) {
                confirmQtyEdit(list);
            }
            if (addingItem) {
                closeAddItemEditor();
            }
            if (editingNameIndex >= 0) {
                confirmNameEdit(list);
            }
            return false;
        }

        if (handleTitleClick(umx, umy, button, list)) {
            return true;
        }
        if (handleFooterClick(umx, umy, button, list)) {
            return true;
        }
        if (handleAddItemClick(umx, umy, button, list)) {
            return true;
        }
        if (handleItemClick(umx, umy, button, list)) {
            return true;
        }

        if (editingQtyIndex >= 0) {
            confirmQtyEdit(list);
        }

        if (button == 0) {
            dragging = true;
            dragOffsetX = (int) mouseX - posX;
            dragOffsetY = (int) mouseY - posY;
            return true;
        }

        return false;
    }

    private boolean handleDropdownClick(double mouseX, double mouseY, int button) {
        List<ShoppingListData> allLists = new ArrayList<>(ShoppingListManager.getInstance().getAllLists());
        int x = getDropdownX();
        int y = getDropdownListY();
        int w = getDropdownWidth();
        int h = allLists.size() * DROPDOWN_ROW_HEIGHT + 4;

        if (!isWithin(mouseX, mouseY, x, y, w, h)) {
            return false;
        }

        int row = (int) ((mouseY - y - 2) / DROPDOWN_ROW_HEIGHT);
        if (row < 0 || row >= allLists.size()) {
            dropdownOpen = false;
            return true;
        }

        ShoppingListData selected = allLists.get(row);
        if (button == 2) {
            ShoppingListRenderer.getInstance().openNewPanelForList(selected.getId());
        } else if (button == 1) {
            if (ShoppingListManager.getInstance().deleteList(selected.getId())) {
                if (selected.getId().equals(pinnedListId)) {
                    ShoppingListData active = ShoppingListManager.getInstance().getActiveList();
                    if (active != null) {
                        pinnedListId = active.getId();
                    }
                }
                clampScroll(getList());
            }
        } else {
            pinnedListId = selected.getId();
            scrollOffset = 0;
        }

        dropdownOpen = false;
        return true;
    }

    private boolean handleTitleClick(double mouseX, double mouseY, int button, ShoppingListData list) {
        int dropdownX = getDropdownX();
        int dropdownY = getDropdownY();
        int dropdownW = getDropdownWidth();
        int dropdownH = getDropdownHeight();

        if (isWithin(mouseX, mouseY, dropdownX, dropdownY, dropdownW, dropdownH)) {
            if (button == 0) {
                if (renaming) {
                    renaming = false;
                } else {
                    dropdownOpen = !dropdownOpen;
                }
                return true;
            }
            if (button == 1) {
                renaming = !renaming;
                renameBuffer = renaming ? list.getName() : "";
                renameAllSelected = false;
                dropdownOpen = false;
                return true;
            }
        }

        TextRenderer textRenderer = WTZClient.client().textRenderer;
        int closeIndex = TITLE_BUTTON_LABELS.length - 1;
        int closeW = getTitleButtonWidth(textRenderer, closeIndex);
        int closeX = posX + getPanelWidth() - 2 - PADDING - closeW;
        int buttonX = dropdownX + dropdownW + TITLE_BUTTON_GAP;
        for (int i = 0; i < TITLE_BUTTON_LABELS.length; i++) {
            if (i == closeIndex) {
                continue;
            }
            int buttonW = getTitleButtonWidth(textRenderer, i);
            if (buttonX + buttonW > closeX - TITLE_BUTTON_GAP) {
                break;
            }
            if (isWithin(mouseX, mouseY, buttonX, dropdownY, buttonW, dropdownH) && button == 0) {
                handleTitleButtonClick(i);
                return true;
            }
            buttonX += buttonW + TITLE_BUTTON_GAP;
        }

        if (button == 0 && isWithin(mouseX, mouseY, closeX, dropdownY, closeW, dropdownH)) {
            handleTitleButtonClick(closeIndex);
            return true;
        }

        return false;
    }

    private void handleTitleButtonClick(int index) {
        ShoppingListManager mgr = ShoppingListManager.getInstance();
        switch (index) {
            case 0 -> {
                clearTransientState();
                ShoppingListData newList = mgr.createList("New List");
                pinnedListId = newList.getId();
                scrollOffset = 0;
            }
            case 1 -> {
                if (mgr.deleteList(pinnedListId)) {
                    ShoppingListData active = mgr.getActiveList();
                    if (active != null) {
                        pinnedListId = active.getId();
                    }
                    scrollOffset = 0;
                }
            }
            case 2 -> close();
            default -> {
            }
        }
    }

    private boolean handleFooterClick(double mouseX, double mouseY, int button, ShoppingListData list) {
        if (button != 0) return false;

        int footerY = getFooterY();
        int buttonX = posX + PADDING;
        int buttonY = footerY + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
        TextRenderer textRenderer = WTZClient.client().textRenderer;
        for (int i = 0; i < FOOTER_BUTTON_LABELS.length; i++) {
            int buttonW = getFooterButtonWidth(textRenderer, i);
            if (isWithin(mouseX, mouseY, buttonX, buttonY, buttonW, BUTTON_HEIGHT)) {
                handleFooterButtonClick(i, list);
                return true;
            }
            buttonX += buttonW + FOOTER_BUTTON_GAP;
        }

        return false;
    }

    private void handleFooterButtonClick(int index, ShoppingListData list) {
        ShoppingListManager mgr = ShoppingListManager.getInstance();
        switch (index) {
            case 0 -> {
                if (isShiftDown()) {
                    mgr.removeCompletedItems(pinnedListId);
                    clampScroll(list);
                } else {
                    long now = System.currentTimeMillis();
                    if (clearConfirmUntil > now) {
                        mgr.clearList(pinnedListId);
                        clearConfirmUntil = 0L;
                        scrollOffset = 0;
                    } else {
                        clearConfirmUntil = now + 2500L;
                    }
                }
            }
            case 1 -> mgr.exportListToClipboard(pinnedListId);
            case 2 -> mgr.importListFromClipboard(pinnedListId, isShiftDown());
            default -> {
            }
        }
    }

    private boolean handleAddItemClick(double mouseX, double mouseY, int button, ShoppingListData list) {
        int inputX = posX + ITEM_NAME_X - 2;
        int inputY = getAddRowY(list) + 2;
        int inputW = ITEM_NAME_WIDTH + QTY_WIDTH + HAVE_WIDTH + 8;
        int inputH = ROW_HEIGHT - 4;
        if (isAddRowVisible(list) && isWithin(mouseX, mouseY, inputX, inputY, inputW, inputH)) {
            if (button == 0) {
                startAddItemEditor(false);
            } else if (button == 1 && addingItem) {
                closeAddItemEditor();
            }
            return true;
        }

        if (button != 0) return false;

        if ((addingItem || editingNameIndex >= 0) && !itemSuggestions.isEmpty()) {
            int listY = getSuggestionListY();
            int listH = getVisibleSuggestionCount() * SUGGESTION_ROW_HEIGHT + 4;
            if (isWithin(mouseX, mouseY, inputX, listY, inputW, listH)) {
                int index = suggestionScrollOffset + (int) ((mouseY - listY - 2) / SUGGESTION_ROW_HEIGHT);
                if (index >= 0 && index < itemSuggestions.size()) {
                    if (editingNameIndex >= 0) {
                        itemNameBuffer = itemSuggestions.get(index).name();
                        selectedSuggestionIndex = index;
                        confirmNameEdit(list);
                    } else {
                        addSuggestedItem(list, itemSuggestions.get(index).name());
                    }
                }
                return true;
            }
        }

        return false;
    }

    private boolean isAddRowVisible(ShoppingListData list) {
        if (list == null) return false;
        int row = list.getItems().size() - scrollOffset;
        return row >= 0 && row < getVisibleRows();
    }

    private boolean isOverSuggestions(double mouseX, double mouseY) {
        if ((!addingItem && editingNameIndex < 0) || itemSuggestions.isEmpty()) return false;
        int listX = posX + ITEM_NAME_X - 2;
        int listY = getSuggestionListY();
        int listW = ITEM_NAME_WIDTH + QTY_WIDTH + HAVE_WIDTH + 8;
        int listH = getVisibleSuggestionCount() * SUGGESTION_ROW_HEIGHT + 4;
        return isWithin(mouseX, mouseY, listX, listY, listW, listH);
    }

    private int getAddRowY(ShoppingListData list) {
        if (list == null) return getItemAreaY();
        int row = Math.clamp(list.getItems().size() - scrollOffset, 0, Math.max(0, getVisibleRows() - 1));
        return getItemAreaY() + row * ROW_HEIGHT;
    }

    private boolean handleItemClick(double mouseX, double mouseY, int button, ShoppingListData list) {
        int itemAreaY = getItemAreaY();
        int itemAreaBottom = itemAreaY + getVisibleRows() * ROW_HEIGHT;
        if (mouseY < itemAreaY || mouseY >= itemAreaBottom) {
            return false;
        }

        int row = (int) ((mouseY - itemAreaY) / ROW_HEIGHT);
        int index = scrollOffset + row;
        if (index == list.getItems().size()) {
            return handleAddItemClick(mouseX, mouseY, button, list);
        }
        if (index < 0 || index >= list.getItems().size()) {
            return false;
        }

        ShoppingListData.ShoppingItem item = list.getItems().get(index);
        int rowY = itemAreaY + row * ROW_HEIGHT;
        int qtyBoxY = rowY + 2;
        int qtyBoxH = ROW_HEIGHT - 4;

        if (button == 1 && isQtyStepButton(mouseX, mouseY, qtyBoxY, qtyBoxH)) {
            return true;
        }

        if (button == 1) {
            ShoppingListManager.getInstance().removeItem(pinnedListId, item.getName());
            closeNameEditor();
            closeAddItemEditor();
            clampScroll(list);
            return true;
        }

        if (button != 0) {
            return false;
        }

        if (isWithin(mouseX, mouseY, getQtyMinusButtonX(), qtyBoxY, QTY_STEP_BUTTON_WIDTH, qtyBoxH)) {
            changeQuantity(item, -getQuantityClickDelta());
            return true;
        }

        if (isWithin(mouseX, mouseY, getQtyPlusButtonX(), qtyBoxY, QTY_STEP_BUTTON_WIDTH, qtyBoxH)) {
            changeQuantity(item, getQuantityClickDelta());
            return true;
        }

        if (isWithin(mouseX, mouseY, getQtyInputX(), qtyBoxY, QTY_INPUT_WIDTH, qtyBoxH)) {
            if (editingNameIndex >= 0) {
                confirmNameEdit(list);
            }
            if (editingQtyIndex >= 0) {
                confirmQtyEdit(list);
            }
            editingQtyIndex = index;
            qtyBuffer = String.valueOf(item.getQuantity());
            qtyAllSelected = false;
            return true;
        }

        if (isWithin(mouseX, mouseY, posX + ITEM_NAME_X, rowY, ITEM_NAME_WIDTH, ROW_HEIGHT)) {
            if (editingNameIndex == index) {
                return true;
            }
            if (isShiftDown()) {
                startNameEdit(list, index);
            } else {
                if (editingNameIndex >= 0) {
                    confirmNameEdit(list);
                }
                if (editingQtyIndex >= 0) {
                    confirmQtyEdit(list);
                }
                QualityOfLife.searchTradeMarket(item.getName());
            }
            return true;
        }

        if (editingQtyIndex >= 0) {
            confirmQtyEdit(list);
        }

        return true;
    }

    private boolean isQtyStepButton(double mouseX, double mouseY, int qtyBoxY, int qtyBoxH) {
        return isWithin(mouseX, mouseY, getQtyMinusButtonX(), qtyBoxY, QTY_STEP_BUTTON_WIDTH, qtyBoxH)
                || isWithin(mouseX, mouseY, getQtyPlusButtonX(), qtyBoxY, QTY_STEP_BUTTON_WIDTH, qtyBoxH);
    }

    private void startAddItemEditor(boolean resetBuffer) {
        if (editingQtyIndex >= 0) {
            confirmQtyEdit(getList());
        }
        if (editingNameIndex >= 0) {
            confirmNameEdit(getList());
        }
        renaming = false;
        dropdownOpen = false;
        addingItem = true;
        if (resetBuffer) {
            addItemBuffer = "";
            addItemAllSelected = false;
            selectedSuggestionIndex = 0;
        }
        updateItemSuggestions();
    }

    private void closeAddItemEditor() {
        addingItem = false;
        addItemBuffer = "";
        addItemAllSelected = false;
        selectedSuggestionIndex = 0;
        suggestionScrollOffset = 0;
        itemSuggestions = List.of();
    }

    private void updateItemSuggestions() {
        itemSuggestions = ShoppingListCache.getInstance().search(getActiveItemSearchBuffer());
        if (selectedSuggestionIndex >= itemSuggestions.size()) {
            selectedSuggestionIndex = Math.max(0, itemSuggestions.size() - 1);
        }
        clampSuggestionScroll();
        ensureSelectedSuggestionVisible();
    }

    private int getVisibleSuggestionCount() {
        return Math.min(itemSuggestions.size(), MAX_VISIBLE_SUGGESTIONS);
    }

    private int getMaxSuggestionScroll() {
        return Math.max(0, itemSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
    }

    private void clampSuggestionScroll() {
        suggestionScrollOffset = Math.clamp(suggestionScrollOffset, 0, getMaxSuggestionScroll());
    }

    private void setSelectedSuggestionIndex(int index) {
        if (itemSuggestions.isEmpty()) {
            selectedSuggestionIndex = 0;
            suggestionScrollOffset = 0;
            return;
        }
        selectedSuggestionIndex = Math.clamp(index, 0, itemSuggestions.size() - 1);
        ensureSelectedSuggestionVisible();
    }

    private void ensureSelectedSuggestionVisible() {
        if (itemSuggestions.isEmpty()) {
            suggestionScrollOffset = 0;
            return;
        }
        if (selectedSuggestionIndex < suggestionScrollOffset) {
            suggestionScrollOffset = selectedSuggestionIndex;
        } else if (selectedSuggestionIndex >= suggestionScrollOffset + MAX_VISIBLE_SUGGESTIONS) {
            suggestionScrollOffset = selectedSuggestionIndex - MAX_VISIBLE_SUGGESTIONS + 1;
        }
        clampSuggestionScroll();
    }

    private String getActiveItemSearchBuffer() {
        return editingNameIndex >= 0 ? itemNameBuffer : addItemBuffer;
    }

    private void addSuggestedItem(ShoppingListData list, String name) {
        String cleanName = ShoppingListData.cleanName(name);
        if (cleanName.isBlank()) return;
        ShoppingListManager.getInstance().addItem(pinnedListId, cleanName);
        clampScroll(list);
        startAddItemEditor(true);
    }

    private void confirmAddItem(ShoppingListData list) {
        if (!itemSuggestions.isEmpty() && selectedSuggestionIndex >= 0 && selectedSuggestionIndex < itemSuggestions.size()) {
            addSuggestedItem(list, itemSuggestions.get(selectedSuggestionIndex).name());
            return;
        }
        addSuggestedItem(list, addItemBuffer);
    }

    private void startNameEdit(ShoppingListData list, int index) {
        if (index < 0 || index >= list.getItems().size()) return;
        if (editingNameIndex >= 0 && editingNameIndex != index) {
            confirmNameEdit(list);
            if (index >= list.getItems().size()) return;
        }
        if (editingQtyIndex >= 0) {
            confirmQtyEdit(list);
        }
        closeAddItemEditor();
        renaming = false;
        dropdownOpen = false;
        editingNameIndex = index;
        itemNameBuffer = list.getItems().get(index).getName();
        itemNameAllSelected = false;
        selectedSuggestionIndex = 0;
        updateItemSuggestions();
    }

    private void closeNameEditor() {
        editingNameIndex = -1;
        itemNameBuffer = "";
        itemNameAllSelected = false;
        selectedSuggestionIndex = 0;
        suggestionScrollOffset = 0;
        itemSuggestions = List.of();
    }

    private void confirmNameEdit(ShoppingListData list) {
        if (editingNameIndex >= 0 && list != null && editingNameIndex < list.getItems().size()) {
            String oldName = list.getItems().get(editingNameIndex).getName();
            String newName = itemNameBuffer;
            if (!itemSuggestions.isEmpty() && selectedSuggestionIndex >= 0 && selectedSuggestionIndex < itemSuggestions.size()) {
                newName = itemSuggestions.get(selectedSuggestionIndex).name();
            }
            ShoppingListManager.getInstance().renameItem(pinnedListId, oldName, newName);
            clampScroll(list);
        }
        closeNameEditor();
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible) return false;

        ShoppingListData list = getList();
        if (list == null) return false;

        double umx = unscaleMouseX(mouseX);
        double umy = unscaleMouseY(mouseY);

        if (isOverSuggestions(umx, umy)) {
            suggestionScrollOffset += amount > 0 ? -1 : 1;
            clampSuggestionScroll();
            return true;
        }

        int itemAreaY = getItemAreaY();
        int itemAreaBottom = itemAreaY + getVisibleRows() * ROW_HEIGHT;
        if (umx < posX || umx >= posX + getPanelWidth() || umy < itemAreaY || umy >= itemAreaBottom) {
            return false;
        }

        if (editingQtyIndex >= 0) {
            return true;
        }

        int row = (int) ((umy - itemAreaY) / ROW_HEIGHT);
        int index = scrollOffset + row;
        if (index >= 0 && index < list.getItems().size()) {
            ShoppingListData.ShoppingItem item = list.getItems().get(index);
            int rowY = itemAreaY + row * ROW_HEIGHT;
            int qtyBoxY = rowY + 2;
            int qtyBoxH = ROW_HEIGHT - 4;
            if (isWithin(umx, umy, getQtyMinusButtonX(), qtyBoxY, QTY_STEP_BUTTON_WIDTH, qtyBoxH)) {
                changeQuantity(item, -getQuantityClickDelta());
                return true;
            }
            if (isWithin(umx, umy, getQtyPlusButtonX(), qtyBoxY, QTY_STEP_BUTTON_WIDTH, qtyBoxH)) {
                changeQuantity(item, getQuantityClickDelta());
                return true;
            }
        }

        scrollOffset -= amount > 0 ? 1 : -1;
        clampScroll(list);
        return true;
    }

    public boolean onKeyPressed(int keyCode) {
        if (!visible) return false;

        if (editingNameIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmNameEdit(getList());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeNameEditor();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                if (!itemSuggestions.isEmpty()) {
                    setSelectedSuggestionIndex(selectedSuggestionIndex - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                if (!itemSuggestions.isEmpty()) {
                    setSelectedSuggestionIndex(selectedSuggestionIndex + 1);
                }
                return true;
            }
            if (isSelectAllShortcut(keyCode)) {
                itemNameAllSelected = !itemNameBuffer.isEmpty();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (itemNameAllSelected) {
                    itemNameBuffer = "";
                    itemNameAllSelected = false;
                } else if (!itemNameBuffer.isEmpty()) {
                    itemNameBuffer = itemNameBuffer.substring(0, itemNameBuffer.length() - 1);
                }
                selectedSuggestionIndex = 0;
                updateItemSuggestions();
                return true;
            }
            return true;
        }

        if (addingItem) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmAddItem(getList());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeAddItemEditor();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                if (!itemSuggestions.isEmpty()) {
                    setSelectedSuggestionIndex(selectedSuggestionIndex - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                if (!itemSuggestions.isEmpty()) {
                    setSelectedSuggestionIndex(selectedSuggestionIndex + 1);
                }
                return true;
            }
            if (isSelectAllShortcut(keyCode)) {
                addItemAllSelected = !addItemBuffer.isEmpty();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (addItemAllSelected) {
                    addItemBuffer = "";
                    addItemAllSelected = false;
                } else if (!addItemBuffer.isEmpty()) {
                    addItemBuffer = addItemBuffer.substring(0, addItemBuffer.length() - 1);
                }
                selectedSuggestionIndex = 0;
                updateItemSuggestions();
                return true;
            }
            return true;
        }

        if (renaming) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!renameBuffer.isBlank()) {
                    ShoppingListManager.getInstance().renameList(pinnedListId, renameBuffer.trim());
                }
                renaming = false;
                renameAllSelected = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                renaming = false;
                renameBuffer = "";
                renameAllSelected = false;
                return true;
            }
            if (isSelectAllShortcut(keyCode)) {
                renameAllSelected = !renameBuffer.isEmpty();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (renameAllSelected) {
                    renameBuffer = "";
                    renameAllSelected = false;
                } else if (!renameBuffer.isEmpty()) {
                    renameBuffer = renameBuffer.substring(0, renameBuffer.length() - 1);
                }
                return true;
            }
            return true;
        }

        if (editingQtyIndex >= 0) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                confirmQtyEdit(getList());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingQtyIndex = -1;
                qtyBuffer = "";
                qtyAllSelected = false;
                return true;
            }
            if (isSelectAllShortcut(keyCode)) {
                qtyAllSelected = !qtyBuffer.isEmpty();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (qtyAllSelected) {
                    qtyBuffer = "";
                    qtyAllSelected = false;
                } else if (!qtyBuffer.isEmpty()) {
                    qtyBuffer = qtyBuffer.substring(0, qtyBuffer.length() - 1);
                }
                return true;
            }
            return true;
        }

        return false;
    }

    public boolean onCharTyped(int codepoint) {
        if (!visible) return false;

        if (editingNameIndex >= 0) {
            if (Character.isValidCodePoint(codepoint) && !Character.isISOControl(codepoint)) {
                if (itemNameAllSelected) {
                    itemNameBuffer = "";
                    itemNameAllSelected = false;
                }
                itemNameBuffer += Character.toString(codepoint);
                selectedSuggestionIndex = 0;
                updateItemSuggestions();
            }
            return true;
        }

        if (addingItem) {
            if (Character.isValidCodePoint(codepoint) && !Character.isISOControl(codepoint)) {
                if (addItemAllSelected) {
                    addItemBuffer = "";
                    addItemAllSelected = false;
                }
                addItemBuffer += Character.toString(codepoint);
                selectedSuggestionIndex = 0;
                updateItemSuggestions();
            }
            return true;
        }

        if (renaming) {
            if (Character.isValidCodePoint(codepoint) && !Character.isISOControl(codepoint)) {
                if (renameAllSelected) {
                    renameBuffer = "";
                    renameAllSelected = false;
                }
                renameBuffer += Character.toString(codepoint);
            }
            return true;
        }

        if (editingQtyIndex >= 0) {
            if (codepoint >= '0' && codepoint <= '9') {
                if (qtyAllSelected) {
                    qtyBuffer = "";
                    qtyAllSelected = false;
                }
                qtyBuffer += (char) codepoint;
            }
            return true;
        }

        return false;
    }

    private void confirmQtyEdit(ShoppingListData list) {
        if (editingQtyIndex >= 0 && list != null && editingQtyIndex < list.getItems().size()) {
            int qty = 1;
            try {
                qty = Integer.parseInt(qtyBuffer);
            } catch (NumberFormatException ignored) {
            }
            qty = Math.max(1, qty);
            ShoppingListData.ShoppingItem item = list.getItems().get(editingQtyIndex);
            ShoppingListManager.getInstance().setQuantity(pinnedListId, item.getName(), qty);
        }
        editingQtyIndex = -1;
        qtyBuffer = "";
        qtyAllSelected = false;
    }

    private int getQtyMinusButtonX() {
        return posX + QTY_X;
    }

    private int getQtyInputX() {
        return posX + QTY_X + QTY_STEP_BUTTON_WIDTH;
    }

    private int getQtyPlusButtonX() {
        return posX + QTY_X + QTY_STEP_BUTTON_WIDTH + QTY_INPUT_WIDTH;
    }

    private void changeQuantity(ShoppingListData.ShoppingItem item, int delta) {
        if (editingQtyIndex >= 0) {
            confirmQtyEdit(getList());
        }
        if (editingNameIndex >= 0) {
            confirmNameEdit(getList());
        }

        int newQty;
        if (isAltDown()) {
            newQty = delta > 0 ? roundUpToNextStack(item.getQuantity()) : roundDownToPreviousStack(item.getQuantity());
        } else {
            newQty = Math.max(1, item.getQuantity() + delta);
        }
        ShoppingListManager.getInstance().setQuantity(pinnedListId, item.getName(), newQty);
    }

    private static int getQuantityClickDelta() {
        if (isControlDown()) return 10;
        if (isShiftDown()) return 5;
        return 1;
    }

    private static int roundUpToNextStack(int quantity) {
        long current = Math.max(1, quantity);
        long rounded = (current / 64L + 1L) * 64L;
        return rounded > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rounded;
    }

    private static int roundDownToPreviousStack(int quantity) {
        int current = Math.max(1, quantity);
        if (current <= 64) return 1;
        return (current - 1) / 64 * 64;
    }

    private void clampScroll(ShoppingListData list) {
        if (list == null) {
            scrollOffset = 0;
            return;
        }
        int maxScroll = Math.max(0, list.getItems().size() + 1 - MAX_VISIBLE_ROWS);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
    }

    private void drawScaledItem(DrawContext context, ItemStack stack, int x, int y) {
        int scaledSize = Math.round(16 * ITEM_ICON_SCALE);
        int centeredY = y + (ROW_HEIGHT - scaledSize) / 2;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x + 2, centeredY);
        context.getMatrices().scale(ITEM_ICON_SCALE, ITEM_ICON_SCALE);
        context.drawItem(stack, 0, 0);
        context.getMatrices().popMatrix();
    }

    private void drawSelectionHighlight(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int maxWidth) {
        if (text.isEmpty()) return;
        int width = Math.min(maxWidth, getScaledTextWidth(textRenderer, text));
        int height = getScaledFontHeight(textRenderer);
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xAA2F5FFF);
    }

    private void drawQtySelectionHighlight(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int height) {
        if (text.isEmpty()) return;
        int width = QTY_INPUT_WIDTH;
        int textWidth = Math.min(width - 4, getScaledTextWidth(textRenderer, text));
        int textHeight = getScaledFontHeight(textRenderer);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - textHeight) / 2 + 1;
        context.fill(textX - 1, textY - 1, textX + textWidth + 1, textY + textHeight + 1, 0xAA2F5FFF);
    }

    private String cursorSuffix() {
        return System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "";
    }

    private static List<ShoppingListTooltip.Line> getQtyStepTooltip(boolean increment, int quantity) {
        String sign = increment ? "+" : "-";
        int amountColor = increment ? QTY_PLUS_COLOR : QTY_MINUS_COLOR;
        int stackQuantity = increment ? roundUpToNextStack(quantity) : roundDownToPreviousStack(quantity);
        return List.of(
                ShoppingListTooltip.line(ShoppingListTooltip.key("Click"), ShoppingListTooltip.text(" : "), ShoppingListTooltip.text(sign + "1", amountColor)),
                ShoppingListTooltip.line(ShoppingListTooltip.key("Shift+Click"), ShoppingListTooltip.text(" : "), ShoppingListTooltip.text(sign + "5", amountColor)),
                ShoppingListTooltip.line(ShoppingListTooltip.key("Ctrl+Click"), ShoppingListTooltip.text(" : "), ShoppingListTooltip.text(sign + "10", amountColor)),
                ShoppingListTooltip.line(ShoppingListTooltip.key("Alt+Click"), ShoppingListTooltip.text(" : "), ShoppingListTooltip.text(String.valueOf(stackQuantity), amountColor))
        );
    }

    private static List<ShoppingListTooltip.Line> getItemNameTooltip() {
        return List.of(
                ShoppingListTooltip.line(ShoppingListTooltip.key("Click"), ShoppingListTooltip.text(" : "), ShoppingListTooltip.text("search Trade Market")),
                ShoppingListTooltip.line(ShoppingListTooltip.key("Shift+Click"), ShoppingListTooltip.text(" : "), ShoppingListTooltip.text("rename item")),
                ShoppingListTooltip.line(ShoppingListTooltip.key("Right-click"), ShoppingListTooltip.text(" : "), ShoppingListTooltip.text("remove item"))
        );
    }

    private int getTitleSymbolXOffset(String label) {
        if ("+".equals(label) || "X".equals(label)) {
            return 1;
        }
        return 0;
    }

    private static boolean isSelectAllShortcut(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_A) return false;
        return isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isShiftDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean isControlDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static boolean isAltDown() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private static boolean isKeyDown(int keyCode) {
        long handle = WTZClient.client().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, keyCode) == GLFW.GLFW_PRESS;
    }
}
