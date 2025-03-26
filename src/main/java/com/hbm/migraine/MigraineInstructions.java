package com.hbm.migraine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;
import com.hbm.migraine.client.ImmediateWorldSceneRenderer;
import com.hbm.migraine.world.EntityAIMoveToLocation;
import com.hbm.util.I18nUtil;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.util.vector.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class MigraineInstructions {

	public final String name;
	public final ComparableStack comparableStack;
	private final JsonObject json;
	private final List<Integer> chapters = new ArrayList<>();

	private static final ResourceLocation guiUtil =  new ResourceLocation(RefStrings.MODID + ":textures/gui/gui_utility.png");

	private MigraineBar progressBar;
	private float[] chapterPercents;

	private static int[] pauseButtonOffset = { -57, -36 };
	private static int[] progressBarXRange = { -23, 57 };
	private static int progressBarY = -31;

	public MigraineInstructions(String name, JsonObject object){
		this.name = name;

		// Im going to put everything in a try block fuck you
		ComparableStack stack;
		JsonObject jsonObj;
		try {
			JsonObject ownerInfo = object.getAsJsonObject("owner");
			stack = new ComparableStack(itemStackFromJson(ownerInfo));

			jsonObj = object;

			JsonArray chapArray = object.getAsJsonArray("chapters");
			this.chapters.add(0);
			for (int i = 0; i < chapArray.size(); i++){
				this.chapters.add(chapArray.get(i).getAsInt());
			}

		} catch (Exception ex){
			MainRegistry.logger.warn("[Migraine] You need to include a owner tag! Skipping " + name);
			stack = new ComparableStack(Item.getItemFromBlock(Blocks.air));
			jsonObj = new JsonObject();
		}

		this.comparableStack = stack;
		this.json = jsonObj;

	}



	/**
	 * Gets an ItemStack
	 * @param item: the json containing the item
	 *            id: string - the same id you would use for the /give command (ex. hbm:tile.machine_press)
	 *            count: integer, optional - the number of items in the stack. Defaults to 1
	 *            meta: integer, optional - the meta (or damage) of the item. Defaults to 0
	 *            nbt: string, optional - the tag for the item. Is the same tag you would use for the /give command
	 * @return the built ItemStack
	 */

	private ItemStack itemStackFromJson(JsonObject item){

		String[] id = item.get("id").getAsString().split(":");

		ItemStack stack = GameRegistry.findItemStack(id[0], id[1], item.has("count") ? item.get("count").getAsInt() : 1);

		ItemStack out = stack.copy();
		out.setItemDamage(item.has("meta") ? item.get("meta").getAsInt() : 0);

		if (item.has("nbt")){
			try {
				out.setTagCompound((NBTTagCompound) JsonToNBT.func_150315_a(item.get("nbt").getAsString()));
			}catch(Exception ex){
				MainRegistry.logger.warn("[Migraine] Error setting item tag in " + name + "!");
			}
		}

		return out;
	}

	private void callFunctionOnFromJson(Object toCallOn, JsonObject action){

		// I know this looks bad, but its a fake world, so whats the worst someone could do?
		try {
			JsonArray args = action.getAsJsonArray("arguments");
			Object[] params = new Object[args.size()];
			Class[] types = new Class[args.size()];
			// Params currently implemented:
			// ints
			// ItemStacks
			// Strings
			// longs
			// doubles
			// floats
			// booleans
			// chars
			// shorts

			for (int i = 0; i < args.size(); i++) {
				JsonObject arg = args.get(i).getAsJsonObject();
				String argType = arg.get("type").getAsString();
				switch (argType) {
					case "itemStack":
						params[i] = itemStackFromJson(arg);
						types[i] = ItemStack.class;
						break;
					case "float":
						params[i] = arg.get("value").getAsFloat();
						types[i] = float.class;
						break;
					case "int":
						params[i] = arg.get("value").getAsInt();
						types[i] = int.class;
						break;
					case "string":
						params[i] = arg.get("value").getAsString();
						types[i] = String.class;
						break;
					case "double":
						params[i] = arg.get("value").getAsDouble();
						types[i] = double.class;
						break;
					case "long":
						params[i] = arg.get("value").getAsLong();
						types[i] = long.class;
						break;
					case "boolean":
						params[i] = arg.get("value").getAsBoolean();
						types[i] = boolean.class;
						break;
					case "short":
						params[i] = arg.get("value").getAsShort();
						types[i] = short.class;
						break;
					case "char":
						params[i] = arg.get("value").getAsCharacter();
						types[i] = char.class;
						break;
				}
			}

			Method method = toCallOn.getClass().getMethod(action.get("method").getAsString(), types);
			method.invoke(toCallOn, params);

		} catch (Exception ex) {
			MainRegistry.logger.warn("[Migraine] Error modifying object in " + name + "!");
		}
	}

	private MigraineDisplay getDisplayFromJson(JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");

		JsonArray array = action.getAsJsonArray("lines");
		Object[][] toDisplay = new Object[array.size()][];

		for (int j = 0; j < array.size(); j++){
			JsonArray next = array.get(j).getAsJsonArray();
			Object[] currentLine = new Object[next.size()];
			for (int i = 0; i < next.size(); i++){
				JsonObject data = next.get(i).getAsJsonObject();

				// Currently only itemstacks and strings are supported

				String type = data.get("type").getAsString();

				switch (type){
					case "itemStack":
						currentLine[i] = itemStackFromJson(data);
						break;
					case "string":
						if (data.has("localized") && data.get("localized").getAsBoolean())
							currentLine[i] = I18nUtil.resolveKey(data.get("value").getAsString());
						else
							currentLine[i] = data.get("value").getAsString();
						break;
					default:
						currentLine[i] = "";
				}
			}
			toDisplay[j] = currentLine;
		}

		MigraineDisplay display = new MigraineDisplay(Minecraft.getMinecraft().fontRenderer, pos.get("x").getAsInt(), pos.get("y").getAsInt(), toDisplay, action.has("autowrap") ? action.get("autowrap").getAsInt() : 0, action.get("forTicks").getAsInt());

		if (action.has("backgroundColor")) display.colorBg = Long.parseLong(action.get("backgroundColor").getAsString(), 16);
		if (action.has("darkerColor")) display.colorDarker = Long.parseLong(action.get("darkerColor").getAsString(), 16);
		if (action.has("lighterColor")) display.colorBrighter = Long.parseLong(action.get("lighterColor").getAsString(), 16);
		if (action.has("frameColor")) display.colorFrame = Long.parseLong(action.get("frameColor").getAsString(), 16);
		if (action.has("orientation")) display.setOrientation(MigraineDisplay.Orientation.valueOf(action.get("orientation").getAsString()));

		return display;
	}


	private void setBlock(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");

		String[] id = action.get("id").getAsString().split(":");

		worldRenderer.world.setBlock(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt(), GameRegistry.findBlock(id[0], id[1]), action.has("meta") ? action.get("meta").getAsInt() : 0, 3);
	}

	private void placeBlock(GuiMigraine gui, ImmediateWorldSceneRenderer worldRenderer, JsonObject action){

		String[] id = action.get("id").getAsString().split(":");

		Block block = GameRegistry.findBlock(id[0], id[1]);

		JsonObject pos = action.getAsJsonObject("position");

		gui.FAKE_PLAYER.rotationYaw = action.get("yaw").getAsFloat();
		gui.FAKE_PLAYER.capabilities.isCreativeMode = true;

		worldRenderer.world.isRemote = false;
		block.onBlockPlacedBy(worldRenderer.world, pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt(), gui.FAKE_PLAYER, new ItemStack(block, 1, action.has("meta") ? action.get("meta").getAsInt() : 0));
		worldRenderer.world.isRemote = true;
	}

	private void fillBlocks(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject posMin = action.getAsJsonObject("positionMin");
		JsonObject posMax = action.getAsJsonObject("positionMax");

		String[] id = action.get("id").getAsString().split(":");

		Block block = GameRegistry.findBlock(id[0], id[1]);

		for (int x = posMin.get("x").getAsInt(); x <= posMax.get("x").getAsInt(); x++){
			for (int y = posMin.get("y").getAsInt(); y <= posMax.get("y").getAsInt(); y++){
				for (int z = posMin.get("z").getAsInt(); z <= posMax.get("z").getAsInt(); z++){
					worldRenderer.world.setBlock(x, y, z, block, action.has("meta") ? action.get("meta").getAsInt() : 0, 3);
					worldRenderer.world.markBlockForUpdate(x, y, z);
				}
			}
		}
	}

	private void modifyTileEntity(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");
		TileEntity te = worldRenderer.world.getTileEntity(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt());
		if (te != null) {
			callFunctionOnFromJson(te, action);
		} else {
			MainRegistry.logger.warn("[Migraine] Tried to access TileEntity at " + pos.get("x").getAsInt() + ", " + pos.get("y").getAsInt() + ", " + pos.get("z").getAsInt() + ", but failed in " + name + "!");
		}
	}

	private void setCenter(GuiMigraine gui, ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");
		if (pos.isJsonNull()){
			gui.center = null;
		}else{
			gui.center = new Vector3f(pos.get("x").getAsFloat(), pos.get("y").getAsFloat(), pos.get("z").getAsFloat());
		}
	}

	private void addCenter(GuiMigraine gui, ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");

		Vector3f size = worldRenderer.world.getSize();
		Vector3f minPos = worldRenderer.world.getMinPos();
		gui.center = gui.center == null ? new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2) : gui.center;

		Vector3f.add(gui.center, new Vector3f(pos.get("x").getAsFloat(), pos.get("y").getAsFloat(), pos.get("z").getAsFloat()), gui.center);
	}

	private void rotateTo(GuiMigraine gui, ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("forTicks").getAsInt();
		toAdd.addProperty("type", "rotateTo");
		toAdd.addProperty("tickLeft", forTicks);
		toAdd.addProperty("addYaw", (action.get("targetYaw").getAsFloat() - gui.yaw) / forTicks);
		toAdd.addProperty("addPitch", (action.get("targetPitch").getAsFloat() - gui.pitch) / forTicks);
		gui.active.add(toAdd);
	}

	private void zoomTo(GuiMigraine gui, ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("forTicks").getAsInt();
		toAdd.addProperty("type", "zoomTo");
		toAdd.addProperty("tickLeft", forTicks);
		toAdd.addProperty("addZoom", (action.get("targetZoom").getAsFloat() - gui.zoom) / forTicks);
		gui.active.add(toAdd);
	}

	private void moveCenterTo(GuiMigraine gui, ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("forTicks").getAsInt();
		toAdd.addProperty("type", "moveCenterTo");
		toAdd.addProperty("tickLeft", forTicks);
		JsonObject targetPos = action.getAsJsonObject("targetPosition");

		Vector3f size = worldRenderer.world.getSize();
		Vector3f minPos = worldRenderer.world.getMinPos();
		gui.center = gui.center == null ? new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2) : gui.center;

		toAdd.addProperty("addX", (targetPos.get("x").getAsFloat() - gui.center.x) / forTicks);
		toAdd.addProperty("addY", (targetPos.get("y").getAsFloat() - gui.center.y) / forTicks);
		toAdd.addProperty("addZ", (targetPos.get("z").getAsFloat() - gui.center.z) / forTicks);

		gui.active.add(toAdd);
	}

	private void display(GuiMigraine gui, ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		gui.displays.add(getDisplayFromJson(action));
	}

	/**
	 * If you are struggling with nbt, take a look at {@link net.minecraft.nbt.JsonToNBT}. It's the same format used in setblock, but setblock is more forgiving for some reason
	 * @param worldRenderer
	 * @param action
	 */
	private void setTileEntityNBT(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");

		int x = pos.get("x").getAsInt();
		int y = pos.get("y").getAsInt();
		int z = pos.get("z").getAsInt();

		TileEntity tile = worldRenderer.world.getTileEntity(x, y, z);

		if (tile != null){
			try {
				NBTTagCompound nbt = (NBTTagCompound) JsonToNBT.func_150315_a(action.get("nbt").getAsString());
				nbt.setInteger("x", x);
				nbt.setInteger("y", y);
				nbt.setInteger("z", z);
				tile.readFromNBT(nbt);
			} catch(NBTException ex){
				MainRegistry.logger.warn("[Migraine] Failed to read NBT String while setting tile entity nbt in " + name + "!");
			}
		}
	}

	/**
	 * Tries to spawn an entity by loading the class or getting the entity from a modid and entity id
	 * @param worldRenderer
	 * @param action
	 * 		position: the position to spawn the entity at
	 * 			x: double
	 * 			y: double
	 * 			z: double
	 * 		class: string - the path to the class of the entity (ex. net.minecraft.entity.entity) (A, optional if both B)
	 * 		modid: string - the modid of the entity (B, optional if A)
	 * 		id: int - the id of the entity in it's mod (B, optional if A)
	 * 		nbt: string, optional - the nbt string of
	 */
	private void spawnEntity(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");

		try {

			double x = pos.get("x").getAsDouble();
			double y = pos.get("y").getAsDouble();
			double z = pos.get("z").getAsDouble();

			NBTTagCompound nbt = new NBTTagCompound();

			if (action.has("nbt")){
				try {
					nbt = (NBTTagCompound) JsonToNBT.func_150315_a(action.get("nbt").getAsString());
				} catch(NBTException ex){
					MainRegistry.logger.warn("[Migraine] Failed to read NBT String while spawning entity in " + name + "!");
				}
			}

			nbt.setString("id", action.get("id").getAsString());

			Entity entity = EntityList.createEntityFromNBT(nbt, worldRenderer.world);

			entity.setLocationAndAngles(x, y, z, action.has("yaw") ? action.get("yaw").getAsFloat() : entity.rotationYaw, action.has("pitch") ? action.get("pitch").getAsFloat() : entity.rotationPitch);

			// for accessing in setEntityNBT (and possibly more)
			if (action.has("entityId")) entity.setEntityId(action.get("entityId").getAsInt());

			worldRenderer.world.spawnEntityInWorld(entity);

			// copied from the summon command
			Entity entity2 = entity;

			for (NBTTagCompound nbttagcompound1 = nbt; entity2 != null && nbttagcompound1.hasKey("Riding", 10); nbttagcompound1 = nbttagcompound1.getCompoundTag("Riding"))
			{
				Entity entity1 = EntityList.createEntityFromNBT(nbttagcompound1.getCompoundTag("Riding"), worldRenderer.world);

				if (entity1 != null)
				{
					entity1.setLocationAndAngles(x, y, z, entity1.rotationYaw, entity1.rotationPitch);
					worldRenderer.world.spawnEntityInWorld(entity1);
					entity2.mountEntity(entity1);
				}

				entity2 = entity1;
			}

		}catch(Exception ex){
			MainRegistry.logger.warn("[Migraine] Error while spawning entity in " + name + "!");
		}
	}

	private void setEntityNBT(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){

		Entity entity = worldRenderer.world.getEntityByID(action.get("entityId").getAsInt());
		if (entity != null){
			try{
				entity.readFromNBT((NBTTagCompound) JsonToNBT.func_150315_a(action.get("nbt").getAsString()));
			} catch (Exception ex){
				MainRegistry.logger.warn("[Migraine] Failed to load entity from nbt in " + name + "!");
			}
		}
	}

	private void setEntityTarget(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		Entity entity = worldRenderer.world.getEntityByID(action.get("entityId").getAsInt());
		if (entity != null){
			JsonObject pos = action.getAsJsonObject("position");
			if (entity instanceof EntityCreature){
				EntityCreature entityCreature = (EntityCreature) entity;
				boolean flag = false;
				for (Object task : entityCreature.tasks.taskEntries) {
					EntityAITasks.EntityAITaskEntry entry = (EntityAITasks.EntityAITaskEntry) task;
					if (entry.action instanceof EntityAIMoveToLocation) {
						((EntityAIMoveToLocation) entry.action).updateTarget(pos.get("x").getAsDouble(), pos.get("y").getAsDouble(), pos.get("z").getAsDouble(), action.has("speed") ? action.get("speed").getAsDouble() : 1);
						flag = true;
						break;
					}
				}
				if (!flag){
					entityCreature.tasks.addTask(0, new EntityAIMoveToLocation(entityCreature, pos.get("x").getAsDouble(), pos.get("y").getAsDouble(), pos.get("z").getAsDouble(), action.has("speed") ? action.get("speed").getAsDouble() : 1));
				}
			} else {
				MainRegistry.logger.warn("[Migraine] Entity is not of type EntityCreature in " + name + "!");
			}
		}
	}

	private void modifyEntity(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		Entity entity = worldRenderer.world.getEntityByID(action.get("entityId").getAsInt());
		if (entity != null){
			callFunctionOnFromJson(entity, action);
		}
	}

	private void removeEntityTasks(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		Entity entity = worldRenderer.world.getEntityByID(action.get("entityId").getAsInt());
		if (entity != null){
			if (entity instanceof EntityCreature){
				EntityCreature entityCreature = (EntityCreature) entity;
				entityCreature.tasks.taskEntries.clear();
			}
		}
	}


	// Called when the gui is opened
	public void init(GuiMigraine gui){
		try {
			if (this.json.has("owner")){
				JsonObject title = this.json.getAsJsonObject("owner");
				ItemStack itemStack = itemStackFromJson(title);
				String text;
				if (title.has("text")){
					text = I18nUtil.resolveKey(title.get("text").getAsString());
				}else{
					text = itemStack.getDisplayName();
				}
				gui.title = new MigraineDisplay(gui.getFontRenderer(), 40, 27, new Object[][]{{text}}, 0, -1, itemStack).setOrientation(MigraineDisplay.Orientation.LEFT).setColors(defaultColors.GOLD.colors);
			}
			if (this.json.has("seeAlso")){
				JsonArray also = this.json.getAsJsonArray("seeAlso");
				for (int i = 0; i < also.size(); i++){
					JsonObject display = also.get(i).getAsJsonObject();
					ItemStack itemStack = itemStackFromJson(display);
					String text;
					if (display.has("text")){
						text = I18nUtil.resolveKey(display.get("text").getAsString());
					}else{
						text = itemStack.getDisplayName();
					}
					gui.seealso.add(new MigraineDisplay(gui.getFontRenderer(), 40, 27 + 36 * (i + 1), new Object[][]{{text}}, 0, -1, itemStack).setOrientation(MigraineDisplay.Orientation.LEFT).setColors(defaultColors.GREY.colors));
				}
			}

			chapterPercents = new float[Math.max(chapters.size() - 2, 0)];
			int last = chapters.get(chapters.size() - 1);

			for (int i = 1; i <= chapterPercents.length; i++){
				chapterPercents[i - 1] = (float) chapters.get(i) / last;
			}

			progressBar = new MigraineBar(progressBarXRange[0], progressBarY, progressBarXRange[1] - progressBarXRange[0], chapterPercents);
			progressBar.setColors(defaultColors.COPPER.colors);
		}catch (Exception ex){
			MainRegistry.logger.warn("[Migraine] Error while initializing " + name + "!");
		}
	}

	// Updated every game tick, not render call
	public void update(GuiMigraine gui, int tickNum){
		try {
			ImmediateWorldSceneRenderer worldRenderer = gui.worldRenderer;
			JsonArray data = this.json.getAsJsonArray("update");

			if (chapters.contains(tickNum) && chapters.indexOf(0) != tickNum && tickNum != chapters.get(chapters.size() - 1)) gui.chapterNumber++;

			MainRegistry.logger.debug(gui.chapterNumber);
			MainRegistry.logger.debug(tickNum);

			gui.displays.forEach((MigraineDisplay display) -> {
				if (display.ticksRemaining <= 0) {
					gui.displays.remove(display);
				} else {
					display.ticksRemaining--;
				}
			});

			for (JsonElement actionElem : data) {
				JsonObject actionGroup = actionElem.getAsJsonObject();

				// If there is tick, then just do that exact tick
				// If there is start and end tick, then do startTick <= tickNum <= endTick, removing the upper or lower bound if it is -1
				if ((actionGroup.has("tick") && tickNum == actionGroup.get("tick").getAsInt()) || // works
					(actionGroup.has("startTick") && actionGroup.has("endTick") && (
						(actionGroup.get("startTick").getAsInt() != -1 && actionGroup.get("endTick").getAsInt() != -1 && tickNum >= actionGroup.get("startTick").getAsInt() && tickNum <= actionGroup.get("endTick").getAsInt()) || // works
							(actionGroup.get("startTick").getAsInt() == -1 && actionGroup.get("endTick").getAsInt() != -1 && tickNum <= actionGroup.get("endTick").getAsInt()) || // works
							((actionGroup.get("startTick").getAsInt() != -1 && actionGroup.get("endTick").getAsInt() == -1 && tickNum >= actionGroup.get("startTick").getAsInt()))))) { // works

					JsonObject action = actionGroup.getAsJsonObject("action");
					String type = action.get("type").getAsString();
					switch (type) {
						case "setBlock":
							setBlock(worldRenderer, action); // works
							break;
						case "placeBlock": // works
							placeBlock(gui, worldRenderer, action);
							break;
						case "fillBlocks":
							fillBlocks(worldRenderer, action); // works
							break;
						case "modifyTileEntity":
							modifyTileEntity(worldRenderer, action); // works // actually works now
							break;
						case "setCamera": // works
							if (action.has("pitch")) gui.pitch = action.get("pitch").getAsFloat();
							if (action.has("yaw")) gui.yaw = action.get("yaw").getAsFloat();
							if (action.has("zoom")) gui.zoom = action.get("zoom").getAsFloat();
							break;
						case "addCamera": // works
							if (action.has("pitch")) gui.pitch += action.get("pitch").getAsFloat();
							if (action.has("yaw")) gui.yaw += action.get("yaw").getAsFloat();
							if (action.has("zoom")) gui.zoom += action.get("zoom").getAsFloat();
							break;
						case "setCenter": // works
							setCenter(gui, worldRenderer, action);
							break;
						case "addCenter":
							addCenter(gui, worldRenderer, action);
							break;
						case "rotateTo": // work
							rotateTo(gui, worldRenderer, action);
							break;
						case "zoomTo": // work
							zoomTo(gui, worldRenderer, action);
							break;
						case "moveCenterTo": // works
							moveCenterTo(gui, worldRenderer, action);
							break;
						case "display": // at least the game didnt crash // welp it did // why no render // ah it was behind
							display(gui, worldRenderer, action);
							break;
						case "setTileEntityNBT": // i dont fucking know // works
							setTileEntityNBT(worldRenderer, action);
							break;
						case "spawnEntity": // works
							spawnEntity(worldRenderer, action);
							break;
						case "setEntityNBT":
							setEntityNBT(worldRenderer, action);
							break;
						case "setEntityTarget":
							setEntityTarget(worldRenderer, action);
							break;
						case "modifyEntity":
							modifyEntity(worldRenderer, action);
							break;
						case "removeEntityTasks":
							removeEntityTasks(worldRenderer, action);
							break;
					}
				}
			}

			for (JsonObject overTime : gui.active) {
				String type = overTime.get("type").getAsString();
				int forTicks = overTime.get("tickLeft").getAsInt();
				if (forTicks <= 0) {
					gui.active.remove(overTime);
					continue;
				}
				switch (type) {
					case "rotateTo":
						gui.yaw += overTime.get("addYaw").getAsFloat();
						gui.pitch += overTime.get("addPitch").getAsFloat();
						break;
					case "zoomTo":
						gui.zoom += overTime.get("addZoom").getAsFloat();
						break;
					case "moveCenterTo":
						Vector3f size = worldRenderer.world.getSize();
						Vector3f minPos = worldRenderer.world.getMinPos();
						gui.center = gui.center == null ? new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2) : gui.center;

						gui.center.x += overTime.get("addX").getAsFloat();
						gui.center.y += overTime.get("addY").getAsFloat();
						gui.center.z += overTime.get("addZ").getAsFloat();
						break;

				}
				overTime.remove("tickLeft");
				overTime.addProperty("tickLeft", --forTicks);
			}
		} catch (Exception ex){
			// I dont feel like trying to track down every single exception that could occur, but i dont want to crash the game if someone makes a bad file. Lets do this instead
			MainRegistry.logger.warn("[Migraine] An error occurred while running tick " + tickNum + " in " + name + "!");
		}
	}

	// Updated every render call, not game tick
	public void render(int mouseX, int mouseY, float partialTicks, int w, int h, GuiMigraine gui){
		GL11.glDisable(GL11.GL_LIGHTING);

		// Buttons
		Minecraft.getMinecraft().getTextureManager().bindTexture(guiUtil);
		RenderHelper.disableStandardItemLighting();
		GL11.glDisable(GL11.GL_DEPTH_TEST);

		int playButton = gui.isPaused ? 64 : 40;

		if(w / 2f + pauseButtonOffset[0] <= mouseX && w / 2f + pauseButtonOffset[0] + 24 > mouseX && h + pauseButtonOffset[1] < mouseY && h + pauseButtonOffset[1] + 24 >= mouseY)
			gui.drawTexturedModalRect(w / 2 + pauseButtonOffset[0], h + pauseButtonOffset[1], playButton, 24, 24, 24);
		else
			gui.drawTexturedModalRect(w / 2 + pauseButtonOffset[0], h + pauseButtonOffset[1], playButton, 48, 24, 24);

//		boolean leftHover = mouseX >= w / 2f + progressBarXRange[0] && mouseX <= w / 2f + progressBarXRange[0] + 9 && mouseY >= h + progressBarY && mouseY <= h + progressBarY + 14;
//		boolean rightHover = mouseX >= w / 2f + progressBarXRange[1] + 9 && mouseX <= w / 2f + progressBarXRange[1] + 18 && mouseY >= h + progressBarY && mouseY <= h + progressBarY + 14;
//		int hoverPixel = (mouseX > w / 2f + progressBarXRange[0] + 9 && mouseX < w / 2f + progressBarXRange[1] + 9 && mouseY >= h + progressBarY + 3 && mouseY <= h + progressBarY + 11) ? (mouseX - (w / 2f + progressBarXRange[0] + 10.5f)) / (progressBarXRange[1] - progressBarXRange[0] - 3f) : -10;

//		MainRegistry.logger.debug(((float) gui.ticks) / chapters.get(chapters.size() - 1));
		progressBar.update(w, h, Math.min(((float) gui.ticks) / chapters.get(chapters.size() - 1), 1f), mouseX, mouseY);

		// Title

		// I couldent figure out why the items went being lighting properly, but spaming this fixes it so fuck it
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		RenderHelper.enableStandardItemLighting();
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);

		Minecraft.getMinecraft().getTextureManager().bindTexture(guiUtil);
		RenderHelper.disableStandardItemLighting();

		gui.drawTexturedModalRect(15, 15, 136, 48, 24, 24);

		RenderHelper.enableGUIStandardItemLighting();
		gui.getItemRenderer().renderItemAndEffectIntoGUI(gui.getFontRenderer(), gui.mc.renderEngine, this.comparableStack.toStack(), 19, 19);
		gui.getItemRenderer().renderItemOverlayIntoGUI(gui.getFontRenderer(), gui.mc.renderEngine, this.comparableStack.toStack(), 19, 19, null);
		RenderHelper.disableStandardItemLighting();

		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		RenderHelper.enableStandardItemLighting();
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);

		if (15 <= mouseX && 39 > mouseX && 15 < mouseY && 39 >= mouseY) {
			gui.title.drawForegroundComponent(0, 0);
			GL11.glDisable(GL11.GL_LIGHTING);
		}

		// See also
		for (int i = 0; i < gui.seealso.size(); i++){
			MigraineDisplay display = gui.seealso.get(i);

			Minecraft.getMinecraft().getTextureManager().bindTexture(guiUtil);
			GL11.glDisable(GL11.GL_LIGHTING);
			gui.drawTexturedModalRect(15, 15 + 36 * (i + 1), 136, 72, 24, 24);
			RenderHelper.enableGUIStandardItemLighting();
			gui.getItemRenderer().renderItemAndEffectIntoGUI(gui.getFontRenderer(), gui.mc.renderEngine, display.getIcon(), 19, 19 + 36 * (i + 1));
			gui.getItemRenderer().renderItemOverlayIntoGUI(gui.getFontRenderer(), gui.mc.renderEngine, display.getIcon(), 19, 19 + 36 * (i + 1), null);
			RenderHelper.disableStandardItemLighting();

			GL11.glEnable(GL11.GL_LIGHTING);
			GL11.glEnable(GL11.GL_DEPTH_TEST);
			RenderHelper.enableStandardItemLighting();
			GL11.glEnable(GL12.GL_RESCALE_NORMAL);

			if(15 <= mouseX && 39 > mouseX && 15 + 36 * (i + 1) < mouseY && 39 + 36 * (i + 1) >= mouseY) {
				display.drawForegroundComponent(0, 0);
			}
		}

		// Render this on top ig
		gui.displays.forEach((MigraineDisplay display) -> {
			GL11.glPushMatrix();
			display.drawForegroundComponent(w, h);
			GL11.glPopMatrix();
		});

		GL11.glEnable(GL11.GL_LIGHTING);
	}

	public void onClick(int mouseX, int mouseY, int button, int width, int height, GuiMigraine gui){

		// TODO: lclick not rclick
		if(width / 2 + pauseButtonOffset[0] <= mouseX && width / 2 + pauseButtonOffset[0] + 24 > mouseX && height + pauseButtonOffset[1] < mouseY && height + pauseButtonOffset[1] + 24 >= mouseY) {
			gui.mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));

			gui.isPaused = !gui.isPaused;
		}

		int last = chapters.get(chapters.size() - 1);

		if (mouseX >= width / 2f + progressBarXRange[0] && mouseX <= width / 2f + progressBarXRange[0] + 9 && mouseY >= height + progressBarY && mouseY <= height + progressBarY + 14){
			if (gui.ticks != 0){
				skip(0, gui.ticks, gui);
				gui.mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
			}
		}

		if (mouseX >= width / 2f + progressBarXRange[1] + 9 && mouseX <= width / 2f + progressBarXRange[1] + 18 && mouseY >= height + progressBarY && mouseY <= height + progressBarY + 14){
			if (gui.ticks < last){
				skip(last, gui.ticks, gui);
				gui.mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
			}
		}

		if (mouseX > width / 2f + progressBarXRange[0] + 9 && mouseX < width / 2f + progressBarXRange[1] + 9 && mouseY >= height + progressBarY + 3 && mouseY <= height + progressBarY + 11){
			float percentClick = (mouseX - (width / 2f + progressBarXRange[0] + 9f)) / (progressBarXRange[1] - progressBarXRange[0] - 3f);
			boolean chaptered = false;
			for (float chapterPercent : chapterPercents){
				int centerPixel = (int) (width / 2f + progressBarXRange[0] + 9f + ((progressBarXRange[1] - progressBarXRange[0] - 3f) * chapterPercent));
				if (Math.abs(centerPixel - mouseX) <= 3){
					chaptered = true;
//					MainRegistry.logger.debug(chapterPercent);
					skip((int) (chapterPercent * last), gui.ticks, gui);
					break;
				}
			}
			if (!chaptered)
				skip((int) (percentClick * last), gui.ticks, gui);
			gui.mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
		}

		for(int i = 0; i < gui.seealso.size(); i++) {

			if(15 <= mouseX && 39 > mouseX && 15 + 36 * (i + 1) < mouseY && 39 + 36 * (i + 1) >= mouseY) {
				MigraineDisplay display = gui.seealso.get(i);
				MigraineInstructions instructions = MigraineLoader.instructions.get(new ComparableStack(display.getIcon()));
				if (instructions != null) {
					gui.mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
					FMLCommonHandler.instance().showGuiScreen(new GuiMigraine(instructions));
					return;
				} else {
					MainRegistry.logger.warn("[Migraine] Failed to load see-also Migraine in " + name + "!");
				}
			}
		}
	}

	private void skip(int toTick, int fromTick, GuiMigraine gui){
		if (Math.abs(toTick - fromTick) <= 1) return;
		if (toTick < fromTick){
			gui.worldRenderer.world.emptyWorld();
			gui.ticks = 0;
			gui.center = null;
			gui.chapterNumber = 0;
			gui.active.clear();
			gui.displays.clear();
			gui.pitch = 20f;
			gui.yaw = 50f;
			gui.zoom = 1f;

			boolean pausedTemp = gui.isPaused;
			gui.isPaused = false;
			for (int i = 0; i < toTick; i++){
				gui.updateScreen();
			}
			gui.updateCamera();
			gui.isPaused = pausedTemp;
		}
		else {
			boolean pausedTemp = gui.isPaused;
			gui.isPaused = false;
			for (int i = 0; i < Math.abs(toTick - fromTick); i++){
				gui.updateScreen();
			}
			gui.updateCamera();
			gui.isPaused = pausedTemp;
		}

	}

	private enum defaultColors {
		COPPER(0xFFFDCA88, 0xFFD57C4F, 0xFFAB4223, 0xFF1A1F22),
		GOLD(0xFFFFFDE0, 0xFFFAD64A, 0xFFDC9613, 0xFF1A1F22),
		BLUE(0xFFA5D9FF, 0xFF39ACFF, 0xFF1A6CA7, 0xFF1A1F22),
		GREY(0xFFD1D1D1, 0xFF919191, 0xFF5D5D5D, 0xFF302E36);

		public final int[] colors;

		defaultColors(int brighter, int frame, int darker, int background){
			this.colors = new int[]{brighter, frame, darker, background};
		}
	}
}
