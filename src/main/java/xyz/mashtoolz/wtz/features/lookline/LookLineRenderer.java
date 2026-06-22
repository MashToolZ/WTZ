package xyz.mashtoolz.wtz.features.lookline;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import xyz.mashtoolz.wtz.client.WTZClient;

public final class LookLineRenderer {

    private LookLineRenderer() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(LookLineRenderer::render);
    }
 
    private static void render(WorldRenderContext context) {
        if (!WTZClient.CONFIG.lookLineEnabled) return;

        if (WTZClient.client().options.getPerspective() != Perspective.THIRD_PERSON_BACK) return;

        ClientPlayerEntity player = WTZClient.player();
        if (player == null) return;

        Vec3d eyePos = player.getCameraPosVec(1.0f);
        Vec3d lookDir = player.getRotationVec(1.0f);
        int maxDist = WTZClient.CONFIG.lookLineMaxDistance;

        Vec3d endPos = eyePos.add(lookDir.multiply(maxDist));

        BlockHitResult hit = player.getEntityWorld().raycast(new RaycastContext(
                eyePos, endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        Vec3d lineEnd;
        if (hit.getType() == HitResult.Type.BLOCK) {
            lineEnd = hit.getPos();
        } else {
            lineEnd = endPos;
        }

        Vec3d camera = context.worldState().cameraRenderState.pos;

        float halfWidth = WTZClient.CONFIG.lookLineWidth / 2.0f;

        Vec3d lineVec = lineEnd.subtract(eyePos);
        if (lineVec.lengthSquared() < 1e-8) return;
        Vec3d lineNorm = lineVec.normalize();

        Vec3d right = lineNorm.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1e-8) {
            right = lineNorm.crossProduct(new Vec3d(1, 0, 0));
        }
        right = right.normalize().multiply(halfWidth);

        Vec3d up = lineNorm.crossProduct(right).normalize().multiply(halfWidth);

        int color = WTZClient.CONFIG.lookLineColor;

        MatrixStack matrices = context.matrices();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);

        org.joml.Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = context.consumers().getBuffer(RenderLayers.debugQuads());

        drawQuad(buffer, posMatrix, eyePos, lineEnd, right, color);
        drawQuad(buffer, posMatrix, eyePos, lineEnd, right.negate(), color);

        drawQuad(buffer, posMatrix, eyePos, lineEnd, up, color);
        drawQuad(buffer, posMatrix, eyePos, lineEnd, up.negate(), color);

        matrices.pop();
    }

    private static void drawQuad(VertexConsumer buffer, org.joml.Matrix4f matrix, Vec3d start, Vec3d end, Vec3d offset, int color) {
        buffer.vertex(matrix, (float) (start.x - offset.x), (float) (start.y - offset.y), (float) (start.z - offset.z)).color(color);
        buffer.vertex(matrix, (float) (start.x + offset.x), (float) (start.y + offset.y), (float) (start.z + offset.z)).color(color);
        buffer.vertex(matrix, (float) (end.x + offset.x), (float) (end.y + offset.y), (float) (end.z + offset.z)).color(color);
        buffer.vertex(matrix, (float) (end.x - offset.x), (float) (end.y - offset.y), (float) (end.z - offset.z)).color(color);
    }
}


