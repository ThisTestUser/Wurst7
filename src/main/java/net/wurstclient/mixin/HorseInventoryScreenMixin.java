/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.client.gui.screens.inventory.HorseInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.AutoStealHack;

@Mixin(HorseInventoryScreen.class)
public abstract class HorseInventoryScreenMixin
	extends AbstractMountInventoryScreen<HorseInventoryMenu>
{
	@Unique
	private final AutoStealHack autoSteal =
		WurstClient.INSTANCE.getHax().autoStealHack;
	
	public HorseInventoryScreenMixin(WurstClient wurst,
		HorseInventoryMenu container, Inventory playerInventory,
		AbstractHorse entity, int inventoryColumns)
	{
		super(container, playerInventory, entity.getDisplayName(),
			inventoryColumns, entity);
	}
	
	@Override
	protected void init()
	{
		super.init();
		
		if(!WurstClient.INSTANCE.isEnabled() || inventoryColumns == 0)
			return;
		
		if(autoSteal.areButtonsVisible())
		{
			if(autoSteal.hasDropButton())
				addRenderableWidget(
					Button
						.builder(Component.literal("Drop"),
							b -> autoSteal.dropHorse(this, inventoryColumns,
								false))
						.bounds(leftPos + imageWidth - 108, topPos + 4, 50, 12)
						.build());
			else
				addRenderableWidget(Button
					.builder(Component.literal("Steal"),
						b -> autoSteal.stealHorse(this, inventoryColumns,
							false))
					.bounds(leftPos + imageWidth - 108, topPos + 4, 50, 12)
					.build());
			
			addRenderableWidget(
				Button
					.builder(Component.literal("Store"),
						b -> autoSteal.storeHorse(this, inventoryColumns,
							false))
					.bounds(leftPos + imageWidth - 56, topPos + 4, 50, 12)
					.build());
		}
		
		if(autoSteal.isEnabled() && autoSteal.stealFromHorses())
			if(autoSteal.shouldDrop())
				autoSteal.dropHorse(this, inventoryColumns, true);
			else
				autoSteal.stealHorse(this, inventoryColumns, true);
	}
}
