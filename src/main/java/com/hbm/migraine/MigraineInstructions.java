package com.hbm.migraine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.main.MainRegistry;
import com.hbm.migraine.client.ImmediateWorldSceneRenderer;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.util.vector.Vector3f;

import java.lang.reflect.Method;

public class MigraineInstructions {

	public final String name;
	public final ComparableStack comparableStack;
	private final JsonObject json;

	// bigger values = more zoom, is not linear
	public float zoom = 1;
	public float yaw = 20;
	public float pitch = 50;

	public Vector3f center;

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

	private void fillBlocks(ImmediateWorldSceneRenderer worldRenderer, JsonObject action){
		JsonObject posMin = action.getAsJsonObject("positionMin");
		JsonObject posMax = action.getAsJsonObject("positionMax");
		for (int x = posMin.get("x").getAsInt(); x <= posMax.get("x").getAsInt(); x++){
			for (int y = posMin.get("y").getAsInt(); y <= posMax.get("y").getAsInt(); y++){
				for (int z = posMin.get("z").getAsInt(); z <= posMax.get("z").getAsInt(); z++){
					worldRenderer.world.setBlock(x, y, z, GameRegistry.findBlock(action.get("modid").getAsString(), action.get("name").getAsString()), action.has("meta") ? action.get("meta").getAsInt() : 0, 3);
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
				Method method = te.getClass().getMethod(action.get("method").getAsString());
				Class clazz = Class.forName(action.get("tileEntityClass").getAsString());
				JsonArray args = action.getAsJsonArray("arguments");
				Object[] params = new Object[args.size()];

				// Params currently implemented:
				// ints
				// ItemStacks
				// Strings
				// longs
				// doubles
				// floats
				// booleans
				// chars

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
							} else {
								Block block = GameRegistry.findBlock(stackModid, stackName);
								if (block != null) {
									params[i] = new ItemStack(block, arg.has("stackSize") ? arg.get("stackSize").getAsInt() : 1, arg.has("meta") ? arg.get("meta").getAsInt() : 0);
								}
							}
							break;
						case "float":
							params[i] = arg.get("value").getAsFloat();
							break;
						case "int":
							params[i] = arg.get("value").getAsInt();
							break;
						case "String":
							params[i] = arg.get("value").getAsString();
							break;
						case "double":
							params[i] = arg.get("value").getAsDouble();
							break;
						case "long":
							params[i] = arg.get("value").getAsLong();
							break;
						case "boolean":
							params[i] = arg.get("value").getAsBoolean();
							break;
						case "short":
							params[i] = arg.get("value").getAsShort();
							break;
						case "char":
							params[i] = arg.get("value").getAsCharacter();
							break;
					}
				}
				method.invoke(clazz.cast(te), params);

			} catch (Exception ex) {
				MainRegistry.logger.warn("[Migraine] Error modifying tile entity " + ex.getLocalizedMessage());
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
			center.set(pos.get("x").getAsFloat(), pos.get("y").getAsFloat(), pos.get("z").getAsFloat());
		}
	}

	public void update(ImmediateWorldSceneRenderer worldRenderer, int tickNum){
		JsonArray data = json.getAsJsonArray("update");

		for (JsonElement actionElem : data){
			JsonObject actionGroup = actionElem.getAsJsonObject();


			// If there is tick, then just do that exact tick
			// If there is start and end tick, then do startTick <= tickNum <= endTick, removing the upper or lower bound if it is -1
			if ((actionGroup.has("tick") && tickNum == actionGroup.get("tick").getAsInt()) ||
				(actionGroup.has("startTick") && actionGroup.has("endTick") && (
					(actionGroup.get("startTick").getAsInt() != -1 && actionGroup.get("endTick").getAsInt() != -1 && tickNum >= actionGroup.get("startTick").getAsInt() && tickNum <= actionGroup.get("endTick").getAsInt()) ||
					(actionGroup.get("startTick").getAsInt() == -1 && actionGroup.get("endTick").getAsInt() != -1 && tickNum <= actionGroup.get("endTick").getAsInt()) ||
					((actionGroup.get("startTick").getAsInt() != -1 && actionGroup.get("endTick").getAsInt() == -1 && tickNum >= actionGroup.get("startTick").getAsInt()))))){

				JsonObject action = actionGroup.getAsJsonObject("action");
				String type = action.get("type").getAsString();
				switch (type) {
					case "setBlock":
						setBlock(worldRenderer, action);
						break;
					case "fillBlocks":
						fillBlocks(worldRenderer, action);
						break;
					case "modifyTileEntity":
						modifyTileEntity(worldRenderer, action);
						break;
					case "setCamera":
						if (action.has("pitch")) pitch = action.get("pitch").getAsFloat();
						if (action.has("yaw")) yaw = action.get("yaw").getAsFloat();
						if (action.has("zoom")) zoom = action.get("zoom").getAsFloat();
						break;
					case "addCamera":
						if (action.has("pitch")) pitch += action.get("pitch").getAsFloat();
						if (action.has("yaw")) yaw += action.get("yaw").getAsFloat();
						if (action.has("zoom")) zoom += action.get("zoom").getAsFloat();
						break;
					case "setCenter":
						setCenter(worldRenderer, action);
						break;
				}
			}
		}
	}
}
