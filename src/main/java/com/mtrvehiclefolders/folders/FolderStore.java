package com.mtrvehiclefolders.folders;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mtrvehiclefolders.platform.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Client-side storage for the folders of the dashboard (stations, routes, depots).
 * Folders are saved to config/mtrvehiclefolders/dashboard_folders.json, separately for every world / server.
 * "tab" identifies the list the folders belong to, for example "stations" or "routes:TRAIN".
 * Folder id 0 always means the root (no folder).
 */
public final class FolderStore {

	public static final class Folder {

		public long id;
		public long parent;
		public String name;
		public int color;
	}

	public static final class TabData {

		public List<Folder> folders = new ArrayList<>();
		// item id (as text, because JSON keys are text) -> folder id
		public Map<String, Long> itemFolder = new HashMap<>();
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("MTR Vehicle Folders");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type FILE_TYPE = new TypeToken<Map<String, Map<String, TabData>>>() {
	}.getType();

	// world key -> tab -> data
	private static Map<String, Map<String, TabData>> all;
	// world key + tab -> the folder that is currently open (only kept while the game is running)
	private static final Map<String, Long> CURRENT_FOLDER = new HashMap<>();

	private FolderStore() {
	}

	public static List<Folder> getSubfolders(String tab, long parent) {
		return data(tab).folders.stream()
				.filter(folder -> folder.parent == parent)
				.sorted((folder1, folder2) -> folder1.name.compareToIgnoreCase(folder2.name))
				.collect(Collectors.toList());
	}

	public static Folder getFolder(String tab, long id) {
		if (id == 0) {
			return null;
		}
		for (final Folder folder : data(tab).folders) {
			if (folder.id == id) {
				return folder;
			}
		}
		return null;
	}

	public static long getCurrentFolder(String tab) {
		final long id = CURRENT_FOLDER.getOrDefault(worldKey() + "|" + tab, 0L);
		return getFolder(tab, id) == null ? 0 : id;
	}

	public static void setCurrentFolder(String tab, long id) {
		CURRENT_FOLDER.put(worldKey() + "|" + tab, id);
	}

	public static long getParent(String tab, long folderId) {
		final Folder folder = getFolder(tab, folderId);
		return folder == null ? 0 : folder.parent;
	}

	/**
	 * @return for example "Folder / Subfolder", or an empty text for the root
	 */
	public static String getPathText(String tab, long folderId) {
		final List<String> names = new ArrayList<>();
		long id = folderId;
		int guard = 0;
		while (id != 0 && guard++ < 100) {
			final Folder folder = getFolder(tab, id);
			if (folder == null) {
				break;
			}
			names.add(0, folder.name);
			id = folder.parent;
		}
		return String.join(" / ", names);
	}

	public static long createFolder(String tab, long parent, String name, int color) {
		final Folder folder = new Folder();
		folder.id = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
		folder.parent = getFolder(tab, parent) == null ? 0 : parent;
		folder.name = name;
		folder.color = color;
		data(tab).folders.add(folder);
		save();
		return folder.id;
	}

	public static void updateFolder(String tab, long id, String name, int color) {
		final Folder folder = getFolder(tab, id);
		if (folder != null) {
			folder.name = name;
			folder.color = color;
			save();
		}
	}

	/**
	 * Deletes the folder and all folders inside it. Items that were inside go back to the root.
	 */
	public static void deleteFolder(String tab, long id) {
		final TabData tabData = data(tab);
		final Folder folder = getFolder(tab, id);
		if (folder == null) {
			return;
		}

		final Set<Long> toDelete = new HashSet<>();
		toDelete.add(id);
		boolean changed = true;
		while (changed) {
			changed = false;
			for (final Folder other : tabData.folders) {
				if (toDelete.contains(other.parent) && toDelete.add(other.id)) {
					changed = true;
				}
			}
		}

		tabData.folders.removeIf(other -> toDelete.contains(other.id));
		tabData.itemFolder.values().removeIf(toDelete::contains);
		if (toDelete.contains(CURRENT_FOLDER.getOrDefault(worldKey() + "|" + tab, 0L))) {
			setCurrentFolder(tab, folder.parent);
		}
		save();
	}

	/**
	 * Moves a folder into another folder (0 = root). Does nothing if that would put a folder inside itself.
	 */
	public static void moveFolder(String tab, long id, long newParent) {
		final Folder folder = getFolder(tab, id);
		if (folder == null || id == newParent) {
			return;
		}
		long check = newParent;
		int guard = 0;
		while (check != 0 && guard++ < 100) {
			if (check == id) {
				return;
			}
			check = getParent(tab, check);
		}
		folder.parent = newParent;
		save();
	}

	/**
	 * @return the folder an item (station, route, depot) is in, or 0 for the root
	 */
	public static long getItemFolder(String tab, long itemId) {
		final long folderId = data(tab).itemFolder.getOrDefault(Long.toString(itemId), 0L);
		return getFolder(tab, folderId) == null ? 0 : folderId;
	}

	public static void moveItem(String tab, long itemId, long folderId) {
		if (folderId == 0) {
			data(tab).itemFolder.remove(Long.toString(itemId));
		} else if (getFolder(tab, folderId) != null) {
			data(tab).itemFolder.put(Long.toString(itemId), folderId);
		}
		save();
	}

	private static TabData data(String tab) {
		load();
		return all.computeIfAbsent(worldKey(), key -> new HashMap<>()).computeIfAbsent(tab, key -> new TabData());
	}

	private static String worldKey() {
		return Platform.getWorldKey();
	}

	private static Path file() {
		return Platform.getConfigDir().resolve("mtrvehiclefolders").resolve("dashboard_folders.json");
	}

	private static void load() {
		if (all != null) {
			return;
		}
		all = new HashMap<>();
		final Path file = file();
		if (Files.exists(file)) {
			try (final Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				final Map<String, Map<String, TabData>> loaded = GSON.fromJson(reader, FILE_TYPE);
				if (loaded != null) {
					all = loaded;
				}
			} catch (Exception e) {
				LOGGER.error("Could not read the folders file", e);
			}
		}
	}

	private static void save() {
		load();
		try {
			final Path file = file();
			Files.createDirectories(file.getParent());
			Files.write(file, GSON.toJson(all, FILE_TYPE).getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			LOGGER.error("Could not save the folders file", e);
		}
	}
}
