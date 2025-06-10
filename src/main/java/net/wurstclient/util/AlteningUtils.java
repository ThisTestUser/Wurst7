/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.MinecraftClientException;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.yggdrasil.request.JoinMinecraftServerRequest;

public enum AlteningUtils
{
	;
	
	private static final MinecraftClient CLIENT =
		MinecraftClient.unauthenticated(Proxy.NO_PROXY);
	private static final Agent MINECRAFT = new Agent("Minecraft", 1);
	
	public static AuthenticationReponse redeem(String token)
		throws MalformedURLException, MinecraftClientException
	{
		AuthenticationRequest request =
			new AuthenticationRequest(MINECRAFT, token, "", "");
		return CLIENT.post(URI
			.create("http://authserver.thealtening.com/authenticate").toURL(),
			request, AuthenticationReponse.class);
	}
	
	public static void joinServer(String accessToken, UUID uuid,
		String serverId) throws MalformedURLException, MinecraftClientException
	{
		CLIENT.post(
			URI.create(
				"http://sessionserver.thealtening.com/session/minecraft/join")
				.toURL(),
			new JoinMinecraftServerRequest(accessToken, uuid, serverId),
			Object.class);
	}
	
	private static record Agent(String name, int version)
	{}
	
	private static record AuthenticationRequest(Agent agent, String username,
		String password, String clientToken)
	{}
	
	public static record User(String id, List<Property> properties)
	{}
	
	public static record AuthenticationReponse(String accessToken,
		String clientToken, GameProfile selectedProfile,
		GameProfile[] availableProfiles, User user)
	{}
	
	public static record AlteningSession(String token, String username,
		String uuid, String session)
	{}
}
