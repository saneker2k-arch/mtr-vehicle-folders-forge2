package com.mtrvehiclefolders.folders;

import org.mtr.mod.screen.DashboardListItem;

/**
 * A row in a dashboard list that is a folder (or the "back" row that goes one level up) instead of a station, route or depot.
 */
public class FolderListItem extends DashboardListItem {

	public static final int BACK_COLOR = 0x808080;

	public final boolean isBack;

	public FolderListItem(long folderId, String name, int color, boolean isBack) {
		super(folderId, name, color);
		this.isBack = isBack;
	}
}
