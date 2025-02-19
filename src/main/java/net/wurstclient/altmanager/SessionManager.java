/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.altmanager;

import net.minecraft.client.session.Session;
import net.wurstclient.WurstClient;

public class SessionManager
{
	/**
	 * The session set when the game is launched.
	 * This field is set to null if this is the current session.
	 */
	private static Session launcherSession;
	
	/**
	 * The current session type. This field must not be null.
	 */
	private static Type type = Type.LAUNCHER;
	
	/**
	 * Sets the current session type, to be run before setting the session.
	 * The launcher session will be stored if needed.
	 *
	 * @param newType
	 *            Type must not be not null or ORIGINAL.
	 */
	public static void setSessionType(Type newType)
	{
		// if this is null, the type should be ORIGINAL
		if(launcherSession == null)
			launcherSession = WurstClient.MC.getSession();
		type = newType;
	}
	
	/**
	 * Resets the session back to the launcher session.
	 * Only use this if the type is not ORIGINAL.
	 */
	public static void loadOrigSession()
	{
		WurstClient.IMC.setSession(launcherSession);
		launcherSession = null;
		type = Type.LAUNCHER;
	}
	
	public static boolean isAltActive()
	{
		return launcherSession != null;
	}
	
	public static boolean isAltening()
	{
		return type == Type.ALTENING;
	}
	
	public static Type getSessionType()
	{
		return type;
	}
	
	public static enum Type
	{
		LAUNCHER,
		PREMIUM_ALT,
		CRACKED_ALT,
		ALTENING,
	}
}
