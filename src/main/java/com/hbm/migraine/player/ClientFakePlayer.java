package com.hbm.migraine.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.stats.StatBase;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Session;
import net.minecraft.world.World;

/** @author kellen, @https://github.com/GTNewHorizons/BlockRenderer6343 */
public class ClientFakePlayer extends EntityPlayer {

	public final DummyEntityClientPlayerMP client;

	public ClientFakePlayer(World world, GameProfile name) {
		super(world, name);
		client = new DummyEntityClientPlayerMP(Minecraft.getMinecraft(), world, new Session("Migraine", "NotValid", "NotValid", "mojang"), new NetHandlerPlayClient(null, null, null), null);
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
		client.prevPosX = prevPosX;
		client.prevPosY = prevPosY;
		client.prevPosZ = prevPosZ;
		client.prevRotationPitch = prevRotationPitch;
		client.prevRotationYaw = prevRotationYaw;
		client.posX = posX;
		client.posY = posY;
		client.posZ = posZ;
		client.rotationPitch = rotationPitch;
		client.rotationYaw = rotationYaw;
		client.lastTickPosX = lastTickPosX;
		client.lastTickPosY = lastTickPosY;
		client.lastTickPosZ = lastTickPosZ;
		return;
	}

	@Override
	public void travelToDimension(int dim) {
		return;
	}
}
