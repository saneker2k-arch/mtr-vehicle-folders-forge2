package com.mtrvehiclefolders.tree;

import org.mtr.core.data.TransportMode;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.VehicleResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groups vehicles into folders:
 * <ol>
 * <li>top level: the green "MTR" folder (vehicles that come with MTR), transport kinds (family tag: bus, tram, subway...)
 * and the orange "Other" folder for everything else</li>
 * <li>country (iso_3166 tag)</li>
 * <li>car type from the vehicle id: head (cab_1), middle (trailer), tail (cab_2), double-ended (cab_3).
 * If none of the vehicles in a country has a recognisable car type, the vehicles are shown directly in the country folder.</li>
 * </ol>
 * Vehicle positions in the list are indices into the list passed to {@link #build}.
 */
public final class VehicleTree {

	public static final class Node {

		public final String name;
		public final int color;
		public final int order;
		// Sorted by order and then by name once the tree is built
		public final Map<String, Node> children = new LinkedHashMap<>();
		public final List<Integer> vehicleIndices = new ArrayList<>();
		public int count;

		private Node(String name, int color, int order) {
			this.name = name;
			this.color = color;
			this.order = order;
		}

		/**
		 * @return the subfolder with the given name, created with the color of this folder if it does not exist yet
		 */
		private Node child(String childName, int childOrder) {
			return child(childName, color, childOrder);
		}

		private Node child(String childName, int childColor, int childOrder) {
			return children.computeIfAbsent(childName, key -> new Node(key, childColor, childOrder));
		}
	}

	public static final int COLOR_KIND = 0xE0B030;
	public static final int COLOR_MTR = 0x3CB43C;
	public static final int COLOR_OTHER = 0xFF8C1A;

	// Car type keys, also used as the end of the language keys
	public static final String CAR_HEAD = "cab_1";
	public static final String CAR_TAIL = "cab_2";
	public static final String CAR_DOUBLE = "cab_3";
	public static final String CAR_MIDDLE = "trailer";
	public static final String CAR_OTHER = "other";

	private static final String TAG_COUNTRY = "iso_3166";
	private static final String TAG_FAMILY = "family";
	private static final Pattern CAR_TYPE_PATTERN = Pattern.compile("(?:^|_)(cab_?([123])|trailer)(?:_|$)");

	private final Node root = new Node("", COLOR_KIND, 0);
	private final Map<String, List<String>> tagsByVehicleId = new HashMap<>();

	private VehicleTree() {
	}

	/**
	 * @param familyName  gives the folder name for a "family" tag value, or null if it is not a known transport kind
	 * @param carTypeName gives the folder name for one of the CAR_ keys
	 */
	public static VehicleTree build(TransportMode transportMode, List<VehicleResource> vehicles, Locale locale, String mtrName, String unknownCountry, String unknownFamily, Function<String, String> familyName, Function<String, String> carTypeName) {
		final VehicleTree tree = new VehicleTree();
		final Map<String, List<String>> countriesByVehicleId = new HashMap<>();
		final Map<String, List<String>> familiesByVehicleId = new HashMap<>();

		// tag key -> tag value -> vehicle ids (provided by MTR)
		final Map<String, ? extends Map<String, ? extends List<String>>> tags = CustomResourceLoader.getVehicleTags(transportMode);
		tags.forEach((key, values) -> values.forEach((value, ids) -> ids.forEach(id -> {
			tree.tagsByVehicleId.computeIfAbsent(id, k -> new ArrayList<>()).add(key + ":" + value);
			if (key.equals(TAG_COUNTRY)) {
				countriesByVehicleId.computeIfAbsent(id, k -> new ArrayList<>()).add(value);
			} else if (key.equals(TAG_FAMILY)) {
				familiesByVehicleId.computeIfAbsent(id, k -> new ArrayList<>()).add(value);
			}
		})));

		final Set<String> builtInIds = BuiltInVehicles.getIds();

		for (int i = 0; i < vehicles.size(); i++) {
			final String id = vehicles.get(i).getId();

			final List<String> countries = new ArrayList<>(countriesByVehicleId.getOrDefault(id, new ArrayList<>()));
			if (countries.isEmpty()) {
				countries.add("");
			}

			// Top level folders: name -> color
			final Map<String, Integer> topFolders = new LinkedHashMap<>();
			if (builtInIds.contains(id)) {
				topFolders.put(mtrName, COLOR_MTR);
			} else {
				// Only recognised transport kinds get their own folder (familyName returns null for anything else)
				final Set<String> kinds = new LinkedHashSet<>();
				for (final String family : familiesByVehicleId.getOrDefault(id, new ArrayList<>())) {
					final String kind = familyName.apply(family);
					if (kind != null) {
						kinds.add(kind);
					}
				}
				if (kinds.isEmpty()) {
					topFolders.put(unknownFamily, COLOR_OTHER);
				} else {
					for (final String kind : kinds) {
						topFolders.put(kind, COLOR_KIND);
					}
				}
			}

			final String carType = getCarType(id);
			final String carFolder = carTypeName.apply(carType);
			final int carOrder = getCarOrder(carType);

			for (final Map.Entry<String, Integer> topFolder : topFolders.entrySet()) {
				final String topName = topFolder.getKey();
				final int topOrder = topName.equals(mtrName) ? -1 : (topName.equals(unknownFamily) ? 9 : 0);
				final Node top = tree.root.child(topName, topFolder.getValue(), topOrder);
				for (final String country : countries) {
					top.child(country.isEmpty() ? unknownCountry : getCountryName(country, locale), country.isEmpty() ? 9 : 0)
							.child(carFolder, carOrder)
							.vehicleIndices.add(i);
				}
			}
		}

		finish(tree.root, 0, carTypeName.apply(CAR_OTHER));
		return tree;
	}

	/**
	 * @return the folder at the given path, or null if it does not exist
	 */
	public Node getNode(List<String> path) {
		Node node = root;
		for (final String key : path) {
			node = node.children.get(key);
			if (node == null) {
				return null;
			}
		}
		return node;
	}

	public Node getRoot() {
		return root;
	}

	/**
	 * @return all tags of a vehicle as "key:value" strings
	 */
	public List<String> getTags(String vehicleId) {
		return tagsByVehicleId.getOrDefault(vehicleId, new ArrayList<>());
	}

	/**
	 * Sorts the folders, counts the vehicles and removes the car type level where it would have only one "other" folder.
	 *
	 * @param depth 0 for the root, 1 for the top level folders, 2 for country folders
	 */
	private static void finish(Node node, int depth, String otherCarsName) {
		if (depth == 2 && node.children.size() == 1 && node.children.containsKey(otherCarsName)) {
			node.vehicleIndices.addAll(node.children.remove(otherCarsName).vehicleIndices);
		}

		final List<Node> sorted = new ArrayList<>(node.children.values());
		sorted.sort(Comparator.comparingInt((Node child) -> child.order).thenComparing(child -> child.name, String.CASE_INSENSITIVE_ORDER));
		node.children.clear();

		int total = node.vehicleIndices.size();
		for (final Node child : sorted) {
			node.children.put(child.name, child);
			finish(child, depth + 1, otherCarsName);
			total += child.count;
		}
		node.count = total;
	}

	/**
	 * @return one of the CAR_ keys, found from the end of the vehicle id (for example "sp1900_cab_1")
	 */
	private static String getCarType(String vehicleId) {
		final Matcher matcher = CAR_TYPE_PATTERN.matcher(vehicleId.toLowerCase(Locale.ROOT));
		if (matcher.find()) {
			return matcher.group(2) == null ? CAR_MIDDLE : "cab_" + matcher.group(2);
		}
		return CAR_OTHER;
	}

	private static int getCarOrder(String carType) {
		switch (carType) {
			case CAR_HEAD:
				return 1;
			case CAR_MIDDLE:
				return 2;
			case CAR_TAIL:
				return 3;
			case CAR_DOUBLE:
				return 4;
			default:
				return 5;
		}
	}

	private static String getCountryName(String code, Locale locale) {
		if (code.length() == 2) {
			final String name = new Locale("", code).getDisplayCountry(locale);
			if (!name.isEmpty()) {
				return name;
			}
		}
		return code;
	}
}
