package net.wurstclient.mixin;

import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.wurstclient.WurstClient;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin
{
	@ModifyConstant(
		method = "renderGuiItemOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V",
		constant = @Constant(floatValue = 200F))
	private static float getRenderOffset(float orig)
	{
		return WurstClient.INSTANCE.getHax().armorEspHack.isRendering() ? 0 : orig;
	}
	
	@Redirect(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)I",
		opcode = Opcodes.INVOKEVIRTUAL,
		ordinal = 0),
		method = "renderGuiItemOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V")
	private int removeShadow(TextRenderer renderer, String text, float x, float y,
		int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers,
		TextLayerType layerType, int backgroundColor, int light)
	{
		if(WurstClient.INSTANCE.getHax().armorEspHack.isRendering())
			return renderer.draw(text, x, y, color, false, matrix, vertexConsumers, layerType, backgroundColor, light);
		
		return renderer.draw(text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);
	}
}
