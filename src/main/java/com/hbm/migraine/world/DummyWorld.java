package com.hbm.migraine.world;

import com.hbm.main.MainRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

import javax.annotation.Nonnull;

public class DummyWorld extends World {

	public static final WorldSettings DEFAULT_SETTINGS = new WorldSettings(
		1L,
		WorldSettings.GameType.CREATIVE, // SURVIVAL
		true,
		false,
		WorldType.DEFAULT);

	public final DummyWorldServer serverWorld;

	public DummyWorld(){
		super(new DummySaveHandler(), "MigraineWorld", DEFAULT_SETTINGS, new WorldProviderSurface(), new Profiler());
		this.provider.setDimension(MainRegistry.MigraineWorldId);
		int providerDim = this.provider.dimensionId;
		this.provider.worldObj = this;
		this.provider.setDimension(providerDim);
		this.chunkProvider = this.createChunkProvider();
		this.calculateInitialSkylight();
		this.calculateInitialWeatherBody();
		this.serverWorld = new DummyWorldServer(this, this.getSaveHandler(), "MigraineServer", MainRegistry.MigraineWorldId, DEFAULT_SETTINGS, new Profiler());
	}

	public void addPacket(long coord){}

	@Override
	public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {}

	@Override
	protected int func_152379_p(){
		return 0;
	}

	@Override
	public Entity getEntityByID(int p_73045_1_){
		return null;
	}

	@Nonnull
	@Override
	protected IChunkProvider createChunkProvider(){
		return new DummyChunkProvider(this);
	}

	@Override
	public BiomeGenBase getBiomeGenForCoords(final int p_72807_1_, final int p_72807_2_)
	{
		return BiomeGenBase.plains;
	}

	@Override
	protected boolean chunkExists(int x, int z){
		return chunkProvider.chunkExists(x, z);
	}

	@Override
	public boolean updateLightByType(EnumSkyBlock p_147463_1_, int p_147463_2_, int p_147463_3_, int p_147463_4_){
		return true;
	}
}
