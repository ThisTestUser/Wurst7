package net.wurstclient.mixin;

import java.util.Arrays;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;

@Mixin(BeaconScreen.class)
public abstract class BeaconScreenMixin extends HandledScreen<BeaconScreenHandler>
{	
	private BeaconScreenMixin(WurstClient wurst, BeaconScreenHandler handler,
		PlayerInventory inventory, Text title)
	{
		super(handler, inventory, title);
	}
	
	@Shadow
	private <T extends ClickableWidget> void addButton(T button)
	{
		
	}
	
	@Inject(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screen/ingame/BeaconScreen;addButton(Lnet/minecraft/client/gui/widget/ClickableWidget;)V",
		ordinal = 1, shift = Shift.AFTER),
		method = "init()V",
		cancellable = true)
	private void addButtons(CallbackInfo ci)
	{
		if(!WurstClient.INSTANCE.getHax().beaconHack.isEnabled())
			return;
		
		StatusEffect[] effects = Arrays.stream(BeaconBlockEntity.EFFECTS_BY_LEVEL)
			.flatMap(Arrays::stream).toArray(StatusEffect[]::new);
		
		for(int i = 0; i < effects.length; i++)
		{
			addButton(((BeaconScreen)(Object)this).new EffectButtonWidget(
				x + (i / 2) * 25 + 25, y + i % 2 * 25 + 32,
				effects[i], true, 0));
			addButton(((BeaconScreen)(Object)this).new EffectButtonWidget(
				x + (i / 2) * 25 + 133, y + i % 2 * 25 + 32,
				effects[i], false, 0)
			{
				@Override
				protected MutableText getEffectName(StatusEffect statusEffect)
				{
					return Text.translatable(statusEffect.getTranslationKey()).append(" II");
				}
			});
		}
		ci.cancel();
	}
	
	@Inject(at = @At("TAIL"),
		method = "drawBackground(Lnet/minecraft/client/util/math/MatrixStack;FII)V")
	private void onDrawBackground(MatrixStack matrices, float delta, int mouseX,
		int mouseY, CallbackInfo ci)
	{
		if(!WurstClient.INSTANCE.getHax().beaconHack.isEnabled())
			return;

		DrawableHelper.fill(matrices, x + 13, y + 19, x + 40,
			y + 94, 0xFF212121);
		DrawableHelper.fill(matrices, x + 152, y + 20, x + 182,
			y + 47, 0xFF212121);
	}
}
