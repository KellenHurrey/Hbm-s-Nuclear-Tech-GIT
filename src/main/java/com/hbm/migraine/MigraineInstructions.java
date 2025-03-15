package com.hbm.migraine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.main.MainRegistry;
import com.hbm.migraine.client.ImmediateWorldSceneRenderer;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.tileentity.TileEntity;

import java.lang.reflect.Method;

public class MigraineInstructions {

	public final String name;
	public final ComparableStack comparableStack;
	private final JsonObject json;

	public MigraineInstructions(String name, JsonObject object){
		this.name = name;

		JsonObject ownerInfo = object.getAsJsonObject("ownerData");

		String type = ownerInfo.get("type").getAsString();
		if (type.equals("block")){
			this.comparableStack = new ComparableStack(GameRegistry.findBlock(ownerInfo.get("modid").getAsString(), ownerInfo.get("name").getAsString()), 1, ownerInfo.has("meta") ? ownerInfo.get("meta").getAsInt() : 0);
		}else if (type.equals("item")){
			this.comparableStack = new ComparableStack(GameRegistry.findItem(ownerInfo.get("modid").getAsString(), ownerInfo.get("name").getAsString()), 1, ownerInfo.has("meta") ? ownerInfo.get("meta").getAsInt() : 0);
		}else{
			this.comparableStack = null;
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

	public void update(ImmediateWorldSceneRenderer worldRenderer, int tickNum){
		JsonArray data = json.getAsJsonArray("update");

		for (JsonElement actionElem : data){
			JsonObject actionGroup = actionElem.getAsJsonObject();

			if (tickNum == actionGroup.get("tick").getAsInt()){
				JsonObject action = actionGroup.getAsJsonObject("action");
				String type = action.get("type").getAsString();
				if (type.equals("setBlock")){
					setBlock(worldRenderer, action);
				}
				else if (type.equals("fillBlocks")){
					fillBlocks(worldRenderer, action);
				}
				else if (type.equals("modifyTileEntity")){
					// I know this looks bad, but its a fake world, so whats the worst someone could do?
					JsonObject pos = action.getAsJsonObject("position");
					TileEntity te = worldRenderer.world.getTileEntity(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt());
					if (te != null){
						try {
							Method method = te.getClass().getMethod(action.get("method").getAsString());
//							te.getBlockType().createTileEntity(null, )
							JsonArray args = action.getAsJsonArray("arguments");
							Object[] params = new Object[args.size()];
							for (int i = 0; i < args.size(); i++){
								JsonObject arg = args.get(i).getAsJsonObject();
								String argType = arg.get("type").getAsString();
								if (argType.equals("int")){
									params[i] = arg.get("value").getAsInt();
								}
							}
							method.invoke()

						}catch (Exception ex){
							MainRegistry.logger.warn("[Migraine] No such method " + action.get("method").getAsString());
						}
					}else{
						MainRegistry.logger.warn("[Migraine] Tried to access TileEntity at " + pos.get("x").getAsInt() + ", " + pos.get("y").getAsInt() + ", " + pos.get("z").getAsInt() + ", but failed!");
					}
				}
			}
		}
	}
}
