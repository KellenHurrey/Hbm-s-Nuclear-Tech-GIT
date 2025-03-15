package com.hbm.migraine;

import codechicken.lib.gui.GuiDraw;
import com.hbm.blocks.ModBlocks;
import com.hbm.migraine.client.ClientFakePlayer;
import com.hbm.migraine.client.ImmediateWorldSceneRenderer;
import com.hbm.migraine.client.WorldSceneRenderer;
import com.hbm.migraine.world.DummyWorld;
import com.hbm.migraine.world.TrackedDummyWorld;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import org.joml.Vector3f;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Collections;
import java.util.UUID;

public class GuiMigraine extends GuiScreen {

	private ImmediateWorldSceneRenderer worldRenderer;

	public static final ClientFakePlayer FAKE_PLAYER = new ClientFakePlayer(DummyWorld.INSTANCE, new GameProfile(UUID.randomUUID(), "Migraine"));

	protected static int guiMouseX, guiMouseY;
	private int ticks = 0;

	private final MigraineInstructions instructions;

	public GuiMigraine(MigraineInstructions instruct){
		this.instructions = instruct;
	}


	@Override
	public void initGui() {
		worldRenderer = new ImmediateWorldSceneRenderer(new TrackedDummyWorld());
		worldRenderer.world.updateEntitiesForNEI();

		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		FAKE_PLAYER.setWorld(worldRenderer.world);
		worldRenderer.world.unloadEntities(Collections.singletonList(FAKE_PLAYER));
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float f){
		this.drawDefaultBackground();

		instructions.update(worldRenderer, ticks);

		updateCamera();

		worldRenderer.render(0, 0, width, height, mouseX, mouseY);

		ticks++;
	}

	private void updateCamera(){
		Vector3f size = worldRenderer.world.getSize();
		Vector3f minPos = worldRenderer.world.getMinPos();
		Vector3f center = new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2);

		worldRenderer.renderedBlocks.clear();
		worldRenderer.addRenderedBlocks(worldRenderer.world.blockMap.keySet());

		float max = Math.max(Math.max(size.x, size.y), size.z);
		float baseZoom = (float) (3.5f * Math.sqrt(max));
		float sizeFactor = (float) (1.0f + Math.log(max) / Math.log(10));

		float zoom = baseZoom * sizeFactor / 1.5f;
		float rotationYaw = 20f;
		float rotationPitch = 50f;
		worldRenderer.setCameraLookAt(center, zoom, Math.toRadians(rotationPitch), Math.toRadians(rotationYaw));
	}
}
