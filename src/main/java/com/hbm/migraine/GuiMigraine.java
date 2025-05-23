package com.hbm.migraine;

import com.google.gson.JsonObject;
import com.hbm.migraine.player.ClientFakePlayer;
import com.hbm.migraine.client.WorldSceneRenderer;
import com.hbm.migraine.world.TrackedDummyWorld;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** @author kellen */
public class GuiMigraine extends GuiScreen {

	public WorldSceneRenderer worldRenderer;

	public boolean updating = false;

	public ClientFakePlayer FAKE_PLAYER;

	public int ticks = 0;
	public boolean isPaused = false;
	public int chapterNumber = 0;

	private final MigraineInstructions instructions;


	public float zoom = 1f;
	public float yaw = -45f;
	public float pitch = -30f;
	public boolean isometric = true;

	public static int debugMode = 0;

	public Vec3 camera = Vec3.createVectorHelper(0, 0, 0);
	public HashSet<JsonObject> active = new HashSet<>();
	public HashSet<MigraineDisplay> displays = new HashSet<>();


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
			worldRenderer = new WorldSceneRenderer(world, FAKE_PLAYER);

			FAKE_PLAYER.setWorld(worldRenderer.world);

			worldRenderer.world.fakePlayer = FAKE_PLAYER;

			chapterNumber = 0;

			active.clear();
			displays.clear();

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

		String[] lines = worldRenderer.render(width, height, mouseX, mouseY, isPaused ? 0 : f);

		// Render on top
		instructions.render(mouseX, mouseY, isPaused ? 0 : f, this.width, this.height, this);

		GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		RenderHelper.disableStandardItemLighting();
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		if (debugMode == 2){
			int x = mouseX - width / 2;
			int y = mouseY - height / 2;

			// mouse pos
			// fontRendererObj.drawString(x + ", " + y, width - fontRendererObj.getStringWidth(x + ", " + y) - 5, height - 15, 0xffffff);
			// tick num
			fontRendererObj.drawString(ticks + "", 5, height - 15, 0xffffff);
		}

		if (debugMode > 0) {
			for (int i = 0; i < lines.length; i++) {
				if (i != 2 || debugMode == 2)
					fontRendererObj.drawString(lines[i], width / 2 - fontRendererObj.getStringWidth(lines[i]) / 2, 5 + (fontRendererObj.FONT_HEIGHT + 3) * i, 0xffffff);
			}
		}
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
		worldRenderer.resetRenders();

		worldRenderer.setCamera(camera, zoom, yaw, pitch, isometric);
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
		if (debugMode == 2){
			if (keyCode == Keyboard.KEY_RIGHT)
				this.instructions.skip(ticks + 1, ticks, this);
			else if (keyCode == Keyboard.KEY_LEFT)
				this.instructions.skip(ticks - 1, ticks, this);
		}
		if (key == 'p'){
			debugMode = ++debugMode > 2 ? 0 : debugMode;
		}
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		this.instructions.onClick(mouseX, mouseY, button, this.width, this.height, this);
	}
}
