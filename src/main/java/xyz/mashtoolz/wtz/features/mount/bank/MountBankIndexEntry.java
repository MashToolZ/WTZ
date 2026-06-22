package xyz.mashtoolz.wtz.features.mount.bank;

import java.util.Map;

public record MountBankIndexEntry(
        int page,
        int slot,
        String itemName,
        String mountType,
        String skin,
        String primarySkin,
        String secondarySkin,
        String potentialText,
        double potential,
        int totalEnergy,
        int maxEnergy,
        Map<String, MountBankStat> stats
) {
}
