/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.easymc;

public class RedeemResponse
{
	private final String token;
	private String session;
	private String name;
	private String uuid;
	private String message;
	
	public RedeemResponse(String token)
	{
		this.token = token;
	}
	
	public String getToken()
	{
		return token;
	}
	
	public String getSession()
	{
		return session;
	}
  
	public void setSession(String session)
	{
		this.session = session;
	}
  
	public String getName()
	{
		return name;
	}
  
	public void setName(String name)
	{
		this.name = name;
	}
	
	public String getUUID()
	{
		return uuid;
	}
	
	public void setUUID(String uuid)
	{
		this.uuid = uuid;
	}
	
	public String getMessage()
	{
		return message;
	}
	
	public void setMessage(String message)
	{
		this.message = message;
	}
}
