package com.hbm.migraine;

import com.hbm.lib.RefStrings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class MigraineBar {
	private static final ResourceLocation guiUtil =  new ResourceLocation(RefStrings.MODID + ":textures/gui/gui_utility.png");

	private TextureManager texman = Minecraft.getMinecraft().getTextureManager();

	private int x;
	private int y;
	private int width;
	private int height;
	private float[] chapters;

	private long colorBrighter = 0xFFCCCCCC;
	private long colorDarker = 0xFF7D7D7D;
	private long colorFrame = 0xFFA0A0A0;
	private long colorBg = 0xFF302E36;

	public MigraineBar(int x, int y, int width, float[] chapters){
		this.x = x;
		this.y = y;
		this.width = width + 18;
		this.height = 14;
		this.chapters = chapters;
	}

	public MigraineBar setColors(int brighter, int frame, int darker, int background) {
		this.colorBrighter = brighter;
		this.colorFrame = frame;
		this.colorDarker = darker;
		this.colorBg = background;
		return this;
	}

	public MigraineBar setColors(int[] colors) {
		return setColors(colors[0], colors[1], colors[2], colors[3]);
	}

	public void update(int w, int h, float progress, int mouseX, int mouseY){
		int posX = w / 2 + x;
		int posY = h + y;

		GL11.glDisable(GL12.GL_RESCALE_NORMAL);
		RenderHelper.disableStandardItemLighting();
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_DEPTH_TEST);

		this.drawRect(posX - 5, posY - 5, posX + width + 5, posY + height + 5, colorFrame);

		this.drawRect(posX - 5, posY - 5, posX - 4, posY + height + 4, colorBrighter);
		this.drawRect(posX - 5, posY - 5, posX + width + 4, posY - 4, colorBrighter);
		this.drawRect(posX + width + 2, posY - 2, posX + width + 3, posY + height + 3, colorBrighter);
		this.drawRect(posX - 2, posY + height + 2, posX + width + 3, posY + height + 3, colorBrighter);

		this.drawRect(posX - 3, posY - 3, posX - 2, posY + height + 2, colorDarker);
		this.drawRect(posX - 3, posY - 3, posX + width + 2, posY - 2, colorDarker);
		this.drawRect(posX + width + 4, posY - 4, posX + width + 5, posY + height + 5, colorDarker);
		this.drawRect(posX - 4, posY + height + 4, posX + width + 5, posY + height + 5, colorDarker);

		this.drawRect(posX - 2, posY - 2, posX + width + 2, posY + height + 2, colorBg);

		Minecraft.getMinecraft().getTextureManager().bindTexture(guiUtil);

		drawArrow(posX, posY, 50, 0, 10, 14, mouseX >= posX && mouseX <= posX + 9 && mouseY >= posY && mouseY <= posY + 14);
		drawArrow(posX + width - 10, posY, 40, 0, 10, 14, mouseX >= posX + width - 9 && mouseX <= posX + width && mouseY >= posY && mouseY <= posY + 14);
		drawBar(posX + 9, posY + 5, width - 18);

		for (float chapterPercent : chapters){
			int leftPixel = (int) (posX + 9 + ((width - 21) * chapterPercent));
			drawVertBar(leftPixel, posY + 7 - 5, 10, Math.abs(posY + 7 - mouseY) <= 5 && Math.abs(leftPixel + 1 - mouseX) <= 1);
		}
		int leftPixel = (int) (posX + 9 + ((width - 21) * progress));
		drawVertBar(leftPixel, posY + 7 - 7, 14, Math.abs(posY + 7 - mouseY) <= 7 && Math.abs(leftPixel + 1 - mouseX) <= 1);
	}

	private void drawVertBar(int posX, int posY, int height, boolean selected){
		this.drawRect(posX, posY, posX + 2, posY + 1, selected ? 0xFFF2BA7C : this.colorBrighter);
		this.drawRect(posX + 2, posY, posX + 3, posY + 1, selected ? 0xFFCA6C43 : this.colorFrame);
		this.drawRect(posX, posY + 1, posX + 1, posY + height - 1, selected ? 0xFFF2BA7C : this.colorBrighter);
		this.drawRect(posX + 1, posY + 1, posX + 2, posY + height - 1, selected ? 0xFFCA6C43 : this.colorFrame);
		this.drawRect(posX + 2, posY + 1, posX + 3, posY + height - 1, selected ? 0xFFA03217 : this.colorDarker);
		this.drawRect(posX, posY + height - 1, posX + 1, posY + height, selected ? 0xFFCA6C43 : this.colorFrame);
		this.drawRect(posX + 1, posY + height - 1, posX + 3, posY + height, selected ? 0xFFA03217 : this.colorDarker);
	}

	private void drawBar(int posX, int posY, int width){
		this.drawRect(posX, posY, posX + width, posY + 1, this.colorBrighter);
		this.drawRect(posX, posY + 1, posX + width, posY + 3, this.colorFrame);
		this.drawRect(posX, posY + 3, posX + width, posY + 4, this.colorDarker);
	}

	private void drawArrow(int posX, int posY, int sourceX, int sourceY, int sizeX, int sizeY, boolean selected) {
		this.drawTexturedModalRect(posX, posY, sourceX + 28 * 0, sourceY, sizeX, sizeY, this.colorBrighter);
		this.drawTexturedModalRect(posX, posY, sourceX + 28 * 1, sourceY, sizeX, sizeY, selected ? 0xFFCA6C43 : this.colorFrame);
		this.drawTexturedModalRect(posX, posY, sourceX + 28 * 2, sourceY, sizeX, sizeY, this.colorDarker);
		this.drawTexturedModalRect(posX, posY, sourceX + 28 * 3, sourceY, sizeX, sizeY, selected ? 0xFFCA6C43 : this.colorFrame);
	}

	private void drawRect(int minX, int minY, int maxX, int maxY, long color) {
		drawGradientRect(minX, minY, maxX, maxY, color, color);
	}

	private void drawGradientRect(int minX, int minY, int maxX, int maxY, long color1, long color2) {

		double zLevel = 300D;
		float a1 = (float) (color1 >> 24 & 255) / 255.0F;
		float r1 = (float) (color1 >> 16 & 255) / 255.0F;
		float g1 = (float) (color1 >> 8 & 255) / 255.0F;
		float b1 = (float) (color1 & 255) / 255.0F;
		float a2 = (float) (color2 >> 24 & 255) / 255.0F;
		float r2 = (float) (color2 >> 16 & 255) / 255.0F;
		float g2 = (float) (color2 >> 8 & 255) / 255.0F;
		float b2 = (float) (color2 & 255) / 255.0F;

		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_ALPHA_TEST);
		GL11.glShadeModel(GL11.GL_SMOOTH);
		OpenGlHelper.glBlendFunc(770, 771, 1, 0);

		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();
		tessellator.setColorRGBA_F(r1, g1, b1, a1);
		tessellator.addVertex((double) maxX, (double) minY, (double) zLevel);
		tessellator.addVertex((double) minX, (double) minY, (double) zLevel);
		tessellator.setColorRGBA_F(r2, g2, b2, a2);
		tessellator.addVertex((double) minX, (double) maxY, (double) zLevel);
		tessellator.addVertex((double) maxX, (double) maxY, (double) zLevel);
		tessellator.draw();

		GL11.glShadeModel(GL11.GL_FLAT);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_ALPHA_TEST);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}

	private void drawTexturedModalRect(int posX, int posY, int u, int v, int sizeX, int sizeY) {
		drawTexturedModalRect(posX, posY, u, v, sizeX, sizeY, 0xffffff);
	}

	private void drawTexturedModalRect(int posX, int posY, int u, int v, int sizeX, int sizeY, long color) {
		double zLevel = 300D;
		float a = (float) (color >> 24 & 255) / 255.0F;
		float r = (float) (color >> 16 & 255) / 255.0F;
		float g = (float) (color >> 8 & 255) / 255.0F;
		float b = (float) (color & 255) / 255.0F;
		float f = 0.00390625F;
		float f1 = 0.00390625F;
		Tessellator tessellator = Tessellator.instance;
		tessellator.startDrawingQuads();
		tessellator.setColorRGBA_F(r, g, b, a);
		tessellator.addVertexWithUV(posX + 0, posY + sizeY, zLevel, (u + 0) * f, (v + sizeY) * f1);
		tessellator.addVertexWithUV(posX + sizeX, posY + sizeY, zLevel, (u + sizeX) * f, (v + sizeY) * f1);
		tessellator.addVertexWithUV(posX + sizeX, posY + 0, zLevel, (u + sizeX) * f, (v + 0) * f1);
		tessellator.addVertexWithUV(posX + 0, posY + 0, zLevel, (u + 0) * f, (v + 0) * f);
		tessellator.draw();
	}
}
