package com.hbm.migraine.client;

import com.hbm.migraine.world.TrackedDummyWorld;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class ImmediateWorldSceneRenderer extends WorldSceneRenderer {

	public ImmediateWorldSceneRenderer(TrackedDummyWorld world, ClientFakePlayer player) {
		super(world, player);
	}

	@Override
	public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks) {
		Minecraft mc = Minecraft.getMinecraft();
		ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
		// compute window size from scaled width & height
		int windowWidth = getScaledX(mc, resolution, width);
		int windowHeight = getScaledY(mc, resolution, height);
		// translate gui coordinates to window's ones (y is inverted)
		int windowX = getScaledX(mc, resolution, x);
		int windowY = mc.displayHeight - getScaledY(mc, resolution, y) - windowHeight;
		int windowMouseX = getScaledX(mc, resolution, mouseX);
		int windowMouseY = mc.displayHeight - getScaledY(mc, resolution, mouseY);
		super.render(windowX, windowY, windowWidth, windowHeight, windowMouseX, windowMouseY, partialTicks);
	}

	private int getScaledX(Minecraft mc, ScaledResolution res, int x) {
		return (int) (x / (res.getScaledWidth() * 1.0) * mc.displayWidth);
	}

	private int getScaledY(Minecraft mc, ScaledResolution res, int y) {
		return (int) (y / (res.getScaledHeight() * 1.0) * mc.displayHeight);
	}

	@Override
	protected void clearView(int x, int y, int width, int height) {
		GL11.glEnable(GL11.GL_SCISSOR_TEST);
		GL11.glScissor(x, y, width, height);
		super.clearView(x, y, width, height);
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
	}
}
