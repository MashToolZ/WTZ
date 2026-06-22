package xyz.mashtoolz.wtz.features.version;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.util.ChatHelper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class VersionUpdateNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-VersionUpdateNotifier");
    private static final String MODRINTH_PROJECT_SLUG = "wynntoolz";
    private static final String MODRINTH_PROJECT_URL = "https://modrinth.com/mod/" + MODRINTH_PROJECT_SLUG;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static volatile boolean checkedThisSession = false;
    private static volatile boolean checkInFlight = false;

    private VersionUpdateNotifier() {
    }

    public static void checkOnce() {
        if (checkedThisSession || checkInFlight) return;
        checkInFlight = true;

        CompletableFuture.runAsync(() -> {
            try {
                Optional<VersionInfo> latest = fetchLatestCompatibleVersion();
                checkedThisSession = true;
                latest.ifPresent(VersionUpdateNotifier::notifyIfNewer);
            } catch (Exception e) {
                LOGGER.debug("Failed to check Modrinth for WynnToolZ updates.", e);
            } finally {
                checkInFlight = false;
            }
        });
    }

    private static Optional<VersionInfo> fetchLatestCompatibleVersion() throws Exception {
        String currentMinecraft = modVersion("minecraft").orElse(null);
        if (currentMinecraft == null) return Optional.empty();

        URI uri = URI.create("https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version"
                + "?loaders=" + encodeJsonArray("fabric")
                + "&game_versions=" + encodeJsonArray(currentMinecraft)
                + "&include_changelog=false");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "WynnToolZ/" + installedVersion() + " (https://modrinth.com/mod/wynntoolz)")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.debug("Modrinth update check returned status {}: {}", response.statusCode(), response.body());
            return Optional.empty();
        }

        JsonElement root = JsonParser.parseString(response.body());
        if (!root.isJsonArray()) return Optional.empty();

        List<VersionInfo> releases = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!"release".equals(stringValue(object, "version_type"))) continue;
            if (!"listed".equals(stringValue(object, "status"))) continue;
            String versionNumber = stringValue(object, "version_number");
            String versionId = stringValue(object, "id");
            if (versionNumber == null) continue;
            releases.add(new VersionInfo(versionNumber, versionId));
        }

        return releases.stream()
                .max((a, b) -> compareVersions(a.versionNumber(), b.versionNumber()));
    }

    private static void notifyIfNewer(VersionInfo latest) {
        String installed = installedVersion();
        if (compareVersions(latest.versionNumber(), installed) <= 0) return;

        WTZClient.client().execute(() -> {
            ChatHelper.sendBlank();
            ChatHelper.send(updateMessage(latest));
        });
    }

    private static MutableText updateMessage(VersionInfo latest) {
        String url = latest.versionId() == null
                ? MODRINTH_PROJECT_URL
                : MODRINTH_PROJECT_URL + "/version/" + latest.versionId();
        Style linkStyle = Style.EMPTY
                .withColor(TextColor.fromRgb(0x1BD96A))
                .withUnderline(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal(url)));
        return Text.literal("Update ")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xD1D1D1)))
                .append(Text.literal("v" + latest.versionNumber()).setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(0xD1D1D1))
                        .withBold(true)))
                .append(Text.literal(" is available ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xD1D1D1))))
                .append(Text.literal("[").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x404040))))
                .append(Text.literal("Modrinth").setStyle(linkStyle))
                .append(Text.literal("]").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x404040))));
    }

    private static String installedVersion() {
        return modVersion(WTZClient.MOD_ID).or(() -> modVersion("wtz")).orElse("unknown");
    }

    private static Optional<String> modVersion(String modId) {
        return FabricLoader.getInstance()
                .getModContainer(modId)
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString());
    }

    private static String encodeJsonArray(String value) {
        return URLEncoder.encode("[\"" + value + "\"]", StandardCharsets.UTF_8);
    }

    private static String stringValue(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return null;
        String value = object.get(key).getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    private static int compareVersions(String left, String right) {
        List<Integer> leftParts = numericParts(left);
        List<Integer> rightParts = numericParts(right);
        int max = Math.max(leftParts.size(), rightParts.size());
        for (int i = 0; i < max; i++) {
            int leftPart = i < leftParts.size() ? leftParts.get(i) : 0;
            int rightPart = i < rightParts.size() ? rightParts.get(i) : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        return left.compareToIgnoreCase(right);
    }

    private static List<Integer> numericParts(String version) {
        List<Integer> parts = new ArrayList<>();
        String[] rawParts = version.split("[^0-9]+");
        for (String rawPart : rawParts) {
            if (rawPart.isEmpty()) continue;
            try {
                parts.add(Integer.parseInt(rawPart));
            } catch (NumberFormatException ignored) {
                parts.add(0);
            }
        }
        return parts;
    }

    private record VersionInfo(String versionNumber, String versionId) {
    }
}
