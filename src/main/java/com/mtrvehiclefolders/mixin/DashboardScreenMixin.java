package com.mtrvehiclefolders.mixin;

import com.mtrvehiclefolders.folders.EditFolderScreen;
import com.mtrvehiclefolders.folders.FolderListItem;
import com.mtrvehiclefolders.folders.FolderPickerScreen;
import com.mtrvehiclefolders.folders.FolderStore;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Route;
import org.mtr.core.data.TransportMode;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.DashboardList;
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.DashboardScreen;
import org.mtr.mod.screen.DeleteConfirmationScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adds user-made folders to the lists of stations, routes and depots in MTR's dashboard.
 * <ul>
 * <li>"Folder" button (next to the add button): create a folder inside the folder that is open</li>
 * <li>click a folder row: open it; click the ".." row: go up</li>
 * <li>edit / delete buttons on a folder row: change its name and color / delete it</li>
 * <li>right-click a station, route, depot or folder: move it into another folder</li>
 * </ul>
 */
@Mixin(value = DashboardScreen.class, remap = false)
public abstract class DashboardScreenMixin extends ScreenExtension {

	@Shadow
	@Final
	private DashboardList dashboardList;
	@Shadow
	@Final
	private TransportMode transportMode;
	@Shadow
	@Final
	private ButtonWidgetExtension buttonTabStations;
	@Shadow
	@Final
	private ButtonWidgetExtension buttonTabRoutes;
	@Shadow
	@Final
	private ButtonWidgetExtension buttonAddStation;
	@Shadow
	@Final
	private ButtonWidgetExtension buttonAddRoute;
	@Shadow
	@Final
	private ButtonWidgetExtension buttonAddDepot;
	@Shadow
	private AreaBase<?, ?> editingArea;
	@Shadow
	private Route editingRoute;

	@Unique
	private ButtonWidgetExtension mtrvf$buttonNewFolder;
	@Unique
	private List<DashboardListItem> mtrvf$shownItems = new ArrayList<>();

	protected DashboardScreenMixin() {
		super();
	}

	@Inject(method = "init2", at = @At("RETURN"))
	private void mtrvf$init(CallbackInfo ci) {
		final int bottomRowY = height - IGui.SQUARE_SIZE;
		final int folderButtonWidth = IGui.PANEL_WIDTH / 3;
		mtrvf$buttonNewFolder = new ButtonWidgetExtension(0, 0, 0, IGui.SQUARE_SIZE, TextHelper.translatable("gui.mtrvehiclefolders.new_folder"), button -> mtrvf$newFolder());
		IDrawing.setPositionAndWidth(mtrvf$buttonNewFolder, IGui.PANEL_WIDTH - folderButtonWidth, bottomRowY, folderButtonWidth);
		// Make room for the new button by making MTR's add buttons a bit narrower
		IDrawing.setPositionAndWidth(buttonAddStation, 0, bottomRowY, IGui.PANEL_WIDTH - folderButtonWidth);
		IDrawing.setPositionAndWidth(buttonAddRoute, 0, bottomRowY, IGui.PANEL_WIDTH - folderButtonWidth);
		IDrawing.setPositionAndWidth(buttonAddDepot, 0, bottomRowY, IGui.PANEL_WIDTH - folderButtonWidth);
		addChild(new ClickableWidget(mtrvf$buttonNewFolder));
	}

	@Inject(method = "tick2", at = @At("RETURN"))
	private void mtrvf$tick(CallbackInfo ci) {
		final boolean listMode = mtrvf$isListMode();
		if (mtrvf$buttonNewFolder != null) {
			mtrvf$buttonNewFolder.visible = listMode;
		}
		if (!listMode) {
			return;
		}

		try {
			final String tab = mtrvf$tabKey();
			final boolean isStations = !buttonTabStations.active;
			final boolean isRoutes = !buttonTabRoutes.active;

			// The same data MTR has just put into the list
			final ObjectArrayList<DashboardListItem> source = new ObjectArrayList<>();
			if (isStations) {
				source.addAll(MinecraftClientData.convertDataSet(MinecraftClientData.getDashboardInstance().stations));
			} else if (isRoutes) {
				source.addAll(MinecraftClientData.getFilteredDataSet(transportMode, MinecraftClientData.getDashboardInstance().routes));
			} else {
				source.addAll(MinecraftClientData.getFilteredDataSet(transportMode, MinecraftClientData.getDashboardInstance().depots));
			}

			final ObjectArrayList<DashboardListItem> view = new ObjectArrayList<>();
			if (MinecraftClientData.DASHBOARD_SEARCH.isEmpty()) {
				final long currentFolder = FolderStore.getCurrentFolder(tab);
				if (currentFolder != 0) {
					view.add(new FolderListItem(0, ".. " + FolderStore.getPathText(tab, currentFolder), FolderListItem.BACK_COLOR, true));
				}
				for (final FolderStore.Folder folder : FolderStore.getSubfolders(tab, currentFolder)) {
					view.add(new FolderListItem(folder.id, folder.name, folder.color, false));
				}
				view.addAll(source.stream().filter(item -> FolderStore.getItemFolder(tab, item.id) == currentFolder).sorted().collect(Collectors.toList()));
			} else {
				// While searching, show everything regardless of folders
				view.addAll(source.stream().sorted().collect(Collectors.toList()));
			}

			mtrvf$shownItems = view;
			dashboardList.setData(view, !isRoutes, true, true, false, false, true);
		} catch (Exception e) {
			LoggerFactory.getLogger("MTR Vehicle Folders").error("Could not show the folders", e);
		}
	}

	@Override
	public boolean mouseClicked2(double mouseX, double mouseY, int button) {
		if (super.mouseClicked2(mouseX, mouseY, button)) {
			return true;
		}
		if (mtrvf$isListMode() && mouseX >= dashboardList.x && mouseX < dashboardList.x + dashboardList.width && mouseY >= dashboardList.y + IGui.SQUARE_SIZE + IGui.TEXT_FIELD_PADDING && mouseY < dashboardList.y + dashboardList.height) {
			final int index = dashboardList.getHoverItemIndex();
			if (index >= 0 && index < mtrvf$shownItems.size()) {
				final DashboardListItem item = mtrvf$shownItems.get(index);
				final String tab = mtrvf$tabKey();
				if (item instanceof FolderListItem) {
					final FolderListItem folderItem = (FolderListItem) item;
					if (button == 0) {
						FolderStore.setCurrentFolder(tab, folderItem.isBack ? FolderStore.getParent(tab, FolderStore.getCurrentFolder(tab)) : folderItem.id);
						mtrvf$resetPage();
						return true;
					} else if (button == 1 && !folderItem.isBack) {
						MinecraftClient.getInstance().openScreen(new Screen(new FolderPickerScreen(tab, folderItem.getName(false), folderItem.id, target -> FolderStore.moveFolder(tab, folderItem.id, target), this)));
						return true;
					}
				} else if (button == 1) {
					MinecraftClient.getInstance().openScreen(new Screen(new FolderPickerScreen(tab, item.getName(false), 0, target -> FolderStore.moveItem(tab, item.id, target), this)));
					return true;
				}
			}
		}
		return false;
	}

	@Inject(method = "onFind", at = @At("HEAD"), cancellable = true)
	private void mtrvf$onFind(DashboardListItem item, int index, CallbackInfo ci) {
		if (item instanceof FolderListItem) {
			ci.cancel();
		}
	}

	@Inject(method = "onDrawArea", at = @At("HEAD"), cancellable = true)
	private void mtrvf$onDrawArea(DashboardListItem item, int index, CallbackInfo ci) {
		if (item instanceof FolderListItem) {
			ci.cancel();
		}
	}

	@Inject(method = "onEdit", at = @At("HEAD"), cancellable = true)
	private void mtrvf$onEdit(DashboardListItem item, int index, CallbackInfo ci) {
		if (item instanceof FolderListItem) {
			ci.cancel();
			final FolderListItem folderItem = (FolderListItem) item;
			if (!folderItem.isBack) {
				MinecraftClient.getInstance().openScreen(new Screen(new EditFolderScreen(mtrvf$tabKey(), 0, folderItem.id, this)));
			}
		}
	}

	@Inject(method = "onDelete", at = @At("HEAD"), cancellable = true)
	private void mtrvf$onDelete(DashboardListItem item, int index, CallbackInfo ci) {
		if (item instanceof FolderListItem) {
			ci.cancel();
			final FolderListItem folderItem = (FolderListItem) item;
			if (!folderItem.isBack) {
				final String tab = mtrvf$tabKey();
				MinecraftClient.getInstance().openScreen(new Screen(new DeleteConfirmationScreen(() -> FolderStore.deleteFolder(tab, folderItem.id), folderItem.getName(false), this)));
			}
		}
	}

	@Unique
	private void mtrvf$newFolder() {
		final String tab = mtrvf$tabKey();
		MinecraftClient.getInstance().openScreen(new Screen(new EditFolderScreen(tab, FolderStore.getCurrentFolder(tab), 0, this)));
	}

	@Unique
	private boolean mtrvf$isListMode() {
		return editingArea == null && editingRoute == null;
	}

	/**
	 * Folders are kept separately for stations, and for routes and depots of each transport mode.
	 */
	@Unique
	private String mtrvf$tabKey() {
		if (!buttonTabStations.active) {
			return "stations";
		}
		return (!buttonTabRoutes.active ? "routes" : "depots") + ":" + transportMode.name();
	}

	/**
	 * DashboardList has no public way to go back to the first page, but scrolling up inside it does exactly that.
	 */
	@Unique
	private void mtrvf$resetPage() {
		for (int i = 0; i < 200; i++) {
			dashboardList.mouseScrolled(dashboardList.x, dashboardList.y, 1);
		}
	}
}
