package com.hbm.migraine.client;

import com.hbm.main.MainRegistry;
import com.hbm.migraine.GuiMigraine;
import com.hbm.migraine.player.ClientFakePlayer;
import com.hbm.migraine.world.TrackedDummyWorld;
import com.hbm.util.BobMathUtil;
import com.hbm.util.CoordinatePacker;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.glu.Project;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashSet;

import static org.lwjgl.opengl.GL11.*;

/** @author kellen, @https://github.com/GTNewHorizons/BlockRenderer6343 */
public class WorldSceneRenderer {

	// The world
	public TrackedDummyWorld world;
	// the Blocks which this renderer needs to render
	public final HashSet<Long> renderedBlocks = new HashSet<>();
	public final HashSet<Long> renderOpaqueBlocks = new HashSet<>();
	public final HashSet<Entity> rendererEntities = new HashSet<>();
	public final EffectRenderer rendererEffect;
	private MovingObjectPosition lastTraceResult;
	private Vec3 centerOffset = Vec3.createVectorHelper(0, 0, 0);
	private Vec3 prevCenterOffset = Vec3.createVectorHelper(0, 0, 0);
	private int width = 0, height = 0;
	private boolean renderAllFaces = false;
	private final RenderBlocks bufferBuilder = new RenderBlocks();
	public final ClientFakePlayer camera;
	private double prevZoom = 1, zoom = 1;
	private boolean isometric = true;
	private Vec3 size, prevSize;
	private Vec3 min, prevMin;

	public WorldSceneRenderer(TrackedDummyWorld world, ClientFakePlayer player) {
		this.world = world;
		this.camera = player;
		this.rendererEffect = new EffectRenderer(world.clientWorld, Minecraft.getMinecraft().renderEngine);
	}

	public void setSize(Vec3 size){
		this.size = size;
		this.prevSize = size;
	}

	public WorldSceneRenderer resetRenders() {
		renderedBlocks.clear();
		renderedBlocks.addAll(world.blockMap.keySet());
		renderOpaqueBlocks.clear();
		rendererEntities.clear();
		rendererEntities.addAll(world.entities);
		return this;
	}

	/**
	 * Renders scene on given coordinates with given width and height, and RGB background color Note that this will
	 * ignore any transformations applied currently to projection/view matrix, so specified coordinates are scaled MC
	 * gui coordinates. It will return matrices of projection and view in previous state after rendering
	 */
	public String[] render(int width, int height, int mouseX, int mouseY, float partialTicks) {

		this.width = width;
		this.height = height;

		// setupCamera
		setupCamera(partialTicks);

		// render TrackedDummyWorld
		drawWorld(partialTicks);

		// get looking at block
		final ScaledResolution scaledresolution = new ScaledResolution(Minecraft.getMinecraft(), Minecraft.getMinecraft().displayWidth, Minecraft.getMinecraft().displayHeight);
		this.lastTraceResult = unProject(mouseX * scaledresolution.getScaleFactor(), mouseY * scaledresolution.getScaleFactor());

		resetCamera();

		// pos
		// localized name
		// registry name, meta
		if (lastTraceResult != null)
		{
			int posX = Math.round(lastTraceResult.blockX);
			int posY = Math.round(lastTraceResult.blockY);
			int posZ = Math.round(lastTraceResult.blockZ);
			Block block = world.getBlock(posX, posY, posZ);
			String[] lines = {"(" + posX + ", " + posY + ", " + posZ + ")", block.getLocalizedName(), "(" + GameRegistry.findUniqueIdentifierFor(block).toString() + ", meta: " + world.getBlockMetadata(posX, posY, posZ) + ")"};

			return lines;
		}

		return new String[]{};

	}

	public void setCamera(Vec3 centerOffset, double zoom, double rotationYaw, double rotationPitch, boolean isometric){
//		this.prevCenterOffset = Vec3.createVectorHelper(this.centerOffset.xCoord, this.centerOffset.yCoord, this.centerOffset.zCoord);
		this.centerOffset = Vec3.createVectorHelper(centerOffset.xCoord, centerOffset.yCoord, centerOffset.zCoord);
		camera.rotationYaw = (float) rotationYaw % 360.0F;
		camera.rotationPitch = (float) rotationPitch % 360.0F;
		this.zoom = zoom;
		this.isometric = isometric;
		camera.onUpdate();
	}

	public void setupCamera(float partialTicks) {

		glPushMatrix();

		Minecraft mc = Minecraft.getMinecraft();
		glPushAttrib(GL_ALL_ATTRIB_BITS);
		glPushClientAttrib(GL_ALL_CLIENT_ATTRIB_BITS);
		mc.entityRenderer.disableLightmap(0);
		glDisable(GL_LIGHTING);
		glEnable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);
		glClear(GL11.GL_DEPTH_BUFFER_BIT);
		glMatrixMode(GL11.GL_PROJECTION);
		glPushMatrix();
		glLoadIdentity();

		ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
		if (isometric)
			GL11.glOrtho(0.0D, scaledresolution.getScaledWidth_double(), scaledresolution.getScaledHeight_double(), 0.0D, 1000.0D, 3000.0D);
		else
			GLU.gluPerspective(40, (float)Minecraft.getMinecraft().displayWidth / (float)Minecraft.getMinecraft().displayHeight, 0.05f, 1024f);

		glMatrixMode(GL11.GL_MODELVIEW);
		glPushMatrix();
		glLoadIdentity();

		// Get interped size
		Vec3 size = this.size != null ? this.size : world.getSize();
		this.prevSize = this.prevSize != null ? this.prevSize : size;
		Vec3 sizeInterp = BobMathUtil.interpVec(size, this.prevSize, partialTicks);
		this.prevSize = size;

		Vec3 min = world.getMinPos();
		this.prevMin = this.prevMin != null ? this.prevMin : min;
		Vec3 minInterp = BobMathUtil.interpVec(min, this.prevMin, partialTicks);
		this.prevMin = min;

		glColor4f(1, 1, 1, 1);
		// rotate around 0,0,0
		if (isometric) {
			glTranslatef(0.0F, 0.0F, -2000.0F);

			double scale = -30;
			glTranslated(width / 2, height / 2, 400);
			glScaled(scale, scale, scale);
			glScaled(1, 1, 0.5);
		} else {
			double max = Math.max(sizeInterp.xCoord, Math.max(sizeInterp.yCoord, sizeInterp.zCoord));

			glTranslated(0, 0, -(max + 13 / scaledresolution.getScaleFactor()));
		}


		double yawInterp = BobMathUtil.interp(camera.rotationYaw, camera.prevRotationYaw, partialTicks);
		double pitchInterp = BobMathUtil.interp(camera.rotationPitch, camera.prevRotationPitch, partialTicks);
		glRotated((isometric ? 1 : -1) * pitchInterp, 1, 0, 0);
		glRotated((isometric ? 0 : 180) + yawInterp, 0, 1, 0);
		camera.prevRotationPitch = camera.rotationPitch;
		camera.prevRotationYaw = camera.rotationYaw;

		Vec3 centerOffsetInterp = BobMathUtil.interpVec(centerOffset, prevCenterOffset, partialTicks);
		this.prevCenterOffset = this.centerOffset;
		glTranslated((sizeInterp.xCoord - minInterp.xCoord) / -2, (sizeInterp.yCoord - minInterp.yCoord) / -2, (sizeInterp.zCoord - minInterp.zCoord) / -2);
		glTranslated(-centerOffsetInterp.xCoord, -centerOffsetInterp.yCoord, -centerOffsetInterp.zCoord);

		// Update camera position
		FloatBuffer matModelView = BufferUtils.createFloatBuffer(16);
		FloatBuffer matProjection = BufferUtils.createFloatBuffer(16);
		IntBuffer viewport = BufferUtils.createIntBuffer(16);
		FloatBuffer cameraPos = BufferUtils.createFloatBuffer(3);

		glGetFloat(GL_MODELVIEW_MATRIX, matModelView);
		glGetFloat(GL_PROJECTION_MATRIX, matProjection);
		glGetInteger(GL_VIEWPORT, viewport);
		GLU.gluUnProject((viewport.get(2) - viewport.get(0)) / 2 , (viewport.get(3) - viewport.get(1)) / 2, 0, matModelView, matProjection, viewport, cameraPos);

		camera.setPosition(cameraPos.get(0), cameraPos.get(1), cameraPos.get(2));
		camera.onUpdate();

		// Update render info
		ActiveRenderInfo.updateRenderInfo(camera, false);
	}

	public void resetCamera() {

		Minecraft minecraft = Minecraft.getMinecraft();
		glViewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);

		glMatrixMode(GL_MODELVIEW);
		glPopMatrix();

		// reset projection matrix
		glMatrixMode(GL_PROJECTION);
		glPopMatrix();

		glMatrixMode(GL_MODELVIEW);

		// reset attributes
		glPopClientAttrib();
		glPopAttrib();
		glPopMatrix();
	}

	protected void drawWorld(float partialTicks) {

		Minecraft mc = Minecraft.getMinecraft();
		glEnable(GL_CULL_FACE);
		glEnable(GL12.GL_RESCALE_NORMAL);
		RenderHelper.disableStandardItemLighting();
		mc.entityRenderer.disableLightmap(0);
		mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
		glDisable(GL_LIGHTING);
		glEnable(GL_TEXTURE_2D);
		glEnable(GL_ALPHA_TEST);

		Tessellator tessellator = Tessellator.instance;
		renderBlocks(tessellator, renderedBlocks, false);
		renderBlocks(tessellator, renderOpaqueBlocks, true);

		double d3 = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * (double)partialTicks;
		double d4 = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * (double)partialTicks;
		double d5 = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * (double)partialTicks;
		TileEntityRendererDispatcher.staticPlayerX = d3;
		TileEntityRendererDispatcher.staticPlayerY = d4;
		TileEntityRendererDispatcher.staticPlayerZ = d5;
		RenderManager.renderPosX = d3;
		RenderManager.renderPosY = d4;
		RenderManager.renderPosZ = d5;
		// render Entity
		RenderManager.instance.worldObj = world;

		glPushMatrix();
		glTranslated(RenderManager.renderPosX, RenderManager.renderPosY, RenderManager.renderPosZ);
		for (Entity entity : rendererEntities) {
			RenderManager.instance.renderEntitySimple(entity, partialTicks);
		}
		glPopMatrix();

		glEnable(GL_CULL_FACE);
		glEnable(GL12.GL_RESCALE_NORMAL);
		RenderHelper.disableStandardItemLighting();
		mc.entityRenderer.disableLightmap(0);
		mc.renderEngine.bindTexture(TextureMap.locationBlocksTexture);
		glDisable(GL_LIGHTING);
		glEnable(GL_TEXTURE_2D);
		glEnable(GL_ALPHA_TEST);

		RenderHelper.enableStandardItemLighting();
		glEnable(GL_LIGHTING);

		// render TESR
		TileEntityRendererDispatcher tesr = TileEntityRendererDispatcher.instance;
		for (int pass = 0; pass < 2; pass++) {
			ForgeHooksClient.setRenderPass(pass);
			int finalPass = pass;
			renderedBlocks.forEach(pos -> {
				int x = CoordinatePacker.unpackX(pos);
				int y = CoordinatePacker.unpackY(pos);
				int z = CoordinatePacker.unpackZ(pos);
				setDefaultPassRenderState(finalPass);
				TileEntity tile = world.getTileEntity(x, y, z);
				if (tile != null && tesr.hasSpecialRenderer(tile)) {
					if (tile.shouldRenderInPass(finalPass)) {
						tesr.renderTileEntityAt(tile, x, y, z, partialTicks);
					}
				}
			});
		}


		ForgeHooksClient.setRenderPass(-1);

		renderParticles(partialTicks);

		glEnable(GL_DEPTH_TEST);
		glDisable(GL_BLEND);
		glDepthMask(true);

	}

	private void renderParticles(float partialTicks) {
		ForgeHooksClient.setRenderPass(0);
		glPushMatrix();

		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glColor3f(1F, 1F, 1F);

		glTranslated(RenderManager.renderPosX, RenderManager.renderPosY, RenderManager.renderPosZ);

		world.clientWorld.setMinecraft();

		Minecraft.getMinecraft().entityRenderer.enableLightmap((double)partialTicks);
		rendererEffect.renderLitParticles(camera, partialTicks);
		RenderHelper.disableStandardItemLighting();

		rendererEffect.renderParticles(camera, partialTicks);
		Minecraft.getMinecraft().entityRenderer.disableLightmap((double)partialTicks);

		world.clientWorld.resetMinecraft();

		glPopMatrix();
		glDepthMask(false);
		glEnable(GL11.GL_CULL_FACE);
	}

	private void renderBlocks(Tessellator tessellator, HashSet<Long> blocksToRender, boolean transparent) {
		if (blocksToRender.isEmpty()) return;
		Minecraft mc = Minecraft.getMinecraft();
		final int savedAo = mc.gameSettings.ambientOcclusion;
		mc.gameSettings.ambientOcclusion = 0;
		tessellator.startDrawingQuads();
		try {
			if (transparent) {
				tessellator.setColorRGBA_F(1f, 1f, 1f, 0.3f);
				tessellator.disableColor();
			}
			tessellator.setBrightness(15 << 20 | 15 << 4);
			for (int i = 0; i < 2; i++) {
				for (long pos : blocksToRender) {
					int x = CoordinatePacker.unpackX(pos);
					int y = CoordinatePacker.unpackY(pos);
					int z = CoordinatePacker.unpackZ(pos);
					Block block = world.getBlock(x, y, z);
					if (block.equals(Blocks.air) || !block.canRenderInPass(i)) continue;

					bufferBuilder.blockAccess = world;
					bufferBuilder.setRenderBounds(0, 0, 0, 1, 1, 1);
					bufferBuilder.renderAllFaces = renderAllFaces;
					bufferBuilder.renderBlockByRenderType(block, x, y, z);
				}
			}
		} finally {
			mc.gameSettings.ambientOcclusion = savedAo;
			tessellator.draw();
			tessellator.setTranslation(0, 0, 0);
		}
	}

	public static void setDefaultPassRenderState(int pass) {
		glColor4f(1, 1, 1, 1);
		if (pass == 0) { // SOLID
			glEnable(GL_DEPTH_TEST);
			glDisable(GL_BLEND);
			glDepthMask(true);
		} else { // TRANSLUCENT
			glEnable(GL_BLEND);
			glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
			glDepthMask(false);
		}
	}

	public MovingObjectPosition unProject(int mouseX, int mouseY) {
		FloatBuffer modelview = BufferUtils.createFloatBuffer(16);
		FloatBuffer projection = BufferUtils.createFloatBuffer(16);
		IntBuffer viewport = BufferUtils.createIntBuffer(16);
		FloatBuffer worldCoords = BufferUtils.createFloatBuffer(3);

		// Get matrices
		glGetFloat(GL_MODELVIEW_MATRIX, modelview);
		glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
		glGetInteger(GL_VIEWPORT, viewport);

		// Convert from window space (Y is flipped in OpenGL)
		float winX = mouseX;
		float winY = viewport.get(3) - mouseY - 1;


		// Unproject near plane
		worldCoords.clear();
		boolean successNear = GLU.gluUnProject(winX, winY, 0, modelview, projection, viewport, worldCoords);
		if (!successNear) return null;

		Vec3 startRay = Vec3.createVectorHelper(worldCoords.get(0), worldCoords.get(1), worldCoords.get(2));


		// Unproject far plane
		worldCoords.clear();
		boolean successFar = GLU.gluUnProject(winX, winY, 1, modelview, projection, viewport, worldCoords);
		if (!successFar) return null;

		Vec3 endRay = Vec3.createVectorHelper(worldCoords.get(0), worldCoords.get(1), worldCoords.get(2));

		return this.world.rayTraceBlocksWithTargetMap(startRay, endRay, new HashSet<>(world.blockMap.keySet()));
	}

	// Leaving this here if needed, basicly the opposite of above. returns (x, y, depth)
	public Vec3 projectToScreen(Vec3 worldPos) {
		FloatBuffer modelview = BufferUtils.createFloatBuffer(16);
		FloatBuffer projection = BufferUtils.createFloatBuffer(16);
		IntBuffer viewport = BufferUtils.createIntBuffer(16);
		FloatBuffer screenCoords = BufferUtils.createFloatBuffer(3);

		// Get the current matrices
		glGetFloat(GL_MODELVIEW_MATRIX, modelview);
		glGetFloat(GL_PROJECTION_MATRIX, projection);
		glGetInteger(GL_VIEWPORT, viewport);

		// Project the world coordinates to screen space
		GLU.gluProject((float) worldPos.xCoord + 0.5f, (float) worldPos.yCoord + 0.5f, (float) worldPos.zCoord + 0.5f, modelview, projection, viewport, screenCoords);

		// Get screen coordinates
		float screenX = screenCoords.get(0);
		float screenY = viewport.get(3) - screenCoords.get(1); // Flip Y-axis
		float screenZ = screenCoords.get(2); // Depth value

		return Vec3.createVectorHelper(screenX, screenY, screenZ);
	}
}
