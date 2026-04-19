/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
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

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.NameResolver;
import net.wurstclient.util.RenderUtils;

@SearchTags({"mob info", "mob owner", "mobowner", "pet owner", "petowner",
	"llama strength"})
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
		for(Entity entity : MC.level.entitiesForRendering())
			if(entity instanceof TamableAnimal
				|| entity instanceof AbstractHorse)
				entities.add((LivingEntity)entity);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		Set<String> onlineCache = new HashSet<>();
		Set<String> offlineCache = new HashSet<>();
		for(LivingEntity entity : entities)
		{
			if((entity instanceof TamableAnimal
				&& ((TamableAnimal)entity).isTame())
				|| (entity instanceof AbstractHorse
					&& ((AbstractHorse)entity).isTamed()))
			{
				EntityReference<LivingEntity> reference =
					entity instanceof TamableAnimal
						? ((TamableAnimal)entity).getOwnerReference()
						: ((AbstractHorse)entity).getOwnerReference();
				
				UUID uuid = reference == null ? null : reference.getUUID();
				MutableComponent text = Component.literal("Owner: ");
				if(uuid == null)
					// entity is tamed but owner cannot be resolved
					text.append(Component.literal("Null")
						.withStyle(ChatFormatting.DARK_RED));
				else
				{
					NameResolver.Response response =
						NameResolver.resolveName(uuid);
					String name =
						response.getStatus() == NameResolver.Status.SUCCESS
							? response.getName() : null;
					boolean online = false;
					
					if(name != null && onlineCache.contains(name))
						online = true;
					else if(name != null && offlineCache.contains(name))
						online = false;
					else
					{
						// check online status
						for(PlayerInfo entry : MC.player.connection
							.getOnlinePlayers())
							if(entry.getProfile().id().equals(uuid))
							{
								if(name == null)
								{
									// offline UUIDs can only be resolved when
									// the player is online
									name = entry.getProfile().name();
									name = StringUtil.stripColor(name);
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
						text.append(Component.literal(name).withStyle(online
							? ChatFormatting.GREEN : ChatFormatting.RED));
					else
						text.append(Component.literal(uuid.toString())
							.withStyle(ChatFormatting.RED));
				}
				double offset =
					WURST.getHax().healthTagsHack.hasMobHealthTags() ? 0.5 : 0;
				if(!entity.hasCustomName())
					offset += 0.5;
				else
					offset += 1;
				RenderUtils.renderTag(matrixStack, text, entity, 0xffffff, 1,
					offset, partialTicks);
			}
			if(entity instanceof Llama llama
				&& llama.getStrength() >= llamaThres.getValueI())
			{
				int strength = llama.getStrength();
				double offset =
					WURST.getHax().healthTagsHack.hasMobHealthTags() ? 1 : 0.5;
				if(!llama.isTamed())
					offset -= 0.5;
				if(!llama.hasCustomName())
					offset += 0.5;
				else
					offset += 1;
				MutableComponent text = Component.literal("Strength: ");
				switch(strength)
				{
					case 1:
					text.append(Component.literal(Integer.toString(strength))
						.withStyle(ChatFormatting.DARK_RED));
					break;
					case 2:
					text.append(Component.literal(Integer.toString(strength))
						.withStyle(ChatFormatting.RED));
					break;
					case 3:
					text.append(Component.literal(Integer.toString(strength))
						.withStyle(ChatFormatting.YELLOW));
					break;
					case 4:
					text.append(Component.literal(Integer.toString(strength))
						.withStyle(ChatFormatting.GREEN));
					break;
					case 5:
					text.append(Component.literal(Integer.toString(strength))
						.withStyle(ChatFormatting.DARK_GREEN));
					break;
				}
				RenderUtils.renderTag(matrixStack, text, llama, 0xffffff, 1,
					offset, partialTicks);
			}
		}
	}
}
