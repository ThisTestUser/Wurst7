/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringHelper;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.NameResolver;
import net.wurstclient.util.RenderUtils;

@SearchTags({"mob info", "mob owner", "mobowner", "pet owner", "petowner", "llama strength"})
public class MobInfoHack extends Hack implements UpdateListener, RenderListener
{
	private final SliderSetting llamaThres =
		new SliderSetting("Llama Strength Threshold",
			"Llamas with strengths below this value will not display"
				+ " their strength. Drag to the right to disable.",
			6, 1, 6, 1, ValueDisplay.INTEGER.withLabel(6, "Disabled"));
	
	private final ArrayList<LivingEntity> entities = new ArrayList<>();
	
	public MobInfoHack()
	{
		super("MobInfo");
		setCategory(Category.RENDER);
		addSetting(llamaThres);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		entities.clear();
		for(Entity entity : MC.world.getEntities())
			if(entity instanceof TameableEntity || entity instanceof AbstractHorseEntity)
				entities.add((LivingEntity)entity);
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		Set<String> onlineCache = new HashSet<>();
		Set<String> offlineCache = new HashSet<>();
		VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());
		for(LivingEntity entity : entities)
		{
			if((entity instanceof TameableEntity && ((TameableEntity)entity).isTamed())
				|| (entity instanceof AbstractHorseEntity && ((AbstractHorseEntity)entity).isTame()))
			{
				UUID uuid = entity instanceof TameableEntity ? ((TameableEntity)entity).getOwnerUuid()
					: ((AbstractHorseEntity)entity).getOwnerUuid();
				MutableText text = Text.literal("Owner: ");
				if(uuid == null)
					// entity is tamed but owner cannot be resolved
					text.append(Text.literal("Null").formatted(Formatting.DARK_RED));
				else
				{
					NameResolver.Response response = NameResolver.resolveName(uuid);
					String name = response.getStatus() == NameResolver.Status.SUCCESS ? response.getName() : null;
					boolean online = false;
					
					if(name != null && onlineCache.contains(name))
						online = true;
					else if(name != null && offlineCache.contains(name))
						online = false;
					else
					{
						// check online status
						for(PlayerListEntry entry : MC.player.networkHandler.getPlayerList())
							if(entry.getProfile().getId().equals(uuid))
							{
								if(name == null)
								{
									// offline UUIDs can only be resolved when the player is online
									name = entry.getProfile().getName();
									name = StringHelper.stripTextFormat(name);
									NameResolver.addOfflineName(uuid, name);
								}
								onlineCache.add(name);
								online = true;
								break;
							}
						if(!online)
							offlineCache.add(name);
					}
					
					if(name != null)
						text.append(Text.literal(name).formatted(online ? Formatting.GREEN : Formatting.RED));
					else
						text.append(Text.literal(uuid.toString()).formatted(Formatting.RED));
				}
				double offset = WURST.getHax().healthTagsHack.hasMobHealthTags() ? 0.5 : 0;
				if(!entity.hasCustomName())
					offset += 0.5;
				else
					offset += 1;
				RenderUtils.renderTag(matrixStack, text, entity, immediate, 0xffffff, offset, partialTicks);
			}
			if(entity instanceof LlamaEntity llama && llama.getStrength() >= llamaThres.getValueI())
			{
				int strength = llama.getStrength();
				double offset = WURST.getHax().healthTagsHack.hasMobHealthTags() ? 1 : 0.5;
				if(!llama.isTame())
					offset -= 0.5;
				if(!llama.hasCustomName())
					offset += 0.5;
				else
					offset += 1;
				MutableText text = Text.literal("Strength: ");
				switch(strength)
				{
					case 1:
						text.append(Text.literal(Integer.toString(strength)).formatted(Formatting.DARK_RED));
						break;
					case 2:
						text.append(Text.literal(Integer.toString(strength)).formatted(Formatting.RED));
						break;
					case 3:
						text.append(Text.literal(Integer.toString(strength)).formatted(Formatting.YELLOW));
						break;
					case 4:
						text.append(Text.literal(Integer.toString(strength)).formatted(Formatting.GREEN));
						break;
					case 5:
						text.append(Text.literal(Integer.toString(strength)).formatted(Formatting.DARK_GREEN));
						break;
				}
				RenderUtils.renderTag(matrixStack, text, llama, immediate, 0xffffff, offset, partialTicks);
			}
		}
		immediate.draw();
	}
}
