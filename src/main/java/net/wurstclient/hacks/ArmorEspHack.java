/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

@SearchTags({"armor esp"})
public final class ArmorEspHack extends Hack
{
	private final CheckboxSetting mobs =
		new CheckboxSetting("Mobs", "Show armor of mobs also.", false);
	
	private final CheckboxSetting enchants = new CheckboxSetting(
		"Show enchantments",
		"Enchantments will be displayed to the left side of the armor.", false);
	
	private final CheckboxSetting impossible = new CheckboxSetting(
		"Ignore impossible enchants",
		"Ignore enchantments that are not compatible with the item rendered.",
		false);
	
	private final SliderSetting scale =
		new SliderSetting("Scale", 1, 0.25, 4, 0.05, ValueDisplay.PERCENTAGE);
	
	private static final EquipmentSlot[] SLOTS =
		{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
			EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	
	public ArmorEspHack()
	{
		super("ArmorESP");
		setCategory(Category.RENDER);
		addSetting(mobs);
		addSetting(enchants);
		addSetting(impossible);
		addSetting(scale);
	}
	
	public void renderArmor(PoseStack matrixStack, float partialTicks)
	{
		if(!isEnabled())
			return;
		
		// render through walls
		RenderSystem.getDevice().createCommandEncoder()
			.clearDepthTexture(MC.getMainRenderTarget().getDepthTexture(), 1.0);
		
		for(Entity entity : mobs.isChecked() ? MC.level.entitiesForRendering()
			: MC.level.players())
			if(entity instanceof LivingEntity living && entity != MC.player)
				for(int i = 0; i < SLOTS.length; i++)
				{
					EquipmentSlot slot = SLOTS[i];
					ItemStack stack = living.getItemBySlot(slot);
					if(!stack.isEmpty())
						RenderUtils.renderArmor(matrixStack, stack, entity, i,
							enchants.isChecked(), impossible.isChecked(),
							scale.getValueF(), 0.75, partialTicks);
				}
	}
}
