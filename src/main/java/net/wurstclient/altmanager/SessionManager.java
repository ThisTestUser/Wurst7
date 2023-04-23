/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.altmanager;

import com.mojang.authlib.minecraft.UserApiService;

import net.minecraft.client.util.Session;
import net.wurstclient.WurstClient;

public class SessionManager
{
	/**
	 * The launcher session (the session that appears when the game is launched).
	 * This field is set to null if this is the current session.
	 */
	private static Session launcherSession;
	private static boolean launcherOnline;
	
	/**
	 * The current session type. This field must not be null.
	 */
	private static Type type = Type.LAUNCHER;
	  
	public static boolean isAltActive()
	{
		return launcherSession != null;
	}

	/**
	 * Sets the current session type. Backs up the launcher session if needed.
	 * Run this BEFORE the session is changed.
	 * @param newType The type of session now. This should not be null and
	 * should not be ORIGINAL.
	 */
	public static void setSessionType(Type newType)
	{
		// if this is null, the type should be ORIGINAL
		if(launcherSession == null)
		{
			launcherSession = WurstClient.MC.getSession();
			launcherOnline = WurstClient.IMC.getUserApiService() != UserApiService.OFFLINE;
		}
		type = newType;
	}
	
	/**
	 * Resets the session back to the launcher session.
	 * This should only be used if an alt session is active.
	 */
	public static void loadOrigSession()
	{
		WurstClient.IMC.setSession(launcherSession, launcherOnline);
		launcherSession = null;
		type = Type.LAUNCHER;
	}
	
	/**
	 * Returns if the current session type is EasyMC.
	 */
	public static boolean isEasyMC()
	{
		return type == Type.EASYMC;
	}
	
	public static Type getAltType()
	{
		return type;
	}
	
	public static enum Type
	{
		LAUNCHER,
		PREMIUM_ALT,
		CRACKED_ALT,
		EASYMC,
	}
}
