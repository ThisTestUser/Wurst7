/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.easymc;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.wurstclient.util.json.JsonUtils;

public class TokenRedeemer
{	
	private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
	
	public static void redeem(String token, Result result)
	{
		EXECUTOR_SERVICE.execute(new Runnable()
	    {
			@Override
			public void run()
			{
				HttpsURLConnection con = sendPostRequest("https://api.easymc.io/v1/token/redeem", 
					"{\"token\":\"" + token + "\"}");
				if(con == null)
				{
					result.error("Error: Could not open connection");
					return;
				}
				Object o = getResult(con);
				if(o instanceof String)
				{
					result.error((String)o);
					return;
				}
				JsonObject jsonObject = (JsonObject)o;
				if(!jsonObject.has("mcName") || !jsonObject.has("session") || !jsonObject.has("uuid"))
				{
					result.error("Error: Malformed JSON response from server");
					return;
				}
				RedeemResponse response = new RedeemResponse(token);
				response.setName(jsonObject.get("mcName").getAsString());
				response.setSession(jsonObject.get("session").getAsString());
				response.setUUID(jsonObject.get("uuid").getAsString());
				if(jsonObject.has("message") && !jsonObject.get("message").isJsonNull())
					response.setMessage(jsonObject.get("message").getAsString());
				        
				result.success(response);
			}
	    });
	}	
	
	private static HttpsURLConnection sendPostRequest(String url, String body)
	{
		try
		{
			HttpsURLConnection con = (HttpsURLConnection)new URL(url).openConnection();
			con.setConnectTimeout(10000);
			con.setReadTimeout(10000);
	      
			con.setRequestMethod("POST");
			con.setRequestProperty("Accept", "application/json");
			con.setRequestProperty("Content-Type", "application/json");
			
			con.setDoOutput(true);
			DataOutputStream wr = new DataOutputStream(con.getOutputStream());
			wr.write(body.getBytes("UTF-8"));
			wr.flush();
			wr.close();
			
			return con;
		}catch(IOException e)
		{
			e.printStackTrace();
	    }
		return null;
	}
	
	private static Object getResult(HttpsURLConnection con)
	{
		try
		{
			if(con.getResponseCode() == 400)
				return "Error: Invalid or expired token";
			BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
	 
			StringBuilder result = new StringBuilder();
			String line;
			while((line = reader.readLine()) != null)
				result.append(line);
			reader.close();
	      
			JsonElement jsonElement = JsonUtils.GSON.fromJson(result.toString(), JsonElement.class);
			if(!jsonElement.isJsonObject())
				return "Error: Response not in JSON format";
			if(jsonElement.getAsJsonObject().has("error"))
				return "Recieved error:" + jsonElement.getAsJsonObject().get("error").getAsString();
			return jsonElement.getAsJsonObject();
		}catch(IOException e)
	    {
			e.printStackTrace();
	    }
		return "Error: Could not parse response";
	}
	
	public static abstract interface Result
	{
		public abstract void success(RedeemResponse response);
		
		public abstract void error(String error);
	}
}
