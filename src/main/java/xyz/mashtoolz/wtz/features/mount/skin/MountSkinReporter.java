package xyz.mashtoolz.wtz.features.mount.skin;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.features.mount.MountSkinColors;
import xyz.mashtoolz.wtz.auth.LinkStateStore;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.features.mount.MountUtils;
import xyz.mashtoolz.wtz.util.ChatHelper;
import xyz.mashtoolz.wtz.config.WTZConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

public final class MountSkinReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-MountSkinReporter");
    private static final int PURCHASE_SCAN_TICKS = 40;
    private static final int PURCHASE_FLUSH_DEBOUNCE_TICKS = 10;
    private static final int MAX_SKINS_PER_BATCH = 30;
    private static final int QUEUE_RETRY_INTERVAL_TICKS = 200;
    private static final int ALERT_STAY_TICKS = 60;
    private static final TextColor LABEL_COLOR = TextColor.fromRgb(0xD1D1D1);
    private static final TextColor SEPARATOR_COLOR = TextColor.fromRgb(0xAAAAAA);

    private static final LinkStateStore LINK_STORE = new LinkStateStore();
    private static final MountSkinQueue QUEUE = new MountSkinQueue();

    private static boolean registered = false;
    private static List<ItemStack> lastInventorySnapshot = null;
    private static List<ItemStack> inventorySnapshot = null;
    private static int ticksRemaining = 0;
    private static int flushTicksRemaining = 0;
    private static int retryTicksRemaining = QUEUE_RETRY_INTERVAL_TICKS;
    private static volatile boolean queuePostInFlight = false;
    private static long lastMissingTokenWarningAt = 0;

    private MountSkinReporter() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        QUEUE.load();
        ClientTickEvents.END_CLIENT_TICK.register(MountSkinReporter::tick);
    }

    public static void onWynncraftJoin() {
        if (!WTZClient.CONFIG.mountSkinReportingEnabled) return;
        if (readToken().isPresent()) {
            flushQueuedSkins();
        }
    }

    public static void onGameMessage(Text message) {
        String text = message.getString();
        if (text.contains("Mount Merchant") && text.contains("Thank you for your business"))
            onMerchantPurchase();
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null) return;

        if (ticksRemaining <= 0 || inventorySnapshot == null) {
            updateLastInventorySnapshot(client);
            tickQueueRetry();
            return;
        }

        ticksRemaining--;
        if (flushTicksRemaining > 0) flushTicksRemaining--;

        var inventory = client.player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack current = inventory.getStack(i);
            ItemStack previous = i < inventorySnapshot.size() ? inventorySnapshot.get(i) : ItemStack.EMPTY;
            if (ItemStack.areEqual(current, previous)) continue;

            if (current.isEmpty() || !isMountItem(current)) {
                updateScanSnapshotSlot(i, current);
                continue;
            }

            String skin = extractSkin(current);
            if (skin != null) {
                String[] parts = skin.split("-", 2);
                if (parts.length != 2) {
                    LOGGER.warn("Mount skin lacks '-' separator: {}", skin);
                } else {
                    String itemName = current.getName().getString();
                    String primary = parts[0].trim();
                    String secondary = parts[1].trim();
                    LOGGER.info("Detected mount skin purchase - {} / {} / {}", itemName, primary, secondary);
                    maybeShowMountColorAlert(itemName, primary, secondary);
                    enqueueSkin(itemName, primary, secondary);
                    flushTicksRemaining = PURCHASE_FLUSH_DEBOUNCE_TICKS;
                }
            }
            updateScanSnapshotSlot(i, current);
        }

        if (ticksRemaining <= 0 || (flushTicksRemaining <= 0 && hasQueuedSkins())) {
            flushQueuedSkins();
            clearPurchaseScan();
            updateLastInventorySnapshot(client);
        }
    }

    private static void onMerchantPurchase() {
        MinecraftClient client = WTZClient.client();
        if (client.player == null) return;

        if (inventorySnapshot == null) {
            inventorySnapshot = lastInventorySnapshot != null
                    ? copyInventorySnapshot(lastInventorySnapshot)
                    : copyPlayerInventory(client);
        }
        ticksRemaining = PURCHASE_SCAN_TICKS;
        flushTicksRemaining = PURCHASE_FLUSH_DEBOUNCE_TICKS;
    }

    private static void updateLastInventorySnapshot(MinecraftClient client) {
        if (client.player == null) return;
        lastInventorySnapshot = copyPlayerInventory(client);
    }

    private static List<ItemStack> copyPlayerInventory(MinecraftClient client) {
        assert client.player != null;
        var inventory = client.player.getInventory();
        List<ItemStack> snapshot = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            snapshot.add(inventory.getStack(i).copy());
        }
        return snapshot;
    }

    private static List<ItemStack> copyInventorySnapshot(List<ItemStack> source) {
        List<ItemStack> snapshot = new ArrayList<>();
        for (ItemStack stack : source) {
            snapshot.add(stack.copy());
        }
        return snapshot;
    }

    private static void updateScanSnapshotSlot(int slot, ItemStack stack) {
        if (inventorySnapshot == null) return;
        while (inventorySnapshot.size() <= slot) {
            inventorySnapshot.add(ItemStack.EMPTY);
        }
        inventorySnapshot.set(slot, stack.copy());
    }

    private static void enqueueSkin(String itemName, String primary, String secondary) {
        QUEUE.add(itemName, primary, secondary);
    }

    private static boolean hasQueuedSkins() {
        return QUEUE.hasPurchases();
    }

    private static void tickQueueRetry() {
        if (!WTZClient.CONFIG.mountSkinReportingEnabled) return;
        if (queuePostInFlight || !hasQueuedSkins()) return;
        if (retryTicksRemaining > 0) {
            retryTicksRemaining--;
            return;
        }

        retryTicksRemaining = QUEUE_RETRY_INTERVAL_TICKS;
        flushQueuedSkins();
    }

    public static void flushQueuedSkins() {
        if (!WTZClient.CONFIG.mountSkinReportingEnabled) return;
        if (queuePostInFlight) return;

        Optional<String> token = readToken();
        if (token.isEmpty()) {
            if (hasQueuedSkins()) warnMissingToken();
            return;
        }

        if (!QUEUE.hasPurchases()) {
            if (inventorySnapshot != null) {
                LOGGER.warn("Mount merchant purchase detected, but no changed mount skin was found.");
            }
            return;
        }

        List<MountSkinQueue.Purchase> batch = QUEUE.firstBatch(MAX_SKINS_PER_BATCH);
        LOGGER.info("Submitting {} queued mount skin purchase(s).", batch.size());
        postSkins(batch, token.get());
    }

    private static void postSkins(List<MountSkinQueue.Purchase> skins, String token) {
        queuePostInFlight = true;
        MountSkinSubmitter.submit(skins, token)
                .whenComplete((result, ex) -> {
                    queuePostInFlight = false;
                    retryTicksRemaining = QUEUE_RETRY_INTERVAL_TICKS;

                    if (ex != null) {
                        LOGGER.error("Mount skin POST error for {} purchase(s)", skins.size(), ex);
                        return;
                    }

                    if (result.unauthorized()) {
                        LOGGER.warn("Mount skin POST rejected as unauthorized. Link a valid token with /wtz link <token>.");
                        warnMissingToken();
                        return;
                    }

                    if (result.accepted()) {
                        LOGGER.info("Mount skin POST accepted for {} purchase(s).", skins.size());
                        QUEUE.removeAccepted(result.acceptedIds());
                        return;
                    }

                    LOGGER.warn("Mount skin POST rejected for {} purchase(s). status={} body={}",
                            skins.size(), result.statusCode(), result.body());
                });
    }

    private static void clearPurchaseScan() {
        inventorySnapshot = null;
        ticksRemaining = 0;
        flushTicksRemaining = 0;
    }

    private static boolean isMountItem(ItemStack stack) {
        return MountUtils.isMountSkinItem(stack);
    }

    private static String extractSkin(ItemStack stack) {
        return MountUtils.extractSkin(stack);
    }

    private static Optional<String> readToken() {
        return LINK_STORE.loadToken();
    }

    private static void warnMissingToken() {
        long now = System.currentTimeMillis();
        if (now - lastMissingTokenWarningAt < 60_000) return;
        lastMissingTokenWarningAt = now;
        MinecraftClient client = WTZClient.client();
        client.execute(() -> {
            if (client.player == null) return;
            ChatHelper.sendWarning("No WynnToolZ token configured. Run /wtz link to open the link page.");
        });
    }

    private static void maybeShowMountColorAlert(String itemName, String primary, String secondary) {
        String mountType = MountUtils.extractMountType(itemName);
        if (mountType == null) return;
        if (!isColorAlertEnabledForMount(mountType)) return;
        if (!matchesPrimaryColor(mountType, primary) || !matchesSecondaryColor(mountType, secondary)) return;

        String mountName = displayMountType(mountType);

        MinecraftClient client = WTZClient.client();
        client.execute(() -> {
            if (client.player == null || client.inGameHud == null) return;
            MutableText pair = coloredPairText(mountName, primary, secondary);
            MutableText title = Text.empty().append(pair);
            MutableText chat = Text.literal(mountName + " color: ").setStyle(Style.EMPTY.withColor(LABEL_COLOR))
                .append(pair);
            MutableText subtitle = Text.literal(mountName + " found").setStyle(Style.EMPTY.withColor(LABEL_COLOR));

            client.inGameHud.setTitleTicks(0, ALERT_STAY_TICKS, 0);
            client.inGameHud.setSubtitle(subtitle);
            client.inGameHud.setTitle(title);

            ChatHelper.send(chat);
        });
    }

    private static MutableText coloredPairText(String mount, String primary, String secondary) {
        int primaryRgb = MountSkinColors.colorFor(mount, "primary", primary, 0xFFFFFFFF) & 0xFFFFFF;
        int secondaryRgb = MountSkinColors.colorFor(mount, "secondary", secondary, 0xFFFFFFFF) & 0xFFFFFF;
        return Text.literal(primary.trim()).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(primaryRgb)))
            .append(Text.literal(" - ").setStyle(Style.EMPTY.withColor(SEPARATOR_COLOR)))
            .append(Text.literal(secondary.trim()).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(secondaryRgb))));
    }

    private static boolean isColorAlertEnabledForMount(String mountType) {
        return switch (mountType.toLowerCase(Locale.ROOT)) {
            case "horse" -> WTZClient.CONFIG.mountHorseColorAlertsEnabled;
            case "wyvern" -> WTZClient.CONFIG.mountWyvernColorAlertsEnabled;
            case "adasaur" -> WTZClient.CONFIG.mountAdasaurColorAlertsEnabled;
            default -> false;
        };
    }

    private static boolean matchesPrimaryColor(String mountType, String primary) {
        String primaryToken = normalizeColorToken(primary);
        return switch (mountType.toLowerCase(Locale.ROOT)) {
            case "horse" -> matchesColorToken(WTZClient.CONFIG.mountHorseAlertPrimaryColor.name(), primaryToken, "Any");
            case "wyvern" -> matchesColorToken(WTZClient.CONFIG.mountWyvernAlertPrimaryColor.name(), primaryToken, "Any");
            case "adasaur" -> matchesColorToken(WTZClient.CONFIG.mountAdasaurAlertPrimaryColor.name(), primaryToken, "Any");
            default -> false;
        };
    }

    private static boolean matchesSecondaryColor(String mountType, String secondary) {
        String secondaryToken = normalizeColorToken(secondary);
        return switch (mountType.toLowerCase(Locale.ROOT)) {
            case "horse" -> matchesColorToken(WTZClient.CONFIG.mountHorseAlertSecondaryColor.name(), secondaryToken, "Any");
            case "wyvern" -> matchesColorToken(WTZClient.CONFIG.mountWyvernAlertSecondaryColor.name(), secondaryToken, "Any");
            case "adasaur" -> matchesColorToken(WTZClient.CONFIG.mountAdasaurAlertSecondaryColor.name(), secondaryToken, "Any");
            default -> false;
        };
    }

    private static boolean matchesColorToken(String selected, String actual, String anyToken) {
        return normalizeColorToken(selected).equals(normalizeColorToken(anyToken))
            || normalizeColorToken(selected).equals(actual);
    }

    private static String displayMountType(String mountType) {
        if (mountType == null || mountType.isBlank()) return "Mount";
        String lower = mountType.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String normalizeColorToken(String value) {
        if (value == null) return "";
        return value.trim().replace('-', ' ').replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
    }
}
