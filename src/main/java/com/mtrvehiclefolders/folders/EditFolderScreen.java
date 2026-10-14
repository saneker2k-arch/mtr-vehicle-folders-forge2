package com.mtrvehiclefolders.folders;

import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextFieldWidgetExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mapping.tool.TextCase;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.MTRScreenBase;
import org.mtr.mod.screen.WidgetColorSelector;

/**
 * Screen for creating a new folder or editing the name and color of an existing one.
 * The folder is saved when the screen is closed (like MTR's own edit screens).
 * A new folder with an empty name is not created.
 */
public class EditFolderScreen extends MTRScreenBase implements IGui {

	private static final int DEFAULT_COLOR = 0xE0B030;
	private static final int NAME_WIDTH = 150;
	private static final int COLOR_WIDTH = 48;

	private final String tab;
	private final long parentId;
	private final long folderId;

	private final TextFieldWidgetExtension textFieldName;
	private final WidgetColorSelector colorSelector;
	private final ButtonWidgetExtension buttonDone;

	/**
	 * @param folderId 0 to create a new folder inside parentId, otherwise the folder to edit
	 */
	public EditFolderScreen(String tab, long parentId, long folderId, ScreenExtension previousScreenExtension) {
		super(previousScreenExtension);
		this.tab = tab;
		this.parentId = parentId;
		this.folderId = folderId;

		textFieldName = new TextFieldWidgetExtension(0, 0, 0, SQUARE_SIZE, 1024, TextCase.DEFAULT, null, null);
		colorSelector = new WidgetColorSelector(this, true, () -> {
		});
		buttonDone = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.translatable("gui.done"), button -> onClose2());
	}

	@Override
	protected void init2() {
		super.init2();
		final int start = (width - NAME_WIDTH - COLOR_WIDTH) / 2;
		final int y = SQUARE_SIZE * 2 + TEXT_FIELD_PADDING / 2;
		IDrawing.setPositionAndWidth(textFieldName, start + TEXT_FIELD_PADDING / 2, y, NAME_WIDTH - TEXT_FIELD_PADDING);
		IDrawing.setPositionAndWidth(colorSelector, start + NAME_WIDTH + TEXT_FIELD_PADDING / 2, y, COLOR_WIDTH - TEXT_FIELD_PADDING);
		IDrawing.setPositionAndWidth(buttonDone, (width - PANEL_WIDTH) / 2, y + SQUARE_SIZE * 3, PANEL_WIDTH);

		final FolderStore.Folder folder = FolderStore.getFolder(tab, folderId);
		textFieldName.setText2(folder == null ? "" : folder.name);
		colorSelector.setColor(folder == null ? DEFAULT_COLOR : folder.color);

		addChild(new ClickableWidget(textFieldName));
		addChild(new ClickableWidget(colorSelector));
		addChild(new ClickableWidget(buttonDone));
	}

	@Override
	public void tick2() {
		textFieldName.tick2();
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);
		final int start = (width - NAME_WIDTH - COLOR_WIDTH) / 2;
		graphicsHolder.drawCenteredText(TextHelper.translatable("gui.mtrvehiclefolders.folder_name"), start + NAME_WIDTH / 2, SQUARE_SIZE + TEXT_PADDING, ARGB_WHITE);
		graphicsHolder.drawCenteredText(TextHelper.translatable("gui.mtrvehiclefolders.folder_color"), start + NAME_WIDTH + COLOR_WIDTH / 2, SQUARE_SIZE + TEXT_PADDING, ARGB_WHITE);
		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	@Override
	public void onClose2() {
		super.onClose2();
		final String name = textFieldName.getText2().trim();
		if (folderId == 0) {
			if (!name.isEmpty()) {
				FolderStore.createFolder(tab, parentId, name, colorSelector.getColor());
			}
		} else {
			final FolderStore.Folder folder = FolderStore.getFolder(tab, folderId);
			FolderStore.updateFolder(tab, folderId, name.isEmpty() && folder != null ? folder.name : name, colorSelector.getColor());
		}
	}

	@Override
	public boolean isPauseScreen2() {
		return false;
	}
}
