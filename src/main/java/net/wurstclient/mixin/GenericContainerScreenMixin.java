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

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.AutoStealHack;

@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin
	extends HandledScreen<GenericContainerScreenHandler>
	implements ScreenHandlerProvider<GenericContainerScreenHandler>
{
	@Shadow
	@Final
	private int rows;
	
	private final AutoStealHack autoSteal =
		WurstClient.INSTANCE.getHax().autoStealHack;
	private int mode;
	
	public GenericContainerScreenMixin(WurstClient wurst,
		GenericContainerScreenHandler handler, PlayerInventory inventory,
		Text title)
	{
		super(handler, inventory, title);
	}
	
	@Override
	protected void init()
	{
		super.init();
		
		if(!WurstClient.INSTANCE.isEnabled())
			return;
		
		if(autoSteal.areButtonsVisible())
		{
			if(autoSteal.hasDropButton())
				addDrawableChild(
					ButtonWidget.builder(Text.literal("Drop"), b -> drop(false))
						.dimensions(x + backgroundWidth - 108, y + 4, 50, 12)
						.build());
			else
				addDrawableChild(ButtonWidget
					.builder(Text.literal("Steal"), b -> steal(false))
					.dimensions(x + backgroundWidth - 108, y + 4, 50, 12)
					.build());
			
			addDrawableChild(ButtonWidget
				.builder(Text.literal("Store"), b -> store(false))
				.dimensions(x + backgroundWidth - 56, y + 4, 50, 12).build());
		}
		
		if(autoSteal.isEnabled())
			if(autoSteal.shouldDrop())
				drop(true);
			else
				steal(true);
	}
	
	private void steal(boolean startingDelay)
	{
		runInThread(() -> shiftClickSlots(0, rows * 9, 1), startingDelay);
	}
	
	private void store(boolean startingDelay)
	{
		runInThread(() -> shiftClickSlots(rows * 9, rows * 9 + 36, 2),
			startingDelay);
	}
	
	private void drop(boolean startingDelay)
	{
		runInThread(() -> shiftClickSlots(0, rows * 9, 3), startingDelay);
	}
	
	private void runInThread(Runnable r, boolean startingDelay)
	{
		new Thread(() -> {
			try
			{
				if(startingDelay)
					Thread.sleep(autoSteal.getStartingDelay());
				r.run();
				
			}catch(Exception e)
			{
				e.printStackTrace();
			}
		}).start();
	}
	
	private void shiftClickSlots(int from, int to, int mode)
	{
		this.mode = mode;
		
		for(int i = from; i < to; i++)
		{
			Slot slot = handler.slots.get(i);
			if(slot.getStack().isEmpty())
				continue;
			
			waitForDelay();
			if(this.mode != mode || client.currentScreen == null)
				break;
			
			if(mode == 3)
			{
				onMouseClick(slot, slot.id, 0, SlotActionType.PICKUP);
				onMouseClick(null, -999, 0, SlotActionType.PICKUP);
			}else
				onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
		}
	}
	
	private void waitForDelay()
	{
		try
		{
			Thread.sleep(autoSteal.getDelay());
			
		}catch(InterruptedException e)
		{
			throw new RuntimeException(e);
		}
	}
}
