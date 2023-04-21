/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
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
	public final CheckboxSetting disconnect = new CheckboxSetting(
		"Disconnect", "If a player shows up in multiplayer,"
			+ "disconnect from the server.", false);
	private String message;
	private boolean disconnectQueue;
	
	private static final DecimalFormat DF = new DecimalFormat("0.#");
	
	public PlayerNotifierHack()
	{
		super("PlayerNotifier");
		setCategory(Category.CHAT);
		addSetting(disconnect);
	}	
	
	public void onAppear(PlayerEntity player)
	{
		if(!isEnabled())
			return;
		
		String displayMsg = player.getName().getString() + " has entered into your render distance at "
			+ DF.format(player.prevX) + " " + DF.format(player.prevY) + " " + DF.format(player.prevZ);
		ChatUtils.message(displayMsg);
		if(!MC.isInSingleplayer() && disconnect.isChecked() && !disconnectQueue)
		{
			disconnectQueue = true;
			message = displayMsg;
			EVENTS.add(UpdateListener.class, this);
		}
	}
	
	public void onDisappear(PlayerEntity player)
	{
		if(!isEnabled())
			return;
		
		ChatUtils.message(player.getName().getString() + " has left your render distance at "
			+ DF.format(player.getX()) + " " + DF.format(player.getY()) + " " + DF.format(player.getZ()));
	}
	
	@Override
	public void onUpdate()
	{
		ClientPlayerEntity player = MC.player;
		MC.getNetworkHandler().getConnection().disconnect(Text.literal(message + "\nYour Position: "
			+ DF.format(player.getX()) + " " + DF.format(player.getY()) + " " + DF.format(player.getZ())));
		disconnectQueue = false;
		EVENTS.remove(UpdateListener.class, this);
	}
}
