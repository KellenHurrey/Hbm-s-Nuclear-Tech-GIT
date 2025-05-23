package com.hbm.migraine.world.server;

import com.hbm.main.MainRegistry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

/** @author kellen */
public class DummyServerConfigurationManager extends ServerConfigurationManager {
	public DummyServerConfigurationManager(MinecraftServer p_i1500_1_) {
		super(p_i1500_1_);
	}

	@Override
	public int getEntityViewDistance(){
		return 100000;
	}

	@Override
	public EntityPlayerMP func_152612_a(String p_152612_1_){
		MainRegistry.logger.debug("HERE: " + p_152612_1_);
		return null;
	}
}
