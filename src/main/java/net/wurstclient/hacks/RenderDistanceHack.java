/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class RenderDistanceHack extends Hack
{
	private final SliderSetting distance = new SliderSetting("Render Distance",
		12, 1, 32, 1, ValueDisplay.INTEGER);
	
	public RenderDistanceHack()
	{
		super("RenderDistance");
		setCategory(Category.RENDER);
		addSetting(distance);
	}
	
	public int getDistance()
	{
		return distance.getValueI();
	}
	
	// See GameOptionsMixin
}
