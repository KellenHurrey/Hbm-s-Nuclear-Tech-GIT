package com.hbm.migraine.world;

import com.hbm.main.MainRegistry;
import com.hbm.migraine.player.ClientFakePlayer;
import com.hbm.migraine.world.client.DummyWorldClient;
import com.hbm.migraine.world.server.DummyWorldServer;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.*;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

import javax.annotation.Nonnull;

/** @author kellen, @https://github.com/GTNewHorizons/BlockRenderer6343 */
public class DummyWorld extends World {

	public static final WorldSettings DEFAULT_SETTINGS = new WorldSettings(
		1L,
		WorldSettings.GameType.CREATIVE,
		true,
		false,
		WorldType.DEFAULT);

	public final DummyWorldServer serverWorld;
	public final DummyWorldClient clientWorld;

	public ClientFakePlayer fakePlayer;

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
		this.clientWorld = DummyWorldClient.createDummyClientWorld(this, DEFAULT_SETTINGS, MainRegistry.MigraineWorldId, EnumDifficulty.HARD, new Profiler());
	}

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

	public void doVoidFogParticles(int p_73029_1_, int p_73029_2_, int p_73029_3_){}

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
	public boolean chunkExists(int x, int z){
		return chunkProvider.chunkExists(x, z);
	}

	@Override
	public boolean updateLightByType(EnumSkyBlock p_147463_1_, int p_147463_2_, int p_147463_3_, int p_147463_4_){
		return true;
	}
}
