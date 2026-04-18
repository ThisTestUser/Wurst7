/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
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

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
	
	public void onAppear(Player player, double x, double y, double z)
	{
		if(!isEnabled())
			return;
		
		String displayMsg = player.getName().getString()
			+ " has entered into your render distance at " + DF.format(x) + " "
			+ DF.format(y) + " " + DF.format(z);
		ChatUtils.message(displayMsg);
		checkEquipments.add(player.getUUID());
		if(disconnectMessage == null && !MC.isLocalServer()
			&& disconnect.isChecked())
		{
			disconnectMessage = displayMsg;
			EVENTS.add(UpdateListener.class, this);
		}
	}
	
	public void onDisappear(Player player)
	{
		if(!isEnabled())
			return;
		
		ChatUtils.message(player.getName().getString()
			+ " has left your render distance at " + DF.format(player.getX())
			+ " " + DF.format(player.getY()) + " " + DF.format(player.getZ()));
		checkEquipments.remove(player.getUUID());
	}
	
	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		MC.getConnection().getConnection()
			.disconnect(Component.literal(disconnectMessage
				+ "\nYour Position: " + DF.format(player.getX()) + " "
				+ DF.format(player.getY()) + " " + DF.format(player.getZ())));
		checkEquipments.clear();
		disconnectMessage = null;
		EVENTS.remove(UpdateListener.class, this);
	}
	
	public void onEquipmentUpdate(int entityId,
		List<Pair<EquipmentSlot, ItemStack>> equipmentList)
	{
		if(!logEquipment.isChecked())
			return;
		
		if(!(MC.level.getEntity(entityId) instanceof Player player)
			|| !checkEquipments.contains(player.getUUID()))
			return;
		
		if(!logEquipmentUpdates.isChecked())
			checkEquipments.remove(player.getUUID());
		
		Path file = WURST.getWurstFolder().resolve("playernotifier.txt");
		try
		{
			StringBuilder sb = new StringBuilder();
			
			sb.append(player.getName().getString()).append(" [")
				.append(ZonedDateTime.now().format(formatter)).append("]")
				.append("\n");
			for(Pair<EquipmentSlot, ItemStack> pair : equipmentList)
				if(!pair.getSecond().isEmpty())
					sb.append(pair.getFirst().getName()).append(": ")
						.append(
							ItemStack.CODEC
								.encodeStart(player.registryAccess()
									.createSerializationContext(
										NbtOps.INSTANCE),
									pair.getSecond())
								.getOrThrow().toString())
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
