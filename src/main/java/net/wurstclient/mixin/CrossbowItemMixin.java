/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.wurstclient.mixinterface.ICrossbowItem;

@Mixin(CrossbowItem.class)
public class CrossbowItemMixin implements ICrossbowItem
{
	@Shadow
	private static List<ItemStack> getProjectiles(ItemStack crossbow)
	{
		return null;
	}

	@Override
	public List<ItemStack> getProjectileItems(ItemStack crossbow)
	{
		return getProjectiles(crossbow);
	}
}
