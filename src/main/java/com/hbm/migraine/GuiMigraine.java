package com.hbm.migraine;

import com.hbm.migraine.client.ClientFakePlayer;
import com.hbm.migraine.client.ImmediateWorldSceneRenderer;
import com.hbm.migraine.world.DummyWorld;
import com.hbm.migraine.world.TrackedDummyWorld;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;

import java.util.HashSet;
import java.util.UUID;

public class GuiMigraine extends GuiScreen {

	private ImmediateWorldSceneRenderer worldRenderer;

	public final ClientFakePlayer FAKE_PLAYER;

	protected static int guiMouseX, guiMouseY;
	private int ticks = 0;

	private boolean isPaused = false;

	private final MigraineInstructions instructions;

	public GuiMigraine(MigraineInstructions instruct){
		this.instructions = instruct;
		FAKE_PLAYER = new ClientFakePlayer(DummyWorld.INSTANCE, new GameProfile(UUID.randomUUID(), "Migraine"));
	}


	@Override
	public void initGui() {
		worldRenderer = new ImmediateWorldSceneRenderer(new TrackedDummyWorld(this.mc.getSoundHandler(), this.mc.thePlayer));
		worldRenderer.world.updateEntitiesForNEI();

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		FAKE_PLAYER.setWorld(worldRenderer.world);

		instructions.zoom = 1f;
		instructions.yaw = 50f;
		instructions.pitch = 20f;
		instructions.center = null;
	}

	// Gets called whenever the fuck it feels like it (fps)
	@Override
	public void drawScreen(int mouseX, int mouseY, float f){

		this.drawDefaultBackground();

		long now = System.currentTimeMillis();
		instructions.render(mouseX, mouseY, f, this.width, this.height);

		updateCamera();

		worldRenderer.render(0, 0, width, height, mouseX, mouseY);
	}

	// Gets called at a normal 20fps
	@Override
	public void updateScreen(){
		if (isPaused) return;

		worldRenderer.world.updateEntitiesForNEI();

		instructions.update(worldRenderer, ticks);

		ticks++;
	}



	private void updateCamera(){
		Vector3f size = worldRenderer.world.getSize();
		Vector3f minPos = worldRenderer.world.getMinPos();
		Vector3f center = instructions.center == null ? new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2) : instructions.center;

		worldRenderer.renderedBlocks.clear();
		worldRenderer.addRenderedBlocks(new HashSet<>(worldRenderer.world.blockMap.keySet()));

		float max = Math.max(Math.max(size.x, size.y), size.z);
		float baseZoom = (float) (3.5f * Math.sqrt(max)) / instructions.zoom;
		float sizeFactor = (float) (1.0f + Math.log(max) / Math.log(10));

		float zoom = baseZoom * sizeFactor / 1.5f;
		// ignore how yaw and pitch are reversed
		worldRenderer.setCameraLookAt(center, zoom, Math.toRadians(instructions.yaw), Math.toRadians(instructions.pitch));
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
