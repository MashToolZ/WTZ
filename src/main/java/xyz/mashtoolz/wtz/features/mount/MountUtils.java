package xyz.mashtoolz.wtz.features.mount;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.util.ChatHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;

public class MountUtils {

    public static final List<String> STAT_ORDER = List.of(
            "Speed", "Acceleration", "Altitude", "Jump Height", "Energy",
            "Handling", "Toughness", "Boost", "Training"
    );

    public static boolean isMounted() {
        ClientPlayerEntity player = WTZClient.player();
        return player != null && player.hasVehicle() && !player.getVehicle().isRemoved();
    }

    





    public static Map<String, int[]> parseFullStats(ItemStack stack) {
        Map<String, int[]> stats = new LinkedHashMap<>();
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return stats;

        for (Text line : lore.lines()) {
            String str = line.getString();
            Matcher m = MountPatterns.STAT.matcher(str);
            if (m.find()) {
                stats.put(m.group(1), new int[]{
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)),
                        m.group(4) != null ? Integer.parseInt(m.group(4)) : -1
                });
            }
        }
        return stats;
    }

    



    public static String extractSkin(List<Text> lines) {
        for (Text line : lines) {
            List<Text> parts = line.getSiblings();
            for (int i = 0; i < parts.size() - 1; i++) {
                if (containsFont(parts.get(i), "tooltip/attribute/sprite")) {
                    String skinName = parts.get(i + 1).getString().trim();
                    if (!skinName.isEmpty()) return skinName;
                }
            }
        }
        return null;
    }

    public static boolean containsFont(Text text, String fontFragment) {
        if (text.getStyle().getFont().toString().contains(fontFragment)) return true;
        for (Text child : text.getSiblings()) {
            if (containsFont(child, fontFragment)) return true;
        }
        return false;
    }

    public static void copyHoveredStats() {
        Screen screen = WTZClient.client().currentScreen;
        if (!(screen instanceof HandledScreen<?> handled)) {
            ChatHelper.sendError("No inventory screen open");
            return;
        }

        Slot slot = handled.focusedSlot;
        if (slot == null || !slot.hasStack()) {
            ChatHelper.sendError("No item hovered");
            return;
        }

        Map<String, int[]> allStats = parseFullStats(slot.getStack());
        Map<String, int[]> stats = new LinkedHashMap<>();
        boolean hasPartialStats = false;
        for (Map.Entry<String, int[]> entry : allStats.entrySet()) {
            if (entry.getValue()[2] >= 0) {
                stats.put(entry.getKey(), entry.getValue());
            } else {
                hasPartialStats = true;
            }
        }

        if (stats.isEmpty()) {
            if (hasPartialStats) {
                ChatHelper.sendWarning("Hover a mount inside an enclosure to copy full stats");
            } else {
                ChatHelper.sendError("No mount stats found");
            }
            return;
        }

        StringJoiner joiner = new StringJoiner(",");
        for (String name : STAT_ORDER) {
            int[] val = stats.get(name);
            if (val == null) continue;
            joiner.add(name + "=" + val[0] + "/" + val[1] + "/" + val[2]);
        }

        WTZClient.client().keyboard.setClipboard(joiner.toString());
        ChatHelper.sendSuccess("Copied Mount Stats to clipboard");
    }
}



