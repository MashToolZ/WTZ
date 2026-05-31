package xyz.mashtoolz.wtz.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import xyz.mashtoolz.wtz.config.WTZConfig;
import xyz.mashtoolz.wtz.config.WTZConfig.TTSVoice;
import xyz.mashtoolz.wtz.commands.WTZCommands;
import xyz.mashtoolz.wtz.features.bankfilter.BankFilterRegistry;
import xyz.mashtoolz.wtz.features.lookline.LookLineRenderer;
import xyz.mashtoolz.wtz.features.qol.QualityOfLife;
import xyz.mashtoolz.wtz.features.tts.ShoutTTS;
import xyz.mashtoolz.wtz.features.mount.MountCamera;
import xyz.mashtoolz.wtz.features.mount.MountHelper;
import xyz.mashtoolz.wtz.features.mount.MountStatsOverlay;
import xyz.mashtoolz.wtz.features.mount.MountStatsUpdater;
import xyz.mashtoolz.wtz.features.mount.MountManager;
import xyz.mashtoolz.wtz.features.mount.MountSkinReporter;
import xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListManager;
import xyz.mashtoolz.wtz.features.shoppinglist.ShoppingListRenderer;
import xyz.mashtoolz.wtz.relay.LocalBrowserBridge;
import xyz.mashtoolz.wtz.relay.RelayManager;

import java.util.concurrent.CompletableFuture;

public class WTZClient implements ClientModInitializer {

    public static final String MOD_ID = "wtz";

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    public static final WTZConfig CONFIG = WTZConfig.register();

    private static TTSVoice lastVoice = null;

    @Override
    public void onInitializeClient() {
        BankFilterRegistry.registerDefaults();
        WTZKeybinds.register();
        ShoppingListManager.getInstance().init();
        ShoppingListRenderer.getInstance().init();
        LookLineRenderer.register();
        MountSkinReporter.register();
        MountHelper.register();
        MountStatsOverlay.register();
        MountStatsUpdater.register();
        QualityOfLife.register();
        WTZCommands.register();

        lastVoice = CONFIG.shoutTTSVoice;
        WTZConfig.holder().registerSaveListener((holder, config) -> {
            ShoutTTS.onTokenChanged();
            if (lastVoice != null && lastVoice != config.shoutTTSVoice) {
                ShoutTTS.previewVoice();
            }
            lastVoice = config.shoutTTSVoice;
            return net.minecraft.util.ActionResult.SUCCESS;
        });

        RelayManager.getInstance().init();
        LocalBrowserBridge.getInstance().start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LocalBrowserBridge.getInstance().stop();
            RelayManager.getInstance().shutdown();
        }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            MountCamera.getInstance().tickPerspective();
        });
    }

    public static void onWynncraftJoin() {
        CompletableFuture.runAsync(MountManager::fetchMountData);
        MountSkinReporter.onWynncraftJoin();
        ShoutTTS.onTokenChanged();
    }

    public static MinecraftClient client() {
        return CLIENT;
    }

    public static ClientPlayerEntity player() {
        return CLIENT.player;
    }
}
