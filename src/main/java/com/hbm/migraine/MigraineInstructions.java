package com.hbm.migraine;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
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
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import static com.hbm.inventory.OreDictManager.NB;

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
			JsonObject owner = object.getAsJsonObject("owner");
			stack = new ComparableStack(itemStackFromJson(owner));

			JsonArray chapArray = object.getAsJsonArray("chapters");
			this.chapters.add(0);
			for (int i = 0; i < chapArray.size(); i++){
				this.chapters.add(chapArray.get(i).getAsInt());
			}

			String displayName;
			if (owner.has("text")){
				displayName = getLocalizedString(owner);
			}else
				displayName = stack.toStack().getDisplayName();
			title = new MigraineDisplay(Minecraft.getMinecraft().fontRenderer, 40, 27, new Object[][]{{displayName}}, 0, -1, stack.toStack(), false).setOrientation(MigraineDisplay.Orientation.LEFT).setColors(defaultColors.GOLD.colors);
			
			JsonArray also = Parameter.getParam(object, "seeAlso").jsonArray();
			if (also != null) {
				for (int i = 0; i < also.size(); i++){
					JsonObject display = also.get(i).getAsJsonObject();
					ItemStack itemStack = itemStackFromJson(display);
					String text;
					if (display.has("text")){
						text = getLocalizedString(display);
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

			size = Parameter.getParam(object, "size").vec3();

		} catch (Exception ex){
			MainRegistry.logger.warn("[Migraine] Error initalizing! Skipping " + name);
		}

		this.comparableStack = stack;
		this.title = title;
		this.size = size;
		this.progressBar = progressBar;
		this.chapterPercents = chapterPercents;
	}

	private String getLocalizedString(JsonObject parent){
		return getLocalizedString(parent, "text");
	}

	private String getLocalizedString(JsonObject parent, String textName){
		String text = Parameter.getParam(parent, textName).string();
		if (Parameter.getParam(parent, "localized").bool(false))
			text = I18nUtil.resolveKey(text);
		return text;
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

		ItemStack stack = GameRegistry.findItemStack(id[0], id[1], Parameter.getParam(item, "count").integer(1));

		ItemStack out = stack.copy();
		out.setItemDamage(Parameter.getParam(item, "meta").integer(0));

		if (item.has("nbt")){
			try {
				out.setTagCompound((NBTTagCompound) JsonToNBT.func_150315_a(item.get("nbt").getAsString()));
			}catch(Exception ex){
				MainRegistry.logger.warn("[Migraine] Error setting item tag in " + name + "!");
			}
		}

		return out;
	}

	private Tuple.Pair<Object[], Class<?>[]> getParamsAndTypes(WorldSceneRenderer worldRenderer, JsonArray args){
		Object[] params = new Object[args.size()];
		Class<?>[] types = new Class[args.size()];

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
		worldRenderer.world.isRemote = !action.has("client") || action.get("client").getAsBoolean();
		// I know this looks bad, but its a fake world, so whats the worst someone could do?
		try {

			Object currentValue = toCallOn;
			if (action.has("callList")) {
				String callList = action.get("callList").getAsString();

				String[] lists = callList.split("\\.");

				for (int i = 0; i < lists.length; i++) {
					String stack = lists[i];
					Class<?> objectClass = currentValue.getClass();
					if (stack.endsWith("()")) {
						stack = stack.replace("()", "");
						Tuple.Pair<Object[], Class<?>[]> paramsTypes = getParamsAndTypes(worldRenderer, action.getAsJsonArray(callList.substring(0, callList.indexOf(stack)).replace("()", "") + stack));
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
				Tuple.Pair<Object[], Class<?>[]> paramType = getParamsAndTypes(worldRenderer, array);
				currentValue = paramType.key[0];
			}

			if (action.has("setTo")){
				String setList = action.get("setTo").getAsString();

				String[] setLists = setList.split("\\.");

				Object setValue = toCallOn;
				for (int i = 0; i < setLists.length; i++){
					String stack = setLists[i];
					Class<?> objectClass = setValue.getClass();
					if (stack.endsWith("()")){
						stack = stack.replace("()", "");
						Tuple.Pair<Object[], Class<?>[]> paramsTypes = getParamsAndTypes(worldRenderer, action.getAsJsonArray(setList.substring(0, setList.indexOf(stack)).replace("()", "") + stack));
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
						currentLine[i] = getLocalizedString(data, "value");
						break;
					default:
						currentLine[i] = "";
				}
			}
			toDisplay[j] = currentLine;
		}

		MigraineDisplay display = new MigraineDisplay(Minecraft.getMinecraft().fontRenderer, pos.get("x").getAsInt(), pos.get("y").getAsInt(), toDisplay, Parameter.getParam(action, "autowrap").integer(0), action.get("ticksFor").getAsInt(), Parameter.getParam(action, "arrowInverted").bool(false));

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

		String[] id = action.get("id").getAsString().split(":");

		Vec3 pos = Parameter.getParam(action, "position").vec3();

		worldRenderer.world.setBlock((int) pos.xCoord, (int) pos.yCoord, (int) pos.zCoord, GameRegistry.findBlock(id[0], id[1]), Parameter.getParam(action, "meta").integer(0), 3);
	}

	private void placeBlock(GuiMigraine gui, WorldSceneRenderer worldRenderer, JsonObject action){

		String[] id = action.get("id").getAsString().split(":");

		Block block = GameRegistry.findBlock(id[0], id[1]);

		Vec3 pos = Parameter.getParam(action, "position").vec3();

		float prevYaw = gui.FAKE_PLAYER.rotationYaw;
		gui.FAKE_PLAYER.rotationYaw = Parameter.getParam(action, "yaw").floatValue(0f);
		gui.FAKE_PLAYER.capabilities.isCreativeMode = true;

		ItemStack itemStack = itemStackFromJson(action);

		block.onBlockPlacedBy(worldRenderer.world, (int) pos.xCoord, (int) pos.yCoord, (int) pos.zCoord, gui.FAKE_PLAYER.client, itemStack);
		worldRenderer.world.isRemote = false;
		block.onBlockPlacedBy(worldRenderer.world, (int) pos.xCoord, (int) pos.yCoord, (int) pos.zCoord, gui.FAKE_PLAYER, itemStack);
		worldRenderer.world.isRemote = true;

		gui.FAKE_PLAYER.rotationYaw = prevYaw;
	}

	private void fillBlocks(WorldSceneRenderer worldRenderer, JsonObject action){
		Vec3 posMin = Parameter.getParam(action, "positionMin").vec3();
		Vec3 posMax = Parameter.getParam(action, "positionMax").vec3();

		String[] id = action.get("id").getAsString().split(":");

		Block block = GameRegistry.findBlock(id[0], id[1]);

		for (int x = (int) posMin.xCoord; x <= (int) posMax.xCoord; x++){
			for (int y = (int) posMin.yCoord; y <= (int) posMax.yCoord; y++){
				for (int z = (int) posMin.zCoord; z <= (int) posMax.zCoord; z++){
					worldRenderer.world.setBlock(x, y, z, block, Parameter.getParam(action, "meta").integer(0), 3);
					worldRenderer.world.markBlockForUpdate(x, y, z);
				}
			}
		}
	}

	private void modifyTileEntity(WorldSceneRenderer worldRenderer, JsonObject action){
		Vec3 pos = Parameter.getParam(action, "position").vec3();
		TileEntity te = worldRenderer.world.getTileEntity((int) pos.xCoord, (int) pos.yCoord, (int) pos.zCoord);
		if (te != null) {
			callFunctionOnFromJson(worldRenderer, te, action);
		} else {
			MainRegistry.logger.warn("[Migraine] Tried to access TileEntity at " + pos.xCoord + ", " + pos.yCoord + ", " + pos.zCoord + ", but failed in " + name + "!");
		}
	}

	private void setCenter(GuiMigraine gui, JsonObject action){
		JsonElement val = action.get("position");
		if (val.isJsonNull()){
			gui.camera = null;
		}else{
			gui.camera = Parameter.getParam(action, "position").vec3();
		}
	}

	private void addCenter(GuiMigraine gui, JsonObject action){
		Vec3 pos = Parameter.getParam(action, "position").vec3();

		if (gui.camera != null) {
			gui.camera = gui.camera.addVector(pos.xCoord, pos.yCoord, pos.zCoord);
		}
	}

	private void rotateTo(GuiMigraine gui, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("ticksFor").getAsInt();
		toAdd.addProperty("type", "rotateTo");
		toAdd.addProperty("tickLeft", forTicks);
		toAdd.addProperty("addYaw", (action.get("yaw").getAsDouble() - gui.yaw) / forTicks);
		toAdd.addProperty("addPitch", (action.get("pitch").getAsDouble() - gui.pitch) / forTicks);
		gui.active.add(toAdd);
	}

	private void zoomTo(GuiMigraine gui, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("ticksFor").getAsInt();
		toAdd.addProperty("type", "zoomTo");
		toAdd.addProperty("tickLeft", forTicks);
		toAdd.addProperty("addZoom", (action.get("zoom").getAsDouble() - gui.zoom) / forTicks);
		gui.active.add(toAdd);
	}

	private void moveCenterTo(GuiMigraine gui, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("ticksFor").getAsInt();
		toAdd.addProperty("type", "moveCenterTo");
		toAdd.addProperty("tickLeft", forTicks);
		Vec3 targetPos = Parameter.getParam(action, "position").vec3();

		toAdd.addProperty("addX", (targetPos.xCoord - gui.camera.xCoord) / forTicks);
		toAdd.addProperty("addY", (targetPos.yCoord - gui.camera.yCoord) / forTicks);
		toAdd.addProperty("addZ", (targetPos.zCoord - gui.camera.zCoord) / forTicks);

		gui.active.add(toAdd);
	}

	private void display(GuiMigraine gui, JsonObject action){
		gui.displays.add(getDisplayFromJson(action));
	}

	/**
	 * If you are struggling with nbt, take a look at {@link net.minecraft.nbt.JsonToNBT}. It's the same format used in setblock, but setblock is more forgiving for some reason
	 * @param worldRenderer
	 * @param action
	 */
	private void setTileEntityNBT(WorldSceneRenderer worldRenderer, JsonObject action){
		Vec3 pos = Parameter.getParam(action, "position").vec3();

		TileEntity tile = worldRenderer.world.getTileEntity((int) pos.xCoord, (int) pos.yCoord, (int) pos.zCoord);

		if (tile != null){
			try {
				NBTTagCompound nbt = (NBTTagCompound) JsonToNBT.func_150315_a(action.get("nbt").getAsString());
				nbt.setInteger("x", (int) pos.xCoord);
				nbt.setInteger("y", (int) pos.yCoord);
				nbt.setInteger("z", (int) pos.zCoord);
				tile.readFromNBT(nbt);
			} catch(NBTException ex){
				MainRegistry.logger.warn("[Migraine] Failed to read NBT String while setting tile entity nbt in " + name + "!");
			}
		}
	}

	private void modifyTileEntityNBT(WorldSceneRenderer worldRenderer, JsonObject action){
		Vec3 pos = Parameter.getParam(action, "position").vec3();

		TileEntity tile = worldRenderer.world.getTileEntity((int) pos.xCoord, (int) pos.yCoord, (int) pos.zCoord);

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
		Vec3 pos = Parameter.getParam(action, "position").vec3();

		try {

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

			entity.setLocationAndAngles(pos.xCoord, pos.yCoord, pos.zCoord, action.has("yaw") ? action.get("yaw").getAsFloat() : entity.rotationYaw, action.has("pitch") ? action.get("pitch").getAsFloat() : entity.rotationPitch);

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
					entity1.setLocationAndAngles(pos.xCoord, pos.yCoord, pos.zCoord, entity1.rotationYaw, entity1.rotationPitch);
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

	// I think this is bugged for setting it when it is already set, but im not to sure
	private void setEntityTarget(WorldSceneRenderer worldRenderer, JsonObject action){
		Entity entity = worldRenderer.world.getEntityByID(action.get("entityId").getAsInt());
		if (entity != null){
			Vec3 pos = Parameter.getParam(action, "position").vec3();
			if (entity instanceof EntityCreature){
				EntityCreature entityCreature = (EntityCreature) entity;
				boolean flag = false;
				for (Object task : entityCreature.tasks.taskEntries) {
					EntityAITasks.EntityAITaskEntry entry = (EntityAITasks.EntityAITaskEntry) task;
					if (entry.action instanceof EntityAIMoveToLocation) {
						((EntityAIMoveToLocation) entry.action).updateTarget(pos.xCoord, pos.yCoord, pos.zCoord, Parameter.getParam(action, "speed").doubleValue(1));
						flag = true;
						break;
					}
				}
				if (!flag){
					entityCreature.tasks.addTask(0, new EntityAIMoveToLocation(entityCreature, pos.xCoord, pos.yCoord, pos.zCoord, Parameter.getParam(action, "speed").doubleValue(1)));
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
		if (entity instanceof EntityCreature){
				EntityCreature entityCreature = (EntityCreature) entity;
				entityCreature.tasks.taskEntries.clear();
			}
		
	}


	// Called when the gui is opened
	public void init(GuiMigraine gui){
		try {
			gui.isometric = Parameter.getParam(json, "isometric").bool(true);
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
				if (display.ticksRemaining != -1) {
					display.ticksRemaining--;
					if (display.ticksRemaining <= 0)
						toRemove.add(display);
				}
			});

			gui.displays.removeAll(toRemove);

			Multimap<Integer, JsonObject> priorities = HashMultimap.create();

			for (JsonElement actionElem : data) {
				JsonObject actionGroup = actionElem.getAsJsonObject();

				int tick = Parameter.getParam(actionGroup, "tick").integer(-2);
				int tickStart = Parameter.getParam(actionGroup, "tickStart").integer(-2);
				int tickEnd = Parameter.getParam(actionGroup, "tickEnd").integer(-2);

				// If there is tick, then just do that exact tick
				// If there is start and end tick, then do startTick <= tickNum <= endTick, removing the upper or lower bound if it is -1
				if ((tickNum == tick) ||
					(tickStart != -2 && tickEnd != -2 && (
						(tickStart != -1 && tickEnd != -1 && tickNum >= tickStart && tickNum <= tickEnd) ||
						(tickStart == -1 && tickEnd != -1 && tickNum <= tickEnd) ||
						(tickStart != -1 && tickEnd == -1 && tickNum >= tickStart) ||
						(tickStart == -1 && tickEnd == -1))
					)
				){
					JsonObject action = actionGroup.getAsJsonObject("action");
					priorities.put(actionGroup.has("priority") ? actionGroup.get("priority").getAsInt() : 0, action);
				}
			}

			List<Integer> priorityNums = new ArrayList<>(priorities.keySet());

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
							setCenter(gui, action);
							break;
						case "addCenter":
							addCenter(gui, action);
							break;
						case "rotateTo":
							rotateTo(gui, action);
							break;
						case "zoomTo":
							zoomTo(gui, action);
							break;
						case "moveCenterTo":
							moveCenterTo(gui, action);
							break;
						case "display":
							display(gui, action);
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
						gui.yaw += overTime.get("addYaw").getAsDouble();
						gui.pitch += overTime.get("addPitch").getAsDouble();
						break;
					case "zoomTo":
						gui.zoom += overTime.get("addZoom").getAsDouble();
						break;
					case "moveCenterTo":
						if (gui.camera != null) {
							gui.camera.xCoord += overTime.get("addX").getAsDouble();
							gui.camera.yCoord += overTime.get("addY").getAsDouble();
							gui.camera.zCoord += overTime.get("addZ").getAsDouble();
						}
						break;

				}
				overTime.remove("tickLeft");
				overTime.addProperty("tickLeft", --forTicks);
			}

			// Checkpoints
			if (chapters.contains(tickNum) && !gui.chapterSaves.containsKey(tickNum)) {
				NBTTagCompound nbt = gui.worldRenderer.world.writeToNBT();
				nbt.setInteger("tick", tickNum);
				nbt.setInteger("chapter", gui.chapterNumber);
				nbt.setFloat("pitch", gui.pitch);
				nbt.setFloat("yaw", gui.yaw);
				nbt.setFloat("zoom", gui.zoom);
				nbt.setBoolean("isometric", gui.isometric);
				nbt.setBoolean("isCameraSet", gui.camera != null);
				if (gui.camera != null) {
					nbt.setDouble("cameraX", gui.camera.xCoord);
					nbt.setDouble("cameraY", gui.camera.yCoord);
					nbt.setDouble("cameraZ", gui.camera.zCoord);
				}
				NBTTagList activeList = new NBTTagList();
				for (JsonObject active : gui.active) {
					NBTTagCompound activeNBT = new NBTTagCompound();
					activeNBT.setString("data", active.toString());
					activeList.appendTag(activeNBT);
				}
				nbt.setTag("active", activeList);
				NBTTagList displayList = new NBTTagList();
				for (MigraineDisplay display : gui.displays) {
					NBTTagCompound displayNBT = new NBTTagCompound();
					display.writeToNBT(displayNBT);
					displayList.appendTag(displayNBT);
				}
				nbt.setTag("displays", displayList);
				gui.chapterSaves.put(tickNum, nbt);
			}
		} catch (Exception ex){
			// I dont feel like trying to track down every single exception that could occur, but i dont want to crash the game if someone makes a bad file. Lets do this instead
			MainRegistry.logger.warn("[Migraine] An error occurred while running tick " + tickNum + " in " + name + ", doing task: " + currentTask + "!");
		}
	}

	// Updated every render call, not game tick
	public void render(int mouseX, int mouseY, int w, int h, GuiMigraine gui){
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

	private void loadFromChapter(GuiMigraine gui, int chapterTick){
		if (gui.chapterSaves.containsKey(chapterTick)) {
			NBTTagCompound chapterData = gui.chapterSaves.get(chapterTick);

			gui.worldRenderer.world.readFromNBT(chapterData);
			gui.ticks = chapterData.getInteger("tick");
			gui.chapterNumber = chapterData.getInteger("chapter");
			gui.pitch = chapterData.getFloat("pitch");
			gui.yaw = chapterData.getFloat("yaw");
			gui.zoom = chapterData.getFloat("zoom");
			if (chapterData.getBoolean("isCameraSet")) {
				gui.camera = Vec3.createVectorHelper(chapterData.getDouble("cameraX"), chapterData.getDouble("cameraY"), chapterData.getDouble("cameraZ"));
			} else {
				gui.camera = null;
			}

			NBTTagList activeList = chapterData.getTagList("active", 10);
			for (int i = 0; i < activeList.tagCount(); i++) {
				NBTTagCompound activeNBT = activeList.getCompoundTagAt(i);
				JsonParser reader = new JsonParser();
				JsonObject active = reader.parse(activeNBT.getString("data")).getAsJsonObject();
				gui.active.add(active);
			}

			NBTTagList displayList = chapterData.getTagList("displays", 10);
			for (int i = 0; i < displayList.tagCount(); i++) {
				NBTTagCompound displayNBT = displayList.getCompoundTagAt(i);
				MigraineDisplay display = new MigraineDisplay(gui.getFontRenderer(), displayNBT);
				gui.displays.add(display);
			}

			gui.isometric = chapterData.getBoolean("isometric");
		} else {
			MainRegistry.logger.warn("[Migraine] Failed to load Migraine from chapter " + chapterTick + " in " + name + "!");
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

			// Find the last chapter that is before the toTick
			int lastChapter = -1;
			for (int i = 0; i < chapters.size(); i++){
				if (chapters.get(i) <= toTick){
					lastChapter = i;
				} else {
					break;
				}
			}

			int chapterTick = chapters.get(lastChapter);

			if (gui.chapterSaves.containsKey(chapterTick) && lastChapter != -1) {
				loadFromChapter(gui, chapterTick);

				for (int i = chapterTick; i < toTick; i++){
					gui.updateScreen();
				}
				gui.updateCamera();
				gui.isPaused = pausedTemp;
			} else {
				for (int i = 0; i < toTick; i++){
					gui.updateScreen();
				}
				gui.updateCamera();
				gui.isPaused = pausedTemp;
			}
		} else {
			boolean pausedTemp = gui.isPaused;
			gui.isPaused = false;

			// find the last chapter that is between fromTick and toTick
			int lastChapter = -1;
			for (int i = 0; i < chapters.size(); i++){
				if (chapters.get(i) > fromTick && chapters.get(i) <= toTick){
					lastChapter = i;
				} else {
					break;
				}
			}

			int chapterTick = chapters.get(lastChapter);

			if (gui.chapterSaves.containsKey(chapterTick) && lastChapter != -1) {
				gui.worldRenderer.world.emptyWorld();
				gui.ticks = 0;
				gui.camera = Vec3.createVectorHelper(0, 0,0);
				gui.chapterNumber = 0;
				gui.active.clear();
				gui.displays.clear();
				gui.pitch = -30f;
				gui.yaw = -45f;
				gui.zoom = 1f;
				loadFromChapter(gui, chapterTick);

				for (int i = chapterTick; i < toTick; i++){
					gui.updateScreen();
				}
				gui.updateCamera();
				gui.isPaused = pausedTemp;

			} else {
				for (int i = 0; i < Math.abs(toTick - fromTick); i++){
					gui.updateScreen();
				}
				gui.updateCamera();
				gui.isPaused = pausedTemp;
			}
		}

	}

	private enum defaultColors {
		COPPER(0xFFFDCA88, 0xFFD57C4F, 0xFFAB4223, 0xFF1A1F22),
		GOLD(0xFFFFFDE0, 0xFFFAD64A, 0xFFDC9613, 0xFF1A1F22),
		BLUE(0xFFA5D9FF, 0xFF39ACFF, 0xFF1A6CA7, 0xFF1A1F22),
		GREY(0xFFD1D1D1, 0xFF919191, 0xFF5D5D5D, 0xFF302E36);

		private final int[] colors;

		defaultColors(int brighter, int frame, int darker, int background){
			this.colors = new int[]{brighter, frame, darker, background};
		}
	}

	private static class Parameter {

		private boolean exist;
		private JsonElement value;

		private Parameter(boolean exist, JsonElement element){
			this.exist = exist;
			this.value = element;
		}

		private static Parameter getParam(JsonObject parent, String fieldName){
			return new Parameter(parent.has(fieldName), parent.get(fieldName));
		}

		private int integer(){
			if (!this.exist || !this.value.isJsonPrimitive() || !this.value.getAsJsonPrimitive().isNumber()){
				throw new IllegalArgumentException("Parameter is not an integer or does not exist");
			}
			return this.integer(0);
		}

		private int integer(int defaultValue){
			return this.exist ? this.value.getAsInt() : defaultValue;
		}

		private String string(){
			if (!this.exist || !this.value.isJsonPrimitive() || !this.value.getAsJsonPrimitive().isString()){
				throw new IllegalArgumentException("Parameter is not a String or does not exist");
			}
			return this.string(null);
		}

		private String string(String defaultValue){
			return this.exist ? this.value.getAsString() : defaultValue;
		}

		private boolean bool(){
			if (!this.exist || !this.value.isJsonPrimitive() || !this.value.getAsJsonPrimitive().isBoolean()){
				throw new IllegalArgumentException("Parameter is not a boolean or does not exist");
			}
			return this.bool(false);
		}

		private boolean bool(boolean defaultValue){
			return this.exist ? this.value.getAsBoolean() : defaultValue;
		}

		private JsonObject jsonObject(){
			if (!this.exist || !this.value.isJsonObject()){
				throw new IllegalArgumentException("Parameter is not a JsonObject or does not exist");
			}
			return this.jsonObject(null);
		}

		private JsonObject jsonObject(JsonObject defaultValue){
			return this.exist ? this.value.getAsJsonObject() : defaultValue;
		}

		private JsonArray jsonArray(){
			if (!this.exist || !this.value.isJsonArray()){
				throw new IllegalArgumentException("Parameter is not a JsonArray or does not exist");
			}
			return this.jsonArray(null);
		}

		private JsonArray jsonArray(JsonArray defaultValue){
			return this.exist ? this.value.getAsJsonArray() : defaultValue;
		}

		private JsonElement jsonElement(){
			if (!this.exist){
				throw new IllegalArgumentException("Parameter does not exist");
			}
			return this.jsonElement(null);
		}

		private JsonElement jsonElement(JsonElement defaultValue){
			return this.exist ? this.value : defaultValue;
		}

		private double doubleValue(){
			if (!this.exist || !this.value.isJsonPrimitive() || !this.value.getAsJsonPrimitive().isNumber()){
				throw new IllegalArgumentException("Parameter is not a double or does not exist");
			}
			return this.doubleValue(0);
		}

		private double doubleValue(double defaultValue){
			return this.exist ? this.value.getAsDouble() : defaultValue;
		}

		private float floatValue(){
			if (!this.exist || !this.value.isJsonPrimitive() || !this.value.getAsJsonPrimitive().isNumber()){
				throw new IllegalArgumentException("Parameter is not a float or does not exist");
			}
			return this.floatValue(0);
		}

		private float floatValue(float defaultValue){
			return this.exist ? this.value.getAsFloat() : defaultValue;
		}

		private Vec3 vec3(){
			if (!this.exist || !this.value.isJsonObject()){
				throw new IllegalArgumentException("Parameter is not a Vec3 or does not exist");
			}
			return this.vec3(null);
		}

		private Vec3 vec3(Vec3 defaultValue){
			if (this.exist && this.value.isJsonObject()){
				JsonObject vec = this.value.getAsJsonObject();
				return Vec3.createVectorHelper(vec.get("x").getAsDouble(), vec.get("y").getAsDouble(), vec.get("z").getAsDouble());
			}
			return defaultValue;
		}

		private long longValue(){
			if (!this.exist || !this.value.isJsonPrimitive() || !this.value.getAsJsonPrimitive().isNumber()){
				throw new IllegalArgumentException("Parameter is not a long or does not exist");
			}
			return this.longValue(0);
		}

		private long longValue(long defaultValue){
			return this.exist ? this.value.getAsLong() : defaultValue;
		}

		private byte byteValue(){
			if (!this.exist || !this.value.isJsonPrimitive() || !this.value.getAsJsonPrimitive().isNumber()){
				throw new IllegalArgumentException("Parameter is not a byte or does not exist");
			}
			return this.byteValue((byte) 0);
		}

		private byte byteValue(byte defaultValue){
			return this.exist ? this.value.getAsByte() : defaultValue;
		}

		private char charValue(){
			if (!this.exist || !this.value.isJsonPrimitive() || !this.value.getAsJsonPrimitive().isString() || this.value.getAsString().length() != 1){
				throw new IllegalArgumentException("Parameter is not a char or does not exist");
			}
			return this.charValue((char) 0);
		}

		private char charValue(char defaultValue){
			return this.exist ? this.value.getAsCharacter() : defaultValue;
		}

		private short shortValue(){
			if (!this.exist || !this.value.isJsonPrimitive() || !this.value.getAsJsonPrimitive().isNumber()){
				throw new IllegalArgumentException("Parameter is not a short or does not exist");
			}
			return this.shortValue((short) 0);
		}
		
		private short shortValue(short defaultValue){
			return this.exist ? this.value.getAsShort() : defaultValue;
		}
	}
}
