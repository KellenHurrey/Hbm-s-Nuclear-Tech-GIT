package com.hbm.migraine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.main.MainRegistry;
import com.hbm.migraine.client.ImmediateWorldSceneRenderer;
import com.hbm.util.I18nUtil;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.common.util.Constants;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MigraineInstructions {

	public final String name;
	public final ComparableStack comparableStack;
	private final JsonObject json;

	// bigger values = more zoom, is not linear
	public float zoom;
	public float yaw;
	public float pitch;

	public Vector3f center;

	private HashSet<JsonObject> active = new HashSet<>();

	private HashSet<MigraineDisplay> displays = new HashSet<>();

	public MigraineInstructions(String name, JsonObject object){
		this.name = name;

		JsonObject ownerInfo = object.getAsJsonObject("owner");

		String stackModid = ownerInfo.get("modid").getAsString();
		String stackName = ownerInfo.get("name").getAsString();

		Item item = GameRegistry.findItem(stackModid, stackName);
		if (item != null){
			this.comparableStack = new ComparableStack(item, 1, ownerInfo.has("meta") ? ownerInfo.get("meta").getAsInt() : 0);
		}
		else{
			Block block = GameRegistry.findBlock(stackModid, stackName);
			if (block != null){
				this.comparableStack = new ComparableStack(block, 1, ownerInfo.has("meta") ? ownerInfo.get("meta").getAsInt() : 0);
			}else{
				this.comparableStack = null;
			}
		}

		this.json = object;

	}

	private void setBlock(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");
		worldRenderer.world.setBlock(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt(), GameRegistry.findBlock(action.get("modid").getAsString(), action.get("name").getAsString()), action.has("meta") ? action.get("meta").getAsInt() : 0, 3);
	}

	private void placeBlock(GuiMigraine gui, ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		Block block = GameRegistry.findBlock(action.get("modid").getAsString(), action.get("name").getAsString());

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
		for (int x = posMin.get("x").getAsInt(); x <= posMax.get("x").getAsInt(); x++){
			for (int y = posMin.get("y").getAsInt(); y <= posMax.get("y").getAsInt(); y++){
				for (int z = posMin.get("z").getAsInt(); z <= posMax.get("z").getAsInt(); z++){
					worldRenderer.world.setBlock(x, y, z, GameRegistry.findBlock(action.get("modid").getAsString(), action.get("name").getAsString()), action.has("meta") ? action.get("meta").getAsInt() : 0, 3);
					worldRenderer.world.markBlockForUpdate(x, y, z);
				}
			}
		}
	}

	private void modifyTileEntity(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		// I know this looks bad, but its a fake world, so whats the worst someone could do?
		JsonObject pos = action.getAsJsonObject("position");
		TileEntity te = worldRenderer.world.getTileEntity(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt());
		if (te != null) {
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
							String stackModid = arg.get("modid").getAsString();
							String stackName = arg.get("name").getAsString();
							Item item = GameRegistry.findItem(stackModid, stackName);
							if (item != null) {
								params[i] = new ItemStack(item, arg.has("stackSize") ? arg.get("stackSize").getAsInt() : 1, arg.has("meta") ? arg.get("meta").getAsInt() : 0);
								types[i] = ItemStack.class;
								if (arg.has("nbt")){
									try {
										((ItemStack) params[i]).setTagCompound((NBTTagCompound) JsonToNBT.func_150315_a(arg.get("nbt").getAsString()));
									}catch (Exception ex){
										MainRegistry.logger.debug("[Migraine] Failed to set item nbt! " + ex.getLocalizedMessage());
									}
								}
							} else {
								Block block = GameRegistry.findBlock(stackModid, stackName);
								if (block != null) {
									params[i] = new ItemStack(block, arg.has("stackSize") ? arg.get("stackSize").getAsInt() : 1, arg.has("meta") ? arg.get("meta").getAsInt() : 0);
									types[i] = ItemStack.class;
									if (arg.has("nbt")){
										try {
											((ItemStack) params[i]).setTagCompound((NBTTagCompound) JsonToNBT.func_150315_a(arg.get("nbt").getAsString()));
										}catch (Exception ex){
											MainRegistry.logger.debug("[Migraine] Failed to set item nbt! " + ex.getLocalizedMessage());
										}
									}
								}
							}
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

				Method method = te.getClass().getMethod(action.get("method").getAsString(), types);
				method.invoke(te, params);

			} catch (Exception ex) {
				MainRegistry.logger.warn("[Migraine] Error modifying tile entity " + ex.getLocalizedMessage());
				ex.printStackTrace();
			}
		} else {
			MainRegistry.logger.warn("[Migraine] Tried to access TileEntity at " + pos.get("x").getAsInt() + ", " + pos.get("y").getAsInt() + ", " + pos.get("z").getAsInt() + ", but failed!");
		}
	}

	private void setCenter(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");
		if (pos.isJsonNull()){
			center = null;
		}else{
			center = new Vector3f(pos.get("x").getAsFloat(), pos.get("y").getAsFloat(), pos.get("z").getAsFloat());
		}
	}

	private void addCenter(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");

		Vector3f size = worldRenderer.world.getSize();
		Vector3f minPos = worldRenderer.world.getMinPos();
		center = center == null ? new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2) : center;

		Vector3f.add(center, new Vector3f(pos.get("x").getAsFloat(), pos.get("y").getAsFloat(), pos.get("z").getAsFloat()), center);
	}

	private void rotateTo(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("forTicks").getAsInt();
		toAdd.addProperty("type", "rotateTo");
		toAdd.addProperty("tickLeft", forTicks);
		toAdd.addProperty("addYaw", (action.get("targetYaw").getAsFloat() - yaw) / forTicks);
		toAdd.addProperty("addPitch", (action.get("targetPitch").getAsFloat() - pitch) / forTicks);
		active.add(toAdd);
	}

	private void zoomTo(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("forTicks").getAsInt();
		toAdd.addProperty("type", "zoomTo");
		toAdd.addProperty("tickLeft", forTicks);
		toAdd.addProperty("addZoom", (action.get("targetZoom").getAsFloat() - zoom) / forTicks);
		active.add(toAdd);
	}

	private void moveCenterTo(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject toAdd = new JsonObject();
		int forTicks = action.get("forTicks").getAsInt();
		toAdd.addProperty("type", "moveCenterTo");
		toAdd.addProperty("tickLeft", forTicks);
		JsonObject targetPos = action.getAsJsonObject("targetPosition");

		Vector3f size = worldRenderer.world.getSize();
		Vector3f minPos = worldRenderer.world.getMinPos();
		center = center == null ? new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2) : center;

		toAdd.addProperty("addX", (targetPos.get("x").getAsFloat() - center.x) / forTicks);
		toAdd.addProperty("addY", (targetPos.get("y").getAsFloat() - center.y) / forTicks);
		toAdd.addProperty("addZ", (targetPos.get("z").getAsFloat() - center.z) / forTicks);

		active.add(toAdd);
	}

	private void display(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){

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
						String stackModid = data.get("modid").getAsString();
						String stackName = data.get("name").getAsString();
						Item item = GameRegistry.findItem(stackModid, stackName);
						if (item != null) {
							currentLine[i] = new ItemStack(item, data.has("stackSize") ? data.get("stackSize").getAsInt() : 1, data.has("meta") ? data.get("meta").getAsInt() : 0);
							if (data.has("nbt")){
								try {
									((ItemStack) currentLine[i]).setTagCompound((NBTTagCompound) JsonToNBT.func_150315_a(data.get("nbt").getAsString()));
								}catch (Exception ex){
									MainRegistry.logger.debug("[Migraine] Failed to set item nbt! " + ex.getLocalizedMessage());
								}
							}
						} else {
							Block block = GameRegistry.findBlock(stackModid, stackName);
							if (block != null) {
								currentLine[i] = new ItemStack(block, data.has("stackSize") ? data.get("stackSize").getAsInt() : 1, data.has("meta") ? data.get("meta").getAsInt() : 0);
								if (data.has("nbt")){
									try {
										((ItemStack) currentLine[i]).setTagCompound((NBTTagCompound) JsonToNBT.func_150315_a(data.get("nbt").getAsString()));
									}catch (Exception ex){
										MainRegistry.logger.debug("[Migraine] Failed to set item nbt! " + ex.getLocalizedMessage());
									}
								}
							}
						}
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

		displays.add(display);
	}

	private void setTileEntityNBT(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject pos = action.getAsJsonObject("position");

		TileEntity tile = worldRenderer.world.getTileEntity(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt());

		if (tile != null){
			try {
				tile.readFromNBT((NBTTagCompound) JsonToNBT.func_150315_a(action.get("nbt").getAsString()));
			} catch(NBTException ex){
				MainRegistry.logger.debug("[Migraine] Failed to read NBT String! " + ex.getLocalizedMessage());
			}
		}
	}

	// Updated every game tick, not render call
	public void update(GuiMigraine gui, int tickNum){
		ImmediateWorldSceneRenderer worldRenderer = gui.worldRenderer;
		JsonArray data = json.getAsJsonArray("update");

		displays.forEach((MigraineDisplay display) -> {
			if (display.ticksRemaining <= 0){
				displays.remove(display);
			}
			else{
				display.ticksRemaining--;
			}
		});

		for (JsonElement actionElem : data){
			JsonObject actionGroup = actionElem.getAsJsonObject();

			// If there is tick, then just do that exact tick
			// If there is start and end tick, then do startTick <= tickNum <= endTick, removing the upper or lower bound if it is -1
			if ((actionGroup.has("tick") && tickNum == actionGroup.get("tick").getAsInt()) || // works
				(actionGroup.has("startTick") && actionGroup.has("endTick") && (
					(actionGroup.get("startTick").getAsInt() != -1 && actionGroup.get("endTick").getAsInt() != -1 && tickNum >= actionGroup.get("startTick").getAsInt() && tickNum <= actionGroup.get("endTick").getAsInt()) || // works
					(actionGroup.get("startTick").getAsInt() == -1 && actionGroup.get("endTick").getAsInt() != -1 && tickNum <= actionGroup.get("endTick").getAsInt()) || // works
					((actionGroup.get("startTick").getAsInt() != -1 && actionGroup.get("endTick").getAsInt() == -1 && tickNum >= actionGroup.get("startTick").getAsInt()))))){ // works

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
						if (action.has("pitch")) pitch = action.get("pitch").getAsFloat();
						if (action.has("yaw")) yaw = action.get("yaw").getAsFloat();
						if (action.has("zoom")) zoom = action.get("zoom").getAsFloat();
						break;
					case "addCamera": // works
						if (action.has("pitch")) pitch += action.get("pitch").getAsFloat();
						if (action.has("yaw")) yaw += action.get("yaw").getAsFloat();
						if (action.has("zoom")) zoom += action.get("zoom").getAsFloat();
						break;
					case "setCenter": // works
						setCenter(worldRenderer, action);
						break;
					case "addCenter":
						addCenter(worldRenderer, action);
						break;
					case "rotateTo": // work
						rotateTo(worldRenderer, action);
						break;
					case "zoomTo": // work
						zoomTo(worldRenderer, action);
						break;
					case "moveCenterTo": // works
						moveCenterTo(worldRenderer, action);
						break;
					case "display": // at least the game didnt crash // welp it did // why no render
						display(worldRenderer, action);
						break;
					case "setTileEntityNBT": // i dont fucking know
						setTileEntityNBT(worldRenderer, action);
						break;
				}
			}
		}

		for (JsonObject overTime : active){
			String type = overTime.get("type").getAsString();
			int forTicks = overTime.get("tickLeft").getAsInt();
			if (forTicks <= 0){
				active.remove(overTime);
				continue;
			}
			switch (type){
				case "rotateTo":
					yaw += overTime.get("addYaw").getAsFloat();
					pitch += overTime.get("addPitch").getAsFloat();
					break;
				case "zoomTo":
					zoom += overTime.get("addZoom").getAsFloat();
					break;
				case "moveCenterTo":
					Vector3f size = worldRenderer.world.getSize();
					Vector3f minPos = worldRenderer.world.getMinPos();
					center = center == null ? new Vector3f(minPos.x + size.x / 2, minPos.y + size.y / 2, minPos.z + size.z / 2) : center;

					center.x += overTime.get("addX").getAsFloat();
					center.y += overTime.get("addY").getAsFloat();
					center.z += overTime.get("addZ").getAsFloat();
					break;

			}
			overTime.remove("tickLeft");
			overTime.addProperty("tickLeft", --forTicks);
		}
	}

	// Updated every render call, not game tick
	public void render(int mouseX, int mouseY, float partialTicks, int w, int h){
		displays.forEach((MigraineDisplay display) -> {
			GL11.glPushMatrix();
			display.drawForegroundComponent(w, h);
			GL11.glPopMatrix();
		});
	}
}
