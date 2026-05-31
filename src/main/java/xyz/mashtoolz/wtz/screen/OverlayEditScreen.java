package xyz.mashtoolz.wtz.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import xyz.mashtoolz.wtz.client.WTZClient;
import xyz.mashtoolz.wtz.features.mount.MountStatsOverlay;

public class OverlayEditScreen extends Screen {

    private static final int BACKDROP_COLOR = 0x66000000;

    public OverlayEditScreen() {
        super(Text.translatable("screen.wtz.overlay_edit"));
        MountStatsOverlay.setEditMode(true);
    }

    public static void toggle() {
        if (WTZClient.client().currentScreen instanceof OverlayEditScreen) {
            WTZClient.client().setScreen(null);
        } else if (WTZClient.client().currentScreen == null) {
            WTZClient.client().setScreen(new OverlayEditScreen());
        }
    }

    @Override
    public void removed() {
        MountStatsOverlay.setEditMode(false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, BACKDROP_COLOR);
        MountStatsOverlay.renderEditOverlay(context);
        MountStatsOverlay.updateEditInteraction(mouseX, mouseY);
        MountStatsOverlay.renderEditAffordance(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void applyBlur(DrawContext context) {
    }

    @Override
    public void blur() {
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubleClick) {
        if (MountStatsOverlay.onScreenMouseClicked(click.x(), click.y(), click.button())) {
            return true;
        }
        return super.mouseClicked(click, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
