/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.HorseScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.HorseScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.AutoStealHack;

@Mixin(HorseScreen.class)
public abstract class HorseScreenMixin extends HandledScreen<HorseScreenHandler>
{
	@Shadow
	@Final
	private int slotColumnCount;
	
	private final AutoStealHack autoSteal =
		WurstClient.INSTANCE.getHax().autoStealHack;
	private int mode;
	
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
		
		if(autoSteal.isEnabled() && autoSteal.stealFromHorses())
			if(autoSteal.shouldDrop())
				drop(true);
			else
				steal(true);
	}
	
	private void steal(boolean startingDelay)
	{
		runInThread(() -> shiftClickSlots(2, slotColumnCount * 3 + 2, 1),
			startingDelay);
	}
	
	private void store(boolean startingDelay)
	{
		runInThread(() -> shiftClickSlots(slotColumnCount * 3 + 2,
			slotColumnCount * 3 + 2 + 36, 2), startingDelay);
	}
	
	private void drop(boolean startingDelay)
	{
		runInThread(() -> shiftClickSlots(2, slotColumnCount * 3 + 2, 3),
			startingDelay);
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
