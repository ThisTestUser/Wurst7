/*
 * Copyright (C) 2014 - 2020 | Alexander01998 | All rights reserved.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.gui.screen.ingame.ContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.container.ShulkerBoxContainer;
import net.minecraft.container.Slot;
import net.minecraft.container.SlotActionType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.AutoStealHack;
import net.wurstclient.hacks.AutoStealHack.Mode;
import net.wurstclient.mixinterface.IContainer;

@Mixin(ShulkerBoxScreen.class)
public abstract class ShulkerBoxScreenMixin extends ContainerScreen<ShulkerBoxContainer> implements IContainer
{
	private final AutoStealHack autoSteal =
		WurstClient.INSTANCE.getHax().autoStealHack;
	private int mode;
	
	public ShulkerBoxScreenMixin(WurstClient wurst,
		ShulkerBoxContainer container, PlayerInventory inventory, Text title)
	{
		super(container, inventory, title);
	}
	
	@Override
	protected void init()
	{
		super.init();
		
		if(!WurstClient.INSTANCE.isEnabled())
			return;
		
		if(autoSteal.areButtonsVisible())
			if(!autoSteal.isDropButtonVisible())
 			{
				addButton(new ButtonWidget(x + containerWidth - 108, y + 4, 50, 12,
					"Steal", b -> steal()));
				
				addButton(new ButtonWidget(x + containerWidth - 56, y + 4, 50, 12,
					"Store", b -> store()));
 			}else
 			{
 				addButton(new ButtonWidget(x + containerWidth - 113, y + 4, 35, 12,
					"Steal", b -> steal()));
				
 				addButton(new ButtonWidget(x + containerWidth - 76, y + 4, 35, 12,
					"Store", b -> store()));
 				
				addButton(new ButtonWidget(x + containerWidth - 39, y + 4, 35, 12,
					"Drop", b -> drop()));
 			}
		
		if(autoSteal.isEnabled() && autoSteal.stealFromShulkers() && !autoSteal.isAutoOpen())
 			if(autoSteal.mode.getSelected() == Mode.STEAL)
 				steal();
 			else
 				drop();
	}
	
	@Override
	public void stealFast()
	{
		for(int i = 0; i < 3 * 9; i++)
		{
			Slot slot = container.slots.get(i);
			if(slot.getStack().isEmpty())
				continue;
			if(minecraft.currentScreen == null)
				break;
			if(autoSteal.mode.getSelected() == Mode.STEAL)
				onMouseClick(slot, slot.id, 0, SlotActionType.QUICK_MOVE);
			else
			{
				onMouseClick(slot, slot.id, 0, SlotActionType.PICKUP);
				onMouseClick(null, -999, 0, SlotActionType.PICKUP);
			}
		}
	}
	
	private void steal()
	{
		runInThread(() -> shiftClickSlots(0, 3 * 9, 1));
	}
	
	private void store()
	{
		runInThread(() -> shiftClickSlots(3 * 9, 3 * 9 + 36, 2));
	}
	
	private void drop()
	{
		runInThread(() -> shiftClickSlots(0, 3 * 9, 3));
	}
	
	private void runInThread(Runnable r)
	{
		new Thread(() -> {
			try
			{
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
			Slot slot = container.slots.get(i);
			if(slot.getStack().isEmpty())
				continue;
			
			waitForDelay();
			if(this.mode != mode || minecraft.currentScreen == null)
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
