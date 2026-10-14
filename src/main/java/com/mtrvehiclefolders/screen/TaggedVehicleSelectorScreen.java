package com.mtrvehiclefolders.screen;

import com.mtrvehiclefolders.tree.VehicleTree;
import org.mtr.core.data.Siding;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.OrderedText;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.generated.lang.TranslationProvider;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.VehicleSelectorScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A replacement for MTR's vehicle selector. It reuses all of the original logic (selection, duplicating cars,
 * disabling vehicles that do not fit, syncing with the server) and only changes how the "available" list is shown:
 * as folders (MTR's own vehicles in a green "MTR" folder; others by transport kind; the rest in an orange "Other" folder; then country > head / middle / tail car) with a details panel in the middle.
 * <p>
 * Folders are opened by clicking on their row. The "Back" button goes one level up (or clears the search).
 * While the search box is not empty, all vehicles are shown as a flat, filtered list.
 */
public class TaggedVehicleSelectorScreen extends VehicleSelectorScreen {

	private static final int PANEL_BACKGROUND = 0x99000000;

	private final List<VehicleResource> vehicles = new ArrayList<>();
	private final VehicleTree tree;
	private final ButtonWidgetExtension buttonBack;
	private final List<String> path = new ArrayList<>();

	private List<DashboardListItem> shownItems = new ArrayList<>();
	private String lastSearch = null;

	public TaggedVehicleSelectorScreen(Siding siding, ScreenExtension previousScreenExtension) {
		super(siding, previousScreenExtension);
		// The same iteration order as in the original screen, so that list item ids are indices into this list
		CustomResourceLoader.iterateVehicles(siding.getTransportMode(), vehicles::add);

		final String localeTag = text("gui.mtrvehiclefolders.locale");
		final Locale locale = localeTag.contains(".") ? Locale.ENGLISH : Locale.forLanguageTag(localeTag);
		tree = VehicleTree.build(
				siding.getTransportMode(),
				vehicles,
				locale,
				"MTR",
				text("gui.mtrvehiclefolders.unknown_country"),
				text("gui.mtrvehiclefolders.unknown_family"),
				TaggedVehicleSelectorScreen::getFamilyName,
				carType -> text("gui.mtrvehiclefolders.car." + carType)
		);

		buttonBack = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.translatable("gui.mtrvehiclefolders.back"), button -> navigateUp());
	}

	@Override
	protected void init2() {
		super.init2();
		final int halfWidth = PANEL_WIDTH / 2;
		// The folder tree goes to the left, the selected list and the duplicate button stay on the right
		availableList.x = SQUARE_SIZE;
		IDrawing.setPositionAndWidth(buttonBack, SQUARE_SIZE, height - SQUARE_SIZE * 2, halfWidth - 1);
		IDrawing.setPositionAndWidth(buttonDone, SQUARE_SIZE + halfWidth + 1, height - SQUARE_SIZE * 2, halfWidth - 1);
		addChild(new ClickableWidget(buttonBack));
		refreshAvailableList();
	}

	@Override
	public void tick2() {
		super.tick2();
		if (!MinecraftClientData.ROUTES_PLATFORMS_SEARCH.equals(lastSearch)) {
			refreshAvailableList();
		}
		buttonBack.active = !path.isEmpty() || !MinecraftClientData.ROUTES_PLATFORMS_SEARCH.isEmpty();
	}

	@Override
	protected void updateList() {
		super.updateList();
		refreshAvailableList();
	}

	@Override
	public boolean mouseClicked2(double mouseX, double mouseY, int button) {
		if (super.mouseClicked2(mouseX, mouseY, button)) {
			return true;
		}
		if (button == 0 && isOverList(availableList.x, availableList.y, availableList.width, availableList.height, mouseX, mouseY)) {
			final int index = availableList.getHoverItemIndex();
			if (index >= 0 && index < shownItems.size() && shownItems.get(index) instanceof FolderItem) {
				path.add(((FolderItem) shownItems.get(index)).key);
				refreshAvailableList();
				resetPage();
				return true;
			}
		}
		return false;
	}

	@Override
	public void renderAdditional(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		final int centerX = availableList.x + PANEL_WIDTH + SQUARE_SIZE;
		final int centerWidth = Math.max(0, selectedList.x - SQUARE_SIZE - centerX);

		// Headers
		drawFitted(graphicsHolder, getPathText(), availableList.x + TEXT_PADDING, SQUARE_SIZE, PANEL_WIDTH - TEXT_PADDING * 2, ARGB_WHITE);
		graphicsHolder.drawCenteredText(TranslationProvider.GUI_MTR_SELECTED.getMutableText(), selectedList.x + PANEL_WIDTH / 2, SQUARE_SIZE, ARGB_WHITE);

		// Details panel for the hovered vehicle
		if (centerWidth > SQUARE_SIZE * 2) {
			final GuiDrawing guiDrawing = new GuiDrawing(graphicsHolder);
			guiDrawing.beginDrawingRectangle();
			guiDrawing.drawRectangle(centerX - TEXT_PADDING, SQUARE_SIZE - TEXT_PADDING, centerX + centerWidth + TEXT_PADDING, height - SQUARE_SIZE, PANEL_BACKGROUND);
			guiDrawing.finishDrawingRectangle();

			final VehicleResource vehicleResource = getHoveredVehicle(mouseX, mouseY);
			if (vehicleResource != null) {
				int y = SQUARE_SIZE;
				y = drawWrapped(graphicsHolder, vehicleResource.getName(), centerX, y, centerWidth, ARGB_WHITE);
				y = drawWrapped(graphicsHolder, TranslationProvider.GUI_MTR_VEHICLE_LENGTH.getMutableText(vehicleResource.getLength()), centerX, y, centerWidth, ARGB_WHITE);
				final String description = vehicleResource.getDescription().getString();
				if (!description.isEmpty()) {
					for (final String line : description.split("[|\n]")) {
						y = drawWrapped(graphicsHolder, TextHelper.literal(line), centerX, y, centerWidth, ARGB_LIGHT_GRAY);
					}
				}
				final List<String> tags = tree.getTags(vehicleResource.getId());
				if (!tags.isEmpty()) {
					drawWrapped(graphicsHolder, TextHelper.translatable("gui.mtrvehiclefolders.tags", String.join(", ", tags)), centerX, y, centerWidth, ARGB_LIGHT_GRAY);
				}
			}
		}
	}

	private void refreshAvailableList() {
		final String search = MinecraftClientData.ROUTES_PLATFORMS_SEARCH;
		lastSearch = search;

		final ObjectArrayList<DashboardListItem> items = new ObjectArrayList<>();
		boolean showAddButton = true;

		if (search.isEmpty()) {
			VehicleTree.Node node = tree.getNode(path);
			if (node == null) {
				path.clear();
				node = tree.getRoot();
			}
			if (node.children.isEmpty()) {
				// A family folder: show the vehicles themselves (with the "+" button, as in the original screen)
				for (final int index : node.vehicleIndices) {
					items.add(allData.get(index));
				}
			} else {
				showAddButton = false;
				for (final VehicleTree.Node child : node.children.values()) {
					items.add(new FolderItem(child.name, child.count, child.color));
				}
			}
		} else {
			// Searching: flat list of all vehicles, filtered by the list's own search field
			items.addAll(allData);
		}

		shownItems = items;
		availableList.setData(items, false, false, false, false, showAddButton, false);
	}

	private void navigateUp() {
		if (!MinecraftClientData.ROUTES_PLATFORMS_SEARCH.isEmpty()) {
			availableList.clearSearch();
		} else if (!path.isEmpty()) {
			path.remove(path.size() - 1);
			refreshAvailableList();
			resetPage();
		}
	}

	/**
	 * DashboardList has no public way to go back to the first page, but scrolling up inside it does exactly that.
	 */
	private void resetPage() {
		for (int i = 0; i < 200; i++) {
			availableList.mouseScrolled(availableList.x, availableList.y, 1);
		}
	}

	private VehicleResource getHoveredVehicle(int mouseX, int mouseY) {
		if (isOverList(availableList.x, availableList.y, availableList.width, availableList.height, mouseX, mouseY)) {
			final int index = availableList.getHoverItemIndex();
			if (index >= 0 && index < shownItems.size() && !(shownItems.get(index) instanceof FolderItem)) {
				return getVehicleByItemId(shownItems.get(index).id);
			}
		} else if (isOverList(selectedList.x, selectedList.y, selectedList.width, selectedList.height, mouseX, mouseY) && selectedIds instanceof LongList) {
			final int index = selectedList.getHoverItemIndex();
			if (index >= 0 && index < selectedIds.size()) {
				return getVehicleByItemId(((LongList) selectedIds).getLong(index));
			}
		}
		return null;
	}

	private VehicleResource getVehicleByItemId(long id) {
		return id >= 0 && id < vehicles.size() ? vehicles.get((int) id) : null;
	}

	private String getPathText() {
		return path.isEmpty() ? text("gui.mtrvehiclefolders.root") : String.join(" > ", path);
	}

	private static boolean isOverList(int x, int y, int width, int height, double mouseX, double mouseY) {
		// The first row of the list starts below the page buttons and the search box
		return mouseX >= x && mouseX < x + width && mouseY >= y + SQUARE_SIZE + TEXT_FIELD_PADDING && mouseY < y + height;
	}

	/**
	 * Folder name for a "family" tag value, or null if it is not one of the known transport kinds
	 * (train, bus, tram, monorail, trolleybus, subway, electricbus, car).
	 */
	private static String getFamilyName(String family) {
		final String key = "gui.mtrvehiclefolders.family." + family.toLowerCase(Locale.ROOT);
		final String translated = text(key);
		return translated.equals(key) ? null : translated;
	}

	private static String text(String key) {
		return TextHelper.translatable(key).getString();
	}

	private void drawFitted(GraphicsHolder graphicsHolder, String text, int x, int y, int maxWidth, int color) {
		final int textWidth = GraphicsHolder.getTextWidth(text);
		graphicsHolder.push();
		graphicsHolder.translate(x, 0, 0);
		if (textWidth > maxWidth && textWidth > 0) {
			graphicsHolder.scale((float) maxWidth / textWidth, 1, 1);
		}
		graphicsHolder.drawText(text, 0, y, color, false, GraphicsHolder.getDefaultLight());
		graphicsHolder.pop();
	}

	private int drawWrapped(GraphicsHolder graphicsHolder, MutableText component, int x, int y, int maxWidth, int color) {
		final List<OrderedText> lines = GraphicsHolder.wrapLines(component, Math.max(1, maxWidth));
		int newY = y;
		for (final OrderedText line : lines) {
			final int nextY = newY + TEXT_HEIGHT + 2;
			if (nextY > height - SQUARE_SIZE - TEXT_HEIGHT) {
				graphicsHolder.drawText("...", x, newY, color, false, GraphicsHolder.getDefaultLight());
				return height;
			}
			graphicsHolder.drawText(line, x, newY, color, false, GraphicsHolder.getDefaultLight());
			newY = nextY;
		}
		return newY + TEXT_PADDING;
	}

	private static class FolderItem extends DashboardListItem {

		private final String key;

		private FolderItem(String key, int count, int color) {
			super(-1, key + " (" + count + ")", color);
			this.key = key;
		}
	}
}
