package com.mtrvehiclefolders.mixin;

import org.mtr.core.data.Siding;
import org.mtr.mod.screen.VehicleSelectorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Gives access to the private {@code siding} field of MTR's original vehicle selector screen,
 * so that the replacement screen can work with the same siding.
 */
@Mixin(value = VehicleSelectorScreen.class, remap = false)
public interface VehicleSelectorScreenAccessor {

	@Accessor(value = "siding", remap = false)
	Siding mtrvf$getSiding();
}
