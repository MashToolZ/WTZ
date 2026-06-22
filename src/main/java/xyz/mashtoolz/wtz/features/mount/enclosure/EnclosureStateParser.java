package xyz.mashtoolz.wtz.features.mount.enclosure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.enums.Enclosure;
import xyz.mashtoolz.wtz.features.mount.MountPatterns;
import xyz.mashtoolz.wtz.features.mount.MountUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

public final class EnclosureStateParser {

    private static final int[] MOUNT_SLOTS = {9, 18, 27, 36, 45};
    private static final int FEED_SLOTS_PER_ROW = 7;
    private static final int FEED_OFFSET = 2;

    private EnclosureStateParser() {
    }

    public static EnclosureState parse(ScreenHandler handler) {
        List<EnclosureState.Row> rows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < MOUNT_SLOTS.length; rowIndex++) {
            int mountSlot = MOUNT_SLOTS[rowIndex];
            EnclosureState.MountData mainMount = mountSlot < handler.slots.size()
                    ? parseMainMount(handler.getSlot(mountSlot).getStack())
                    : null;

            List<EnclosureState.QueueItem> queue = new ArrayList<>();
            if (mainMount != null) {
                for (int offset = 0; offset < FEED_SLOTS_PER_ROW; offset++) {
                    int feedSlot = mountSlot + FEED_OFFSET + offset;
                    if (feedSlot >= handler.slots.size()) break;
                    EnclosureState.QueueItem item = parseQueueItem(handler.getSlot(feedSlot).getStack());
                    if (item != null) queue.add(item);
                }
            }
            rows.add(new EnclosureState.Row(rowIndex, mountSlot, mainMount, List.copyOf(queue)));
        }
        return new EnclosureState(getClosestEnclosureLocation(), List.copyOf(rows));
    }

    private static EnclosureState.MountData parseMainMount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String slotName = stripUnicode(stack.getName().getString());
        if (slotName.toLowerCase().contains("drag a mount into")) return null;
        return new EnclosureState.MountData(parseMountData(stack), hasBreedingAlert(stack));
    }

    private static EnclosureState.QueueItem parseQueueItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String name = stripUnicode(stack.getName().getString());
        if (name.isEmpty()) return null;

        if (isMountLikeItem(stack)) {
            return new EnclosureState.QueueItem(EnclosureState.QueueKind.MOUNT, parseMountData(stack));
        }

        JsonObject material = parseMaterialData(stack, name);
        return material == null ? null
                : new EnclosureState.QueueItem(EnclosureState.QueueKind.MATERIAL, material);
    }

    private static JsonObject parseMaterialData(ItemStack stack, String name) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        for (Text line : lore.lines()) {
            Matcher matcher = MountPatterns.PROFESSION_LEVEL.matcher(line.getString());
            if (!matcher.find()) continue;

            JsonObject material = new JsonObject();
            String[] words = name.split(" ");
            material.addProperty("type", words[words.length - 1]);
            material.addProperty("level", Integer.parseInt(matcher.group(2)));
            return material;
        }
        return null;
    }

    private static boolean isMountLikeItem(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return false;
        if (MountUtils.hasMountSkin(stack)) return true;

        for (Text line : lore.lines()) {
            String text = line.getString();
            if (MountPatterns.ENERGY.matcher(text).find()
                    || MountPatterns.POTENTIAL.matcher(text).find()
                    || MountPatterns.STATUS.matcher(text).find()
                    || MountPatterns.STAT.matcher(text).find()) return true;
        }
        return false;
    }

    private static boolean hasBreedingAlert(ItemStack stack) {
        CustomModelDataComponent modelData = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
        return modelData != null && modelData.strings().contains("alert");
    }

    private static JsonObject parseMountData(ItemStack stack) {
        JsonObject mount = new JsonObject();
        mount.addProperty("name", stripUnicode(stack.getName().getString()));

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text line : lore.lines()) parseMountLoreLine(mount, line.getString());
        }

        String skin = MountUtils.extractSkin(stack);
        if (skin != null) {
            mount.addProperty("skin", skin);
            String[] parts = skin.split("-", 2);
            mount.addProperty("primarySkin", parts[0].trim());
            if (parts.length == 2) mount.addProperty("secondarySkin", parts[1].trim());
        }
        return mount;
    }

    private static void parseMountLoreLine(JsonObject mount, String text) {
        Matcher energy = MountPatterns.ENERGY.matcher(text);
        if (energy.find()) {
            JsonArray energyBar = new JsonArray();
            energyBar.add(Integer.parseInt(energy.group(1)));
            energyBar.add(Integer.parseInt(energy.group(2)));
            mount.add("energyBar", energyBar);
            return;
        }

        Matcher potential = MountPatterns.POTENTIAL.matcher(text);
        if (potential.find()) {
            mount.addProperty("potential", potential.group(1));
            return;
        }

        Matcher status = MountPatterns.STATUS.matcher(text);
        if (status.find()) {
            String raw = status.group(1);
            if (raw.startsWith("Feeding")) {
                mount.addProperty("status", "FEEDING");
                mount.addProperty("timer", status.group(2));
            } else if (raw.startsWith("Breeding")) {
                mount.addProperty("status", "BREEDING");
                mount.addProperty("timer", status.group(3));
            } else {
                mount.addProperty("status", "IDLE");
            }
            return;
        }

        Matcher stat = MountPatterns.STAT.matcher(text);
        if (stat.find()) {
            JsonArray values = new JsonArray();
            values.add(Integer.parseInt(stat.group(2)));
            values.add(Integer.parseInt(stat.group(3)));
            if (stat.group(4) != null) values.add(Integer.parseInt(stat.group(4)));
            mount.add(stat.group(1).toLowerCase().replace(" ", "_"), values);
        }
    }

    private static String getClosestEnclosureLocation() {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return null;
        Enclosure enclosure = Enclosure.closest(player.getX(), player.getZ());
        return enclosure != null ? enclosure.getDisplayName() : null;
    }

    private static String stripUnicode(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "").trim();
    }
}
