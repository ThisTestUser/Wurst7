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

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.AutoStealHack;

@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin
	extends HandledScreen<GenericContainerScreenHandler>
{
	@Shadow
	@Final
	private int rows;
	
	@Unique
	private final AutoStealHack autoSteal =
		WurstClient.INSTANCE.getHax().autoStealHack;
	
	public GenericContainerScreenMixin(WurstClient wurst,
		GenericContainerScreenHandler handler, PlayerInventory inventory,
		Text title)
	{
		super(handler, inventory, title);
	}
	
	@Override
	public void init()
	{
		super.init();
		
		if(!WurstClient.INSTANCE.isEnabled())
			return;
		
		if(autoSteal.areButtonsVisible())
		{
			if(autoSteal.hasDropButton())
				addDrawableChild(ButtonWidget
					.builder(Text.literal("Drop"),
						b -> autoSteal.drop(this, rows, false))
					.dimensions(x + backgroundWidth - 108, y + 4, 50, 12)
					.build());
			else
				addDrawableChild(ButtonWidget
					.builder(Text.literal("Steal"),
						b -> autoSteal.steal(this, rows, false))
					.dimensions(x + backgroundWidth - 108, y + 4, 50, 12)
					.build());
			
			addDrawableChild(ButtonWidget
				.builder(Text.literal("Store"),
					b -> autoSteal.store(this, rows, false))
				.dimensions(x + backgroundWidth - 56, y + 4, 50, 12).build());
		}
		
		if(autoSteal.isEnabled())
			if(autoSteal.shouldDrop())
				autoSteal.drop(this, rows, true);
			else
				autoSteal.steal(this, rows, true);
	}
}
