/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.stream.Stream;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.EntityUtils;

@SearchTags({"AutoBlock", "BlockHitting", "auto block", "block hitting"})
public class BlockHitHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Determines how close the entity has to get for BlockHit to start blocking.",
		4, 2, 9, 0.1, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting alwaysBlock = new CheckboxSetting(
		"Always block", false);
	
	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();
	
	private boolean shouldBlock;
	private boolean prevShouldBlock;
	
	public BlockHitHack()
	{
		super("BlockHit");
		addSetting(range);
		addSetting(alwaysBlock);
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		shouldBlock = false;
		prevShouldBlock = false;
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		Stream<Entity> stream = EntityUtils.getAttackableEntities();
		double rangeSq = Math.pow(range.getValue(), 2);
		stream = stream.filter(e -> MC.player.squaredDistanceTo(e) <= rangeSq);
		stream = entityFilters.applyTo(stream);
		
		boolean hasShield = MC.player.getOffHandStack().getItem() instanceof ShieldItem;
		
		boolean isEntityNear = hasShield && stream.count() > 0;
		boolean permaBlock = alwaysBlock.isChecked() && hasShield;
		
		ItemStack item = MC.player.getMainHandStack();
		boolean isUsingItem = MC.options.useKey.isPressed()
			&& item.getUseAction() != UseAction.BLOCK && item.getUseAction() != UseAction.NONE;
		if((isEntityNear || permaBlock) && !isUsingItem)
			shouldBlock = true;
		else
			shouldBlock = false;
		
		if(isUsingItem && MC.player.getActiveItem().getItem() instanceof ShieldItem)
			MC.interactionManager.stopUsingItem(MC.player);
		if(shouldBlock && hasShield && (!prevShouldBlock || !MC.player.isBlocking()))
		{
			if(MC.interactionManager.interactItem(MC.player, Hand.OFF_HAND) == ActionResult.SUCCESS)
				MC.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.OFF_HAND);
		}
		prevShouldBlock = shouldBlock;
	}
	
	public boolean isBlocking()
	{
		return shouldBlock;
	}
}
