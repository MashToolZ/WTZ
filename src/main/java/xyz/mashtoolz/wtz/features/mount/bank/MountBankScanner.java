package xyz.mashtoolz.wtz.features.mount.bank;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.mashtoolz.wtz.client.WTZClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MountBankScanner {
    public static final int FIRST_CONTENT_SLOT = 0;
    public static final int LAST_CONTENT_SLOT = 44;
    public static final int PREVIOUS_PAGE_SLOT = 51;
    public static final int NEXT_PAGE_SLOT = 52;

    private static final Pattern PAGE_NUMBER = Pattern.compile("Page\\s+(\\d+)");

    private MountBankScanner() {
    }

    public static List<MountBankIndexEntry> scanPage(HandledScreen<?> screen, int page) {
        ScreenHandler handler = screen.getScreenHandler();
        List<MountBankIndexEntry> entries = new ArrayList<>();
        for (Slot slot : handler.slots) {
            if (slot.id < FIRST_CONTENT_SLOT || slot.id > LAST_CONTENT_SLOT) continue;
            if (!slot.hasStack()) continue;
            MountBankIndexParser.parse(slot.getStack(), page, slot.id).ifPresent(entries::add);
        }
        return entries;
    }

    public static Optional<Integer> readCurrentPage(HandledScreen<?> screen) {
        ScreenHandler handler = screen.getScreenHandler();
        Optional<Integer> previousTarget = readPreviousPageNumber(handler);
        if (previousTarget.isPresent()) {
            return Optional.of(previousTarget.get() + 1);
        }

        Optional<Integer> nextTarget = readNextPageNumber(handler);
        return nextTarget.map(page -> page - 1);
    }

    public static boolean isBankScreen(HandledScreen<?> screen) {
        if (screen == null) return false;
        return readCurrentPage(screen).isPresent()
                || readNextPageNumber(screen.getScreenHandler()).isPresent()
                || readPreviousPageNumber(screen.getScreenHandler()).isPresent();
    }

    public static Optional<Integer> readPreviousPageNumber(ScreenHandler handler) {
        return readPageButtonNumber(handler, PREVIOUS_PAGE_SLOT, "<");
    }

    public static Optional<Integer> readNextPageNumber(ScreenHandler handler) {
        return readPageButtonNumber(handler, NEXT_PAGE_SLOT, ">");
    }

    public static boolean hasNextPage(ScreenHandler handler) {
        return readNextPageNumber(handler).isPresent();
    }

    public static boolean hasPreviousPage(ScreenHandler handler) {
        return readPreviousPageNumber(handler).isPresent();
    }

    public static boolean clickPreviousPage(HandledScreen<?> screen) {
        ScreenHandler handler = screen.getScreenHandler();
        if (!hasPreviousPage(handler)) return false;

        MinecraftClient client = WTZClient.client();
        if (client.interactionManager == null || WTZClient.player() == null) return false;
        client.interactionManager.clickSlot(handler.syncId, PREVIOUS_PAGE_SLOT, 0, SlotActionType.PICKUP, WTZClient.player());
        return true;
    }

    public static boolean clickQuickJumpPage(HandledScreen<?> screen, int page) {
        ScreenHandler handler = screen.getScreenHandler();
        QuickJump quickJump = findQuickJump(handler, page);
        if (quickJump == null) return false;

        MinecraftClient client = WTZClient.client();
        if (client.interactionManager == null || WTZClient.player() == null) return false;
        client.interactionManager.clickSlot(handler.syncId, quickJump.slot(), quickJump.button(), SlotActionType.SWAP, WTZClient.player());
        return true;
    }

    public static boolean clickNextPage(HandledScreen<?> screen) {
        ScreenHandler handler = screen.getScreenHandler();
        if (!hasNextPage(handler)) return false;

        MinecraftClient client = WTZClient.client();
        if (client.interactionManager == null || WTZClient.player() == null) return false;
        client.interactionManager.clickSlot(handler.syncId, NEXT_PAGE_SLOT, 0, SlotActionType.PICKUP, WTZClient.player());
        return true;
    }

    public static String pageSignature(ScreenHandler handler) {
        return pageButtonText(handler, PREVIOUS_PAGE_SLOT) + "|" + pageButtonText(handler, NEXT_PAGE_SLOT);
    }

    private static QuickJump findQuickJump(ScreenHandler handler, int page) {
        QuickJump previous = findQuickJump(handler, PREVIOUS_PAGE_SLOT, page);
        if (previous != null) return previous;
        return findQuickJump(handler, NEXT_PAGE_SLOT, page);
    }

    private static QuickJump findQuickJump(ScreenHandler handler, int slotId, int page) {
        if (slotId < 0 || slotId >= handler.slots.size()) return null;
        ItemStack stack = handler.getSlot(slotId).getStack();
        if (stack == null || stack.isEmpty()) return null;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        int button = 0;
        for (Text line : lore.lines()) {
            String text = Formatting.strip(line.getString());
            if (text == null) continue;

            Matcher matcher = PAGE_NUMBER.matcher(text);
            if (!matcher.find()) continue;

            if (Integer.parseInt(matcher.group(1)) == page) {
                return new QuickJump(slotId, button);
            }
            button++;
        }

        return null;
    }

    private static String pageButtonText(ScreenHandler handler, int slotId) {
        if (slotId < 0 || slotId >= handler.slots.size()) return "";
        ItemStack stack = handler.getSlot(slotId).getStack();
        if (stack == null || stack.isEmpty()) return "";
        return Formatting.strip(stack.getName().getString());
    }

    private static Optional<Integer> readPageButtonNumber(ScreenHandler handler, int slotId, String directionMarker) {
        String text = pageButtonText(handler, slotId);
        if (!text.contains(directionMarker)) return Optional.empty();

        Matcher matcher = PAGE_NUMBER.matcher(text);
        if (!matcher.find()) return Optional.empty();
        return Optional.of(Integer.parseInt(matcher.group(1)));
    }

    private record QuickJump(int slot, int button) {
    }
}
