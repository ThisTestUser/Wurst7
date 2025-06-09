/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.wurstclient.WurstClient;

@Mixin(BeaconBlockEntityRenderer.class)
public class BeaconBlockEntityRendererMixin
{
	@Inject(at = @At("HEAD"),
		method = "renderBeam(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;FFJIII)V",
		cancellable = true)
	private static void onRenderBeam(MatrixStack matrices,
		VertexConsumerProvider vertexConsumers, float tickProgress, float scale,
		long worldTime, int yOffset, int maxY, int color, CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHax().noBeaconBeamHack.isEnabled())
			ci.cancel();
	}
}
