package com.mtrvehiclefolders.mixin;

import com.mtrvehiclefolders.screen.TaggedVehicleSelectorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
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
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

	@Shadow
	public Screen currentScreen;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen mtrvf$replaceVehicleSelector(Screen screen) {
		if (screen instanceof VehicleSelectorScreen && !(screen instanceof TaggedVehicleSelectorScreen)) {
			try {
				final Siding siding = ((VehicleSelectorScreenAccessor) (Object) screen).mtrvf$getSiding();
				final ScreenExtension previousScreen = currentScreen instanceof ScreenExtension ? (ScreenExtension) currentScreen : null;
				return (Screen) (Object) new TaggedVehicleSelectorScreen(siding, previousScreen);
			} catch (Throwable e) {
				final Logger logger = LoggerFactory.getLogger("MTR Vehicle Folders");
				logger.error("Could not open the folder-based vehicle selector, falling back to the original screen", e);
			}
		}
		return screen;
	}
}
