package com.hbm.migraine.world;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;

/** @author kellen */
public class EntityAIMoveToLocation extends EntityAIBase {
	private final EntityCreature entity;
	private double targetX, targetY, targetZ;
	private double speed;

	public EntityAIMoveToLocation(EntityCreature entity, double x, double y, double z, double speed) {
		this.entity = entity;
		this.targetX = x;
		this.targetY = y;
		this.targetZ = z;
		this.speed = speed;
	}

	@Override
	public boolean shouldExecute() {
		return true;
	}

	@Override
	public void startExecuting() {
		this.entity.getNavigator().tryMoveToXYZ(targetX, targetY, targetZ, speed);
	}

	public void updateTarget(double x, double y, double z, double speed){
		this.targetX = x;
		this.targetY = y;
		this.targetZ = z;
		this.speed = speed;
	}
}
