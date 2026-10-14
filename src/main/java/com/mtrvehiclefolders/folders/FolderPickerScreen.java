package com.mtrvehiclefolders.folders;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.DashboardList;
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.MTRScreenBase;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * Lets the player browse the folders and choose where to move a station, route, depot or another folder.
 * Click a folder to open it, click the first row (..) to go up, then press "Move here".
 */
public class FolderPickerScreen extends MTRScreenBase implements IGui {

	private static final int BACK_COLOR = 0x808080;

	private final String tab;
	private final String title;
	private final long excludedFolderId;
	private final LongConsumer onPick;

	private final DashboardList list;
	private final ButtonWidgetExtension buttonMoveHere;
	private final ButtonWidgetExtension buttonCancel;

	private long currentFolderId = 0;
	private List<DashboardListItem> shownItems = new ArrayList<>();

	/**
	 * @param excludedFolderId a folder that must not be offered (the folder being moved), or 0
	 * @param onPick           called with the chosen folder id (0 = root)
	 */
	public FolderPickerScreen(String tab, String title, long excludedFolderId, LongConsumer onPick, ScreenExtension previousScreenExtension) {
		super(previousScreenExtension);
		this.tab = tab;
		this.title = title;
		this.excludedFolderId = excludedFolderId;
		this.onPick = onPick;

		list = new DashboardList(null, null, null, null, null, null, null, () -> "", text -> {
		});
		buttonMoveHere = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.translatable("gui.mtrvehiclefolders.move_here"), button -> {
			onPick.accept(currentFolderId);
			onClose2();
		});
		buttonCancel = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.translatable("gui.cancel"), button -> onClose2());
	}

	@Override
	protected void init2() {
		super.init2();
		list.x = (width - PANEL_WIDTH) / 2;
		list.y = SQUARE_SIZE * 2 - TEXT_PADDING;
		list.height = height - SQUARE_SIZE * 5 + TEXT_PADDING;
		list.width = PANEL_WIDTH;
		list.init(this::addChild);

		IDrawing.setPositionAndWidth(buttonMoveHere, list.x, height - SQUARE_SIZE * 2, PANEL_WIDTH / 2 - 1);
		IDrawing.setPositionAndWidth(buttonCancel, list.x + PANEL_WIDTH / 2 + 1, height - SQUARE_SIZE * 2, PANEL_WIDTH / 2 - 1);
		addChild(new ClickableWidget(buttonMoveHere));
		addChild(new ClickableWidget(buttonCancel));
		refresh();
	}

	@Override
	public void tick2() {
		list.tick();
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);
		list.render(graphicsHolder);
		super.render(graphicsHolder, mouseX, mouseY, delta);
		graphicsHolder.drawCenteredText(TextHelper.literal(title), width / 2, TEXT_PADDING, ARGB_WHITE);
		final String path = FolderStore.getPathText(tab, currentFolderId);
		graphicsHolder.drawCenteredText(path.isEmpty() ? TextHelper.translatable("gui.mtrvehiclefolders.root_folder") : TextHelper.literal(path), width / 2, SQUARE_SIZE, ARGB_LIGHT_GRAY);
	}

	@Override
	public void mouseMoved2(double mouseX, double mouseY) {
		list.mouseMoved(mouseX, mouseY);
	}

	@Override
	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		list.mouseScrolled(mouseX, mouseY, amount);
		return super.mouseScrolled2(mouseX, mouseY, amount);
	}

	@Override
	public boolean mouseClicked2(double mouseX, double mouseY, int button) {
		if (super.mouseClicked2(mouseX, mouseY, button)) {
			return true;
		}
		if (button == 0 && mouseX >= list.x && mouseX < list.x + list.width && mouseY >= list.y + SQUARE_SIZE + TEXT_FIELD_PADDING && mouseY < list.y + list.height) {
			final int index = list.getHoverItemIndex();
			if (index >= 0 && index < shownItems.size() && shownItems.get(index) instanceof FolderListItem) {
				final FolderListItem item = (FolderListItem) shownItems.get(index);
				currentFolderId = item.isBack ? FolderStore.getParent(tab, currentFolderId) : item.id;
				refresh();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isPauseScreen2() {
		return false;
	}

	private void refresh() {
		final ObjectArrayList<DashboardListItem> items = new ObjectArrayList<>();
		if (currentFolderId != 0) {
			items.add(new FolderListItem(0, "..", BACK_COLOR, true));
		}
		for (final FolderStore.Folder folder : FolderStore.getSubfolders(tab, currentFolderId)) {
			if (folder.id != excludedFolderId) {
				items.add(new FolderListItem(folder.id, folder.name, folder.color, false));
			}
		}
		shownItems = items;
		list.setData(items, false, false, false, false, false, false);
	}
}
