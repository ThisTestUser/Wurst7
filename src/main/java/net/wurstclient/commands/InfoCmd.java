/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import net.minecraft.SharedConstants;
import net.minecraft.client.network.ServerInfo;
import net.wurstclient.command.CmdError;
import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.LastServerRememberer;

public final class InfoCmd extends Command
{
	public InfoCmd()
	{
		super("info",
			"Gives you information about either the client or the server.",
			".info (client|server)");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length == 1)
		{
			if(args[0].equalsIgnoreCase("client"))
			{
				String version = "Version: " + SharedConstants.VERSION_NAME;
				String protocolversion = "Protocol Version: "
					+ SharedConstants.RELEASE_TARGET_PROTOCOL_VERSION;
				String name = "Name: " + MC.getSession().getUsername();
				String sessionid =
					"Session id: " + MC.getSession().getSessionId();
				String uuid = "UUID: " + MC.player.getUuid();
				ChatUtils.message(version);
				ChatUtils.message(protocolversion);
				ChatUtils.message(name);
				ChatUtils.message(sessionid);
				ChatUtils.message(uuid);
			}else if(args[0].equalsIgnoreCase("server"))
			{
				ServerInfo info = LastServerRememberer.getLastServer();
				if(info == null)
					throw new CmdError("You haven't joined a server!");
				String version = "Server version: " + info.version.getString();
				String ping = "Ping: " + info.ping;
				String list = "Players: " + info.playerListSummary;
				String slots =
					"Player Slots: " + info.playerCountLabel.getString();
				String ip = "IP: " + info.address;
				String motd = "MOTD: " + info.label.toString();
				String name = "Server Name: " + info.name;
				String brand =
					"Server Brand: " + MC.player.networkHandler.getBrand();
				String protocolversion = "Protocol version: " + info.version;
				ChatUtils.message(version);
				ChatUtils.message(ping);
				ChatUtils.message(list);
				ChatUtils.message(slots);
				ChatUtils.message(ip);
				ChatUtils.message(motd);
				ChatUtils.message(name);
				ChatUtils.message(brand);
				ChatUtils.message(protocolversion);
			}else
				throw new CmdSyntaxError();
		}else
			throw new CmdSyntaxError();
	}
}
