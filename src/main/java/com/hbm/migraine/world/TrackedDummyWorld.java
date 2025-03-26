package com.hbm.migraine.world;

import com.hbm.main.MainRegistry;
import com.hbm.migraine.GuiMigraine;
import com.hbm.migraine.client.RecordMovingSound;
import com.hbm.tileentity.IBufPacketReceiver;
import com.hbm.util.CoordinatePacker;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraftforge.common.DimensionManager;
import org.lwjgl.util.vector.Vector3f;

import java.util.*;

public class TrackedDummyWorld extends DummyWorld {
	public final HashMap<Long, Block> blockMap = new HashMap<>();
	public final HashMap<Long, TileEntity> tileMap = new HashMap<>();
	public final HashMap<Long, Integer> blockMetaMap = new HashMap<>();

	public final HashMap<Long, Integer> pendingBlockTicks = new HashMap<>();
	public final HashMap<Long, Integer> pendingTickPriority = new HashMap<>();

	public final HashSet<Long> tilesToReserialize = new HashSet<>();

	public final IntHashMap entityIdMap = new IntHashMap();
	public final HashSet<Entity> entities = new HashSet<>();

	private final Vector3f minPos = new Vector3f(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
	private final Vector3f maxPos = new Vector3f(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
	private final Vector3f size = new Vector3f();

	public final SoundHandler SoundHandler;

	public final EntityPlayer player;

	private int updateEntityTick = 0;

	public TrackedDummyWorld(SoundHandler soundHandler, EntityPlayer owner) {
		this.SoundHandler = soundHandler;
		this.player = owner;
		this.rand = new Random(0);

	}

	public void calcMinMax(){
		minPos.scale(0);
		maxPos.scale(0);
		for (Long pos : blockMap.keySet()){
			int x = CoordinatePacker.unpackX(pos);
			int y = CoordinatePacker.unpackY(pos);
			int z = CoordinatePacker.unpackZ(pos);

			minPos.x = Math.min(minPos.x, x);
			minPos.y = Math.min(minPos.y, y);
			minPos.z = Math.min(minPos.z, z);
			maxPos.x = Math.max(maxPos.x, x);
			maxPos.y = Math.max(maxPos.y, y);
			maxPos.z = Math.max(maxPos.z, z);
		}
	}

	// Blocks
	@Override
	public boolean setBlock(int x, int y, int z, Block block, int meta, int flags) {
		long pos = CoordinatePacker.pack(x, y, z);
		if (block == Blocks.air) {
			if (block.hasTileEntity(meta))
				removeTileEntity(x, y, z);
			blockMap.remove(pos);
			blockMetaMap.remove(pos);
		} else {
			blockMap.put(pos, block);
			blockMetaMap.put(pos, meta);
			if (block.hasTileEntity(meta)) {
				TileEntity tile = block.createTileEntity(this, meta);
				if (tile != null)
					setTileEntity(x, y, z, tile);
			}
			block.onBlockAdded(this, x, y, z);
		}

		return y >= 0 && y < 256;
	}

	@Override
	public Block getBlock(int x, int y, int z) {
		Block block = blockMap.get(CoordinatePacker.pack(x, y, z));
		return block == null ? Blocks.air : block;
	}

	@Override
	public int getBlockMetadata(int x, int y, int z) {
		Integer meta = blockMetaMap.get(CoordinatePacker.pack(x, y, z));
		return meta == null ? 0 : meta;
	}

	@Override
	public boolean setBlockMetadataWithNotify(int x, int y, int z, int meta, int flag) {
		long pos = CoordinatePacker.pack(x, y, z);
		if (!blockMap.containsKey(pos)) return false;
		blockMetaMap.put(pos, meta);

		TileEntity tile = tileMap.get(pos);
		if (tile != null) {
			tile.updateContainingBlockInfo();
			tile.blockMetadata = meta;
		}

		return true;
	}

	@Override
	public void scheduleBlockUpdate(int x, int y, int z, Block block, int delay) {
		this.scheduleBlockUpdateWithPriority(x, y, z, block, delay, 0);
	}

	@Override
	public void scheduleBlockUpdateWithPriority(int x, int y, int z, Block block, int delay, int priority){
		long pos = CoordinatePacker.pack(x, y, z);
		if (!pendingBlockTicks.containsKey(pos)) {
			pendingBlockTicks.put(pos, delay);
			pendingTickPriority.put(pos, priority);
		}
	}


	// Tile Entites
	@Override
	public void setTileEntity(int x, int y, int z, TileEntity tile) {
		if (tile == null) {
			tileMap.remove(CoordinatePacker.pack(x, y, z));
			return;
		}

		long pos = CoordinatePacker.pack(x, y, z);
		tile.setWorldObj(this);
		tile.xCoord = x;
		tile.yCoord = y;
		tile.zCoord = z;

		tile.validate();

		tileMap.put(pos, tile);
	}

	@Override
	public void removeTileEntity(int x, int y, int z) {
		tileMap.remove(CoordinatePacker.pack(x, y, z)).invalidate();
	}

	@Override
	public TileEntity getTileEntity(int x, int y, int z) {
		return tileMap.get(CoordinatePacker.pack(x, y, z));
	}

	@Override
	public void addPacket(long coord){
		this.tilesToReserialize.add(coord);
	}

	// Entities
	@Override
	public void updateEntities(){
		if (!this.isRemote) {
			if (this.updateEntityTick++ >= 1200)
				return;
			else
				this.updateEntityTick = 0;
		}

		HashSet<Entity> toRemove = new HashSet<>();
		for (Entity entity : entities){
			if (entity.ridingEntity != null)
			{
				if (!entity.ridingEntity.isDead && entity.ridingEntity.riddenByEntity == entity)
				{
					continue;
				}

				entity.ridingEntity.riddenByEntity = null;
				entity.ridingEntity = null;
			}

			if (!entity.isDead) {
				if (this.isRemote) {
					entity.worldObj = this;
					this.updateEntity(entity);
				}else{
					entity.worldObj = serverWorld;
					this.updateEntity(entity);
					entity.worldObj = this;
				}
			}else{
				toRemove.add(entity);
			}
		}


		// remove the ded ones
		toRemove.forEach(this::onEntityRemoved);

	}

	@Override
	public boolean spawnEntityInWorld(Entity entity){
		this.entities.add(entity);
		this.onEntityAdded(entity);
		return true;
	}

	@Override
	public void updateEntityWithOptionalForce(Entity entity, boolean forcedUpdate){
		entity.lastTickPosX = entity.posX;
		entity.lastTickPosY = entity.posY;
		entity.lastTickPosZ = entity.posZ;
		entity.prevRotationYaw = entity.rotationYaw;
		entity.prevRotationPitch = entity.rotationPitch;

		if (forcedUpdate)
		{
			++entity.ticksExisted;

			if (entity.ridingEntity != null)
			{
				entity.updateRidden();
			}
			else
			{
				entity.onUpdate();
			}
		}

		if (Double.isNaN(entity.posX) || Double.isInfinite(entity.posX))
		{
			entity.posX = entity.lastTickPosX;
		}

		if (Double.isNaN(entity.posY) || Double.isInfinite(entity.posY))
		{
			entity.posY = entity.lastTickPosY;
		}

		if (Double.isNaN(entity.posZ) || Double.isInfinite(entity.posZ))
		{
			entity.posZ = entity.lastTickPosZ;
		}

		if (Double.isNaN((double)entity.rotationPitch) || Double.isInfinite((double)entity.rotationPitch))
		{
			entity.rotationPitch = entity.prevRotationPitch;
		}

		if (Double.isNaN((double)entity.rotationYaw) || Double.isInfinite((double)entity.rotationYaw))
		{
			entity.rotationYaw = entity.prevRotationYaw;
		}

		if (forcedUpdate && entity.riddenByEntity != null)
		{
			if (!entity.riddenByEntity.isDead && entity.riddenByEntity.ridingEntity == entity)
			{
				this.updateEntity(entity.riddenByEntity);
			}
			else
			{
				entity.riddenByEntity.ridingEntity = null;
				entity.riddenByEntity = null;
			}
		}
	}

	@Override
	public Entity getEntityByID(int id)
	{
		return (Entity) this.entityIdMap.lookup(id);
	}

	@Override
	public void onEntityAdded(Entity entity){
		entities.add(entity);
		this.entityIdMap.addKey(entity.getEntityId(), entity);
		Entity[] aentity = entity.getParts();

		if (aentity != null)
		{
			for (Entity value : aentity) {
				this.entityIdMap.addKey(value.getEntityId(), value);
			}
		}
	}

	@Override
	public void onEntityRemoved(Entity entity){
		entities.remove(entity);
		this.entityIdMap.removeObject(entity.getEntityId());
		Entity[] aentity = entity.getParts();

		if (aentity != null)
		{
			for (Entity value : aentity) {
				this.entityIdMap.removeObject(value.getEntityId());
			}
		}
	}


	// Particles
	@Override
	public void spawnParticle(String particleName, double x, double y, double z, double velX, double velY, double velZ){
		if (!this.isRemote) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (!(mc.currentScreen instanceof GuiMigraine)) return;
		GuiMigraine gui = (GuiMigraine) mc.currentScreen;
		EntityLivingBase renderViewEntity = gui.worldRenderer.camera;
		EffectRenderer effectRenderer = gui.worldRenderer.rendererEffect;
		TextureManager renderEngine = mc.renderEngine;
		
		if (renderViewEntity != null && effectRenderer != null)
		{
			int i = mc.gameSettings.particleSetting;

			if (i == 1 && this.rand.nextInt(3) == 0)
			{
				i = 2;
			}

			double d6 = renderViewEntity.posX - x;
			double d7 = renderViewEntity.posY - y;
			double d8 = renderViewEntity.posZ - z;
			EntityFX entityfx = null;

			switch (particleName) {
				case "hugeexplosion":
					effectRenderer.addEffect(entityfx = new EntityHugeExplodeFX(this, x, y, z, velX, velY, velZ));
					break;
				case "largeexplode":
					effectRenderer.addEffect(entityfx = new EntityLargeExplodeFX(renderEngine, this, x, y, z, velX, velY, velZ));
					break;
				case "fireworksSpark":
					effectRenderer.addEffect(entityfx = new EntityFireworkSparkFX(this, x, y, z, velX, velY, velZ, effectRenderer));
					break;
			}

			if (entityfx == null)
			{
				double d9 = 16.0D;

				if (!(d6 * d6 + d7 * d7 + d8 * d8 > d9 * d9) && i <= 1) {
					switch (particleName) {
						case "bubble":
							entityfx = new EntityBubbleFX(this, x, y, z, velX, velY, velZ);
							break;
						case "suspended":
							entityfx = new EntitySuspendFX(this, x, y, z, velX, velY, velZ);
							break;
						case "depthsuspend":
						case "townaura":
							entityfx = new EntityAuraFX(this, x, y, z, velX, velY, velZ);
							break;
						case "crit":
							entityfx = new EntityCritFX(this, x, y, z, velX, velY, velZ);
							break;
						case "magicCrit":
							entityfx = new EntityCritFX(this, x, y, z, velX, velY, velZ);
							entityfx.setRBGColorF(entityfx.getRedColorF() * 0.3F, entityfx.getGreenColorF() * 0.8F, entityfx.getBlueColorF());
							entityfx.nextTextureIndexX();
							break;
						case "smoke":
							entityfx = new EntitySmokeFX(this, x, y, z, velX, velY, velZ);
							break;
						case "mobSpell":
							entityfx = new EntitySpellParticleFX(this, x, y, z, 0.0D, 0.0D, 0.0D);
							entityfx.setRBGColorF((float) velX, (float) velY, (float) velZ);
							break;
						case "mobSpellAmbient":
							entityfx = new EntitySpellParticleFX(this, x, y, z, 0.0D, 0.0D, 0.0D);
							entityfx.setAlphaF(0.15F);
							entityfx.setRBGColorF((float) velX, (float) velY, (float) velZ);
							break;
						case "spell":
							entityfx = new EntitySpellParticleFX(this, x, y, z, velX, velY, velZ);
							break;
						case "instantSpell":
							entityfx = new EntitySpellParticleFX(this, x, y, z, velX, velY, velZ);
							((EntitySpellParticleFX) entityfx).setBaseSpellTextureIndex(144);
							break;
						case "witchMagic":
							entityfx = new EntitySpellParticleFX(this, x, y, z, velX, velY, velZ);
							((EntitySpellParticleFX) entityfx).setBaseSpellTextureIndex(144);
							float f = this.rand.nextFloat() * 0.5F + 0.35F;
							entityfx.setRBGColorF(1.0F * f, 0.0F * f, 1.0F * f);
							break;
						case "note":
							entityfx = new EntityNoteFX(this, x, y, z, velX, velY, velZ);
							break;
						case "portal":
							entityfx = new EntityPortalFX(this, x, y, z, velX, velY, velZ);
							break;
						case "enchantmenttable":
							entityfx = new EntityEnchantmentTableParticleFX(this, x, y, z, velX, velY, velZ);
							break;
						case "explode":
							entityfx = new EntityExplodeFX(this, x, y, z, velX, velY, velZ);
							break;
						case "flame":
							entityfx = new EntityFlameFX(this, x, y, z, velX, velY, velZ);
							break;
						case "lava":
							entityfx = new EntityLavaFX(this, x, y, z);
							break;
						case "footstep":
							entityfx = new EntityFootStepFX(renderEngine, this, x, y, z);
							break;
						case "splash":
							entityfx = new EntitySplashFX(this, x, y, z, velX, velY, velZ);
							break;
						case "wake":
							entityfx = new EntityFishWakeFX(this, x, y, z, velX, velY, velZ);
							break;
						case "largesmoke":
							entityfx = new EntitySmokeFX(this, x, y, z, velX, velY, velZ, 2.5F);
							break;
						case "cloud":
							entityfx = new EntityCloudFX(this, x, y, z, velX, velY, velZ);
							break;
						case "reddust":
							entityfx = new EntityReddustFX(this, x, y, z, (float) velX, (float) velY, (float) velZ);
							break;
						case "snowballpoof":
							entityfx = new EntityBreakingFX(this, x, y, z, Items.snowball);
							break;
						case "dripWater":
							entityfx = new EntityDropParticleFX(this, x, y, z, Material.water);
							break;
						case "dripLava":
							entityfx = new EntityDropParticleFX(this, x, y, z, Material.lava);
							break;
						case "snowshovel":
							entityfx = new EntitySnowShovelFX(this, x, y, z, velX, velY, velZ);
							break;
						case "slime":
							entityfx = new EntityBreakingFX(this, x, y, z, Items.slime_ball);
							break;
						case "heart":
							entityfx = new EntityHeartFX(this, x, y, z, velX, velY, velZ);
							break;
						case "angryVillager":
							entityfx = new EntityHeartFX(this, x, y + 0.5D, z, velX, velY, velZ);
							entityfx.setParticleTextureIndex(81);
							entityfx.setRBGColorF(1.0F, 1.0F, 1.0F);
							break;
						case "happyVillager":
							entityfx = new EntityAuraFX(this, x, y, z, velX, velY, velZ);
							entityfx.setParticleTextureIndex(82);
							entityfx.setRBGColorF(1.0F, 1.0F, 1.0F);
							break;
						default:
							int k;
							String[] astring;

							if (particleName.startsWith("iconcrack_")) {
								astring = particleName.split("_", 3);
								int j = Integer.parseInt(astring[1]);

								if (astring.length > 2) {
									k = Integer.parseInt(astring[2]);
									entityfx = new EntityBreakingFX(this, x, y, z, velX, velY, velZ, Item.getItemById(j), k);
								} else {
									entityfx = new EntityBreakingFX(this, x, y, z, velX, velY, velZ, Item.getItemById(j), 0);
								}
							} else {
								Block block;

								if (particleName.startsWith("blockcrack_")) {
									astring = particleName.split("_", 3);
									block = Block.getBlockById(Integer.parseInt(astring[1]));
									k = Integer.parseInt(astring[2]);
									entityfx = (new EntityDiggingFX(this, x, y, z, velX, velY, velZ, block, k)).applyRenderColor(k);
								} else if (particleName.startsWith("blockdust_")) {
									astring = particleName.split("_", 3);
									block = Block.getBlockById(Integer.parseInt(astring[1]));
									k = Integer.parseInt(astring[2]);
									entityfx = (new EntityBlockDustFX(this, x, y, z, velX, velY, velZ, block, k)).applyRenderColor(k);
								}
							}
							break;
					}

					if (entityfx != null) {
						effectRenderer.addEffect(entityfx);
					}

				}
			}
		}
	}

	// main loop
	public void update() {
		// Time
		this.func_82738_a(this.getTotalWorldTime() + 1L);

		// Override server to client packets (new system)
		tilesToReserialize.forEach((Long pos) -> {
			TileEntity tile = getTileEntity(CoordinatePacker.unpackX(pos), CoordinatePacker.unpackY(pos), CoordinatePacker.unpackZ(pos));
			if (tile instanceof IBufPacketReceiver && !tile.isInvalid()){
				ByteBuf buf = Unpooled.buffer();
				((IBufPacketReceiver) tile).serialize(buf);
				try {
					((IBufPacketReceiver) tile).deserialize(buf);
				}catch (Exception e){
					MainRegistry.logger.warn("[Migraine] Failed to deserialize a tile entity! ", e);
				}
			}
		});
		tilesToReserialize.clear();

		// Update tile entities as client
		this.isRemote = true;
		HashMap<Long, TileEntity> tiles = new HashMap<>(tileMap);
		tiles.forEach((Long pos, TileEntity tile) -> {
			if (tile.canUpdate() && !tile.isInvalid())
				tile.updateEntity();
		});

		// Update tile entities as server
		this.isRemote = false;
		tiles.forEach((Long pos, TileEntity tile) -> {
			if (!tile.isInvalid()) {
				tile.setWorldObj(serverWorld);
				if (tile.canUpdate())
					tile.updateEntity();
				tile.setWorldObj(this);
			}
		});
		this.isRemote = true;

		// Tick scheduled updates

		// Decrement counters and get blocks that should be updated now
		List<Long> blocksToUpdate = new ArrayList<>();
		for (Long pos : pendingBlockTicks.keySet()){
			int tick = pendingBlockTicks.get(pos);
			if (tick <= 0)
				blocksToUpdate.add(pos);
			else
				pendingBlockTicks.replace(pos, --tick);
		}

		// Remove all that are being ticked now
		blocksToUpdate.forEach(pendingBlockTicks::remove);

		// Sort by tick priority
		blocksToUpdate.sort(Comparator.comparingInt(pendingTickPriority::get)); // wow intellij is smart

		// Remove tick priorities
		blocksToUpdate.forEach(pendingTickPriority::remove);

		// Actually update the blocks (as the server)
		this.isRemote = false;
		for (Long pos : blocksToUpdate){
			int x = CoordinatePacker.unpackX(pos);
			int y = CoordinatePacker.unpackY(pos);
			int z = CoordinatePacker.unpackZ(pos);
			getBlock(x, y, z).updateTick(serverWorld, x, y, z, this.rand);
		}

		// Tick blocks every tick, still as the server
		HashMap<Long, Block> blocks = new HashMap<>(blockMap);
		blocks.forEach((Long pos, Block block) -> {
			if (block.getTickRandomly())
				block.updateTick(serverWorld, CoordinatePacker.unpackX(pos), CoordinatePacker.unpackY(pos), CoordinatePacker.unpackZ(pos), this.rand);
		});
		this.isRemote = true;

		// Update entities as server and client
		this.isRemote = false;
		this.updateEntities();
		this.isRemote = true;
		this.updateEntities();

		if (Minecraft.getMinecraft().currentScreen instanceof GuiMigraine)
			((GuiMigraine) Minecraft.getMinecraft().currentScreen).worldRenderer.rendererEffect.updateEffects();
	}

	public void unload(){
		tileMap.forEach((Long pos, TileEntity tile) -> tile.onChunkUnload());
		tileMap.forEach((Long pos, TileEntity tile) -> tile.invalidate());
		tileMap.clear();
		blockMap.clear();
		blockMetaMap.clear();
		entities.forEach(Entity::setDead);
		entities.clear();
		if (Minecraft.getMinecraft().currentScreen instanceof GuiMigraine)
			((GuiMigraine) Minecraft.getMinecraft().currentScreen).worldRenderer.rendererEffect.clearEffects(this);
		DimensionManager.unloadWorld(MainRegistry.MigraineWorldId);
	}

	public void emptyWorld(){
		// fucking concurrent modifications
		HashSet<Long> temp = new HashSet<>(blockMap.keySet());
		temp.forEach((Long pos) -> setBlock(CoordinatePacker.unpackX(pos), CoordinatePacker.unpackY(pos), CoordinatePacker.unpackZ(pos), Blocks.air));
		entities.forEach(Entity::setDead);
		tileMap.forEach((Long pos, TileEntity tile) -> tile.invalidate());
		tileMap.clear();
		if (Minecraft.getMinecraft().currentScreen instanceof GuiMigraine)
			((GuiMigraine) Minecraft.getMinecraft().currentScreen).worldRenderer.rendererEffect.clearEffects(this);
	}



	/**
	 * Enable fullbright rendering
	 */
	@SideOnly(Side.CLIENT)
	@Override
	public int getLightBrightnessForSkyBlocks(int p_72802_1_, int p_72802_2_, int p_72802_3_, int p_72802_4_) {
		return 15 << 20 | 15 << 4;
	}

	public Vector3f getSize() {
		calcMinMax();
		size.set(maxPos.x - minPos.x + 1, maxPos.y - minPos.y + 1, maxPos.z - minPos.z + 1);
		return size;
	}

	public Vector3f getMinPos() {
		return minPos;
	}

	public Vector3f getMaxPos() {
		return maxPos;
	}

	public MovingObjectPosition rayTraceBlocksWithTargetMap(Vec3 start, Vec3 end, HashSet<Long> targetedBlocks) {
		return rayTraceBlocksWithTargetMap(start, end, targetedBlocks, false, false, false);
	}

	public MovingObjectPosition rayTraceBlocksWithTargetMap(Vec3 start, Vec3 end, HashSet<Long> targetedBlocks,
															boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
		if (!Double.isNaN(start.xCoord) && !Double.isNaN(start.yCoord) && !Double.isNaN(start.zCoord)) {
			if (!Double.isNaN(end.xCoord) && !Double.isNaN(end.yCoord) && !Double.isNaN(end.zCoord)) {
				int i = MathHelper.floor_double(end.xCoord);
				int j = MathHelper.floor_double(end.yCoord);
				int k = MathHelper.floor_double(end.zCoord);
				int l = MathHelper.floor_double(start.xCoord);
				int i1 = MathHelper.floor_double(start.yCoord);
				int j1 = MathHelper.floor_double(start.zCoord);
				Block block = this.getBlock(l, i1, j1);
				int k1 = this.getBlockMetadata(l, i1, j1);

				if ((!ignoreBlockWithoutBoundingBox || block.getCollisionBoundingBoxFromPool(this, l, i1, j1) != null)
					&& block.canCollideCheck(k1, stopOnLiquid)) {
					MovingObjectPosition movingobjectposition = block.collisionRayTrace(this, l, i1, j1, start, end);

					if (movingobjectposition != null && isBlockTargeted(movingobjectposition, targetedBlocks)) {
						return movingobjectposition;
					}
				}

				MovingObjectPosition movingobjectposition2 = null;
				k1 = 200;

				while (k1-- >= 0) {
					if (Double.isNaN(start.xCoord) || Double.isNaN(start.yCoord) || Double.isNaN(start.zCoord)) {
						return null;
					}

					if (l == i && i1 == j && j1 == k) {
						return returnLastUncollidableBlock ? movingobjectposition2 : null;
					}

					boolean flag6 = true;
					boolean flag3 = true;
					boolean flag4 = true;
					double d0 = 999.0D;
					double d1 = 999.0D;
					double d2 = 999.0D;

					if (i > l) {
						d0 = (double) l + 1.0D;
					} else if (i < l) {
						d0 = (double) l + 0.0D;
					} else {
						flag6 = false;
					}

					if (j > i1) {
						d1 = (double) i1 + 1.0D;
					} else if (j < i1) {
						d1 = (double) i1 + 0.0D;
					} else {
						flag3 = false;
					}

					if (k > j1) {
						d2 = (double) j1 + 1.0D;
					} else if (k < j1) {
						d2 = (double) j1 + 0.0D;
					} else {
						flag4 = false;
					}

					double d3 = 999.0D;
					double d4 = 999.0D;
					double d5 = 999.0D;
					double d6 = end.xCoord - start.xCoord;
					double d7 = end.yCoord - start.yCoord;
					double d8 = end.zCoord - start.zCoord;

					if (flag6) {
						d3 = (d0 - start.xCoord) / d6;
					}

					if (flag3) {
						d4 = (d1 - start.yCoord) / d7;
					}

					if (flag4) {
						d5 = (d2 - start.zCoord) / d8;
					}

					boolean flag5 = false;
					byte b0;

					if (d3 < d4 && d3 < d5) {
						if (i > l) {
							b0 = 4;
						} else {
							b0 = 5;
						}

						start.xCoord = d0;
						start.yCoord += d7 * d3;
						start.zCoord += d8 * d3;
					} else if (d4 < d5) {
						if (j > i1) {
							b0 = 0;
						} else {
							b0 = 1;
						}

						start.xCoord += d6 * d4;
						start.yCoord = d1;
						start.zCoord += d8 * d4;
					} else {
						if (k > j1) {
							b0 = 2;
						} else {
							b0 = 3;
						}

						start.xCoord += d6 * d5;
						start.yCoord += d7 * d5;
						start.zCoord = d2;
					}

					Vec3 vec32 = Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord);
					l = (int) (vec32.xCoord = MathHelper.floor_double(start.xCoord));

					if (b0 == 5) {
						--l;
						++vec32.xCoord;
					}

					i1 = (int) (vec32.yCoord = MathHelper.floor_double(start.yCoord));

					if (b0 == 1) {
						--i1;
						++vec32.yCoord;
					}

					j1 = (int) (vec32.zCoord = MathHelper.floor_double(start.zCoord));

					if (b0 == 3) {
						--j1;
						++vec32.zCoord;
					}

					Block block1 = this.getBlock(l, i1, j1);
					int l1 = this.getBlockMetadata(l, i1, j1);

					if (!ignoreBlockWithoutBoundingBox
						|| block1.getCollisionBoundingBoxFromPool(this, l, i1, j1) != null) {
						if (block1.canCollideCheck(l1, stopOnLiquid)) {
							MovingObjectPosition movingobjectposition1 = block1
								.collisionRayTrace(this, l, i1, j1, start, end);

							if (movingobjectposition1 != null
								&& isBlockTargeted(movingobjectposition1, targetedBlocks)) {
								return movingobjectposition1;
							}
						} else {
							movingobjectposition2 = new MovingObjectPosition(l, i1, j1, b0, start, false);
						}
					}
				}

				return returnLastUncollidableBlock ? movingobjectposition2 : null;
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	private boolean isBlockTargeted(MovingObjectPosition result, HashSet<Long> targetedBlocks) {
		return targetedBlocks.contains(CoordinatePacker.pack(result.blockX, result.blockY, result.blockZ));
	}

	@Override
	public void playSoundEffect(double x, double y, double z, String soundName, float volume, float pitch) {
		if (this.SoundHandler != null)
			this.SoundHandler.playSound(new RecordMovingSound(player, new ResourceLocation(soundName), volume, pitch, (float) x, (float) y, (float) z));
	}

}
