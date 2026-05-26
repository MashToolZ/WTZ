package xyz.mashtoolz.wtz.features.mount;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import xyz.mashtoolz.wtz.util.ColorUtils;
import xyz.mashtoolz.wtz.util.ChatHelper;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.config.WTZConfig;

import java.util.ArrayList;
import java.util.List;

public final class MountHelper {

    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static final double VISIBLE_RADIUS = 200.0;
    private static final double MIN_VISIBLE_DISTANCE = 1.5;
    private static final int LABEL_BACKGROUND_COLOR = 0x80000000;
    private static final int LIGHT = 0xF000F0;

    private static int tickCounter = 0;

    private MountHelper() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!WTZClient.CONFIG.mountHelperEnabled) return;

            tickCounter++;
            if (tickCounter % REFRESH_INTERVAL_TICKS == 0) {
                MountManager.refresh();
                MountManager.filter();
            }
        });

        WorldRenderEvents.END_MAIN.register(MountHelper::render);
    }

    public static void toggle() {
        boolean enabled = !WTZClient.CONFIG.mountHelperEnabled;
        WTZClient.CONFIG.mountHelperEnabled = enabled;
        WTZConfig.save();
        if (enabled) {
            tickCounter = REFRESH_INTERVAL_TICKS - 1;
            ChatHelper.sendSuccess("Mount Helper Enabled");
        } else {
            ChatHelper.sendError("Mount Helper Disabled");
        }
    }

    private static void render(WorldRenderContext context) {
        if (!WTZClient.CONFIG.mountHelperEnabled) return;

        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;

        List<Powerup> powerups = MountManager.getFilteredPowerups();
        if (powerups.isEmpty()) return;

        Vec3d playerPos = player.getEntityPos();
        Vec3d camera = context.worldState().cameraRenderState.pos;

        TextRenderer textRenderer = WTZClient.client().textRenderer;
        MatrixStack matrices = context.matrices();
        MountStatsOverlay.ParsedMount parsedMount = MountStatsOverlay.parseAll(MountStatsOverlay.getLastUsedItem());
        int spentEnergy = getSpentEnergy(parsedMount);
        double energyHeadroom = getEnergyHeadroom(parsedMount);

        List<Powerup> sorted = new ArrayList<>(powerups);
        sorted.sort((a, b) -> Double.compare(b.pos().distanceTo(playerPos), a.pos().distanceTo(playerPos)));

        for (Powerup powerup : sorted) {
            double distance = powerup.pos().distanceTo(playerPos);
            if (distance > VISIBLE_RADIUS || distance < MIN_VISIBLE_DISTANCE) continue;
            if (!hasLineOfSight(player, camera, powerup.pos())) continue;

            Label label = getLabel(powerup, spentEnergy, energyHeadroom);
            int alpha = powerup.isMaxed() ? maxedLabelAlpha() : 0xFF;
            renderLabel(context, matrices, textRenderer, camera, powerup.pos(), label, alpha);
        }
    }

    private static int maxedLabelAlpha() {
        return Math.round(WTZClient.CONFIG.mountHelperMaxedOpacity * 255.0f / 100.0f);
    }

    private static Label getLabel(Powerup powerup, int spentEnergy, double energyHeadroom) {
        if (isEnergyPowerup(powerup.name()) && spentEnergy >= 0) {
            int gain = (int) Math.round(Math.min(spentEnergy, energyHeadroom));
            int color = powerup.color();
            float scaleMultiplier = 1.0f;
            if (gain < energyHeadroom) {
                color = ColorUtils.energyGradient((float) (spentEnergy / energyHeadroom));
                scaleMultiplier = 0.75f;
            }
            return new Label(gain + "/" + Math.round(energyHeadroom) + " Energy", color, scaleMultiplier);
        }

        return new Label(powerup.name(), powerup.color(), 1.0f);
    }

    private static boolean isEnergyPowerup(String name) {
        return "Energy Boost".equals(name);
    }

    private static double getEnergyHeadroom(MountStatsOverlay.ParsedMount parsed) {
        int[] boost = parsed.stats().get("Boost");
        if (boost == null) return 50;
        double b = Math.max(1.0, boost[0]);
        return Math.round((30 + 7.5 * Math.log10(b)) * 10.0) / 10.0 - 2;
    }

    private static int getSpentEnergy(MountStatsOverlay.ParsedMount parsed) {
        if (parsed.energyPool() == null) return -1;
        return parsed.energyPool()[1] - parsed.energyPool()[0];
    }

    private static boolean hasLineOfSight(ClientPlayerEntity player, Vec3d cameraPos, Vec3d powerupPos) {
        Vec3d target = powerupPos.add(0, 1.0, 0);

        BlockHitResult hit = player.getEntityWorld().raycast(new RaycastContext(
                cameraPos,
                target,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hit.getType() == HitResult.Type.MISS) return true;
        return hit.getPos().squaredDistanceTo(target) < 0.25;
    }

    private static void renderLabel(WorldRenderContext context, MatrixStack matrices, TextRenderer textRenderer,
                                    Vec3d camera, Vec3d pos, Label label, int alpha) {
        matrices.push();

        double labelY = pos.y + 3.5;
        matrices.translate(pos.x - camera.x, labelY - camera.y, pos.z - camera.z);
        matrices.multiply(context.worldState().cameraRenderState.orientation);

        double distance = camera.distanceTo(new Vec3d(pos.x, labelY, pos.z));
        float scale = (float) (distance * 0.01 + 0.025) * WTZClient.CONFIG.mountHelperLabelScale * label.scaleMultiplier;
        matrices.scale(scale, -scale, scale);

        int width = textRenderer.getWidth(label.text);
        float x = -width / 2.0f;

        int textColor = ((alpha & 0xFF) << 24) | (label.color & 0xFFFFFF);
        VertexConsumerProvider consumers = context.consumers();
        org.joml.Matrix4f matrix = matrices.peek().getPositionMatrix();

        textRenderer.draw(label.text, x, 0, textColor, false,
                matrix, consumers, TextRenderer.TextLayerType.NORMAL, LABEL_BACKGROUND_COLOR, LIGHT);

        matrices.pop();
    }

    private record Label(String text, int color, float scaleMultiplier) {
    }
}
