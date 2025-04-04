package com.hbm.migraine.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Session;
import net.minecraft.world.World;

public class DummyEntityClientPlayerMP extends EntityClientPlayerMP {
	public DummyEntityClientPlayerMP(Minecraft p_i45064_1_, World p_i45064_2_, Session p_i45064_3_, NetHandlerPlayClient p_i45064_4_, StatFileWriter p_i45064_5_) {
		super(p_i45064_1_, p_i45064_2_, p_i45064_3_, p_i45064_4_, p_i45064_5_);
	}

	@Override
	public void addChatMessage(IChatComponent message) {}

	@Override
	public boolean canCommandSenderUseCommand(int i, String s) {
		return false;
	}

	@Override
	public ChunkCoordinates getPlayerCoordinates() {
		return new ChunkCoordinates(0, 0, 0);
	}

	@Override
	public void addChatComponentMessage(IChatComponent message) {}

	@Override
	public void addStat(StatBase par1StatBase, int par2) {}

	@Override
	public void openGui(Object mod, int modGuiId, World world, int x, int y, int z) {}

	@Override
	public boolean isEntityInvulnerable() {
		return true;
	}

	@Override
	public boolean canAttackPlayer(EntityPlayer player) {
		return false;
	}

	@Override
	public void onDeath(DamageSource source) {
		return;
	}

	@Override
	public void onUpdate() {
		return;
	}

	@Override
	public void travelToDimension(int dim) {
		return;
	}
}
