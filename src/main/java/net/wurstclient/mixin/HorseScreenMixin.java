/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HorseScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.AutoStealHack;

@Mixin(HorseScreen.class)
public abstract class HorseScreenMixin extends HandledScreen<HorseScreenHandler>
{
	@Shadow
	@Final
	private int slotColumnCount;
	
	@Unique
	private final AutoStealHack autoSteal =
		WurstClient.INSTANCE.getHax().autoStealHack;
	
	public HorseScreenMixin(WurstClient wurst, HorseScreenHandler handler,
		PlayerInventory inventory, AbstractHorseEntity entity,
		int slotColumnCount)
	{
		super(handler, inventory, entity.getDisplayName());
	}
	
	@Override
	protected void init()
	{
		super.init();
		
		if(!WurstClient.INSTANCE.isEnabled() || slotColumnCount == 0)
			return;
		
		if(autoSteal.areButtonsVisible())
		{
			if(autoSteal.hasDropButton())
				addDrawableChild(
					ButtonWidget
						.builder(Text.literal("Drop"),
							b -> autoSteal.dropHorse(this, slotColumnCount,
								false))
						.dimensions(x + backgroundWidth - 108, y + 4, 50, 12)
						.build());
			else
				addDrawableChild(
					ButtonWidget
						.builder(Text.literal("Steal"),
							b -> autoSteal.stealHorse(this, slotColumnCount,
								false))
						.dimensions(x + backgroundWidth - 108, y + 4, 50, 12)
						.build());
			
			addDrawableChild(ButtonWidget
				.builder(Text.literal("Store"),
					b -> autoSteal.storeHorse(this, slotColumnCount, false))
				.dimensions(x + backgroundWidth - 56, y + 4, 50, 12).build());
		}
		
		if(autoSteal.isEnabled() && autoSteal.stealFromHorses())
			if(autoSteal.shouldDrop())
				autoSteal.dropHorse(this, slotColumnCount, true);
			else
				autoSteal.stealHorse(this, slotColumnCount, true);
	}
}
