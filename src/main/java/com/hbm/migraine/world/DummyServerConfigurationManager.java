package com.hbm.migraine.world;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

public class DummyServerConfigurationManager extends ServerConfigurationManager {
	public DummyServerConfigurationManager(MinecraftServer p_i1500_1_) {
		super(p_i1500_1_);
	}

	@Override
	public int getEntityViewDistance(){
		return 100000;
	}

	@Override
	public EntityPlayerMP func_152612_a(String p_152612_1_){ return null; }
}
