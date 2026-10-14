package com.mtrvehiclefolders.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mtrvehiclefolders.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * The ids of all vehicles that come with MTR itself.
 * They are read straight from the vehicle list inside the MTR mod file, so nothing has to be kept up to date by hand.
 */
public final class BuiltInVehicles {

	private static final Logger LOGGER = LoggerFactory.getLogger("MTR Vehicle Folders");
	private static Set<String> ids;

	private BuiltInVehicles() {
	}

	public static synchronized Set<String> getIds() {
		if (ids == null) {
			ids = new HashSet<>();
			try {
				final Path path = Platform.findMtrResource("assets/mtr/mtr_custom_resources.json");
				if (path != null) {
					try (final Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
						final JsonElement root = JsonParser.parseReader(reader);
						if (root.isJsonObject() && root.getAsJsonObject().has("vehicles")) {
							for (final JsonElement vehicle : root.getAsJsonObject().getAsJsonArray("vehicles")) {
								if (vehicle.isJsonObject()) {
									final JsonObject vehicleObject = vehicle.getAsJsonObject();
									if (vehicleObject.has("id") && vehicleObject.get("id").isJsonPrimitive()) {
										ids.add(vehicleObject.get("id").getAsString());
									}
								}
							}
						}
					}
				} else {
					LOGGER.warn("Could not find the vehicle list inside the MTR mod file");
				}
			} catch (Exception e) {
				LOGGER.warn("Could not read the list of MTR's own vehicles", e);
			}
		}
		return ids;
	}
}
