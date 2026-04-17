/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.wurstclient.WurstClient;

@Mixin(BeaconRenderer.class)
public class BeaconRendererMixin
{
	@Inject(
		method = "submitBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;FFIII)V",
		at = @At("HEAD"),
		cancellable = true)
	private static void onRenderBeam(PoseStack matrices,
		SubmitNodeCollector collector, float scale, float rotationDegrees,
		int minHeight, int maxHeight, int color, CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHax().noBeaconBeamHack.isEnabled())
			ci.cancel();
	}
}
