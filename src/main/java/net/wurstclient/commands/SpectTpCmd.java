/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import java.util.UUID;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.c2s.play.SpectatorTeleportC2SPacket;
import net.minecraft.util.StringHelper;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;

public final class SpectTpCmd extends Command
{
	public SpectTpCmd()
	{
		super("specttp",
			"Allows you to spectate an entity with a UUID "
				+ "or player name. Requires spectator mode.",
			".specttp [<entity>]");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(!MC.player.isSpectator())
			throw new CmdError("Spectator mode only.");
		if(args.length == 1)
		{
			if(args[0].length() == 36 && args[0].split("-").length == 5)
			{
				try
				{
					UUID uuid = UUID.fromString(args[0]);
					MC.player.networkHandler
						.sendPacket(new SpectatorTeleportC2SPacket(uuid));
					ChatUtils.message("Spectate request sent.");
				}catch(IllegalArgumentException e)
				{
					throw new CmdSyntaxError("Invalid UUID specified.");
				}
			}else
			{
				UUID uuid = null;
				for(PlayerListEntry entry : MC.player.networkHandler
					.getPlayerList())
				{
					String name = entry.getProfile().getName();
					name = StringHelper.stripTextFormat(name);
					
					if(name.equals(args[0]))
						uuid = entry.getProfile().getId();
				}
				if(uuid == null)
					throw new CmdError(
						"Player \"" + args[0] + "\" could not be found.");
				MC.player.networkHandler
					.sendPacket(new SpectatorTeleportC2SPacket(uuid));
				ChatUtils.message("Spectate request sent.");
			}
		}else
			throw new CmdSyntaxError();
	}
}
