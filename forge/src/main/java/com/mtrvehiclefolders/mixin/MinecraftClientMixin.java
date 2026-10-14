package com.mtrvehiclefolders.mixin;

import com.mtrvehiclefolders.screen.TaggedVehicleSelectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.mtr.core.data.Siding;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mod.screen.VehicleSelectorScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Whenever MTR opens its vehicle selector screen, open the folder-based screen instead.
 * If anything goes wrong, the original MTR screen is opened as usual.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

	@Shadow
	public Screen screen;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen mtrvf$replaceVehicleSelector(Screen newScreen) {
		if (newScreen instanceof VehicleSelectorScreen && !(newScreen instanceof TaggedVehicleSelectorScreen)) {
			try {
				final Siding siding = ((VehicleSelectorScreenAccessor) (Object) newScreen).mtrvf$getSiding();
				final ScreenExtension previousScreen = screen instanceof ScreenExtension ? (ScreenExtension) screen : null;
				return (Screen) (Object) new TaggedVehicleSelectorScreen(siding, previousScreen);
			} catch (Throwable e) {
				final Logger logger = LoggerFactory.getLogger("MTR Vehicle Folders");
				logger.error("Could not open the folder-based vehicle selector, falling back to the original screen", e);
			}
		}
		return newScreen;
	}
}
