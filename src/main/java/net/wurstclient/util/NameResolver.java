/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonElement;

import net.wurstclient.util.json.JsonUtils;

public enum NameResolver
{
	;
	
	private static final Map<UUID, String> cache = new HashMap<>();
	private static final Set<UUID> failedLookups = new HashSet<>();
	private static long lastFailedTime = -1L;
	
	public static void addOfflineName(UUID uuid, String name)
	{
		cache.put(uuid, name);
	}
	
	public static Response resolveName(UUID uuid)
	{
		if(cache.containsKey(uuid))
			return new Response(Status.SUCCESS, cache.get(uuid));
		if(failedLookups.contains(uuid))
			return new Response(Status.NOT_FOUND, null);
		if(lastFailedTime + 10000L > System.currentTimeMillis())
			return new Response(Status.ERROR, null);
		
		try
		{
			String url = "https://sessionserver.mojang.com/session/minecraft/profile/";
			BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(
				url + uuid.toString().replace("-", "")).openStream()));
			 
			StringBuilder response = new StringBuilder();
			String line;
			while((line = reader.readLine()) != null)
				response.append(line);
			reader.close();
			
			JsonElement elem = JsonUtils.GSON.fromJson(response.toString(), JsonElement.class);
			
			if(elem == null || !elem.isJsonObject())
			{
				//Player not found (offline mode or deleted account)
				failedLookups.add(uuid);
				return new Response(Status.NOT_FOUND, null);
			}

			return new Response(Status.SUCCESS, elem.getAsJsonObject().get("name").getAsString());
		}catch(Exception e)
		{
			lastFailedTime = System.currentTimeMillis();
			return new Response(Status.ERROR, null);
		}
	}
	
	public static class Response
	{
		private final Status status;
		private final String name;
		
		public Response(Status status, String name)
		{
			this.status = status;
			this.name = name;
		}
		
		public Status getStatus()
		{
			return status;
		}
		
		public String getName()
		{
			return name;
		}
	}
	
	public enum Status
	{
		SUCCESS,
		NOT_FOUND,
		ERROR;
	}
}