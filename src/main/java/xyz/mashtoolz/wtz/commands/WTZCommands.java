package xyz.mashtoolz.wtz.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import xyz.mashtoolz.wtz.auth.LinkStateStore;
import xyz.mashtoolz.wtz.features.mount.enclosure.BreedingResultReporter;
import xyz.mashtoolz.wtz.features.mount.skin.MountSkinReporter;
import xyz.mashtoolz.wtz.net.Endpoints;
import xyz.mashtoolz.wtz.relay.RelayManager;
import xyz.mashtoolz.wtz.util.ChatHelper;
import xyz.mashtoolz.wtz.util.UrlHelper;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class WTZCommands {

    private static final LinkStateStore STORE = new LinkStateStore();
    private static boolean registered = false;

    private WTZCommands() {
    }

    public static void register() {
        if (registered) return;
        registered = true;

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("wtz")
                        .then(literal("link")
                                .executes(context -> {
                                    openAuthLink();
                                    return 1;
                                })
                                .then(argument("token", StringArgumentType.word())
                                        .executes(context -> {
                                            String token = StringArgumentType.getString(context, "token");
                                            linkWithToken(token);
                                            return 1;
                                        })
                                )
                        )
                        .then(literal("unlink")
                                .executes(context -> {
                                    unlink();
                                    return 1;
                                })
                        )
        ));
    }

    private static void linkWithToken(String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (STORE.rejectsToken(token)) {
            ChatHelper.sendError("Invalid WynnToolZ token.");
            return;
        }

        STORE.saveToken(token);
        if (!STORE.hasToken()) {
            ChatHelper.sendError("Failed to save Mount API token.");
            return;
        }

        ChatHelper.sendSuccess("Mount API connected");
        RelayManager.getInstance().refreshConnection();
        MountSkinReporter.flushQueuedSkins();
        BreedingResultReporter.flushQueuedReports();
    }

    private static void unlink() {
        if (!STORE.hasToken()) {
            ChatHelper.sendInfo("No WynnToolZ API token saved.");
            return;
        }

        STORE.clearToken();
        ChatHelper.sendSuccess("WynnToolZ API token removed.");
        RelayManager.getInstance().refreshConnection();
    }

    private static void openAuthLink() {
        UrlHelper.openOrSendFallback(
                Endpoints.AUTH_LINK_URL,
                "Automatically opened WynnToolZ in your browser",
                "If nothing opened, use this link instead:"
        );
    }
}
