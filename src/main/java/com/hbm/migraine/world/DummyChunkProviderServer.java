package com.hbm.migraine.world;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.LongHashMap;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.IChunkLoader;
import net.minecraft.world.gen.ChunkProviderServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class DummyChunkProviderServer extends ChunkProviderServer {
	private LongHashMap loadedChunks = new LongHashMap();

	public DummyChunkProviderServer(WorldServer world, IChunkLoader loader, IChunkProvider provider) {
		super(world, loader, provider);
	}

	@Nullable
	@Override
	public Chunk loadChunk(int x, int z) {
		return (Chunk) loadedChunks.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x, z));
	}

	@Nonnull
	@Override
	public Chunk provideChunk(int x, int z) {
		long chunkKey = ChunkCoordIntPair.chunkXZ2Int(x, z);
		if (loadedChunks.containsItem(chunkKey)) {
			return (Chunk) loadedChunks.getValueByKey(chunkKey);
		}

		Chunk chunk = new Chunk(worldObj, x, z);
		chunk.generateSkylightMap();
		chunk.setChunkModified();

		loadedChunks.add(chunkKey, chunk);

		chunk.onChunkLoad();

		return chunk;
	}

	@Override
	public void populate(IChunkProvider p_73153_1_, int p_73153_2_, int p_73153_3_) {}

	@Override
	public boolean saveChunks(boolean p_73151_1_, IProgressUpdate p_73151_2_) {
		return false;
	}

	@Override
	public boolean unloadQueuedChunks() {
		return false;
	}

	@Override
	public boolean canSave() {
		return true;
	}

	@Nonnull
	@Override
	public String makeString() {
		return "Dummy";
	}

	@Override
	public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType p_73155_1_, int p_73155_2_,
																  int p_73155_3_, int p_73155_4_) {
		return null;
	}

	@Override
	public ChunkPosition func_147416_a(World p_147416_1_, String p_147416_2_, int p_147416_3_, int p_147416_4_,
									   int p_147416_5_) {
		return null;
	}

	@Override
	public int getLoadedChunkCount() {
		return 0;
	}

	@Override
	public void recreateStructures(int p_82695_1_, int p_82695_2_) {}

	@Override
	public void saveExtraData() {}

	@Override
	public boolean chunkExists(int x, int z) {
		return true;
	}
}
