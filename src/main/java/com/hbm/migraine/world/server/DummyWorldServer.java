package com.hbm.migraine.world.server;

import com.hbm.main.MainRegistry;
import com.hbm.migraine.world.DummyWorld;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nonnull;
import java.io.File;

/** @author kellen */
public class DummyWorldServer extends WorldServer {

	private final DummyWorld duplicateTo;

	public DummyWorldServer(DummyWorld world, ISaveHandler saveHandler, String name, int demId, WorldSettings settings, Profiler profiler) {
		super(new DummyMinecraftServer(MinecraftServer.getServer()), saveHandler, name, demId, settings, profiler);
		this.duplicateTo = world;
		this.rand.setSeed(0);
		this.provider.setDimension(demId);
		int providerDim = this.provider.dimensionId;
		this.provider.worldObj = this;
		this.provider.setDimension(providerDim);
		this.chunkProvider = duplicateTo.getChunkProvider();
		File toRemove = new File(MainRegistry.configHbmDir, "data");
		if (toRemove.exists() && toRemove.isDirectory()){
			try {
				FileUtils.deleteDirectory(toRemove);
			}catch (Exception ignored){}
		}
	}

//	public void addPacket(long coord){
//		this.duplicateTo.addPacket(coord);
//	}

	@Override
	public boolean setBlock(int x, int y, int z, Block block, int meta, int flags){
		return this.duplicateTo.setBlock(x, y, z, block, meta, flags);
	}

	@Override
	public Block getBlock(int x, int y, int z){
		return this.duplicateTo.getBlock(x, y, z);
	}

	@Override
	public int getBlockMetadata(int x, int y, int z){
		return this.duplicateTo.getBlockMetadata(x, y, z);
	}

	@Override
	public boolean setBlockMetadataWithNotify(int x, int y, int z, int meta, int flag){
		return this.duplicateTo.setBlockMetadataWithNotify(x, y, z, meta, flag);
	}

	@Override
	public void scheduleBlockUpdate(int x, int y, int z, Block block, int delay){
		this.duplicateTo.scheduleBlockUpdate(x, y, z, block, delay);
	}

	@Override
	public void scheduleBlockUpdateWithPriority(int x, int y, int z, Block block, int delay, int priority){
		this.duplicateTo.scheduleBlockUpdateWithPriority(x, y, z, block, delay, priority);
	}

	@Override
	public int getBlockLightOpacity(int x, int y, int z) {
		return this.duplicateTo.getBlockLightOpacity(x, y, z);
	}

	@Override
	public void setTileEntity(int x, int y, int z, TileEntity tile){
		this.duplicateTo.setTileEntity(x, y, z, tile);
	}

	@Override
	public void removeTileEntity(int x, int y, int z){
		this.duplicateTo.removeTileEntity(x, y, z);
	}

	@Override
	public TileEntity getTileEntity(int x, int y, int z){
		return this.duplicateTo.getTileEntity(x, y, z);
	}

	@Override
	public void updateEntities(){}

	@Override
	public void tick(){}

	@Override
	public void flush(){}

	@Override
	public boolean spawnEntityInWorld(Entity entity){
		return this.duplicateTo.spawnEntityInWorld(entity);
	}

	@Override
	public void updateEntityWithOptionalForce(Entity entity, boolean forcedUpdate){
		this.duplicateTo.updateEntityWithOptionalForce(entity, forcedUpdate);
	}

	@Override
	public Entity getEntityByID(int id){
		return this.duplicateTo.getEntityByID(id);
	}

	@Override
	public void onEntityAdded(Entity entity){
		this.duplicateTo.onEntityAdded(entity);
	}

	@Override
	public void onEntityRemoved(Entity entity){
		this.duplicateTo.onEntityRemoved(entity);
	}

	@Override
	public void playSoundEffect(double x, double y, double z, String soundName, float volume, float pitch){
		this.duplicateTo.playSoundEffect(x, y, z, soundName, volume, pitch);
	}

	@Override
	public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {}

	@Override
	protected int func_152379_p(){
		return 0;
	}

	@Nonnull
	@Override
	protected IChunkProvider createChunkProvider(){
		return new DummyChunkProviderServer(this, this.getSaveHandler().getChunkLoader(this.provider), this.provider.createChunkGenerator());
	}

	@Override
	protected boolean chunkExists(int x, int z){
		return this.duplicateTo.chunkExists(x, z);
	}

	@Override
	public boolean updateLightByType(EnumSkyBlock p_147463_1_, int p_147463_2_, int p_147463_3_, int p_147463_4_){
		return true;
	}

	@Override
	public File getChunkSaveLocation(){
		return MainRegistry.configHbmDir;
	}

	@Override
	public void saveAllChunks(boolean p_73044_1_, IProgressUpdate p_73044_2_) throws MinecraftException {}

	@Override
	protected void createSpawnPosition(WorldSettings settings){
		this.worldInfo.setSpawnPosition(0, 64, 0);
	}
}
