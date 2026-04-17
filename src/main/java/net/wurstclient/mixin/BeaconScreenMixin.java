/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.util.Collection;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.wurstclient.WurstClient;

@Mixin(BeaconScreen.class)
public abstract class BeaconScreenMixin
	extends AbstractContainerScreen<BeaconMenu>
{
	@Shadow
	@Final
	private List<?> beaconButtons;
	
	private BeaconScreenMixin(WurstClient wurst, BeaconMenu handler,
		Inventory inventory, Component title)
	{
		super(handler, inventory, title);
	}
	
	@Inject(method = "init()V",
		at = @At(value = "INVOKE",
			target = "Ljava/util/List;clear()V",
			ordinal = 0,
			shift = Shift.AFTER),
		cancellable = true)
	private void addButtons(CallbackInfo ci)
	{
		if(!WurstClient.INSTANCE.getHax().beaconHack.isEnabled())
			return;
		
		List<Holder<MobEffect>> effects = BeaconBlockEntity.BEACON_EFFECTS
			.stream().flatMap(Collection::stream).toList();
		
		for(int i = 0; i < effects.size(); i++)
		{
			addBeaconButton(((BeaconScreen)(Object)this).new BeaconPowerButton(
				leftPos + (i / 2) * 25 + 25, topPos + i % 2 * 25 + 32,
				effects.get(i), true, 0));
			addBeaconButton(((BeaconScreen)(Object)this).new BeaconPowerButton(
				leftPos + (i / 2) * 25 + 133, topPos + i % 2 * 25 + 32,
				effects.get(i), false, 0)
			{
				@Override
				protected MutableComponent createEffectDescription(
					Holder<MobEffect> effect)
				{
					return Component
						.translatable(effect.value().getDescriptionId())
						.append(" II");
				}
			});
		}
		
		addBeaconButton(((BeaconScreen)(Object)this).new BeaconConfirmButton(
			leftPos + 164, topPos + 107));
		addBeaconButton(((BeaconScreen)(Object)this).new BeaconCancelButton(
			leftPos + 190, topPos + 107));
		
		ci.cancel();
	}
	
	@Inject(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
		at = @At("TAIL"))
	private void onDrawBackground(GuiGraphics context, float delta, int mouseX,
		int mouseY, CallbackInfo ci)
	{
		if(!WurstClient.INSTANCE.getHax().beaconHack.isEnabled())
			return;
		
		context.fill(leftPos + 13, topPos + 19, leftPos + 40, topPos + 94,
			0xFF212121);
		context.fill(leftPos + 152, topPos + 20, leftPos + 182, topPos + 47,
			0xFF212121);
	}
	
	@Shadow
	protected abstract <T extends AbstractWidget> void addBeaconButton(
		T button);
}
