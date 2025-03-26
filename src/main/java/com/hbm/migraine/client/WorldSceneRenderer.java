package com.hbm.migraine.client;

import com.hbm.migraine.world.TrackedDummyWorld;
import com.hbm.util.CoordinatePacker;
import com.hbm.util.Vector4i;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import java.util.HashSet;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.*;

public abstract class WorldSceneRenderer {

	// you have to place blocks in the world before use
	public final TrackedDummyWorld world;
	// the Blocks which this renderer needs to render
	public final HashSet<Long> renderedBlocks = new HashSet<>();
	public final HashSet<Long> renderOpaqueBlocks = new HashSet<>();
	public final HashSet<Entity> rendererEntities = new HashSet<>();
	public final EffectRenderer rendererEffect;
	private Consumer<WorldSceneRenderer> beforeRender;
	private Consumer<WorldSceneRenderer> onRender;
	private Consumer<MovingObjectPosition> onLookingAt;
	private Consumer<WorldSceneRenderer> onPostBlockRendered;
	private MovingObjectPosition lastTraceResult;
	private final Vector3f eyePos = new Vector3f(0, 0, -10f);
	private final Vector3f lookAt = new Vector3f(0, 0, 0);
	private final Vector3f worldUp = new Vector3f(0, 1, 0);
	private final Vector3f prevEyePos = new Vector3f(0, 0, -10f);
	protected Vector4i rect = new Vector4i();
	private boolean renderAllFaces = false;
	private final RenderBlocks bufferBuilder = new RenderBlocks();
	public final ClientFakePlayer camera;

	public WorldSceneRenderer(TrackedDummyWorld world, ClientFakePlayer player) {
		this.world = world;
		this.camera = player;
		this.rendererEffect = new EffectRenderer(world, Minecraft.getMinecraft().renderEngine);
	}

	public WorldSceneRenderer setBeforeWorldRender(Consumer<WorldSceneRenderer> callback) {
		this.beforeRender = callback;
		return this;
	}

	public WorldSceneRenderer setPostBlockRender(Consumer<WorldSceneRenderer> callback) {
		this.onPostBlockRendered = callback;
		return this;
	}

	public WorldSceneRenderer setOnWorldRender(Consumer<WorldSceneRenderer> callback) {
		this.onRender = callback;
		return this;
	}

	public WorldSceneRenderer addRenderedBlocks(HashSet<Long> blocks) {
		if (blocks != null) {
			this.renderedBlocks.addAll(blocks);
		}
		return this;
	}

	public WorldSceneRenderer addRenderEntities(HashSet<Entity> entities){
		if (entities != null){
			this.rendererEntities.addAll(entities);
		}
		return this;
	}

	public WorldSceneRenderer setOnLookingAt(Consumer<MovingObjectPosition> onLookingAt) {
		this.onLookingAt = onLookingAt;
		return this;
	}

	public void setRenderAllFaces(boolean renderAllFaces) {
		this.renderAllFaces = renderAllFaces;
	}

	public MovingObjectPosition getLastTraceResult() {
		return lastTraceResult;
	}

	public WorldSceneRenderer resetRenders() {
		renderedBlocks.clear();
		renderOpaqueBlocks.clear();
		rendererEntities.clear();
		return this;
	}

	/**
	 * Renders scene on given coordinates with given width and height, and RGB background color Note that this will
	 * ignore any transformations applied currently to projection/view matrix, so specified coordinates are scaled MC
	 * gui coordinates. It will return matrices of projection and view in previous state after rendering
	 */
	public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks) {

		rect.set(x, y, width, height);
		// setupCamera
		setupCamera();

		// render TrackedDummyWorld
		drawWorld(partialTicks);

		// check lookingAt
		this.lastTraceResult = null;
		if (onLookingAt != null && isInsideRect(mouseX, mouseY)) {
			Vector3f lookVec = unProject(rect, eyePos, lookAt, mouseX, mouseY);
			MovingObjectPosition result = rayTrace(lookVec);
			if (result != null) {
				this.lastTraceResult = result;
				onLookingAt.accept(result);
			}
		}

		resetCamera();

		prevEyePos.set(eyePos);
	}

	public Vector3f getEyePos() {
		return eyePos;
	}

	public Vector3f getLookAt() {
		return lookAt;
	}

	public Vector3f getWorldUp() {
		return worldUp;
	}

	public void setCameraLookAt(Vector3f eyePos, Vector3f lookAt, Vector3f worldUp) {
		this.eyePos.set(eyePos);
		this.lookAt.set(lookAt);
		this.worldUp.set(worldUp);
	}

	public void setCameraLookAt(Vector3f lookAt, double radius, double rotationYaw, double rotationPitch) {
		this.lookAt.set(lookAt);

		double radYaw = Math.toRadians(rotationYaw % 360.0F);
		double radPitch = Math.toRadians(rotationPitch % 360.0F);

		eyePos.x = (float) (Math.cos(radYaw) * Math.cos(radPitch) * radius);
		eyePos.y = (float) (Math.sin(radPitch) * radius) + camera.getEyeHeight();
		eyePos.z = (float) (Math.sin(radYaw) * Math.cos(radPitch) * radius);
		Vector3f.add(eyePos, lookAt, eyePos);

		camera.setPosition(eyePos.x, eyePos.y, eyePos.z);
		camera.prevRotationPitch = camera.rotationPitch;
		camera.prevRotationYaw = camera.rotationYaw;
		camera.rotationYaw = (float) -rotationYaw % 360.0F;
		camera.rotationPitch = (float) rotationPitch % 360.0F;

	}

	public void setupCamera() {
		int x = rect.x;
		int y = rect.y;
		int width = rect.z;
		int height = rect.w;

		Minecraft mc = Minecraft.getMinecraft();
		glPushAttrib(GL_ALL_ATTRIB_BITS);
		glPushClientAttrib(GL_ALL_CLIENT_ATTRIB_BITS);
		mc.entityRenderer.disableLightmap(0);
		glDisable(GL_LIGHTING);
		glEnable(GL_DEPTH_TEST);
		glEnable(GL_BLEND);

		glViewport(x, y, width, height);

		clearView(x, y, width, height);

		glMatrixMode(GL_PROJECTION);
		glPushMatrix();
		glLoadIdentity();

		float aspectRatio = width / (height * 1.0f);
		GLU.gluPerspective(60.0f, aspectRatio, 0.1f, 10000.0f);

		// setup modelview matrix
		glMatrixMode(GL_MODELVIEW);
		glPushMatrix();
		glLoadIdentity();
		GLU.gluLookAt(eyePos.x, eyePos.y, eyePos.z, lookAt.x, lookAt.y, lookAt.z, worldUp.x, worldUp.y, worldUp.z);

		ActiveRenderInfo.updateRenderInfo(camera, false);
	}


	protected void clearView(int x, int y, int width, int height) {
		glClear(GL_DEPTH_BUFFER_BIT);
	}

	public void resetCamera() {
		// reset viewport
		Minecraft minecraft = Minecraft.getMinecraft();
		glViewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);

		// reset modelview matrix
		glMatrixMode(GL_MODELVIEW);
		glPopMatrix();

		// reset projection matrix
		glMatrixMode(GL_PROJECTION);
		glPopMatrix();

		glMatrixMode(GL_MODELVIEW);

		// reset attributes
		glPopClientAttrib();
		glPopAttrib();
	}

	protected void drawWorld(float partialTicks) {
		if (beforeRender != null) {
			beforeRender.accept(this);
		}

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

		if (onPostBlockRendered != null) {
			onPostBlockRendered.accept(this);
		}

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

		GL11.glPushMatrix();
		GL11.glTranslated(RenderManager.renderPosX, RenderManager.renderPosY, RenderManager.renderPosZ);
		for (Entity entity : rendererEntities) {
			RenderManager.instance.renderEntitySimple(entity, partialTicks);
		}
		GL11.glPopMatrix();

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
//		GL11.glPushMatrix();

		ForgeHooksClient.setRenderPass(0);
//		RenderHelper.disableStandardItemLighting();
//		Minecraft.getMinecraft().entityRenderer.disableLightmap((double)partialTicks);
//		GL11.glMatrixMode(GL11.GL_MODELVIEW);
//		GL11.glPopMatrix();
//		GL11.glPushMatrix();
//
//		GL11.glMatrixMode(GL11.GL_MODELVIEW);
//		GL11.glPopMatrix();
		GL11.glPushMatrix();

		GL11.glTranslated(RenderManager.renderPosX, RenderManager.renderPosY, RenderManager.renderPosZ);

		Minecraft.getMinecraft().entityRenderer.enableLightmap((double)partialTicks);
		rendererEffect.renderLitParticles(camera, partialTicks);
		RenderHelper.disableStandardItemLighting();

		rendererEffect.renderParticles(camera, partialTicks);
		Minecraft.getMinecraft().entityRenderer.disableLightmap((double)partialTicks);

		GL11.glPopMatrix();
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_CULL_FACE);
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
			if (onRender != null) {
				onRender.accept(this);
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

	public boolean isInsideRect(int x, int y) {
		return x > rect.x && x < rect.x + rect.z && y > rect.y && y < rect.y + rect.w;
	}

	public MovingObjectPosition rayTrace(Vector3f lookVec) {
		Vec3 startPos = Vec3.createVectorHelper(this.eyePos.x, this.eyePos.y, this.eyePos.z);
		lookVec.scale(100); // range: 100 Blocks
		Vec3 endPos = Vec3.createVectorHelper(
			(lookVec.x + startPos.xCoord),
			(lookVec.y + startPos.yCoord),
			(lookVec.z + startPos.zCoord));
		return this.world.rayTraceBlocksWithTargetMap(startPos, endPos, new HashSet<>(world.blockMap.keySet()));
	}


	private static final Matrix4f ROT = new Matrix4f();
	private static final Vector3f MUT_3F = new Vector3f();
	private static final Vector3f RESULT = new Vector3f();

	// actually lets not use joml
	public static Vector3f unProject(Vector4i rect, Vector3f eyePos, Vector3f lookAt, int mouseX, int mouseY) {
		int width = rect.z;
		int height = rect.w;

		double aspectRatio = (double) width / (double) height;
		double fov = Math.toRadians(30);

		double a = -((double) (mouseX - rect.x) / width - 0.5) * 2;
		double b = -((double) (height - (mouseY - rect.y)) / height - 0.5) * 2;
		double tanf = Math.tan(fov);

		Vector3f.sub(eyePos, lookAt, MUT_3F);
		float yawn = (float) Math.atan2(MUT_3F.x, -MUT_3F.z);
		float pitch = (float) Math.atan2(MUT_3F.y, Math.sqrt(MUT_3F.x * MUT_3F.x + MUT_3F.z * MUT_3F.z));

		ROT.setIdentity();
		rotateY(ROT, yawn);
		rotateX(ROT, pitch);

		MUT_3F.set(0, 0, 1);
		transformPosition(ROT, MUT_3F, RESULT);
		MUT_3F.set(1, 0, 0);
		transformPosition(ROT, MUT_3F, MUT_3F);

		RESULT.x += MUT_3F.x * tanf * aspectRatio * a;
		RESULT.y += MUT_3F.y * tanf * aspectRatio * a;
		RESULT.z += MUT_3F.z * tanf * aspectRatio * a;

		MUT_3F.set(0, 1, 0);
		transformPosition(ROT, MUT_3F, MUT_3F);

		RESULT.x += MUT_3F.x * tanf * b;
		RESULT.y += MUT_3F.y * tanf * b;
		RESULT.z += MUT_3F.z * tanf * b;

		return normalize(RESULT);
	}

	private static void rotateX(Matrix4f mat, float angle) {
		Matrix4f.rotate(angle, new Vector3f(1, 0, 0), mat, mat);
	}

	private static void rotateY(Matrix4f mat, float angle) {
		Matrix4f.rotate(angle, new Vector3f(0, -1, 0), mat, mat);
	}

	private static void transformPosition(Matrix4f mat, Vector3f vec, Vector3f dest) {
		Vector4f temp = new Vector4f(vec.x, vec.y, vec.z, 1.0f); // Homogeneous coordinate w = 1
		Matrix4f.transform(mat, temp, temp);
		dest.set(temp.x, temp.y, temp.z); // Extract only x, y, z
	}

	public static Vector3f normalize(Vector3f vec) {
		float length = (float) Math.sqrt(vec.x * vec.x + vec.y * vec.y + vec.z * vec.z);
		if (length != 0) {
			vec.x /= length;
			vec.y /= length;
			vec.z /= length;
		}
		return vec;
	}
}
