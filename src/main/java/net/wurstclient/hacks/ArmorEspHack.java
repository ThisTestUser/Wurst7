/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RenderUtils;

@SearchTags({"armor esp"})
public final class ArmorEspHack extends Hack implements RenderListener
{
	private final CheckboxSetting mobs = new CheckboxSetting(
		"Mobs", "Show armor of mobs also.", false);
	
	private final CheckboxSetting enchants = new CheckboxSetting(
		"Show enchantments",
		"Enchantments will be displayed to the left side of the armor.", false);
	
	private final CheckboxSetting impossible = new CheckboxSetting(
		"Ignore impossible enchants",
		"Ignore enchantments that wouldn't be possible to apply.", false);
	
	private final SliderSetting scale = new SliderSetting("Scale",
		1, 0.5, 2, 0.05, ValueDisplay.PERCENTAGE);
	
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	
	private Matrix4f position;
	private Matrix3f normal;
	
	public ArmorEspHack()
	{
		super("ArmorESP");
		setCategory(Category.RENDER);
		addSetting(mobs);
		addSetting(enchants);
		addSetting(impossible);
		addSetting(scale);
	}

	@Override
	public void onEnable()
	{
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		position = matrixStack.peek().getPositionMatrix().copy();
		normal = matrixStack.peek().getNormalMatrix().copy();
	}
	
	public void renderArmor(float partialTicks)
	{
		if(position == null)
			return;
		
		MatrixStack matrixStack = new MatrixStack();
		matrixStack.peek().getPositionMatrix().load(position);
		matrixStack.peek().getNormalMatrix().load(normal);
		
		for(Entity entity : mobs.isChecked() ? MC.world.getEntities() : MC.world.getPlayers())
			if(entity instanceof LivingEntity living && entity != MC.player)
			{
				int i = 0;
				for(EquipmentSlot slot : SLOTS)
				{
					ItemStack stack = living.getEquippedStack(slot);
					if(!stack.isEmpty())
						RenderUtils.renderArmor(matrixStack, entity, stack, 0.75, 500, i,
							scale.getValue(), enchants.isChecked(), impossible.isChecked(), partialTicks);
					i++;
				}
			}
	}
}
