package com.hbm.migraine.client;

import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/** @author kellen */
public class RecordMovingSound extends MovingSound {

	private final Entity entity;
	private float field_147669_l = 0.0F;
	private final float xOffset;
	private final float yOffset;
	private final float zOffset;

	public RecordMovingSound(Entity entity, ResourceLocation resourceLocation, float volume, float pitch, float x, float y, float z)
	{
		super(resourceLocation);
		this.entity = entity;
		this.repeat = false;
		this.field_147666_i = AttenuationType.LINEAR;
		this.field_147665_h = 0;
		this.xOffset = x;
		this.yOffset = y;
		this.zOffset = z;
	}

	/**
	 * Updates the JList with a new model.
	 */
	public void update()
	{
		this.xPosF = (float)(this.entity.posX + xOffset);
		this.yPosF = (float)(this.entity.posY + yOffset);
		this.zPosF = (float)(this.entity.posZ + zOffset);
	}
}
