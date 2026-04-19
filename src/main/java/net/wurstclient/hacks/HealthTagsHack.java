/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.RoundingPrecisionSetting;
import net.wurstclient.util.RenderUtils;

@SearchTags({"health tags"})
public final class HealthTagsHack extends Hack implements RenderListener
{
	private final CheckboxSetting mobs = new CheckboxSetting("Mobs",
		"Displays health tags above mobs also.", false);
	
	private final CheckboxSetting showMaxHealth =
		new CheckboxSetting("Show max health", "Also displays the entity's"
			+ " maximum health in addition to its current health.", false);
	
	private final RoundingPrecisionSetting precision =
		new RoundingPrecisionSetting("Precision",
			"Rounds the health value to the given number of decimal places.", 0,
			0, 3, true);
	
	public HealthTagsHack()
	{
		super("HealthTags");
		setCategory(Category.RENDER);
		addSetting(mobs);
		addSetting(showMaxHealth);
		addSetting(precision);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(!mobs.isChecked())
			return;
		
		for(Entity e : MC.level.entitiesForRendering())
		{
			if(!(e instanceof Mob entity))
				continue;
			
			Component text = addHealth(entity, Component.literal(""));
			RenderUtils.renderTag(matrixStack, text, entity, 0xffffff, 1,
				!entity.hasCustomName() ? 0.5 : 1, partialTicks);
		}
	}
	
	public Component addHealth(LivingEntity entity, MutableComponent nametag)
	{
		if(!isEnabled())
			return nametag;
		
		float health = entity.getHealth();
		float maxHealth = entity.getMaxHealth();
		ChatFormatting color = getColor(health, maxHealth);
		
		String healthString = precision.format(health);
		if(showMaxHealth.isChecked())
			healthString += "/" + precision.format(maxHealth);
		
		if(!nametag.getString().isEmpty())
			nametag = nametag.append(Component.literal(" "));
		
		return nametag.append(Component.literal(healthString).withStyle(color));
	}
	
	private ChatFormatting getColor(float health, float maxHealth)
	{
		if(health <= maxHealth * 0.25)
			return ChatFormatting.DARK_RED;
		
		if(health <= maxHealth * 0.5)
			return ChatFormatting.GOLD;
		
		if(health <= maxHealth * 0.75)
			return ChatFormatting.YELLOW;
		
		return ChatFormatting.GREEN;
	}
	
	public boolean hasMobHealthTags()
	{
		return isEnabled() && mobs.isChecked();
	}
	
	// See EntityRendererMixin
}
