package xyz.mashtoolz.wtz.util;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class WTZPaths {

    private WTZPaths() {
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("wtz");
    }

    public static Path configFile(String name) {
        return configDir().resolve(name);
    }
}
