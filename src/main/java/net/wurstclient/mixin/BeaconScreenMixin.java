/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.util.Collection;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.BeaconScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.BeaconScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;

@Mixin(BeaconScreen.class)
public abstract class BeaconScreenMixin
	extends HandledScreen<BeaconScreenHandler>
{
	private BeaconScreenMixin(WurstClient wurst, BeaconScreenHandler handler,
		PlayerInventory inventory, Text title)
	{
		super(handler, inventory, title);
	}
	
	@Inject(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screen/ingame/BeaconScreen;addButton(Lnet/minecraft/client/gui/widget/ClickableWidget;)V",
		ordinal = 1,
		shift = Shift.AFTER), method = "init()V", cancellable = true)
	private void addButtons(CallbackInfo ci)
	{
		if(!WurstClient.INSTANCE.getHax().beaconHack.isEnabled())
			return;
		
		List<RegistryEntry<StatusEffect>> effects =
			BeaconBlockEntity.EFFECTS_BY_LEVEL.stream()
				.flatMap(Collection::stream).toList();
		
		for(int i = 0; i < effects.size(); i++)
		{
			addButton(((BeaconScreen)(Object)this).new EffectButtonWidget(
				x + (i / 2) * 25 + 25, y + i % 2 * 25 + 32, effects.get(i),
				true, 0));
			addButton(((BeaconScreen)(Object)this).new EffectButtonWidget(
				x + (i / 2) * 25 + 133, y + i % 2 * 25 + 32, effects.get(i),
				false, 0)
			{
				@Override
				protected MutableText getEffectName(
					RegistryEntry<StatusEffect> effect)
				{
					return Text.translatable(effect.value().getTranslationKey())
						.append(" II");
				}
			});
		}
		ci.cancel();
	}
	
	@Inject(at = @At("TAIL"),
		method = "drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V")
	private void onDrawBackground(DrawContext context, float delta, int mouseX,
		int mouseY, CallbackInfo ci)
	{
		if(!WurstClient.INSTANCE.getHax().beaconHack.isEnabled())
			return;
		
		context.fill(x + 13, y + 19, x + 40, y + 94, 0xFF212121);
		context.fill(x + 152, y + 20, x + 182, y + 47, 0xFF212121);
	}
	
	@Shadow
	protected abstract <T extends ClickableWidget> void addButton(T button);
}
