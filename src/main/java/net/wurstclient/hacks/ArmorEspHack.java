/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
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
	
	private boolean rendering;
	
	public ArmorEspHack()
	{
		super("ArmorESP");
		setCategory(Category.RENDER);
		addSetting(mobs);
		addSetting(enchants);
		addSetting(impossible);
		addSetting(scale);
	}
	
	public void renderArmor(MatrixStack matrixStack, float partialTicks)
	{
		if(!isEnabled())
			return;
		
		// render armor through walls
		RenderSystem.clear(GlConst.GL_DEPTH_BUFFER_BIT,
			MinecraftClient.IS_SYSTEM_MAC);
		
		rendering = true;
		for(Entity entity : mobs.isChecked() ? MC.world.getEntities()
			: MC.world.getPlayers())
			if(entity instanceof LivingEntity living && entity != MC.player)
			{
				int i = 0;
				for(EquipmentSlot slot : SLOTS)
				{
					ItemStack stack = living.getEquippedStack(slot);
					if(!stack.isEmpty())
						RenderUtils.renderArmor(matrixStack, stack, entity, i,
							enchants.isChecked(), impossible.isChecked(),
							scale.getValueF(), 0.75, partialTicks);
					i++;
				}
			}
		rendering = false;
	}
	
	public boolean isRendering()
	{
		return rendering;
	}
}
