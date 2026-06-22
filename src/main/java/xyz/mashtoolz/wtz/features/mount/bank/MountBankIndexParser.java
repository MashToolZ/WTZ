package xyz.mashtoolz.wtz.features.mount.bank;

import net.minecraft.item.ItemStack;
import xyz.mashtoolz.wtz.features.mount.MountUtils;
import xyz.mashtoolz.wtz.features.mount.stats.MountStatsOverlay;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MountBankIndexParser {
    private MountBankIndexParser() {
    }

    public static Optional<MountBankIndexEntry> parse(ItemStack stack, int page, int slot) {
        if (!MountUtils.isMountSkinItem(stack)) return Optional.empty();

        Map<String, int[]> rawStats = MountUtils.parseFullStats(stack);
        if (rawStats.isEmpty()) return Optional.empty();

        String skin = MountUtils.extractSkin(stack);
        MountUtils.MountSkinParts skinParts = MountUtils.parseSkinParts(skin);
        if (skin == null || skinParts == null) return Optional.empty();

        MountUtils.MountPotential potential = MountUtils.derivePotential(rawStats);
        if (potential == null) {
            potential = MountUtils.extractPotential(stack);
        }
        if (potential == null) return Optional.empty();

        MountStatsOverlay.ParsedMount parsed = MountStatsOverlay.parseAll(stack);
        int[] energyPool = parsed.energyPool();
        Map<String, MountBankStat> stats = new LinkedHashMap<>();
        for (String statName : MountUtils.STAT_ORDER) {
            int[] values = rawStats.get(statName);
            if (values != null) {
                stats.put(statName, MountBankStat.from(values));
            }
        }

        return Optional.of(new MountBankIndexEntry(
                page,
                slot,
                stack.getName().getString(),
                MountUtils.extractMountType(stack),
                skin,
                skinParts.primary(),
                skinParts.secondary(),
                potential.formatted(),
                potential.value(),
                energyPool == null ? 0 : energyPool[0],
                energyPool == null ? 0 : energyPool[1],
                Collections.unmodifiableMap(new LinkedHashMap<>(stats))
        ));
    }
}
