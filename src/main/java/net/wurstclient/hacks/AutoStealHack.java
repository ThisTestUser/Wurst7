/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.List;
import java.util.stream.IntStream;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"auto steal", "ChestStealer", "chest stealer",
	"steal store buttons", "Steal/Store buttons"})
public final class AutoStealHack extends Hack
{
	private final CheckboxSetting buttons =
		new CheckboxSetting("Steal/Store buttons", true);
	
	private final CheckboxSetting dropButton = new CheckboxSetting(
		"Drop button", "Replaces the steal button with a drop button.", false);
	
	private final CheckboxSetting drop = new CheckboxSetting("Drop",
		"Makes AutoSteal drop items instead of storing them.", false);
	
	private final SliderSetting delay = new SliderSetting("Delay",
		"Delay between moving stacks of items.\n"
			+ "Should be at least 70ms for NoCheat+ servers.",
		100, 0, 500, 10, ValueDisplay.INTEGER.withSuffix("ms"));
	
	private final SliderSetting startingDelay =
		new SliderSetting("Starting delay",
			"Initial delay after opening a chest before stealing.\n"
				+ "This needs to be higher for laggier servers.",
			150, 0, 2000, 25, ValueDisplay.INTEGER.withSuffix("ms"));
	
	private final CheckboxSetting horses =
		new CheckboxSetting("Steal from chested entities",
			"Automatically steals from chested donkeys, llamas, etc.", true);
	
	private final CheckboxSetting reverseSteal =
		new CheckboxSetting("Reverse steal order", false);
	
	private Thread thread;
	
	public AutoStealHack()
	{
		super("AutoSteal");
		setCategory(Category.ITEMS);
		addSetting(buttons);
		addSetting(dropButton);
		addSetting(drop);
		addSetting(delay);
		addSetting(startingDelay);
		addSetting(horses);
		addSetting(reverseSteal);
	}
	
	public void steal(HandledScreen<?> screen, int rows, boolean automatic)
	{
		startClickingSlots(screen, 0, rows * 9, 0, automatic);
	}
	
	public void drop(HandledScreen<?> screen, int rows, boolean automatic)
	{
		startClickingSlots(screen, 0, rows * 9, 1, automatic);
	}
	
	public void store(HandledScreen<?> screen, int rows, boolean automatic)
	{
		startClickingSlots(screen, rows * 9, rows * 9 + 36, 2, automatic);
	}
	
	public void stealHorse(HandledScreen<?> screen, int slotColumns,
		boolean automatic)
	{
		startClickingSlots(screen, 2, slotColumns * 3 + 2, 0, automatic);
	}
	
	public void dropHorse(HandledScreen<?> screen, int slotColumns,
		boolean automatic)
	{
		startClickingSlots(screen, 2, slotColumns * 3 + 2, 1, automatic);
	}
	
	public void storeHorse(HandledScreen<?> screen, int slotColumns,
		boolean automatic)
	{
		startClickingSlots(screen, slotColumns * 3 + 2,
			slotColumns * 3 + 2 + 36, 2, automatic);
	}
	
	private void startClickingSlots(HandledScreen<?> screen, int from, int to,
		int mode, boolean automatic)
	{
		if(thread != null && thread.isAlive())
			thread.interrupt();
		
		thread = Thread.ofPlatform().name("AutoSteal")
			.uncaughtExceptionHandler((t, e) -> e.printStackTrace()).daemon()
			.start(() -> shiftClickSlots(screen, from, to, mode, automatic));
	}
	
	private void shiftClickSlots(HandledScreen<?> screen, int from, int to,
		int mode, boolean automatic)
	{
		List<Slot> slots = IntStream.range(from, to)
			.mapToObj(i -> screen.getScreenHandler().slots.get(i)).toList();
		
		if(reverseSteal.isChecked() && mode == 2)
			slots = slots.reversed();
		
		if(automatic)
		{
			try
			{
				Thread.sleep(startingDelay.getValueI());
			}catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				return;
			}
		}
		
		for(Slot slot : slots)
			try
			{
				if(slot.getStack().isEmpty())
					continue;
				
				Thread.sleep(delay.getValueI());
				
				if(MC.currentScreen == null)
					break;
				
				if(mode == 1)
				{
					screen.onMouseClick(slot, slot.id, 0,
						SlotActionType.PICKUP);
					screen.onMouseClick(null, -999, 0, SlotActionType.PICKUP);
				}else
					screen.onMouseClick(slot, slot.id, 0,
						SlotActionType.QUICK_MOVE);
				
			}catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				break;
			}
	}
	
	public boolean areButtonsVisible()
	{
		return buttons.isChecked();
	}
	
	public boolean hasDropButton()
	{
		return dropButton.isChecked();
	}
	
	public boolean shouldDrop()
	{
		return drop.isChecked();
	}
	
	public boolean stealFromHorses()
	{
		return horses.isChecked();
	}
	
	// See GenericContainerScreenMixin, ShulkerBoxScreenMixin, HorseScreenMixin
}
