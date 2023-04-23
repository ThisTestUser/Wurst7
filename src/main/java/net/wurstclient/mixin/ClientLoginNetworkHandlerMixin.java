/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.io.DataOutputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.util.Session;
import net.minecraft.text.Text;
import net.wurstclient.WurstClient;
import net.wurstclient.altmanager.SessionManager;

@Mixin(ClientLoginNetworkHandler.class)
public abstract class ClientLoginNetworkHandlerMixin
{
	@Inject(at = @At("HEAD"),
		method = "joinServerSession(Ljava/lang/String;)Lnet/minecraft/text/Text;",
		cancellable = true)
	private void onAuthClient(String serverId, CallbackInfoReturnable<Text> cir)
	{
		//Sanity checks
		Session session = WurstClient.MC.getSession();
		boolean easymc = SessionManager.isEasyMC();
		boolean easyMCToken = session.getAccessToken().startsWith("easymc_");
		int tokenLength = session.getAccessToken().length();
		if(!((easymc && easyMCToken && tokenLength == 50) || (!easymc && !easyMCToken && tokenLength != 50)))
		{
			cir.setReturnValue(Text.literal(
				"Mismatch between EasyMC account and session token!\n"
					+ "No data was sent over."));
			return;
		}
		
		if(!easymc)
			return;
		
		String token = session.getAccessToken().substring(7, 50);
		String uuid = session.getUuid();
		try
		{
			String jsonBody = "{\"accessToken\":\"" + token
				+ "\",\"selectedProfile\":\"" + uuid + "\",\"serverId\":\"" + serverId + "\"}";
			
			HttpsURLConnection con = (HttpsURLConnection)new URL(
				"https://sessionserver.easymc.io/session/minecraft/join").openConnection();
			con.setConnectTimeout(10000);
			con.setReadTimeout(10000);
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/json");
			con.setDoOutput(true);
			
			DataOutputStream outputStream =
				new DataOutputStream(con.getOutputStream());
			outputStream.write(jsonBody.getBytes("UTF-8"));
			outputStream.flush();
			outputStream.close();
			
			if(con.getResponseCode() == 403)
			{
				cir.setReturnValue(Text.literal(
					"Error: Invalid or expired EasyMC token"));
				return;
			}
		}catch(Exception e)
		{
			cir.setReturnValue(Text.literal(
				"Error: " + e.toString()));
			return;
		}
		cir.setReturnValue(null);
	}
}
