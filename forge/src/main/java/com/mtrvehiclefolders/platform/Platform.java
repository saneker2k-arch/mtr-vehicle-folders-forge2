package com.mtrvehiclefolders.platform;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Everything that differs between Fabric and Forge lives in this class (the Fabric project has its own version of it).
 */
public final class Platform {

	private static final Logger LOGGER = LoggerFactory.getLogger("MTR Vehicle Folders");

	private Platform() {
	}

	/**
	 * @return the game's config folder
	 */
	public static Path getConfigDir() {
		return FMLPaths.CONFIGDIR.get();
	}

	/**
	 * @return a text that identifies the world or server the player is in
	 */
	public static String getWorldKey() {
		try {
			final Minecraft client = Minecraft.getInstance();
			if (client.getSingleplayerServer() != null) {
				return "singleplayer:" + client.getSingleplayerServer().getWorldData().getLevelName();
			} else if (client.getCurrentServer() != null) {
				return "server:" + client.getCurrentServer().ip;
			}
		} catch (Exception e) {
			LOGGER.warn("Could not determine the world name", e);
		}
		return "unknown";
	}

	/**
	 * @param path a path inside the MTR mod file, for example "assets/mtr/mtr_custom_resources.json"
	 * @return the file, or null if MTR or the file was not found
	 */
	public static Path findMtrResource(String path) {
		final IModFileInfo modFileInfo = ModList.get().getModFileById("mtr");
		if (modFileInfo == null) {
			return null;
		}
		final Path file = modFileInfo.getFile().findResource(path.split("/"));
		return file != null && Files.exists(file) ? file : null;
	}
}
