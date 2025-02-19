/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.wurstclient.WurstClient;

@Mixin(DrawContext.class)
public class DrawContextMixin
{
	@ModifyConstant(method = "drawItemBar(Lnet/minecraft/item/ItemStack;II)V",
		constant = @Constant(intValue = 200))
	private int removeItemBarOffset(int original)
	{
		if(WurstClient.INSTANCE.getHax().armorEspHack.isRendering())
			return 0;
		
		return original;
	}
	
	@WrapOperation(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V",
		ordinal = 0),
		method = "drawStackCount(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V")
	private void removeTranslation(MatrixStack matrices, float x, float y,
		float z, Operation<Void> original)
	{
		if(WurstClient.INSTANCE.getHax().armorEspHack.isRendering())
			return;
		
		original.call(matrices, x, y, z);
	}
	
	@WrapOperation(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I",
		ordinal = 0),
		method = "drawText(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;IIIZ)I")
	private int removeShadow(TextRenderer renderer, String text, float x,
		float y, int color, boolean shadow, Matrix4f matrix,
		VertexConsumerProvider vertexConsumers, TextLayerType layerType,
		int backgroundColor, int light, Operation<Integer> original)
	{
		if(WurstClient.INSTANCE.getHax().armorEspHack.isRendering())
		{
			renderer.draw(text, x, y, -1, false, matrix, vertexConsumers,
				TextLayerType.SEE_THROUGH, 0, light);
			return original.call(renderer, text, x, y, color, false, matrix,
				vertexConsumers, layerType, backgroundColor, light);
		}
		
		return original.call(renderer, text, x, y, color, shadow, matrix,
			vertexConsumers, layerType, backgroundColor, light);
	}
}
