/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

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
	
	public long getDelay()
	{
		return delay.getValueI();
	}
	
	public long getStartingDelay()
	{
		return startingDelay.getValueI();
	}
	
	public boolean stealFromHorses()
	{
		return horses.isChecked();
	}
	
	// See GenericContainerScreenMixin, ShulkerBoxScreenMixin, HorseScreenMixin
}
