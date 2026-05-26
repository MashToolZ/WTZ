package xyz.mashtoolz.wtz.features.shoppinglist;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
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
    private boolean visible;
    private long clearConfirmUntil;
    private float previewScale = 1.0f;
    private boolean draggingSlider;

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
        return Math.min(itemCount, MAX_VISIBLE_ROWS);
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

    private int getTitleButtonWidth(int index) {
        if (index == 1 || index == 3) {
            return getDropdownHeight();
        }
        return 30;
    }

    private int getFooterButtonWidth() {
        return 50;
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
            int unscaledSliderX = getSliderX();
            float s = getScale();
            int screenSliderX = posX + Math.round((unscaledSliderX - posX) * s);
            int screenSliderW = Math.round(getSliderWidth() * s);
            float ratio = (float) (mouseX - screenSliderX - 2) / (screenSliderW - 4 - Math.round(SLIDER_HANDLE_WIDTH * s));
            previewScale = MIN_SCALE + Math.clamp(ratio, 0.0f, 1.0f) * (MAX_SCALE - MIN_SCALE);
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
        renderScaleSlider(context, umx, umy);

        if (dropdownOpen) {
            renderDropdown(context, textRenderer, umx, umy);
        }

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
            String text = renameBuffer + "_";
            drawTextScaled(context, textRenderer, trimToScaledWidth(textRenderer, text, dropdownW - arrowW - 10), dropdownX + 5, dropdownTextY, TITLE_TEXT);
        } else {
            String label = trimToScaledWidth(textRenderer, list.getName(), dropdownW - arrowW - 12);
            boolean hovered = isWithin(mouseX, mouseY, dropdownX, dropdownY, dropdownW, dropdownH);
            drawTextScaled(context, textRenderer, label, dropdownX + 4, dropdownTextY, hovered ? HOVER_TEXT : TITLE_TEXT);
            drawCenteredText(context, textRenderer, "v", dropdownX + dropdownW - arrowW - 2, dropdownY + 2, arrowW + 1, dropdownH - 4, hovered ? HOVER_TEXT : TITLE_TEXT, 0);
        }

        int closeIndex = 3;
        int closeW = getTitleButtonWidth(closeIndex);
        int closeX = posX + getPanelWidth() - 2 - PADDING - closeW;
        int buttonX = dropdownX + dropdownW + TITLE_BUTTON_GAP;
        int hoveredButton = -1;
        for (int i = 0; i < TITLE_BUTTON_LABELS.length; i++) {
            if (i == closeIndex) {
                continue;
            }
            int buttonW = getTitleButtonWidth(i);
            if (buttonX + buttonW > closeX - TITLE_BUTTON_GAP) {
                break;
            }
            boolean hovered = isWithin(mouseX, mouseY, buttonX, dropdownY, buttonW, dropdownH);
            if (hovered) hoveredButton = i;

            int fillColor = hovered ? BUTTON_HOVER_FILL : BUTTON_FILL;
            if (i == 2 && ShoppingListManager.getInstance().getAllLists().size() <= 1) {
                fillColor = 0xFF2A2A2A;
            }
            drawBevelBox(context, buttonX, dropdownY, buttonW, dropdownH, fillColor);

            int color = hovered ? HOVER_TEXT : TITLE_TEXT;
            if (i == 2 && ShoppingListManager.getInstance().getAllLists().size() <= 1) {
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
            drawTooltip(context, textRenderer, TITLE_BUTTON_TOOLTIPS[hoveredButton], mouseX, mouseY);
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
            if (itemIndex >= items.size()) {
                context.fill(posX + 2, rowY, posX + w - 2, rowY + ROW_HEIGHT, bgColor);
                continue;
            }

            ShoppingListData.ShoppingItem item = items.get(itemIndex);
            int have = mgr.getHaveCount(item.getName());
            int need = item.getQuantity();
            boolean completed = have >= need;

            if (isWithin(mouseX, mouseY, posX + 2, rowY, w - 4, ROW_HEIGHT)) {
                bgColor = BODY_ROW_HOVER;
            }
            context.fill(posX + 2, rowY, posX + w - 2, rowY + ROW_HEIGHT, bgColor);
            context.fill(posX + 2, rowY + ROW_HEIGHT - 1, posX + w - 2, rowY + ROW_HEIGHT, PANEL_BG);

            int textY = rowY + (ROW_HEIGHT - getScaledFontHeight(textRenderer)) / 2 + 1;
            int itemColor = completed ? MUTED_TEXT : ITEM_TEXT;
            if (isWithin(mouseX, mouseY, posX + ITEM_NAME_X, rowY, ITEM_NAME_WIDTH, ROW_HEIGHT)) {
                itemColor = completed ? 0xFFFF915D : HOVER_TEXT;
            }

            String displayName = trimToScaledWidth(textRenderer, item.getName(), ITEM_NAME_WIDTH - 4);
            drawTextScaled(context, textRenderer, displayName, posX + ITEM_NAME_X, textY, itemColor);
            if (completed) {
                int width = getScaledTextWidth(textRenderer, displayName);
                int strikeY = textY + getScaledFontHeight(textRenderer) / 2;
                context.fill(posX + ITEM_NAME_X, strikeY, posX + ITEM_NAME_X + width, strikeY + 1, itemColor);
            }

            int qtyBoxX = posX + QTY_X;
            int qtyBoxY = rowY + 2;
            int qtyBoxH = ROW_HEIGHT - 4;
            drawBevelBox(context, qtyBoxX, qtyBoxY, QTY_WIDTH, qtyBoxH,
                    editingQtyIndex == itemIndex ? 0xFF2A2A2A : INPUT_BG);
            context.fill(qtyBoxX + 2, qtyBoxY + 2, qtyBoxX + QTY_WIDTH - 2, qtyBoxY + qtyBoxH - 2,
                    editingQtyIndex == itemIndex ? 0xFF383838 : INPUT_FILL);

            String qtyText = editingQtyIndex == itemIndex ? qtyBuffer + "_" : String.valueOf(need);
            drawCenteredText(context, textRenderer, qtyText, qtyBoxX, qtyBoxY, QTY_WIDTH, qtyBoxH, completed ? MUTED_TEXT : ITEM_TEXT, 0);

            String haveText = have + "/" + need;
            int haveColor = have >= need ? SUCCESS_TEXT : (have > 0 ? PARTIAL_TEXT : MISSING_TEXT);
            drawCenteredText(context, textRenderer, haveText, posX + HAVE_X, rowY, HAVE_WIDTH, ROW_HEIGHT, haveColor, 0);
        }
    }

    private void renderFooter(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        int footerY = getFooterY();

        int buttonX = posX + PADDING;
        int buttonY = footerY + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
        for (int i = 0; i < FOOTER_BUTTON_LABELS.length; i++) {
            String label = getFooterButtonLabel(i);
            int buttonW = getFooterButtonWidth();
            boolean hovered = isWithin(mouseX, mouseY, buttonX, buttonY, buttonW, BUTTON_HEIGHT);
            boolean active = i == 0 && clearConfirmUntil > System.currentTimeMillis();
            int fillColor = active ? BUTTON_ACTIVE_FILL : (hovered ? BUTTON_HOVER_FILL : BUTTON_FILL);
            drawBevelBox(context, buttonX, buttonY, buttonW, BUTTON_HEIGHT, fillColor);
            drawCenteredText(context, textRenderer, label, buttonX, buttonY, buttonW, BUTTON_HEIGHT, hovered ? HOVER_TEXT : TITLE_TEXT);
            buttonX += buttonW + FOOTER_BUTTON_GAP;
        }

    }

    private int getSliderX() {
        int x = posX + PADDING;
        for (int i = 0; i < FOOTER_BUTTON_LABELS.length; i++) {
            x += getFooterButtonWidth() + FOOTER_BUTTON_GAP;
        }
        return x;
    }

    private int getSliderY() {
        return getFooterY() + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
    }

    private int getSliderWidth() {
        return posX + getPanelWidth() - PADDING - 2 - getSliderX();
    }

    private void renderScaleSlider(DrawContext context, int mouseX, int mouseY) {
        int sliderX = getSliderX();
        int sliderY = getSliderY();
        int sliderW = getSliderWidth();

        drawBevelBox(context, sliderX, sliderY, sliderW, BUTTON_HEIGHT, INPUT_BG);
        context.fill(sliderX + 2, sliderY + 2, sliderX + sliderW - 2, sliderY + BUTTON_HEIGHT - 2, INPUT_FILL);

        float displayScale = draggingSlider ? previewScale : getScale();
        float ratio = (displayScale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE);
        int trackInner = sliderW - 4 - SLIDER_HANDLE_WIDTH;
        int handleX = sliderX + 2 + Math.round(ratio * trackInner);
        boolean hovered = isWithin(mouseX, mouseY, sliderX, sliderY, sliderW, BUTTON_HEIGHT) || draggingSlider;
        int handleColor = hovered ? PRIMARY_ORANGE : OUTLINE_LIGHT;
        context.fill(handleX, sliderY + 2, handleX + SLIDER_HANDLE_WIDTH, sliderY + BUTTON_HEIGHT - 2, handleColor);
    }

    private boolean isOverScaleSlider(double mouseX, double mouseY) {
        double umx = unscaleMouseX(mouseX);
        double umy = unscaleMouseY(mouseY);
        int sliderX = getSliderX();
        int sliderY = getSliderY();
        int sliderW = getSliderWidth();
        return umx >= sliderX && umx < sliderX + sliderW
                && umy >= sliderY && umy < sliderY + BUTTON_HEIGHT;
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

        if (button == 0 && isOverScaleSlider(mouseX, mouseY)) {
            draggingSlider = true;
            previewScale = getScale();
            return true;
        }

        double umx = unscaleMouseX(mouseX);
        double umy = unscaleMouseY(mouseY);

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
            return false;
        }

        if (handleTitleClick(umx, umy, button, list)) {
            return true;
        }
        if (handleFooterClick(umx, umy, button, list)) {
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

        int closeIndex = 3;
        int closeW = getTitleButtonWidth(closeIndex);
        int closeX = posX + getPanelWidth() - 2 - PADDING - closeW;
        int buttonX = dropdownX + dropdownW + TITLE_BUTTON_GAP;
        for (int i = 0; i < TITLE_BUTTON_LABELS.length; i++) {
            if (i == closeIndex) {
                continue;
            }
            int buttonW = getTitleButtonWidth(i);
            if (buttonX + buttonW > closeX - TITLE_BUTTON_GAP) {
                break;
            }
            if (isWithin(mouseX, mouseY, buttonX, dropdownY, buttonW, dropdownH) && button == 0) {
                handleTitleButtonClick(i, list);
                return true;
            }
            buttonX += buttonW + TITLE_BUTTON_GAP;
        }

        if (button == 0 && isWithin(mouseX, mouseY, closeX, dropdownY, closeW, dropdownH)) {
            handleTitleButtonClick(closeIndex, list);
            return true;
        }

        return false;
    }

    private void handleTitleButtonClick(int index, ShoppingListData list) {
        ShoppingListManager mgr = ShoppingListManager.getInstance();
        switch (index) {
            case 0 -> {
                renaming = true;
                dropdownOpen = false;
                renameBuffer = list.getName();
                renameAllSelected = false;
            }
            case 1 -> {
                clearTransientState();
                ShoppingListData newList = mgr.createList("New List");
                pinnedListId = newList.getId();
                scrollOffset = 0;
            }
            case 2 -> {
                if (mgr.deleteList(pinnedListId)) {
                    ShoppingListData active = mgr.getActiveList();
                    if (active != null) {
                        pinnedListId = active.getId();
                    }
                    scrollOffset = 0;
                }
            }
            case 3 -> close();
            default -> {
            }
        }
    }

    private boolean handleFooterClick(double mouseX, double mouseY, int button, ShoppingListData list) {
        if (button != 0) return false;

        int footerY = getFooterY();
        int buttonX = posX + PADDING;
        int buttonY = footerY + (FOOTER_HEIGHT - BUTTON_HEIGHT) / 2;
        for (int i = 0; i < FOOTER_BUTTON_LABELS.length; i++) {
            int buttonW = getFooterButtonWidth();
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

    private boolean handleItemClick(double mouseX, double mouseY, int button, ShoppingListData list) {
        int itemAreaY = getItemAreaY();
        int itemAreaBottom = itemAreaY + getVisibleRows() * ROW_HEIGHT;
        if (mouseY < itemAreaY || mouseY >= itemAreaBottom) {
            return false;
        }

        int row = (int) ((mouseY - itemAreaY) / ROW_HEIGHT);
        int index = scrollOffset + row;
        if (index < 0 || index >= list.getItems().size()) {
            return false;
        }

        ShoppingListData.ShoppingItem item = list.getItems().get(index);
        int rowY = itemAreaY + row * ROW_HEIGHT;
        int qtyBoxX = posX + QTY_X;
        int qtyBoxY = rowY + 2;
        int qtyBoxH = ROW_HEIGHT - 4;

        if (button == 1) {
            ShoppingListManager.getInstance().removeItem(pinnedListId, item.getName());
            clampScroll(list);
            return true;
        }

        if (button != 0) {
            return false;
        }

        if (isWithin(mouseX, mouseY, qtyBoxX, qtyBoxY, QTY_WIDTH, qtyBoxH)) {
            if (editingQtyIndex >= 0) {
                confirmQtyEdit(list);
            }
            editingQtyIndex = index;
            qtyBuffer = String.valueOf(item.getQuantity());
            qtyAllSelected = false;
            return true;
        }

        if (isWithin(mouseX, mouseY, posX + ITEM_NAME_X, rowY, ITEM_NAME_WIDTH, ROW_HEIGHT)) {
            QualityOfLife.searchTradeMarket(item.getName());
            return true;
        }

        if (editingQtyIndex >= 0) {
            confirmQtyEdit(list);
        }

        return true;
    }

    public boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (!visible) return false;

        ShoppingListData list = getList();
        if (list == null) return false;

        double umx = unscaleMouseX(mouseX);
        double umy = unscaleMouseY(mouseY);

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
            int qtyBoxX = posX + QTY_X;
            if (umx >= qtyBoxX && umx < qtyBoxX + QTY_WIDTH) {
                int delta = isShiftDown() ? 10 : 1;
                int step = amount > 0 ? 1 : -1;
                ShoppingListData.ShoppingItem item = list.getItems().get(index);
                int newQty = Math.max(1, item.getQuantity() + step * delta);
                ShoppingListManager.getInstance().setQuantity(pinnedListId, item.getName(), newQty);
                return true;
            }
        }

        scrollOffset -= amount > 0 ? 1 : -1;
        clampScroll(list);
        return true;
    }

    public boolean onKeyPressed(int keyCode) {
        if (!visible) return false;

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

    private void clampScroll(ShoppingListData list) {
        if (list == null) {
            scrollOffset = 0;
            return;
        }
        int maxScroll = Math.max(0, list.getItems().size() - MAX_VISIBLE_ROWS);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
    }

    private void drawTooltip(DrawContext context, TextRenderer textRenderer, String tooltip, int mouseX, int mouseY) {
        int width = getScaledTextWidth(textRenderer, tooltip);
        int x = mouseX + 8;
        int textHeight = getScaledFontHeight(textRenderer);
        int y = mouseY - textHeight - 5;
        context.fill(x - 3, y - 3, x + width + 3, y + textHeight + 3, 0xE0000000);
        drawTextScaled(context, textRenderer, tooltip, x, y, TITLE_TEXT);
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

    private static boolean isKeyDown(int keyCode) {
        long handle = WTZClient.client().getWindow().getHandle();
        return GLFW.glfwGetKey(handle, keyCode) == GLFW.GLFW_PRESS;
    }
}
