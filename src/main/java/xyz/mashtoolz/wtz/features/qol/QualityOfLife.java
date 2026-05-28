package xyz.mashtoolz.wtz.features.qol;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.enums.GUI;

import java.util.List;
import java.util.Locale;

public class QualityOfLife {

    private static final boolean QUICK_SELL_ENABLED = false;

    private static String pendingAmount = null;
    private static String pendingSearchName = null;
    private static String suppressSearchEcho = null;
    private static boolean openChatNextTick = false;
    private static boolean renderingActionbarAboveChat = false;
    private static final long AUTO_REOPEN_FREEZE_MS = 500L;
    private static long frozenUntilMs = 0L;

    public static void register() {
        ItemTooltipCallback.EVENT.register(QualityOfLife::onItemTooltip);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openChatNextTick) {
                openChatNextTick = false;
                client.setScreen(new ChatScreen("", false));
            }
        });
    }

    private static void onItemTooltip(ItemStack stack, Item.TooltipContext context, TooltipType type, List<Text> lines) {
        if (!WTZClient.CONFIG.qualityOfLifeEnabled) return;
        if (!QUICK_SELL_ENABLED) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return;

        if (GUI.TRADE_MARKET.is(screen) && isSellOrder(screen)) {
            if (stack.getName().getString().equals("Set Amount")) {
                Text originalLine = lines.getLast();
                if (originalLine.getSiblings().isEmpty() || originalLine.getSiblings().getFirst().getSiblings().isEmpty()) return;

                Style keybindStyle = originalLine.getSiblings().getFirst().getSiblings().getFirst().getStyle();
                MutableText newLine = Text.literal("§7")
                        .setStyle(Style.EMPTY.withColor(Formatting.DARK_PURPLE).withItalic(false))
                        .append(Text.empty()
                                .setStyle(Style.EMPTY.withColor(Formatting.WHITE))
                                .append(Text.literal("\uF001").setStyle(keybindStyle))
                                .append(Text.literal(" Click to set ALL").setStyle(Style.EMPTY.withColor(Formatting.GREEN)))
                        );
                lines.add(newLine);
            }
        }
    }

    public static boolean trySellAll(String message) {
        if (!QUICK_SELL_ENABLED) return false;
        if (!WTZClient.CONFIG.qualityOfLifeEnabled) return false;
        boolean hasBuyPrompt = message.contains("\uDAFF\uDFFC\uE001\uDB00\uDC06 Type the amount you wish to sell or type 'cancel' to cancel:");
        if (hasBuyPrompt && pendingAmount != null) {
            String amount = pendingAmount;
            pendingAmount = null;
            WTZClient.player().networkHandler.sendChatMessage(amount);
            return true;
        } else if (hasBuyPrompt) {
            openChatNextTick = true;
        }
        return false;
    }

    public static boolean trySearchPrompt(String message) {
        boolean hasSearchPrompt = message.contains("\uDAFF\uDFFC\uE001\uDB00\uDC06 Type the item name or type 'cancel' to cancel:");
        if (hasSearchPrompt && pendingSearchName != null) {
            String name = pendingSearchName;
            pendingSearchName = null;
            suppressSearchEcho = name;
            WTZClient.player().networkHandler.sendChatMessage(name);
            return true;
        } else if (hasSearchPrompt) {
            openChatNextTick = true;
        }
        return false;
    }

    public static boolean trySuppressSearchEcho(String message) {
        if (suppressSearchEcho != null && message.contains(suppressSearchEcho)) {
            suppressSearchEcho = null;
            return true;
        }
        return false;
    }

    public static boolean shouldShowActionbarAboveChat() {
        return WTZClient.CONFIG.qualityOfLifeEnabled
                && WTZClient.CONFIG.qolActionbarAboveChat;
    }

    public static boolean shouldHideActionbarOnChat() {
        return WTZClient.CONFIG.qualityOfLifeEnabled
                && WTZClient.CONFIG.qolHideActionbarInChat;
    }

    public static boolean isRenderingActionbarAboveChat() {
        return renderingActionbarAboveChat;
    }

    public static void renderActionbarAboveChat(Runnable renderer) {
        renderingActionbarAboveChat = true;
        try {
            renderer.run();
        } finally {
            renderingActionbarAboveChat = false;
        }
    }

    public static void searchTradeMarket(String itemName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) return;
        if (!GUI.TRADE_MARKET.is(screen)) return;

        ScreenHandler handler = screen.getScreenHandler();
        Slot searchSlot = handler.getSlot(47);
        if (!searchSlot.hasStack() || !searchSlot.getStack().getName().getString().contains("Search and Filter")) return;

        pendingSearchName = itemName;
        freezeScreen();
        if (client.interactionManager == null || WTZClient.player() == null) return;
        client.interactionManager.clickSlot(handler.syncId, 47, 0, SlotActionType.PICKUP, WTZClient.player());
    }

    public static boolean onHandledScreenClick(HandledScreen<?> screen, Click click, Slot slot) {
        if (!WTZClient.CONFIG.qualityOfLifeEnabled) return false;
        if (click.button() != 1) return false;

        if (tryQuickSellAll(screen, slot)) return true;
        return tryRightClickBack(screen, slot);
    }

    private static boolean tryQuickSellAll(HandledScreen<?> screen, Slot slot) {
        if (!QUICK_SELL_ENABLED) return false;
        if (slot == null || !slot.hasStack()) return false;
        if (!slot.getStack().getName().getString().equals("Set Amount")) return false;
        if (!isSellOrder(screen)) return false;

        ScreenHandler handler = screen.getScreenHandler();
        pendingAmount = String.valueOf(countMatchingItems(handler, handler.getSlot(22).getStack()));
        freezeScreen();
        MinecraftClient client = WTZClient.client();
        if (client.interactionManager == null || WTZClient.player() == null) return false;
        client.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, WTZClient.player());
        return true;
    }

    private static int countMatchingItems(ScreenHandler handler, ItemStack itemStack) {
        int count = 0;
        String target = itemStack.getName().getString();
        for (Slot slot : handler.slots) {
            if (!(slot.inventory instanceof PlayerInventory) || !slot.hasStack()) continue;
            ItemStack stack = slot.getStack();
            if (stack.getName().getString().equals(target)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean tryRightClickBack(HandledScreen<?> screen, Slot slot) {
        if (!WTZClient.CONFIG.qolRightClickBack) return false;
        if (slot != null && slot.hasStack() && hasRightClickAction(slot.getStack())) return false;

        ScreenHandler handler = screen.getScreenHandler();
        for (Slot backSlot : handler.slots) {
            if (backSlot.inventory instanceof PlayerInventory) continue;
            if (!backSlot.hasStack()) continue;
            if (backSlot.getStack().getName().getString().contains("Back")) {
                MinecraftClient client = WTZClient.client();
                if (client.interactionManager == null || WTZClient.player() == null) return false;
                client.interactionManager.clickSlot(handler.syncId, backSlot.id, 0, SlotActionType.PICKUP, WTZClient.player());
                return true;
            }
        }
        return false;
    }

    private static boolean hasRightClickAction(ItemStack stack) {
        List<Text> lines = stack.getTooltip(Item.TooltipContext.DEFAULT, WTZClient.player(), TooltipType.BASIC);
        for (Text line : lines) {
            if (containsRightClickIcon(line)) return true;
            String text = Formatting.strip(line.getString().toLowerCase(Locale.ROOT));
            System.out.println(text);
            if (text.matches(".*\\bright[-\\s]?click\\b.*")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRightClickIcon(Text text) {
        String content = text.getString();
        if (content.contains("\uF001")) return true;
        for (Text sibling : text.getSiblings()) {
            if (containsRightClickIcon(sibling)) return true;
        }
        return false;
    }

    private static boolean isSellOrder(HandledScreen<?> screen) {
        ScreenHandler handler = screen.getScreenHandler();
        for (Slot s : handler.slots) {
            if (s.hasStack() && s.getStack().getName().getString().equals("Sell Order Summary")) return true;
        }
        return false;
    }

    public static boolean isScreenFrozen() {
        return System.currentTimeMillis() < frozenUntilMs;
    }

    private static void freezeScreen() {
        frozenUntilMs = System.currentTimeMillis() + AUTO_REOPEN_FREEZE_MS;
    }
}
