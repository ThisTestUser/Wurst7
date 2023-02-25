/*
 * Copyright (c) 2014-2022 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings.filters;

import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AllayEntity;

public final class FilterAllaySetting extends EntityFilterCheckbox
{
	public FilterAllaySetting(String description, boolean checked)
	{
		super("Filter allays", description, checked);
	}
	
	@Override
	public boolean test(Entity e)
	{
		return !(e instanceof AllayEntity);
	}
	
	public static FilterAllaySetting genericCombat(boolean checked)
	{
		return new FilterAllaySetting("Won't attack allays.", checked);
	}
}
