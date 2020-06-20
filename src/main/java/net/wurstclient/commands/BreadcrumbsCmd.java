/*
 * Copyright (C) 2014 - 2020 | Alexander01998 | All rights reserved.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.commands;

import net.wurstclient.command.CmdException;
import net.wurstclient.command.CmdSyntaxError;
import net.wurstclient.command.Command;
import net.wurstclient.util.ChatUtils;

public final class BreadcrumbsCmd extends Command
{
	public boolean clearCrumbs;
	
	public BreadcrumbsCmd()
	{
		super("breadcrumbs", "Clears the breadcrumbs trail.", 
			"[<clear>]");
	}
	
	@Override
	public void call(String[] args) throws CmdException
	{
		if(args.length == 1 && args[0].equalsIgnoreCase("clear"))
		{
			clearCrumbs = true;
			ChatUtils.message("Breadcrumbs cleared.");
		}else
			throw new CmdSyntaxError();
	}
}