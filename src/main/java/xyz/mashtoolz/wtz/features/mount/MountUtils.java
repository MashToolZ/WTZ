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

import java.util.*;
import java.util.regex.Matcher;

public class MountUtils {

    public static final List<String> STAT_ORDER = List.of(
            "Speed", "Acceleration", "Altitude", "Jump Height", "Energy",
            "Handling", "Toughness", "Boost", "Training"
    );

    private static final List<String> MOUNT_SKIN_ITEM_KEYWORDS = List.of("Saddle", "Reins", "Harness");

    public static boolean isMounted() {
        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return false;
        var vehicle = player.getVehicle();
        return vehicle != null && !vehicle.isRemoved();
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

    public static boolean isMountSkinItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && isMountSkinItemName(stack.getName().getString());
    }

    public static boolean isMountSkinItemName(String name) {
        if (name == null) return false;
        String normalizedName = name.trim().toLowerCase(Locale.ROOT);
        for (String keyword : MOUNT_SKIN_ITEM_KEYWORDS) {
            if (normalizedName.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public static String extractMountType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return extractMountType(stack.getName().getString());
    }

    public static String extractMountType(String itemName) {
        if (itemName == null) return null;
        if (itemName.contains("Harness")) return "adasaur";
        if (itemName.contains("Saddle")) return "horse";
        if (itemName.contains("Reins")) return "wyvern";
        return null;
    }

    public static String extractSkin(ItemStack stack) {
        if (!isMountSkinItem(stack)) return null;
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;
        return extractSkin(lore.lines());
    }

    public static boolean hasMountSkin(ItemStack stack) {
        return extractSkin(stack) != null;
    }

    public static MountSkinParts parseSkinParts(String skin) {
        if (skin == null) return null;
        String[] parts = skin.split("-", 2);
        if (parts.length != 2) return null;

        String primary = parts[0].trim();
        String secondary = parts[1].trim();
        if (primary.isEmpty() || secondary.isEmpty()) return null;
        return new MountSkinParts(primary, secondary);
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

    public static MountPotential extractPotential(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        for (Text line : lore.lines()) {
            String str = line.getString();
            Matcher m = MountPatterns.POTENTIAL.matcher(str);
            if (m.find()) {
                String raw = m.group(1);
                String lower = raw.toLowerCase(Locale.ROOT);
                double value;
                if (lower.endsWith("k")) {
                    value = Double.parseDouble(lower.substring(0, lower.length() - 1)) * 1000;
                } else {
                    value = Double.parseDouble(lower.replace(",", "."));
                }
                return new MountPotential(formatPotential(raw), value);
            }
        }
        return null;
    }

    public static MountPotential derivePotential(Map<String, int[]> mountStats) {
        int totalMax = 0;
        boolean hasMax = false;
        for (int[] val : mountStats.values()) {
            if (val[2] > 0) {
                totalMax += val[2];
                hasMax = true;
            } else {
                totalMax += val[1];
            }
        }

        if (!hasMax || totalMax <= 0) return null;
        return new MountPotential(String.valueOf(totalMax), totalMax);
    }

    private static String formatPotential(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.endsWith("k")) {
            try {
                double val = Double.parseDouble(lower.substring(0, lower.length() - 1));
                long whole = Math.round(val * 1000);
                if (whole >= 1000 && whole % 1000 == 0) {
                    return (whole / 1000) + "k";
                } else if (whole >= 100 && whole % 100 == 0) {
                    return String.format(Locale.ROOT, "%.1fk", whole / 1000.0);
                }
                return raw;
            } catch (NumberFormatException e) {
                return raw;
            }
        }

        try {
            int val = Integer.parseInt(raw);
            if (val >= 1000) {
                double k = val / 1000.0;
                if (val % 1000 == 0) {
                    return (val / 1000) + "k";
                }
                return String.format(Locale.ROOT, "%.1fk", k);
            }
            return raw;
        } catch (NumberFormatException e) {
            return raw;
        }
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

    public record MountPotential(String formatted, double value) {
    }

    public record MountSkinParts(String primary, String secondary) {
    }
}
