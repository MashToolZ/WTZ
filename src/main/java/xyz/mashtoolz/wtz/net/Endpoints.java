package xyz.mashtoolz.wtz.net;

public final class Endpoints {
    private static final String DEFAULT_API_BASE_URL = "https://wynn.mashtoolz.xyz";
    private static final String DEFAULT_MOD_LINK_URL = "wss://wynn.mashtoolz.xyz/api/ws/mod-link";

    public static final String MOUNT_DATA_URL = apiBaseUrl() + "/api/mount/data";
    public static final String MOUNT_SUBMIT_URL = apiBaseUrl() + "/api/mount/submit";
    public static final String AUTH_LINK_URL = apiBaseUrl() + "/auth/discord";
    public static final String MOD_LINK_URL = modLinkUrl();

    private Endpoints() {
    }

    private static String apiBaseUrl() {
        String property = System.getProperty("wtz.apiBaseUrl");
        if (property != null && !property.isBlank())
            return stripTrailingSlash(property.trim());
        String env = System.getenv("WTZ_API_BASE_URL");
        if (env != null && !env.isBlank())
            return stripTrailingSlash(env.trim());
        return DEFAULT_API_BASE_URL;
    }

    private static String modLinkUrl() {
        String property = normalizeWebSocketUrl(System.getProperty("wtz.modLinkUrl"));
        if (property != null) return property;
        String env = normalizeWebSocketUrl(System.getenv("WTZ_MOD_LINK_URL"));
        if (env != null) return env;
        return DEFAULT_MOD_LINK_URL;
    }

    private static String normalizeWebSocketUrl(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (!normalized.startsWith("ws://") && !normalized.startsWith("wss://"))
            return null;
        return normalized;
    }

    private static String stripTrailingSlash(String value) {
        while (value.endsWith("/"))
            value = value.substring(0, value.length() - 1);
        return value;
    }
}
