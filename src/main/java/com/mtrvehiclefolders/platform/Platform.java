package com.mtrvehiclefolders.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Everything that differs between Fabric and Forge lives in this class (the Forge project has its own version of it).
 */
public final class Platform {

	private static final Logger LOGGER = LoggerFactory.getLogger("MTR Vehicle Folders");

	private Platform() {
	}

	/**
	 * @return the game's config folder
	 */
	public static Path getConfigDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	/**
	 * @return a text that identifies the world or server the player is in
	 */
	public static String getWorldKey() {
		try {
			final MinecraftClient client = MinecraftClient.getInstance();
			if (client.getServer() != null) {
				return "singleplayer:" + client.getServer().getSaveProperties().getLevelName();
			} else if (client.getCurrentServerEntry() != null) {
				return "server:" + client.getCurrentServerEntry().address;
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
		return FabricLoader.getInstance().getModContainer("mtr").flatMap(container -> container.findPath(path)).orElse(null);
	}
}
