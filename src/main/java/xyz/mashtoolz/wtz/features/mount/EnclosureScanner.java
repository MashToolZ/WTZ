package xyz.mashtoolz.wtz.features.mount;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.client.network.ClientPlayerEntity;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.relay.RelayManager;
import xyz.mashtoolz.wtz.enums.Enclosure;
import xyz.mashtoolz.wtz.enums.GUI;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class EnclosureScanner {

    private static final int[] MOUNT_SLOTS = {9, 18, 27, 36, 45};
    private static final int FEED_SLOTS_PER_ROW = 7;
    private static final int FEED_OFFSET = 2;

    private static final long DEBOUNCE_MS = 500;

    private static final ScheduledExecutorService debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "WTZ-Enclosure-Debounce");
        t.setDaemon(true);
        return t;
    });
    private static volatile ScheduledFuture<?> pendingScan;

    private static String lastSentJson = "";

    public static void onScreenClose(HandledScreen<?> screen) {
        if (!GUI.ENCLOSURE.is(screen)) return;
        ScheduledFuture<?> prev = pendingScan;
        if (prev != null) prev.cancel(false);
        lastSentJson = "";
        scan(screen);
    }

    public static void onSlotChanged(HandledScreen<?> screen) {
        if (!GUI.ENCLOSURE.is(screen)) return;
        scheduleScan(screen);
    }

    public static void onSlotsUpdated(HandledScreen<?> screen) {
        if (!GUI.ENCLOSURE.is(screen)) return;
        lastSentJson = "";
        scheduleScan(screen);
    }

    private static void scheduleScan(HandledScreen<?> screen) {
        ScheduledFuture<?> prev = pendingScan;
        if (prev != null) prev.cancel(false);
        pendingScan = debounceExecutor.schedule(() ->
                        WTZClient.client().execute(() -> scan(screen)),
                DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private static void scan(HandledScreen<?> screen) {
        ScreenHandler handler = screen.getScreenHandler();

        JsonArray mounts = new JsonArray();

        for (int mountSlot : MOUNT_SLOTS) {
            if (mountSlot >= handler.slots.size()) continue;
            ItemStack mountStack = handler.getSlot(mountSlot).getStack();

            if (mountStack == null || mountStack.isEmpty()) {
                mounts.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            }

            String slotName = stripUnicode(mountStack.getName().getString());
            if (slotName.toLowerCase().contains("drag a mount into")) {
                mounts.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            }

            JsonObject mount = parseMountData(mountStack);

            JsonArray feedItems = new JsonArray();
            for (int f = 0; f < FEED_SLOTS_PER_ROW; f++) {
                int feedSlot = mountSlot + FEED_OFFSET + f;
                if (feedSlot >= handler.slots.size()) break;
                ItemStack feedStack = handler.getSlot(feedSlot).getStack();
                if (feedStack == null || feedStack.isEmpty()) continue;

                JsonObject feed = parseFeedSlotData(feedStack);
                if (feed == null) continue;
                feedItems.add(feed);
            }
            mount.add("feedSlots", feedItems);

            mounts.add(mount);
        }

        JsonObject data = new JsonObject();
        data.add("mounts", mounts);

        String location = getClosestEnclosureLocation();
        if (location != null) {
            data.addProperty("location", location);
        }

        String json = data.toString();
        if (json.equals(lastSentJson)) return;
        lastSentJson = json;

        RelayManager.getInstance().sendAppMessage("wtz.enclosure", data);
    }

    private static JsonObject parseMaterialData(ItemStack stack, String name) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        for (Text line : lore.lines()) {
            Matcher profM = MountPatterns.PROFESSION_LEVEL.matcher(line.getString());
            if (profM.find()) {
                JsonObject mat = new JsonObject();
                String[] words = name.split(" ");
                mat.addProperty("type", words[words.length - 1]);
                mat.addProperty("level", Integer.parseInt(profM.group(2)));
                return mat;
            }
        }
        return null;
    }

    private static JsonObject parseFeedSlotData(ItemStack stack) {
        String feedName = stripUnicode(stack.getName().getString());
        if (feedName.isEmpty()) return null;

        if (isMountLikeItem(stack)) {
            JsonObject mount = parseMountData(stack);
            mount.addProperty("kind", "mount");
            return mount;
        }

        JsonObject mat = parseMaterialData(stack, feedName);
        if (mat == null) return null;
        mat.addProperty("kind", "material");
        return mat;
    }

    private static boolean isMountLikeItem(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return false;

        List<Text> lines = lore.lines();
        if (MountUtils.extractSkin(lines) != null) return true;

        for (Text line : lines) {
            String str = line.getString();
            if (MountPatterns.ENERGY.matcher(str).find()) return true;
            if (MountPatterns.POTENTIAL.matcher(str).find()) return true;
            if (MountPatterns.STATUS.matcher(str).find()) return true;
            if (MountPatterns.STAT.matcher(str).find()) return true;
        }
        return false;
    }

    private static String getClosestEnclosureLocation() {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return null;
        Enclosure enc = Enclosure.closest(player.getX(), player.getZ());
        return enc != null ? enc.getDisplayName() : null;
    }

    private static String stripUnicode(String s) {
        return s.replaceAll("[^\\x20-\\x7E]", "").trim();
    }

    private static JsonObject parseMountData(ItemStack stack) {
        JsonObject mount = new JsonObject();
        mount.addProperty("name", stripUnicode(stack.getName().getString()));

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return mount;

        List<Text> lines = lore.lines();
        for (Text line : lines) {
            String str = line.getString();

            
            Matcher energyM = MountPatterns.ENERGY.matcher(str);
            if (energyM.find()) {
                JsonArray energyBar = new JsonArray();
                energyBar.add(Integer.parseInt(energyM.group(1)));
                energyBar.add(Integer.parseInt(energyM.group(2)));
                mount.add("energyBar", energyBar);
                continue;
            }

            
            Matcher potM = MountPatterns.POTENTIAL.matcher(str);
            if (potM.find()) {
                mount.addProperty("potential", potM.group(1));
                continue;
            }

            
            Matcher statusM = MountPatterns.STATUS.matcher(str);
            if (statusM.find()) {
                String raw = statusM.group(1);
                if (raw.startsWith("Feeding")) {
                    mount.addProperty("status", "FEEDING");
                    mount.addProperty("timer", statusM.group(2));
                } else if (raw.startsWith("Breeding")) {
                    mount.addProperty("status", "BREEDING");
                    mount.addProperty("timer", statusM.group(3));
                } else {
                    mount.addProperty("status", "IDLE");
                }
                continue;
            }

            
            Matcher m = MountPatterns.STAT.matcher(str);
            if (m.find()) {
                JsonArray stat = new JsonArray();
                stat.add(Integer.parseInt(m.group(2)));
                stat.add(Integer.parseInt(m.group(3)));
                if (m.group(4) != null) stat.add(Integer.parseInt(m.group(4)));
                mount.add(m.group(1).toLowerCase().replace(" ", "_"), stat);
            }
        }

        
        String skin = MountUtils.extractSkin(lines);
        if (skin != null) mount.addProperty("skin", skin);

        return mount;
    }
} 
