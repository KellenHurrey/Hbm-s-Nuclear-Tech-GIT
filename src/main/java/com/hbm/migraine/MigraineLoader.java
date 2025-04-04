package com.hbm.migraine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.*;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** @author kellen, hbm */
public class MigraineLoader implements IResourceManagerReloadListener {

	public static final HashSet<File> registeredModFiles = new HashSet<>();

	private static final JsonParser parser = new JsonParser();

	public static HashMap<ComparableStack, MigraineInstructions> instructions = new HashMap<>();

	@Override
	public void onResourceManagerReload(IResourceManager resMan) {
		long timestamp = System.currentTimeMillis();
		MainRegistry.logger.info("[Migraine] Reloading migraines...");
		init();
		MainRegistry.logger.info("[Migraine] Loaded " + instructions.size() + " migraines (" + (System.currentTimeMillis() - timestamp) + " ms)");

	}

	public static void init(){
		registerModFileURL(new File(MigraineLoader.class.getProtectionDomain().getCodeSource().getLocation().getPath()));

		instructions.clear();

		loadPacks();
	}

	public static void registerModFileURL(File file){
		registeredModFiles.add(file);
	}

	private static void logPackAttempt(String name){ MainRegistry.logger.info("[Migraine] Attempting to read " + name); }
	private static void logFoundMigraine(String name){ MainRegistry.logger.info("[Migraine] Found migraine " + name); }

	private static void loadPacks(){
		// Load the files from the mods, you know, the only place where there will actually be any of these
		for (File file : registeredModFiles) dissectZip(file);

		// Load mod files if we are in a dev env
		File devEnvManualFolder = new File(Minecraft.getMinecraft().mcDataDir.getAbsolutePath().replace("/eclipse/.", "") + "/src/main/resources/assets/hbm/migraine");
		if(devEnvManualFolder.exists() && devEnvManualFolder.isDirectory()) {
			MainRegistry.logger.info("[Migraine] Exploring " + devEnvManualFolder.getAbsolutePath());
			dissectManualFolder(devEnvManualFolder);
		}

		// Load resource packs
		ResourcePackRepository repo = Minecraft.getMinecraft().getResourcePackRepository();

		for(Object o : repo.getRepositoryEntries()) {
			ResourcePackRepository.Entry entry = (ResourcePackRepository.Entry) o;
			IResourcePack pack = entry.getResourcePack();

			logPackAttempt(pack.getPackName());

			if(pack instanceof FileResourcePack) {
				dissectZip(((FileResourcePack) pack).resourcePackFile);
			}

			if(pack instanceof FolderResourcePack) {
				dissectFolder(((FolderResourcePack) pack).resourcePackFile);
			}
		}

	}

	// Why do the work when it has already been done?
	private static void dissectZip(File pack){

		if (pack == null){
			MainRegistry.logger.warn("[Migraine] Pack file does not exist!");
			return;
		}

		// Wow intellij is smart i didnt even know that this was a thing
		try (ZipFile zip = new ZipFile(pack)) {

			Enumeration<? extends ZipEntry> enumerator = zip.entries();

			while (enumerator.hasMoreElements()) {
				ZipEntry entry = enumerator.nextElement();
				String name = entry.getName();
				if (name.startsWith("assets/hbm/migraine/") && name.endsWith(".json")) {
					InputStream fileStream = zip.getInputStream(entry);
					InputStreamReader reader = new InputStreamReader(fileStream);
					try {
						JsonObject obj = (JsonObject) parser.parse(reader);
						String manName = name.replace("assets/hbm/migraine/", "");
						registerJson(manName, obj);
						reader.close();
						logFoundMigraine(manName);
					} catch (Exception ex) {
						MainRegistry.logger.warn("[Migraine] Error reading migraine " + name + ", probably due to malformed json!");
					}
				}
			}

		} catch (Exception e) {
			MainRegistry.logger.warn("[Migraine] Error dissecting zip " + pack.getName());
		}
	}

	private static void dissectFolder(File folder){
		File manualFolder = new File(folder, "/assets/migraine");
		if(manualFolder.exists() && manualFolder.isDirectory()) dissectManualFolder(manualFolder);
	}

	private static void dissectManualFolder(File folder){
		File[] files = folder.listFiles();
		for(File file : files) {
			String name = file.getName();
			if(file.isFile() && name.endsWith(".json")) {
				try (FileReader reader = new FileReader(file)){
					JsonObject obj = (JsonObject) parser.parse(reader);
					registerJson(name, obj);
					logFoundMigraine(name);
				} catch(Exception ex) {
					MainRegistry.logger.warn("[Migraine] Error reading migraine " + name + ", probably due to malformed json!");
				}
			}
		}
	}

	private static void registerJson(String name, JsonObject object){
		MigraineInstructions instruction = new MigraineInstructions(name, object);
		if (instruction.comparableStack != null){
			instructions.put(instruction.comparableStack, instruction);
		}
	}
}
