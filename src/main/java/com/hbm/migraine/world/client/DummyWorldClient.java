package com.hbm.migraine.world.client;

import com.hbm.migraine.GuiMigraine;
import com.hbm.migraine.world.DummyChunkProvider;
import com.hbm.migraine.world.DummyWorld;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.IChunkProvider;

import javax.annotation.Nonnull;

public class DummyWorldClient extends WorldClient {

	private final DummyWorld duplicateTo;

	public DummyWorldClient(DummyWorld world, WorldSettings worldSettings, int dim, EnumDifficulty difficulty, Profiler profiler) {
		super(new NetHandlerPlayClient(null, null, null), worldSettings, dim, difficulty, profiler);
		duplicateTo = world;
		this.chunkProvider = this.createChunkProvider();
	}

	private EntityClientPlayerMP prevThePlayer;
	private EffectRenderer prevEffectRenderer;
	private WorldClient prevWorldClient;

	public void setMinecraft(){
		Minecraft mc = Minecraft.getMinecraft();

		prevThePlayer = mc.thePlayer;
		mc.thePlayer = duplicateTo.fakePlayer.client;
		mc.renderViewEntity = mc.thePlayer;

		prevEffectRenderer = mc.effectRenderer;
		if (mc.currentScreen instanceof GuiMigraine) mc.effectRenderer = ((GuiMigraine) mc.currentScreen).worldRenderer.rendererEffect;

		prevWorldClient = mc.theWorld;
		mc.theWorld = this;
	}

	public void resetMinecraft(){
		Minecraft mc = Minecraft.getMinecraft();

		if (prevThePlayer != null)
			mc.thePlayer = prevThePlayer;
		mc.renderViewEntity = mc.thePlayer;

		if (prevEffectRenderer != null)
			mc.effectRenderer = prevEffectRenderer;

		if (prevWorldClient != null)
			mc.theWorld = prevWorldClient;

	}

	public static DummyWorldClient createDummyClientWorld(DummyWorld world, WorldSettings worldSettings, int dim, EnumDifficulty difficulty, Profiler profiler) {
//		suppressWorldLoadEvents();
		DummyWorldClient clientWorld = new DummyWorldClient(world, worldSettings, dim, difficulty, profiler);
//		restoreWorldLoadEvents();
		return clientWorld;
	}

	@SideOnly(Side.CLIENT)
	@Override
	protected void finishSetup(){}


	@Nonnull
	@Override
	protected IChunkProvider createChunkProvider(){
		return new DummyChunkProvider(this);
	}


	@Override
	public void setSpawnLocation(int p_72950_1_, int p_72950_2_, int p_72950_3_){}

//	private static List<IEventListener> removedListeners = new ArrayList<>();
//
//	@SuppressWarnings("unchecked")
//	public static void suppressWorldLoadEvents() {
//		try {
//			Field listenersField = EventBus.class.getDeclaredField("listeners");
//			listenersField.setAccessible(true);
//			Map<Object, IEventListener[]> listenersMap = (Map<Object, IEventListener[]>) listenersField.get(MinecraftForge.EVENT_BUS);
//
//			if (listenersMap.containsKey(WorldEvent.Load.class)) {
//				removedListeners.clear();
//				for (IEventListener listener : listenersMap.get(WorldEvent.Load.class)) {
//					removedListeners.add(listener);
//				}
//				listenersMap.remove(WorldEvent.Load.class);
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}
//
//	@SuppressWarnings("unchecked")
//	public static void restoreWorldLoadEvents() {
//		try {
//			Field listenersField = EventBus.class.getDeclaredField("listeners");
//			listenersField.setAccessible(true);
//			Map<Object, IEventListener[]> listenersMap = (Map<Object, IEventListener[]>) listenersField.get(MinecraftForge.EVENT_BUS);
//
//			if (!removedListeners.isEmpty()) {
//				listenersMap.put(WorldEvent.Load.class, removedListeners.toArray(new IEventListener[0]));
//				removedListeners.clear();
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//	}

	public EntityPlayer getClosestPlayerToEntity(Entity p_72890_1_, double p_72890_2_)
	{
		return duplicateTo.getClosestPlayerToEntity(p_72890_1_, p_72890_2_);
	}

	public EntityPlayer getClosestPlayer(double p_72977_1_, double p_72977_3_, double p_72977_5_, double p_72977_7_) {
		return duplicateTo.getClosestPlayer(p_72977_1_, p_72977_3_, p_72977_5_, p_72977_7_);
	}

	@Override
	public void setAllowedSpawnTypes(boolean p_72891_1_, boolean p_72891_2_){}

	@Override
	public float getSunBrightness(float p){ return 15 << 20 | 15 << 4; }

	@Override
	public float getLightBrightness(int x, int y, int z){
		return this.duplicateTo.getLightBrightness(x, y, z);
	}

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
	public void doVoidFogParticles(int p_73029_1_, int p_73029_2_, int p_73029_3_){
		this.duplicateTo.doVoidFogParticles(p_73029_1_, p_73029_2_, p_73029_3_);
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
	public void spawnParticle(String particleName, double x, double y, double z, double velX, double velY, double velZ){
		this.duplicateTo.spawnParticle(particleName, x, y, x, velX, velY, velZ);
	}

	@Override
	public void playSoundEffect(double x, double y, double z, String soundName, float volume, float pitch){
		this.duplicateTo.playSoundEffect(x, y, z, soundName, volume, pitch);
	}

	@Override
	public int getLightBrightnessForSkyBlocks(int p_72802_1_, int p_72802_2_, int p_72802_3_, int p_72802_4_){
		return this.duplicateTo.getLightBrightnessForSkyBlocks(p_72802_1_, p_72802_2_, p_72802_3_, p_72802_4_);
	}

	@Override
	public void markBlockRangeForRenderUpdate(int x1, int y1, int z1, int x2, int y2, int z2) {}

	@Override
	protected int func_152379_p(){
		return 0;
	}

//	@Nonnull
//	@Override
//	protected IChunkProvider createChunkProvider(){
//		return new DummyChunkProviderServer(this, this.getSaveHandler().getChunkLoader(this.provider), this.provider.createChunkGenerator());
//	}

	@Override
	protected boolean chunkExists(int x, int z){
		return this.duplicateTo.chunkExists(x, z);
	}

	@Override
	public boolean updateLightByType(EnumSkyBlock p_147463_1_, int p_147463_2_, int p_147463_3_, int p_147463_4_){
		return true;
	}
}
