package com.hbm.migraine;

import com.google.gson.JsonObject;
import com.hbm.migraine.client.ClientFakePlayer;
import com.hbm.migraine.client.ImmediateWorldSceneRenderer;
import com.hbm.migraine.world.TrackedDummyWorld;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.entity.RenderItem;
import org.lwjgl.util.vector.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class GuiMigraine extends GuiScreen {

	public ImmediateWorldSceneRenderer worldRenderer;

	public boolean updating = false;

	public ClientFakePlayer FAKE_PLAYER;

	public int ticks = 0;
	public boolean isPaused = false;
	public int chapterNumber = 0;

	private final MigraineInstructions instructions;


	public float zoom = 1f;
	public float yaw = 50f;
	public float pitch = 20f;

	public Vector3f center;

	public HashSet<JsonObject> active = new HashSet<>();

	public HashSet<MigraineDisplay> displays = new HashSet<>();

	public List<MigraineDisplay> seealso = new ArrayList<>();

	public MigraineDisplay title;


	public GuiMigraine(MigraineInstructions instruct){
		this.instructions = instruct;
	}


	public RenderItem getItemRenderer(){
		return itemRender;
	}

	public FontRenderer getFontRenderer(){
		return this.fontRendererObj;
	}

	private boolean first = true;

	@Override
	public void initGui() {
		if (first) {
			first = false;
			TrackedDummyWorld world = new TrackedDummyWorld(this.mc.getSoundHandler(), this.mc.thePlayer);
			FAKE_PLAYER = new ClientFakePlayer(world, new GameProfile(UUID.randomUUID(), "Migraine"));
			worldRenderer = new ImmediateWorldSceneRenderer(world, FAKE_PLAYER);

			FAKE_PLAYER.setWorld(worldRenderer.world);

			chapterNumber = 0;

			active.clear();
			displays.clear();
			seealso.clear();

			instructions.init(this);
		}
	}

	// Gets called whenever the fuck it feels like it (fps)
	@Override
	public void drawScreen(int mouseX, int mouseY, float f){

		this.drawDefaultBackground();

		// Don't render if we haven't set up yet
		if (ticks <= 0) return;

		updateCamera();

		worldRenderer.render(0, 0, width, height, mouseX, mouseY, isPaused ? 0 : f);

		// Render on top
		instructions.render(mouseX, mouseY, isPaused ? 0 : f, this.width, this.height, this);
	}

	// Gets called at a normal 20fps
	@Override
	public void updateScreen(){
		if (isPaused && ticks != 0) return;

		this.updating = true;
		worldRenderer.world.update();
		this.updating = false;

		instructions.update(this, ticks);

		ticks++;
	}

	public void updateCamera(){
		Vector3f size = worldRenderer.world.getSize();
		Vector3f minPos = worldRenderer.world.getMinPos();
		Vector3f center = this.center == null ? new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2) : this.center;

		worldRenderer.resetRenders().addRenderedBlocks(new HashSet<>(worldRenderer.world.blockMap.keySet())).addRenderEntities(worldRenderer.world.entities);

		float max = Math.max(Math.max(size.x, size.y), size.z);
		float baseZoom = (float) (3.5f * Math.sqrt(max)) / this.zoom;
		float sizeFactor = (float) (1.0f + Math.log(max) / Math.log(10));

		float zoom = baseZoom * sizeFactor / 1.5f;
		// ignore how yaw and pitch are reversed
		worldRenderer.setCameraLookAt(center, zoom, this.yaw, this.pitch);
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}

	// Probably should unload the world and invalidate the tile entities
	@Override
	public void onGuiClosed() {
		worldRenderer.world.unload();
	}

	@Override
	protected void keyTyped(char key, int keyCode){
		if (keyCode == 1 || keyCode == this.mc.gameSettings.keyBindInventory.getKeyCode())
		{
			this.mc.thePlayer.closeScreen();
		}
		if (keyCode == 32 || keyCode == this.mc.gameSettings.keyBindJump.getKeyCode()){
			this.isPaused = !this.isPaused;
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		this.instructions.onClick(mouseX, mouseY, button, this.width, this.height, this);
	}
}
