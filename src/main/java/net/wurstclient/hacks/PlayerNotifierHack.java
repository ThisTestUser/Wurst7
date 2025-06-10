/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.text.DecimalFormat;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
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
	
	private String disconnectMessage;
	private static final DecimalFormat DF = new DecimalFormat("0.#");
	
	public PlayerNotifierHack()
	{
		super("PlayerNotifier");
		setCategory(Category.CHAT);
		addSetting(disconnect);
	}
	
	public void onAppear(PlayerEntity player, double x, double y, double z)
	{
		if(!isEnabled())
			return;
		
		String displayMsg = player.getName().getString()
			+ " has entered into your render distance at " + DF.format(x) + " "
			+ DF.format(y) + " " + DF.format(z);
		ChatUtils.message(displayMsg);
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
	}
	
	@Override
	public void onUpdate()
	{
		ClientPlayerEntity player = MC.player;
		MC.getNetworkHandler().getConnection()
			.disconnect(Text.literal(disconnectMessage + "\nYour Position: "
				+ DF.format(player.getX()) + " " + DF.format(player.getY())
				+ " " + DF.format(player.getZ())));
		disconnectMessage = null;
		EVENTS.remove(UpdateListener.class, this);
	}
}
