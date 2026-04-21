/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.block.Block;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.wurstclient.Category;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

public class NoInteractHack extends Hack
{
	private final BlockListSetting blacklist = new BlockListSetting("Blacklist",
		"Blocks that you won't interact with when this hack is enabled.");
	
	private final CheckboxSetting notify = new CheckboxSetting("Notify",
		"Send a message to chat when you try to interact with a blacklisted block.",
		false);
	
	public NoInteractHack()
	{
		super("NoInteract");
		setCategory(Category.BLOCKS);
		addSetting(blacklist);
		addSetting(notify);
	}
	
	public boolean shouldCancelInteraction(BlockHitResult result)
	{
		if(!isEnabled())
			return false;
		
		BlockPos pos = result.getBlockPos();
		Block block = MC.world.getBlockState(pos).getBlock();
		if(blacklist.contains(block))
		{
			if(notify.isChecked())
				ChatUtils.message(
					"Prevented interaction with " + block.getName().getString()
						+ " at " + pos.toShortString());
			return true;
		}
		
		return false;
	}
}
