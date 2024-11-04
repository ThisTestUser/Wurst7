/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.net.MalformedURLException;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.exceptions.MinecraftClientException;

import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.altmanager.SessionManager;
import net.wurstclient.util.AlteningUtils;

@Mixin(ClientLoginNetworkHandler.class)
public abstract class ClientLoginNetworkHandlerMixin
{
	@Inject(at = @At("HEAD"),
		method = "joinServerSession(Ljava/lang/String;)Lnet/minecraft/text/Text;",
		cancellable = true)
	private void onAuthClient(String serverId, CallbackInfoReturnable<Text> cir)
	{
		Session session = WurstClient.MC.getSession();
		
		boolean alteningType = SessionManager.isAltening();
		boolean isAltToken = session.getAccessToken().startsWith("alt_");
		boolean isAltLength = session.getAccessToken().length() == 36;
		
		boolean altening = alteningType && isAltToken && isAltLength;
		boolean vanilla = !alteningType && !isAltToken && !isAltLength;
		
		if(!altening && !vanilla)
		{
			cir.setReturnValue(Text.literal(
				"Unexpected session format found, no connection was made."));
			return;
		}
		
		if(vanilla)
			return;
		
		String token = session.getAccessToken().substring(0, 36);
		try
		{
			AlteningUtils.joinServer(token, session.getUuidOrNull(), serverId);
		}catch(MalformedURLException e)
		{
			cir.setReturnValue(
				Text.literal("Authentication error: Malformed URL"));
			return;
		}catch(MinecraftClientException e)
		{
			cir.setReturnValue(
				Text.literal("Authentication error: " + e.getMessage()));
			return;
		}
		cir.setReturnValue(null);
	}
}
