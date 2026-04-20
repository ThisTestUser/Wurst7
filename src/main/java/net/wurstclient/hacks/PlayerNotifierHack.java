/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.datafixers.util.Pair;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"player notifier", "auto disconnect"})
public final class PlayerNotifierHack extends Hack implements UpdateListener
{
	private final CheckboxSetting disconnect = new CheckboxSetting("Disconnect",
		"If a player shows up in multiplayer," + "disconnect from the server.",
		false);
	
	private final CheckboxSetting logEquipment =
		new CheckboxSetting("Log equipment",
			"Log the player's armor and held item when they appear.", false);
	
	private final CheckboxSetting logEquipmentUpdates =
		new CheckboxSetting("Log equipment updates",
			"Log the player's equipment when it updates.", false);
	
	private String disconnectMessage;
	private static final DecimalFormat DF = new DecimalFormat("0.#");
	private static final DateTimeFormatter formatter =
		DateTimeFormatter.ofPattern("MM-dd-yy hh:mm:ss z");
	private List<UUID> checkEquipments = new ArrayList<>();
	
	public PlayerNotifierHack()
	{
		super("PlayerNotifier");
		setCategory(Category.CHAT);
		addSetting(disconnect);
		addSetting(logEquipment);
		addSetting(logEquipmentUpdates);
	}
	
	@Override
	protected void onDisable()
	{
		checkEquipments.clear();
	}
	
	public void onAppear(PlayerEntity player, double x, double y, double z)
	{
		if(!isEnabled())
			return;
		
		String displayMsg = player.getName().getString()
			+ " has entered into your render distance at " + DF.format(x) + " "
			+ DF.format(y) + " " + DF.format(z);
		ChatUtils.message(displayMsg);
		checkEquipments.add(player.getUuid());
		if(disconnectMessage == null && !MC.isInSingleplayer()
			&& disconnect.isChecked())
		{
			disconnectMessage = displayMsg;
			EVENTS.add(UpdateListener.class, this);
		}
	}
	
	public void onDisappear(PlayerEntity player)
	{
		if(!isEnabled())
			return;
		
		ChatUtils.message(player.getName().getString()
			+ " has left your render distance at " + DF.format(player.getX())
			+ " " + DF.format(player.getY()) + " " + DF.format(player.getZ()));
		checkEquipments.remove(player.getUuid());
	}
	
	@Override
	public void onUpdate()
	{
		ClientPlayerEntity player = MC.player;
		MC.getNetworkHandler().getConnection()
			.disconnect(Text.literal(disconnectMessage + "\nYour Position: "
				+ DF.format(player.getX()) + " " + DF.format(player.getY())
				+ " " + DF.format(player.getZ())));
		checkEquipments.clear();
		disconnectMessage = null;
		EVENTS.remove(UpdateListener.class, this);
	}
	
	public void onEquipmentUpdate(int entityId,
		List<Pair<EquipmentSlot, ItemStack>> equipmentList)
	{
		if(!logEquipment.isChecked())
			return;
		
		if(!(MC.world.getEntityById(entityId) instanceof PlayerEntity player)
			|| !checkEquipments.contains(player.getUuid()))
			return;
		
		if(!logEquipmentUpdates.isChecked())
			checkEquipments.remove(player.getUuid());
		
		Path file = WURST.getWurstFolder().resolve("playernotifier.txt");
		try
		{
			StringBuilder sb = new StringBuilder();
			
			sb.append(player.getName().getString()).append(" [")
				.append(ZonedDateTime.now().format(formatter)).append("]")
				.append("\n");
			for(Pair<EquipmentSlot, ItemStack> pair : equipmentList)
				if(!pair.getSecond().isEmpty())
					sb.append(pair.getFirst().name()).append(": ")
						.append(pair.getSecond()
							.toNbt(player.getRegistryManager()).toString())
						.append("\n");
				
			Files.writeString(file, sb.toString(), StandardOpenOption.CREATE,
				StandardOpenOption.APPEND);
		}catch(IOException | IllegalStateException e)
		{
			ChatUtils.error(
				"Failed to log equipment for " + player.getName().getString());
		}
	}
}
