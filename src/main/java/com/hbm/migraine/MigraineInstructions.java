package com.hbm.migraine;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.lib.RefStrings;
import com.hbm.main.MainRegistry;
import com.hbm.migraine.client.WorldSceneRenderer;
import com.hbm.migraine.world.EntityAIMoveToLocation;
import com.hbm.util.I18nUtil;
import com.hbm.util.Tuple;
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
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** @author kellen */
public class MigraineInstructions {

	public final String name;
	public final ComparableStack comparableStack;
	private final JsonObject json;
	private final List<Integer> chapters = new ArrayList<>();
	public final List<MigraineDisplay> seealso = new ArrayList<>();
	public final MigraineDisplay title;
	public final Vec3 size;

	private static final ResourceLocation guiUtil =  new ResourceLocation(RefStrings.MODID + ":textures/gui/gui_utility.png");

	private final MigraineBar progressBar;
	private final float[] chapterPercents;

	private static final int[] pauseButtonOffset = { -63, -36 };
	private static final int[] progressBarXRange = { -28, 52 };
	private static final int progressBarY = -31;

	public MigraineInstructions(String name, JsonObject object){
		this.name = name;

		// Im going to put everything in a try fuck you
		ComparableStack stack = new ComparableStack(Item.getItemFromBlock(Blocks.air));
		this.json = object;
		MigraineDisplay title = null;
		Vec3 size = null;
		MigraineBar progressBar = null;
		float[] chapterPercents = new float[0];
		try {
			JsonObject ownerInfo = object.getAsJsonObject("owner");
			stack = new ComparableStack(itemStackFromJson(ownerInfo));

			JsonArray chapArray = object.getAsJsonArray("chapters");
			this.chapters.add(0);
			for (int i = 0; i < chapArray.size(); i++){
				this.chapters.add(chapArray.get(i).getAsInt());
			}

			if (object.has("owner")){
				JsonObject owner = object.getAsJsonObject("owner");
				ItemStack itemStack = itemStackFromJson(owner);
				String text;
				if (owner.has("text")){
					if (owner.has("localized") && owner.get("localized").getAsBoolean())
						text = I18nUtil.resolveKey(owner.get("text").getAsString());
					else
						text = owner.get("text").getAsString();
				}else
					text = itemStack.getDisplayName();
				title = new MigraineDisplay(Minecraft.getMinecraft().fontRenderer, 40, 27, new Object[][]{{text}}, 0, -1, itemStack, false).setOrientation(MigraineDisplay.Orientation.LEFT).setColors(defaultColors.GOLD.colors);
			}
			if (object.has("seeAlso")){
				JsonArray also = object.getAsJsonArray("seeAlso");
				for (int i = 0; i < also.size(); i++){
					JsonObject display = also.get(i).getAsJsonObject();
					ItemStack itemStack = itemStackFromJson(display);
					String text;
					if (display.has("text")){
						if (display.has("localized") && display.get("localized").getAsBoolean())
							text = I18nUtil.resolveKey(display.get("text").getAsString());
						else
							text = display.get("text").getAsString();
					}else{
						text = itemStack.getDisplayName();
					}
					seealso.add(new MigraineDisplay(Minecraft.getMinecraft().fontRenderer, 40, 27 + 36 * (i + 1), new Object[][]{{text}}, 0, -1, itemStack, false).setOrientation(MigraineDisplay.Orientation.LEFT).setColors(defaultColors.GREY.colors));
				}
			}

			chapterPercents = new float[Math.max(chapters.size() - 2, 0)];
			int last = chapters.get(chapters.size() - 1);

			for (int i = 1; i <= chapterPercents.length; i++){
				chapterPercents[i - 1] = (float) chapters.get(i) / last;
			}

			progressBar = new MigraineBar(progressBarXRange[0], progressBarY, progressBarXRange[1] - progressBarXRange[0], chapterPercents);
			progressBar.setColors(defaultColors.COPPER.colors);

			if (object.has("size")) {
				JsonObject sise = object.getAsJsonObject("size");
				size = Vec3.createVectorHelper(sise.get("x").getAsDouble(), sise.get("y").getAsDouble(), sise.get("z").getAsDouble());
			}

		} catch (Exception ex){
			MainRegistry.logger.warn("[Migraine] You need to include a owner tag! Skipping " + name);
		}

		this.comparableStack = stack;
		this.title = title;
		this.size = size;
		this.progressBar = progressBar;
		this.chapterPercents = chapterPercents;
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

	private Tuple.Pair<Object[], Class[]> getParamsAndTypes(WorldSceneRenderer worldRenderer, JsonArray args){
		Object[] params = new Object[args.size()];
		Class[] types = new Class[args.size()];

		// Params currently implemented:
		// World (only migraine world)
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
				case "world":
					params[i] = arg.has("value") && arg.get("value").isJsonNull() ? null : worldRenderer.world;
					types[i] = World.class;
				case "itemStack":
					params[i] = arg.get("id").isJsonNull() ? null : itemStackFromJson(arg);
					types[i] = ItemStack.class;
					break;
				case "float":
					params[i] = arg.get("value").isJsonNull() ? 0f : arg.get("value").getAsFloat();
					types[i] = float.class;
					break;
				case "int":
					params[i] = arg.get("value").isJsonNull() ? 0 : arg.get("value").getAsInt();
					types[i] = int.class;
					break;
				case "string":
					params[i] = arg.get("value").isJsonNull() ? null : arg.get("value").getAsString();
					types[i] = String.class;
					break;
				case "double":
					params[i] = arg.get("value").isJsonNull() ? 0d : arg.get("value").getAsDouble();
					types[i] = double.class;
					break;
				case "long":
					params[i] = arg.get("value").isJsonNull() ? 0l : arg.get("value").getAsLong();
					types[i] = long.class;
					break;
				case "boolean":
					params[i] = arg.get("value").isJsonNull() ? false : arg.get("value").getAsBoolean();
					types[i] = boolean.class;
					break;
				case "short":
					params[i] = arg.get("value").isJsonNull() ? 0 : arg.get("value").getAsShort();
					types[i] = short.class;
					break;
				case "char":
					params[i] = arg.get("value").isJsonNull() ? '\u0000' : arg.get("value").getAsCharacter();
					types[i] = char.class;
					break;
				case "byte":
					params[i] = arg.get("value").isJsonNull() ? 0 : arg.get("value").getAsByte();
					types[i] = byte.class;
					break;
			}
		}

		return new Tuple.Pair<>(params, types);
	}

	private static Field getFieldRecursive(Class<?> clazz, String fieldName) throws NoSuchFieldException {
		Class<?> currentClass = clazz;
		while (currentClass != null) {
			try {
				return currentClass.getDeclaredField(fieldName);
			} catch (NoSuchFieldException e) {
				currentClass = currentClass.getSuperclass();
			}
		}

		throw new NoSuchFieldException(clazz.getName() + "." + fieldName);
	}

	private static Method getMethodRecursive(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
		Class<?> currentClass = clazz;
		while (currentClass != null) {
			try {
				return currentClass.getDeclaredMethod(methodName, parameterTypes);
			} catch (NoSuchMethodException e) {
				currentClass = currentClass.getSuperclass();
			}
		}

		// Copied from Class
		StringBuilder buf = new StringBuilder();
		buf.append("(");
		if (parameterTypes != null) {
			for (int i = 0; i < parameterTypes.length; i++) {
				if (i > 0) {
					buf.append(", ");
				}
				Class<?> c = parameterTypes[i];
				buf.append((c == null) ? "null" : c.getName());
			}
		}
		buf.append(")");
		throw new NoSuchMethodException(clazz.getName() + "." + methodName + buf.toString());
	}

	private void callFunctionOnFromJson(WorldSceneRenderer worldRenderer, Object toCallOn, JsonObject action){
		worldRenderer.world.isRemote = action.has("client") ? action.get("client").getAsBoolean() : true;
		// I know this looks bad, but its a fake world, so whats the worst someone could do?
		try {

			Object currentValue = toCallOn;
			if (action.has("callList")) {
				String callList = action.get("callList").getAsString();

				String[] lists = callList.split("\\.");

				for (int i = 0; i < lists.length; i++) {
					String stack = lists[i];
					Class objectClass = currentValue.getClass();
					if (stack.endsWith("()")) {
						stack = stack.replace("()", "");
						Tuple.Pair<Object[], Class[]> paramsTypes = getParamsAndTypes(worldRenderer, action.getAsJsonArray(callList.substring(0, callList.indexOf(stack)).replace("()", "") + stack));
						Method method = getMethodRecursive(objectClass, stack, paramsTypes.value);
						method.setAccessible(true);
						currentValue = method.invoke(currentValue, paramsTypes.key);
					} else {
						Field field = getFieldRecursive(objectClass, stack);
						field.setAccessible(true);
						currentValue = field.get(currentValue);
					}
				}
			}

			if (action.has("value")) {
				JsonArray array = new JsonArray();
				array.add(action.getAsJsonObject("value"));
				Tuple.Pair<Object[], Class[]> paramType = getParamsAndTypes(worldRenderer, array);
				currentValue = paramType.key[0];
			}

			if (action.has("setTo")){
				String setList = action.get("setTo").getAsString();

				String[] setLists = setList.split("\\.");

				Object setValue = toCallOn;
				for (int i = 0; i < setLists.length; i++){
					String stack = setLists[i];
					Class objectClass = setValue.getClass();
					if (stack.endsWith("()")){
						stack = stack.replace("()", "");
						Tuple.Pair<Object[], Class[]> paramsTypes = getParamsAndTypes(worldRenderer, action.getAsJsonArray(setList.substring(0, setList.indexOf(stack)).replace("()", "") + stack));
						Method method = getMethodRecursive(objectClass, stack, paramsTypes.value);
						method.setAccessible(true);
						setValue = method.invoke(setValue, paramsTypes.key);
					}else{
						Field field = getFieldRecursive(objectClass, stack);
						field.setAccessible(true);
						if (i == setLists.length - 1)
							field.set(setValue, currentValue);
						else
							setValue = field.get(setValue);
					}
				}
			}

		} catch (Exception ex) {
			MainRegistry.logger.warn("[Migraine] Error modifying object in " + name + "!");
			ex.printStackTrace();
		}
		worldRenderer.world.isRemote = true;
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

		MigraineDisplay display = new MigraineDisplay(Minecraft.getMinecraft().fontRenderer, pos.get("x").getAsInt(), pos.get("y").getAsInt(), toDisplay, action.has("autowrap") ? action.get("autowrap").getAsInt() : 0, action.get("ticksFor").getAsInt(), action.has("arrowInverted") ? action.get("arrowInverted").getAsBoolean() : false);

		if (action.has("colors")){
			display.setColors(defaultColors.valueOf(action.get("colors").getAsString()).colors);
		}else {
			if (action.has("backgroundColor"))
				display.colorBg = Long.parseLong(action.get("backgroundColor").getAsString(), 16);
			if (action.has("darkerColor"))
				display.colorDarker = Long.parseLong(action.get("darkerColor").getAsString(), 16);
			if (action.has("lighterColor"))
				display.colorBrighter = Long.parseLong(action.get("lighterColor").getAsString(), 16);
			if (action.has("frameColor"))
				display.colorFrame = Long.parseLong(action.get("frameColor").getAsString(), 16);
		}
		if (action.has("orientation")) display.setOrientation(MigraineDisplay.Orientation.valueOf(action.get("orientation").getAsString()));

		return display;
	}

	private void setBlock(WorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");

		String[] id = action.get("id").getAsString().split(":");

		worldRenderer.world.setBlock(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt(), GameRegistry.findBlock(id[0], id[1]), action.has("meta") ? action.get("meta").getAsInt() : 0, 3);
	}

	private void placeBlock(GuiMigraine gui, WorldSceneRenderer worldRenderer, JsonObject action){

		String[] id = action.get("id").getAsString().split(":");

		Block block = GameRegistry.findBlock(id[0], id[1]);

		JsonObject pos = action.getAsJsonObject("position");

		float prevYaw = gui.FAKE_PLAYER.rotationYaw;
		gui.FAKE_PLAYER.rotationYaw = action.has("yaw") ? action.get("yaw").getAsFloat() : 0f;
		gui.FAKE_PLAYER.capabilities.isCreativeMode = true;

		worldRenderer.world.isRemote = false;
		block.onBlockPlacedBy(worldRenderer.world, pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt(), gui.FAKE_PLAYER, itemStackFromJson(action));
		worldRenderer.world.isRemote = true;

		gui.FAKE_PLAYER.rotationYaw = prevYaw;
	}

	private void fillBlocks(WorldSceneRenderer worldRenderer, JsonObject action){
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

	private void modifyTileEntity(WorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");
		TileEntity te = worldRenderer.world.getTileEntity(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt());
		if (te != null) {
			callFunctionOnFromJson(worldRenderer, te, action);
		} else {
			MainRegistry.logger.warn("[Migraine] Tried to access TileEntity at " + pos.get("x").getAsInt() + ", " + pos.get("y").getAsInt() + ", " + pos.get("z").getAsInt() + ", but failed in " + name + "!");
		}
	}

	private void setCenter(GuiMigraine gui, WorldSceneRenderer worldRenderer, JsonObject action){
		JsonElement val = action.get("position");
		if (val.isJsonNull()){
			gui.camera = null;
		}else{
			JsonObject pos = val.getAsJsonObject();
			gui.camera = Vec3.createVectorHelper(pos.get("x").getAsDouble(), pos.get("y").getAsDouble(), pos.get("z").getAsDouble());
		}
	}

	private void addCenter(GuiMigraine gui, WorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");

		if (gui.camera != null) {
			gui.camera = gui.camera.addVector(pos.get("x").getAsDouble(), pos.get("y").getAsDouble(), pos.get("z").getAsDouble());
		}
	}

	private void rotateTo(GuiMigraine gui, WorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("ticksFor").getAsInt();
		toAdd.addProperty("type", "rotateTo");
		toAdd.addProperty("tickLeft", forTicks);
		toAdd.addProperty("addYaw", (action.get("yaw").getAsFloat() - gui.yaw) / forTicks);
		toAdd.addProperty("addPitch", (action.get("pitch").getAsFloat() - gui.pitch) / forTicks);
		gui.active.add(toAdd);
	}

	private void zoomTo(GuiMigraine gui, WorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("ticksFor").getAsInt();
		toAdd.addProperty("type", "zoom");
		toAdd.addProperty("tickLeft", forTicks);
		toAdd.addProperty("addZoom", (action.get("targetZoom").getAsFloat() - gui.zoom) / forTicks);
		gui.active.add(toAdd);
	}

	private void moveCenterTo(GuiMigraine gui, WorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("ticksFor").getAsInt();
		toAdd.addProperty("type", "moveCenterTo");
		toAdd.addProperty("tickLeft", forTicks);
		JsonObject targetPos = action.getAsJsonObject("position");

		toAdd.addProperty("addX", (targetPos.get("x").getAsFloat() - gui.camera.xCoord) / forTicks);
		toAdd.addProperty("addY", (targetPos.get("y").getAsFloat() - gui.camera.yCoord) / forTicks);
		toAdd.addProperty("addZ", (targetPos.get("z").getAsFloat() - gui.camera.zCoord) / forTicks);

		gui.active.add(toAdd);
	}

	private void display(GuiMigraine gui, WorldSceneRenderer worldRenderer, JsonObject action){
		gui.displays.add(getDisplayFromJson(action));
	}

	/**
	 * If you are struggling with nbt, take a look at {@link net.minecraft.nbt.JsonToNBT}. It's the same format used in setblock, but setblock is more forgiving for some reason
	 * @param worldRenderer
	 * @param action
	 */
	private void setTileEntityNBT(WorldSceneRenderer worldRenderer, JsonObject action){
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

	private void modifyTileEntityNBT(WorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");

		int x = pos.get("x").getAsInt();
		int y = pos.get("y").getAsInt();
		int z = pos.get("z").getAsInt();

		TileEntity tile = worldRenderer.world.getTileEntity(x, y, z);

		if (tile != null){
			try {
				NBTTagCompound nbt = new NBTTagCompound();
				tile.writeToNBT(nbt);

				NBTTagCompound values = (NBTTagCompound) JsonToNBT.func_150315_a(action.get("nbt").getAsString());

				for (Object key : values.func_150296_c()){
					nbt.setTag((String) key, values.getTag((String) key));
				}

				tile.readFromNBT(nbt);
			} catch(NBTException ex){
				MainRegistry.logger.warn("[Migraine] Failed to read NBT String while modifying tile entity nbt in " + name + "!");
			}
		}
	}

	private void spawnEntity(WorldSceneRenderer worldRenderer, JsonObject action){
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

	private void setEntityNBT(WorldSceneRenderer worldRenderer, JsonObject action){

		Entity entity = worldRenderer.world.getEntityByID(action.get("entityId").getAsInt());
		if (entity != null){
			try{
				entity.readFromNBT((NBTTagCompound) JsonToNBT.func_150315_a(action.get("nbt").getAsString()));
			} catch (Exception ex){
				MainRegistry.logger.warn("[Migraine] Failed to load entity from nbt in " + name + "!");
			}
		}
	}

	private void setEntityTarget(WorldSceneRenderer worldRenderer, JsonObject action){
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

	private void modifyEntity(WorldSceneRenderer worldRenderer, JsonObject action){
		Entity entity = worldRenderer.world.getEntityByID(action.get("entityId").getAsInt());
		if (entity != null){
			callFunctionOnFromJson(worldRenderer, entity, action);
		}
	}

	private void removeEntityTasks(WorldSceneRenderer worldRenderer, JsonObject action){
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
			gui.isometric = this.json.has("isometric") ? this.json.get("isometric").getAsBoolean() : true;
			gui.worldRenderer.setSize(size);
		}catch (Exception ex){
			MainRegistry.logger.warn("[Migraine] Error while initializing " + name + "!");
		}
	}

	// Updated every game tick, not render call
	public void update(GuiMigraine gui, int tickNum){
		String currentTask = "none";
		try {
			currentTask = "displays";
			WorldSceneRenderer worldRenderer = gui.worldRenderer;
			JsonArray data = this.json.getAsJsonArray("update");

			if (chapters.contains(tickNum) && chapters.indexOf(0) != tickNum && tickNum != chapters.get(chapters.size() - 1)) gui.chapterNumber++;

			HashSet<MigraineDisplay> toRemove = new HashSet<>();
			gui.displays.forEach((MigraineDisplay display) -> {
				display.ticksRemaining--;
				if (display.ticksRemaining <= 0)
					toRemove.add(display);
			});

			gui.displays.removeAll(toRemove);

			Multimap<Integer, JsonObject> priorities = HashMultimap.create();

			for (JsonElement actionElem : data) {
				JsonObject actionGroup = actionElem.getAsJsonObject();

				// If there is tick, then just do that exact tick
				// If there is start and end tick, then do startTick <= tickNum <= endTick, removing the upper or lower bound if it is -1
				if ((actionGroup.has("tick") && tickNum == actionGroup.get("tick").getAsInt()) ||
					(actionGroup.has("tickStart") && actionGroup.has("tickEnd") && (
						(actionGroup.get("tickStart").getAsInt() != -1 && actionGroup.get("tickEnd").getAsInt() != -1 && tickNum >= actionGroup.get("tickStart").getAsInt() && tickNum <= actionGroup.get("tickEnd").getAsInt()) ||
						(actionGroup.get("tickStart").getAsInt() == -1 && actionGroup.get("tickEnd").getAsInt() != -1 && tickNum <= actionGroup.get("tickEnd").getAsInt()) ||
						(actionGroup.get("tickStart").getAsInt() != -1 && actionGroup.get("tickEnd").getAsInt() == -1 && tickNum >= actionGroup.get("tickStart").getAsInt())))) {

					JsonObject action = actionGroup.getAsJsonObject("action");
					priorities.put(actionGroup.has("priority") ? actionGroup.get("priority").getAsInt() : 0, action);
				}
			}

			List<Integer> priorityNums = new ArrayList<>(priorities.keys());

			priorityNums.sort(Integer::compareTo);

			// Bigger numbers first
			for (int i = priorityNums.size() - 1; i >= 0; i--){
				for (JsonObject action : priorities.get(priorityNums.get(i))){
					String type = action.get("type").getAsString();
					currentTask = type;
					switch (type) {
						case "setBlock":
							setBlock(worldRenderer, action);
							break;
						case "placeBlock":
							placeBlock(gui, worldRenderer, action);
							break;
						case "fillBlocks":
							fillBlocks(worldRenderer, action);
							break;
						case "modifyTileEntity":
							modifyTileEntity(worldRenderer, action);
							break;
						case "setCamera":
							if (action.has("pitch")) gui.pitch = action.get("pitch").getAsFloat();
							if (action.has("yaw")) gui.yaw = action.get("yaw").getAsFloat();
							if (action.has("zoom")) gui.zoom = action.get("zoom").getAsFloat();
							break;
						case "addCamera":
							if (action.has("pitch")) gui.pitch += action.get("pitch").getAsFloat();
							if (action.has("yaw")) gui.yaw += action.get("yaw").getAsFloat();
							if (action.has("zoom")) gui.zoom += action.get("zoom").getAsFloat();
							break;
						case "setCenter":
							setCenter(gui, worldRenderer, action);
							break;
						case "addCenter":
							addCenter(gui, worldRenderer, action);
							break;
						case "rotateTo":
							rotateTo(gui, worldRenderer, action);
							break;
						case "zoomTo":
							zoomTo(gui, worldRenderer, action);
							break;
						case "moveCenterTo":
							moveCenterTo(gui, worldRenderer, action);
							break;
						case "display":
							display(gui, worldRenderer, action);
							break;
						case "setTileEntityNBT":
							setTileEntityNBT(worldRenderer, action);
							break;
						case "modifyTileEntityNBT":
							modifyTileEntityNBT(worldRenderer, action);
							break;
						case "spawnEntity":
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

			currentTask = "active actions";
			for (JsonObject overTime : gui.active) {
				String type = overTime.get("type").getAsString();
				currentTask = type;

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
						if (gui.camera != null) {
							gui.camera.xCoord += overTime.get("addX").getAsFloat();
							gui.camera.yCoord += overTime.get("addY").getAsFloat();
							gui.camera.zCoord += overTime.get("addZ").getAsFloat();
						}
						break;

				}
				overTime.remove("tickLeft");
				overTime.addProperty("tickLeft", --forTicks);
			}
		} catch (Exception ex){
			// I dont feel like trying to track down every single exception that could occur, but i dont want to crash the game if someone makes a bad file. Lets do this instead
			MainRegistry.logger.warn("[Migraine] An error occurred while running tick " + tickNum + " in " + name + ", doing task: " + currentTask + "!");
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

		progressBar.update(w, h, Math.min(((float) gui.ticks) / chapters.get(chapters.size() - 1), 1f), mouseX, mouseY);

		// I couldent figure out why the items went being lit properly, but spaming this fixes it so fuck it
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		RenderHelper.enableStandardItemLighting();
		GL11.glEnable(GL12.GL_RESCALE_NORMAL);

		// Render this below title and see also but above buttons, cuz that just makes sense
		gui.displays.forEach((MigraineDisplay display) -> {
			GL11.glPushMatrix();
			display.drawForegroundComponent(w, h);
			GL11.glPopMatrix();
		});

		// Title

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
			title.drawForegroundComponent(0, 0);
			GL11.glDisable(GL11.GL_LIGHTING);
		}

		// See also
		for (int i = 0; i < seealso.size(); i++){
			GL11.glPushMatrix();
			MigraineDisplay display = seealso.get(i);

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
			GL11.glPopMatrix();
		}

		GL11.glEnable(GL11.GL_LIGHTING);
	}

	public void onClick(int mouseX, int mouseY, int button, int width, int height, GuiMigraine gui){
		if (button == 0) {

			if (width / 2 + pauseButtonOffset[0] <= mouseX && width / 2 + pauseButtonOffset[0] + 24 > mouseX && height + pauseButtonOffset[1] < mouseY && height + pauseButtonOffset[1] + 24 >= mouseY) {
				gui.mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));

				gui.isPaused = !gui.isPaused;
			}

			int last = chapters.get(chapters.size() - 1);

			if (mouseX >= width / 2f + progressBarXRange[0] && mouseX <= width / 2f + progressBarXRange[0] + 9 && mouseY >= height + progressBarY && mouseY <= height + progressBarY + 14) {
				if (gui.ticks != 0) {
					skip(0, gui.ticks, gui);
					gui.mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
				}
			}

			if (mouseX >= width / 2f + progressBarXRange[1] + 9 && mouseX <= width / 2f + progressBarXRange[1] + 18 && mouseY >= height + progressBarY && mouseY <= height + progressBarY + 14) {
				if (gui.ticks < last) {
					skip(last, gui.ticks, gui);
					gui.mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
				}
			}

			if (mouseX > width / 2f + progressBarXRange[0] + 9 && mouseX < width / 2f + progressBarXRange[1] + 9 && mouseY >= height + progressBarY + 3 && mouseY <= height + progressBarY + 11) {
				float percentClick = (mouseX - (width / 2f + progressBarXRange[0] + 9f)) / (progressBarXRange[1] - progressBarXRange[0] - 3f);
				boolean chaptered = false;
				for (float chapterPercent : chapterPercents) {
					int centerPixel = (int) (width / 2f + progressBarXRange[0] + 9f + ((progressBarXRange[1] - progressBarXRange[0] - 3f) * chapterPercent));
					if (Math.abs(centerPixel - mouseX) <= 2) {
						chaptered = true;
						skip((int) (chapterPercent * last), gui.ticks, gui);
						break;
					}
				}
				if (!chaptered)
					skip((int) (percentClick * last), gui.ticks, gui);
				gui.mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
			}

			for (int i = 0; i < seealso.size(); i++) {

				if (15 <= mouseX && 39 > mouseX && 15 + 36 * (i + 1) < mouseY && 39 + 36 * (i + 1) >= mouseY) {
					MigraineDisplay display = seealso.get(i);
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
	}

	public void skip(int toTick, int fromTick, GuiMigraine gui){
		// TODO: maybe make it so it saves from chapters or something so it doesnt need to redo the entire world in one frame?
		if (Math.abs(toTick - fromTick) < 1) return;
		if (toTick < fromTick){
			gui.worldRenderer.world.emptyWorld();
			gui.ticks = 0;
			gui.camera = Vec3.createVectorHelper(0, 0,0);
			gui.chapterNumber = 0;
			gui.active.clear();
			gui.displays.clear();
			gui.pitch = -30f;
			gui.yaw = -45f;
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
