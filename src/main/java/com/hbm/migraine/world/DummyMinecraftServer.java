package com.hbm.migraine.world;

import com.hbm.main.MainRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldSettings;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;

public class DummyMinecraftServer extends MinecraftServer {

	public DummyMinecraftServer(MinecraftServer reset) {
		super(new File(MainRegistry.configHbmDir, "data"), Proxy.NO_PROXY);
		mcServer = reset;
		this.func_152361_a(new DummyServerConfigurationManager(this));
	}

	@Override
	protected boolean startServer() throws IOException {
		return false;
	}

	@Override
	public boolean canStructuresSpawn() {
		return false;
	}

	@Override
	public WorldSettings.GameType getGameType() {
		return null;
	}

	@Override
	public EnumDifficulty func_147135_j() {
		return null;
	}

	@Override
	public boolean isHardcore() {
		return false;
	}

	@Override
	public int getOpPermissionLevel() {
		return 0;
	}

	@Override
	public boolean func_152363_m() {
		return false;
	}

	@Override
	public boolean isDedicatedServer() {
		return false;
	}

	@Override
	public boolean isCommandBlockEnabled() {
		return false;
	}

	@Override
	public String shareToLAN(WorldSettings.GameType p_71206_1_, boolean p_71206_2_) {
		return null;
	}

	@Override
	public void tick(){}

	@Override
	public void updateTimeLightAndEntities(){}
}
