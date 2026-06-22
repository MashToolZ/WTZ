package xyz.mashtoolz.wtz.features.mount.stats;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.config.WTZConfig;
import xyz.mashtoolz.wtz.features.mount.MountPatterns;
import xyz.mashtoolz.wtz.features.mount.MountUtils;
import xyz.mashtoolz.wtz.features.mount.helper.MountManager;
import xyz.mashtoolz.wtz.features.overlay.EditableOverlayHandle;
import xyz.mashtoolz.wtz.features.overlay.OverlayBounds;
import xyz.mashtoolz.wtz.util.ColorUtils;
import xyz.mashtoolz.wtz.util.TextRenderColorUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

public class MountStatsOverlay {

    private static final EditableOverlayHandle EDITOR_HANDLE = new EditableOverlayHandle(
            "mount_stats",
            () -> WTZClient.CONFIG.mountStatsEnabled,
            MountStatsOverlay::editBounds,
            value -> WTZClient.CONFIG.mountStatsDragPctX = value,
            value -> WTZClient.CONFIG.mountStatsDragPctY = value,
            WTZConfig.DEFAULT_MOUNT_STATS_DRAG_PCT_X,
            WTZConfig.DEFAULT_MOUNT_STATS_DRAG_PCT_Y,
            () -> WTZClient.CONFIG.mountStatsDragScale,
            value -> WTZClient.CONFIG.mountStatsDragScale = value,
            0.3f,
            2.0f,
            () -> WTZClient.CONFIG.mountStatsEditLocked,
            value -> WTZClient.CONFIG.mountStatsEditLocked = value,
            null,
            null
    );

    
    private static final int DEFAULT_STAT_COLOR = 0xFFACFAC6;
    private static final int ENERGY_LABEL_COLOR = 0xFFD1D1D1;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int CAP_COLOR = 0xFF808080;
    private static final int NAME_ACTIVE_COLOR = 0xFF55FF55;
    private static final int NAME_INACTIVE_COLOR = 0xFF808080;
    private static final int PADDING = 3;
    private static final int LINE_HEIGHT = 10;
    private static final int GAP = 12;

    
    private static final int DELTA_DURATION_MS = 2000;
    private static final int DELTA_POSITIVE_COLOR = 0xFF55FF55;
    private static final int DELTA_NEGATIVE_COLOR = 0xFFFF5555;

    
    private static final int SLOT_REFRESH_INTERVAL = 20;
    private static final int MOUNTED_OUTLINE_COLOR = 0xFFFFFFFF;
    private static final int NOT_MOUNTED_OUTLINE_COLOR = 0xFF7A7A7A;
    private static final int NO_OUTLINE = Integer.MIN_VALUE;

    
    private static int lastUsedSlot = -1;
    private static int lastTrackedSlot = -1;
    private static boolean wasMounted = false;
    private static int slotRefreshCounter = 0;
    private static final Map<String, Integer> prevValues = new HashMap<>();
    private static final Map<String, Delta> deltas = new HashMap<>();

    
    private static boolean editMode = false;

    
    private static int lastOverlayX, lastOverlayY, lastOverlayScaledW, lastOverlayScaledH;
    private static boolean screenOverlayVisible = false;

    

    public static void onItemUsed(int slot) {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;
        ItemStack stack = player.getInventory().getStack(slot);
        if (stack == null || stack.isEmpty()) return;
        if (parseAll(stack).stats().isEmpty()) return;
        lastUsedSlot = slot;
    }

    public static ItemStack getLastUsedItem() {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return ItemStack.EMPTY;
        if (WTZClient.CONFIG.mountStatsTrackHeld) {
            ItemStack heldItem = player.getMainHandStack();
            if (isMountItem(heldItem)) return heldItem;

            ItemStack mountedItem = getMountedItem();
            if (mountedItem != null && !mountedItem.isEmpty()) return mountedItem;

            return heldItem;
        }
        if (cannotUseTrackedSlot(lastUsedSlot)) return ItemStack.EMPTY;
        return player.getInventory().getStack(lastUsedSlot);
    }

    public static ItemStack getMountedItem() {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null || cannotUseTrackedSlot(lastUsedSlot)) return ItemStack.EMPTY;
        return player.getInventory().getStack(lastUsedSlot);
    }

    public static ItemStack getActiveMountItem() {
        return getLastUsedItem();
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.SUBTITLES,
                Identifier.of("wtz", "mount_stats"),
                MountStatsOverlay::onHudRender
        );
        ClientTickEvents.END_CLIENT_TICK.register(client -> tickSlotRefresh());
    }

    public static Map<String, int[]> parse(ItemStack stack) {
        return parseAll(stack).stats();
    }

    public static ParsedMount parseAll(ItemStack stack) {
        Map<String, int[]> stats = new LinkedHashMap<>();
        Map<String, Integer> statColors = new HashMap<>();
        int[] energyPool = null;

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return new ParsedMount(stats, null, statColors);

        for (Text line : lore.lines()) {
            String str = line.getString();

            Matcher em = MountPatterns.ENERGY.matcher(str);
            if (em.find()) {
                energyPool = new int[]{Integer.parseInt(em.group(1)), Integer.parseInt(em.group(2))};
                continue;
            }

            Matcher m = MountPatterns.STAT.matcher(str);
            if (m.find()) {
                stats.put(m.group(1), new int[]{
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3))
                });
            }
        }

        statColors.putAll(MountManager.getStatColors());

        return new ParsedMount(stats, energyPool, statColors);
    }

    

    public static void setEditMode(boolean enabled) {
        editMode = enabled;
    }

    public static EditableOverlayHandle editorHandle() {
        return EDITOR_HANDLE;
    }

    public static OverlayBounds editBounds() {
        if (!WTZClient.CONFIG.mountStatsEnabled || !screenOverlayVisible) return null;
        return new OverlayBounds(lastOverlayX, lastOverlayY, lastOverlayScaledW, lastOverlayScaledH);
    }

    

    private static void hideScreenOverlay() {
        screenOverlayVisible = false;
    }

    private static int getBgColor() {
        int alpha = (int) (WTZClient.CONFIG.mountStatsBgOpacity / 100.0f * 255);
        return (alpha << 24);
    }

    private static float getScale() {
        return WTZClient.CONFIG.mountStatsDragScale;
    }

    private static void tickSlotRefresh() {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null || !player.hasVehicle() || lastUsedSlot < 0 || lastUsedSlot > 8) return;

        ClientPlayNetworkHandler net = WTZClient.client().getNetworkHandler();
        if (net == null) return;
        if (player.getInventory().getSelectedSlot() != lastUsedSlot) return;

        slotRefreshCounter++;
        if (slotRefreshCounter % SLOT_REFRESH_INTERVAL != 0) return;

        net.sendPacket(new UpdateSelectedSlotC2SPacket(lastUsedSlot));
    }

    private static void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (!WTZClient.CONFIG.mountStatsEnabled) return;

        if (!editMode) {
            hideScreenOverlay();
        }

        renderCurrentOverlay(context, editMode);
    }

    public static void renderEditOverlay(DrawContext context) {
        if (!WTZClient.CONFIG.mountStatsEnabled) {
            hideScreenOverlay();
            return;
        }

        renderCurrentOverlay(context, true);
    }

    private static void renderCurrentOverlay(DrawContext context, boolean keepInteractiveBounds) {

        ClientPlayerEntity player = WTZClient.player();
        boolean mounted = player != null && player.hasVehicle();
        boolean showWhenNotMounted = WTZClient.CONFIG.mountStatsShowWhenNotMounted;

        if (!mounted) {
            if (showWhenNotMounted) {
                wasMounted = false;
            } else if (wasMounted) {
                lastUsedSlot = -1;
                lastTrackedSlot = -1;
                wasMounted = false;
                prevValues.clear();
                deltas.clear();
                slotRefreshCounter = 0;
            }
            if (!showWhenNotMounted) return;
            if (player == null) return;
        }

        wasMounted = mounted;

        int currentSlot = WTZClient.CONFIG.mountStatsTrackHeld ? player.getInventory().getSelectedSlot() : lastUsedSlot;
        if (currentSlot != lastTrackedSlot) {
            prevValues.clear();
            deltas.clear();
            lastTrackedSlot = currentSlot;
        }

        ItemStack displayedStack = getDisplayStack(player, mounted);
        if (displayedStack == null || displayedStack.isEmpty()) return;

        int outlineColor = NO_OUTLINE;
        if (mounted && WTZClient.CONFIG.mountStatsTrackHeld) {
            ItemStack mountedStack = getMountedItem();
            outlineColor = isSameMount(displayedStack, mountedStack)
                    ? MOUNTED_OUTLINE_COLOR
                    : NOT_MOUNTED_OUTLINE_COLOR;
        }

        ParsedMount parsed = parseAll(displayedStack);
        if (parsed.stats().isEmpty()) return;

        String mountName = displayedStack.getName().getString().replaceAll("[^\\x20-\\x7E]", "").trim();
        renderStats(context, mountName, parsed.stats, parsed.energyPool, parsed.statColors, outlineColor);
        screenOverlayVisible = keepInteractiveBounds;
    }

    private static ItemStack getDisplayStack(ClientPlayerEntity player, boolean mounted) {
        ItemStack displayedStack = getLastUsedItem();
        if ((displayedStack == null || displayedStack.isEmpty()) && !mounted) {
            displayedStack = player.getMainHandStack();
        }
        return displayedStack;
    }

    private static void trackDelta(String key, int currentValue) {
        long now = System.currentTimeMillis();
        Integer prev = prevValues.get(key);
        if (prev != null && prev != currentValue) {
            int change = currentValue - prev;
            Delta existing = deltas.get(key);
            if (existing != null && now - existing.time < DELTA_DURATION_MS) {
                existing.amount += change;
                existing.time = now;
            } else {
                deltas.put(key, new Delta(change, now));
            }
        }
        prevValues.put(key, currentValue);
    }

    private static String getDeltaText(String key) {
        Delta delta = deltas.get(key);
        if (delta == null || delta.amount == 0) return null;
        if (System.currentTimeMillis() - delta.time > DELTA_DURATION_MS) return null;
        return delta.amount > 0 ? "(+" + delta.amount + ")" : "(" + delta.amount + ")";
    }

    private static int getDeltaColor(String key) {
        Delta delta = deltas.get(key);
        if (delta == null) return DELTA_POSITIVE_COLOR;
        return delta.amount >= 0 ? DELTA_POSITIVE_COLOR : DELTA_NEGATIVE_COLOR;
    }

    private static int dimColor(int argb) {
        int a = ((argb >> 24) & 0xFF) / 2;
        int r = (int) (((argb >> 16) & 0xFF) * 0.7f);
        int g = (int) (((argb >> 8) & 0xFF) * 0.7f);
        int b = (int) ((argb & 0xFF) * 0.7f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int textColor(int argb) {
        return TextRenderColorUtils.avoidShaderMarkerColor(argb);
    }

    private static int[] computeScreenPosition(int screenWidth, int screenHeight, int scaledBoxWidth, int scaledBoxHeight) {
        WTZConfig config = WTZClient.CONFIG;

        if (config.mountStatsDragPctX >= 0 && config.mountStatsDragPctY >= 0) {
            int x = (int) Math.round(screenWidth * config.mountStatsDragPctX / 100.0);
            int y = (int) Math.round(screenHeight * config.mountStatsDragPctY / 100.0);
            x = Math.clamp(x, 0, Math.max(0, screenWidth - scaledBoxWidth));
            y = Math.clamp(y, 0, Math.max(0, screenHeight - scaledBoxHeight));
            return new int[]{x, y};
        }

        
        int x = screenWidth - scaledBoxWidth;
        int y = (screenHeight - scaledBoxHeight) / 2;
        x = Math.clamp(x, 0, Math.max(0, screenWidth - scaledBoxWidth));
        y = Math.clamp(y, 0, Math.max(0, screenHeight - scaledBoxHeight));
        return new int[]{x, y};
    }

    private static void renderStats(DrawContext context, String mountName, Map<String, int[]> stats, int[] energyPool, Map<String, Integer> statColors, int outlineColor) {

        TextRenderer textRenderer = WTZClient.client().textRenderer;
        int screenWidth = WTZClient.client().getWindow().getScaledWidth();
        int screenHeight = WTZClient.client().getWindow().getScaledHeight();
        float scale = getScale();

        int nameColor = NAME_COLOR;
        if (outlineColor == MOUNTED_OUTLINE_COLOR) nameColor = NAME_ACTIVE_COLOR;
        else if (outlineColor == NOT_MOUNTED_OUTLINE_COLOR) nameColor = NAME_INACTIVE_COLOR;

        int maxLabelWidth = 0;
        int maxValueWidth = 0;
        for (Map.Entry<String, int[]> entry : stats.entrySet()) {
            maxLabelWidth = Math.max(maxLabelWidth, textRenderer.getWidth(entry.getKey()));
            maxValueWidth = Math.max(maxValueWidth, textRenderer.getWidth(entry.getValue()[0] + "/" + entry.getValue()[1]));
        }

        for (Map.Entry<String, int[]> entry : stats.entrySet()) {
            trackDelta(entry.getKey(), entry.getValue()[0]);
        }
        if (energyPool != null) {
            trackDelta("_energy", energyPool[0]);
        }

        int deltaReserved = textRenderer.getWidth(" (+000)");
        String energyDelta = getDeltaText("_energy");

        boolean hasEnergy = energyPool != null;
        String energyLabel = "Total Energy";
        String energyValue = hasEnergy ? energyPool[0] + "/" + energyPool[1] : "";

        if (hasEnergy) {
            maxLabelWidth = Math.max(maxLabelWidth, textRenderer.getWidth(energyLabel));
            maxValueWidth = Math.max(maxValueWidth, textRenderer.getWidth(energyValue));
        }

        int contentWidth = maxLabelWidth + GAP + maxValueWidth + deltaReserved;
        int nameWidth = mountName != null ? textRenderer.getWidth(mountName) : 0;
        contentWidth = Math.max(contentWidth, nameWidth);
        int boxWidth = contentWidth + PADDING * 2;

        int nameHeight = (mountName != null && !mountName.isEmpty()) ? LINE_HEIGHT : 0;
        int separatorHeight = LINE_HEIGHT;
        int energyHeight = hasEnergy ? LINE_HEIGHT + 4 : 0;
        int boxHeight = PADDING + nameHeight + separatorHeight + energyHeight + (stats.size() * LINE_HEIGHT) + PADDING;

        int scaledBoxWidth = (int) (boxWidth * scale);
        int scaledBoxHeight = (int) (boxHeight * scale);

        int[] pos = computeScreenPosition(screenWidth, screenHeight, scaledBoxWidth, scaledBoxHeight);
        int x = pos[0];
        int y = pos[1];

        
        lastOverlayX = x;
        lastOverlayY = y;
        lastOverlayScaledW = scaledBoxWidth;
        lastOverlayScaledH = scaledBoxHeight;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);

        
        int bg = getBgColor();
        context.fill(1, 0, boxWidth - 1, 1, bg);
        context.fill(0, 1, boxWidth, boxHeight - 1, bg);
        context.fill(1, boxHeight - 1, boxWidth - 1, boxHeight, bg);

        
        int textX = PADDING;
        int textY = PADDING;

        
        if (mountName != null && !mountName.isEmpty()) {
            int nameX = PADDING + (contentWidth - nameWidth) / 2;
            context.drawText(textRenderer, mountName, nameX, textY, textColor(nameColor), true);
            textY += LINE_HEIGHT;
        }

        
        textY += separatorHeight;

        if (hasEnergy) {
            float ratio = energyPool[1] > 0 ? (float) energyPool[0] / energyPool[1] : 0f;
            int energyColor = 0xFF000000 | ColorUtils.energyGradient(ratio);

            context.drawText(textRenderer, energyLabel, textX, textY, textColor(ENERGY_LABEL_COLOR), true);

            int valueX = textX + maxLabelWidth + GAP;
            context.drawText(textRenderer, energyValue, valueX, textY, textColor(energyColor), true);

            if (energyDelta != null) {
                int dx = valueX + textRenderer.getWidth(energyValue) + 3;
                context.drawText(textRenderer, energyDelta, dx, textY, textColor(getDeltaColor("_energy")), true);
            }
            textY += LINE_HEIGHT + 4;
        }

        for (String statName : MountUtils.STAT_ORDER) {
            int[] stat = stats.get(statName);
            if (stat == null) continue;

            int cur = stat[0];
            int cap = stat[1];
            boolean maxed = cur >= cap;
            float ratio = cap > 0 ? Math.min(1f, (float) cur / cap) : 0f;

            int serverColor = statColors.getOrDefault(statName, DEFAULT_STAT_COLOR);
            int progressColor = 0xFF000000 | ColorUtils.energyGradient(ratio);
            int capColor = CAP_COLOR;

            int labelColor = maxed ? dimColor(serverColor) : serverColor;
            int valueColor = maxed ? dimColor(progressColor) : progressColor;
            capColor = maxed ? dimColor(capColor) : capColor;

            context.drawText(textRenderer, statName, textX, textY, textColor(labelColor), true);

            String value = String.valueOf(cur);
            String max = "/" + cap;
            int valueX = textX + maxLabelWidth + GAP;

            context.drawText(textRenderer, value, valueX, textY, textColor(valueColor), true);
            int maxX = valueX + textRenderer.getWidth(value);
            context.drawText(textRenderer, max, maxX, textY, textColor(capColor), true);

            String dt = getDeltaText(statName);
            if (dt != null) {
                int dx = maxX + textRenderer.getWidth(max) + 3;
                context.drawText(textRenderer, dt, dx, textY, textColor(getDeltaColor(statName)), true);
            }

            textY += LINE_HEIGHT;
        }

        context.getMatrices().popMatrix();
    }

    private static boolean isSameMount(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        ParsedMount pa = parseAll(a);
        ParsedMount pb = parseAll(b);
        if (pa.stats().isEmpty() || pb.stats().isEmpty()) return false;
        if (!pa.stats().keySet().equals(pb.stats().keySet())) return false;
        for (Map.Entry<String, int[]> entry : pa.stats().entrySet()) {
            int[] other = pb.stats().get(entry.getKey());
            if (other == null) return false;
            int[] value = entry.getValue();
            if (value[0] != other[0] || value[1] != other[1]) return false;
        }
        int[] ea = pa.energyPool();
        int[] eb = pb.energyPool();
        if (ea == null || eb == null) return ea == eb;
        return ea[0] == eb[0] && ea[1] == eb[1];
    }

    private static boolean isMountItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && !parseAll(stack).stats().isEmpty();
    }

    private static boolean cannotUseTrackedSlot(int slot) {
        return (slot < 0 || slot > 8) && slot != 40;
    }

    public record ParsedMount(Map<String, int[]> stats, int[] energyPool, Map<String, Integer> statColors) {
    }

    private static class Delta {
        private int amount;
        private long time;

        private Delta(int amount, long time) {
            this.amount = amount;
            this.time = time;
        }
    }
}
