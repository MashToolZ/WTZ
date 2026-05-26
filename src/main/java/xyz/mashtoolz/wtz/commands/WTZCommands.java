package xyz.mashtoolz.wtz.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.util.Util;
import xyz.mashtoolz.wtz.auth.LinkStateStore;
import xyz.mashtoolz.wtz.net.Endpoints;
import xyz.mashtoolz.wtz.relay.RelayManager;
import xyz.mashtoolz.wtz.util.ChatHelper;

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
        if (!STORE.acceptsToken(token)) {
            ChatHelper.sendError("Invalid WynnToolZ token.");
            return;
        }

        STORE.saveToken(token);
        ChatHelper.sendSuccess("WynnToolZ token saved.");
        RelayManager.getInstance().refreshConnection();
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
        try {
            Util.getOperatingSystem().open(Endpoints.AUTH_LINK_URL);
            ChatHelper.sendInfo("Opened WynnToolZ authentication in your browser. Copy the shown /wtz link command back into Minecraft.");
        } catch (Exception ignored) {
            ChatHelper.sendWarning("Open this URL to get your WynnToolZ API token: " + Endpoints.AUTH_LINK_URL);
        }
    }
}
