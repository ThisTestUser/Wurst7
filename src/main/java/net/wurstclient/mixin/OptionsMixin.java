/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Options;
import net.wurstclient.WurstClient;

@Mixin(Options.class)
public class OptionsMixin
{
	@Inject(at = @At("HEAD"),
		method = "getEffectiveRenderDistance()I",
		cancellable = true)
	private void onGetViewDistance(CallbackInfoReturnable<Integer> cir)
	{
		if(WurstClient.INSTANCE.getHax().renderDistanceHack.isEnabled())
			cir.setReturnValue(
				WurstClient.INSTANCE.getHax().renderDistanceHack.getDistance());
	}
}
