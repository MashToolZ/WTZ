package xyz.mashtoolz.wtz.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.regex.Pattern;

public final class LinkStateStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("WTZ-LinkKeyStore");
    private static final Gson GSON = new Gson();
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
    );
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^wtz_[A-Za-z0-9_-]{20,}$");

    private final Path stateFilePath;

    public LinkStateStore() {
        this(FabricLoader.getInstance().getConfigDir().resolve("wtz").resolve("link.json"));
    }

    public LinkStateStore(Path stateFilePath) {
        this.stateFilePath = stateFilePath;
    }

    public Optional<String> loadPairingKey() {
        return readStateValue("pairingKey", UUID_PATTERN);
    }

    public Optional<String> loadToken() {
        return readStateValue("token", TOKEN_PATTERN);
    }

    public void savePairingKey(String key) {
        String normalized = key == null ? "" : key.trim();
        if (!isValidUuid(normalized))
            throw new IllegalArgumentException("Pairing key must be a UUID.");

        JsonObject state = readState();
        state.addProperty("pairingKey", normalized);
        writeState(state);
    }

    public void saveToken(String token) {
        String normalized = token == null ? "" : token.trim();
        if (!acceptsToken(normalized))
            throw new IllegalArgumentException("Token must start with wtz_.");

        JsonObject state = readState();
        state.addProperty("token", normalized);
        writeState(state);
    }

    public void clearPairingKey() {
        JsonObject state = readState();
        state.remove("pairingKey");
        writeStateOrDeleteIfEmpty(state);
    }

    public void clearToken() {
        JsonObject state = readState();
        state.remove("token");
        writeStateOrDeleteIfEmpty(state);
    }

    public boolean hasToken() {
        return loadToken().isPresent();
    }

    public boolean acceptsToken(String value) {
        return value != null && TOKEN_PATTERN.matcher(value.trim()).matches();
    }

    private Optional<String> readStateValue(String key, Pattern pattern) {
        JsonObject state = readState();
        if (!state.has(key)) return Optional.empty();
        try {
            String value = state.get(key).getAsString().trim();
            if (!pattern.matcher(value).matches()) {
                LOGGER.warn("Ignoring invalid {} format in {}", key, stateFilePath);
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (Exception e) {
            LOGGER.warn("Ignoring invalid {} value in {}", key, stateFilePath);
            return Optional.empty();
        }
    }

    private JsonObject readState() {
        if (!Files.exists(stateFilePath)) return new JsonObject();

        try {
            JsonObject state = GSON.fromJson(Files.readString(stateFilePath, StandardCharsets.UTF_8), JsonObject.class);
            return state == null ? new JsonObject() : state;
        } catch (Exception e) {
            LOGGER.warn("Failed to read link state file {}", stateFilePath, e);
            return new JsonObject();
        }
    }

    private void writeStateOrDeleteIfEmpty(JsonObject state) {
        if (state.entrySet().isEmpty()) {
            try {
                Files.deleteIfExists(stateFilePath);
            } catch (IOException e) {
                LOGGER.warn("Failed to delete empty link state file {}", stateFilePath, e);
            }
            return;
        }
        writeState(state);
    }

    private void writeState(JsonObject state) {
        try {
            Path parent = stateFilePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(
                    stateFilePath,
                    GSON.toJson(state) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException e) {
            LOGGER.warn("Failed to save link state file {}", stateFilePath, e);
        }
    }

    private boolean isValidUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }
}
