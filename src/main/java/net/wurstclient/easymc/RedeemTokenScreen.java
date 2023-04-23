/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.easymc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.Session;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.wurstclient.WurstClient;
import net.wurstclient.altmanager.SessionManager;
import net.wurstclient.util.json.JsonUtils;

public class RedeemTokenScreen extends Screen
{
	private TextFieldWidget tokenField;
	private ButtonWidget restoreButton;
	private ButtonWidget redeemButton;
	private String message;
	private String recievedMessage;
	
	private final Screen prevScreen;
	private final Path easymc = WurstClient.INSTANCE.getWurstFolder().resolve("easymc.json");
	
	public RedeemTokenScreen(Screen prevScreen)
	{
		super(Text.literal(""));
		this.prevScreen = prevScreen;
	}
	
	@Override
	public final void tick()
	{
		tokenField.tick();
	}
	
	@Override
	public final void init()
	{
		addDrawableChild(restoreButton = new ButtonWidget(width / 2 - 150, height / 4 + 96 + 18, 128, 20, 
			Text.literal("Restore Session"), b -> restoreSession()));
	    restoreButton.active = SessionManager.isAltActive();
	    
	    addDrawableChild(redeemButton = new ButtonWidget(width / 2 - 18, height / 4 + 96 + 18, 168, 20,
			Text.literal("Redeem Token"), b -> redeem(b)));
	    
	    addDrawableChild(new ButtonWidget(width / 2 - 150, height / 4 + 120 + 18, 158, 20,
	    	Text.literal("Get Token"), b -> Util.getOperatingSystem().open("https://easymc.io/")));
	    
	    addDrawableChild(new ButtonWidget(width / 2 + 12, height / 4 + 120 + 18, 138, 20,
	    	Text.literal("Cancel"), b -> client.setScreen(prevScreen)));
	    
	    addDrawableChild(new ButtonWidget(width / 2 - 150, height / 4 + 144 + 18, 300, 20,
	    	Text.literal("Load Saved Session"), b -> loadSavedSession()));
    
	    tokenField = new TextFieldWidget(textRenderer, width / 2 - 100, 128, 200, 20, Text.literal(""));
	    tokenField.setMaxLength(20);
	    
	    setInitialFocus(tokenField);
	}
	
	private void restoreSession()
	{
		SessionManager.loadOrigSession();
		restoreButton.active = false;
		message = Formatting.GREEN + "Your orginal session has been restored!";
		recievedMessage = null;
	}
	
	private void redeem(ButtonWidget button)
	{
		if(tokenField.getText().length() != 20)
		{
			message = Formatting.RED + "The token has to be 20 characters long!";
			return;
		}
		button.active = false;
		button.setMessage(Text.literal("Please wait..."));
		recievedMessage = null;

		TokenRedeemer.redeem(tokenField.getText(), new TokenRedeemer.Result()
		{
			@Override
			public void success(RedeemResponse response)
			{
				SessionManager.setSessionType(SessionManager.Type.EASYMC);
				WurstClient.IMC.setSession(new Session(response.getName(), response.getUUID().replace("-", ""),
					"easymc_" + response.getSession(),
					Optional.empty(), Optional.empty(), Session.AccountType.MOJANG), false);
				saveEasyMCSession(response);
				message = Formatting.GREEN + "Your token was redeemed successfully!";
				recievedMessage = response.getMessage();
				restoreButton.active = true;
				button.active = true;
				button.setMessage(Text.literal("Redeem Token"));
			}

			@Override
			public void error(String error)
			{
				message = Formatting.RED + error;
				button.active = true;
				button.setMessage(Text.literal("Redeem Token"));
			}
		});
	}
	
	private void loadSavedSession()
	{
		RedeemResponse response = loadEasyMCSession();
		recievedMessage = null;
		if(response == null)
			message = Formatting.RED + "Error: No saved session found";
		else if(response.getName() == null || response.getUUID() == null || response.getSession() == null)
			message = Formatting.RED + "Error: Saved session is in an invalid informat";
		else
		{
			SessionManager.setSessionType(SessionManager.Type.EASYMC);
			WurstClient.IMC.setSession(new Session(response.getName(), response.getUUID().replace("-", ""),
				"easymc_" + response.getSession(),
				Optional.empty(), Optional.empty(), Session.AccountType.MOJANG), false);
			message = Formatting.GREEN + "Session loaded! Note: The session may be expired.";
			recievedMessage = response.getMessage();
			restoreButton.active = true;
		}
	}
	
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int int_3)
	{
		if(keyCode == GLFW.GLFW_KEY_ENTER)
			redeemButton.onPress();
		
		return super.keyPressed(keyCode, scanCode, int_3);
	}
  
	@Override
	public boolean mouseClicked(double x, double y, int button)
	{
		tokenField.mouseClicked(x, y, button);
		return super.mouseClicked(x, y, button);
	}
	
	@Override
	public void render(MatrixStack matrixStack, int mouseX, int mouseY,
		float partialTicks)
	{
		renderBackground(matrixStack);
		
		drawCenteredText(matrixStack, textRenderer, Formatting.DARK_AQUA + "EasyMC.io", width / 2, 17, 16777215);
		drawCenteredText(matrixStack, textRenderer, "Free minecraft accounts", width / 2, 32, 16777215);
		
		drawCenteredText(matrixStack, textRenderer, "Status:", width / 2, 68, 16777215);
		drawCenteredText(matrixStack, textRenderer, getCurrentStatus(), width / 2, 78, 16777215);
  	 
		drawStringWithShadow(matrixStack, textRenderer, "Token", width / 2 - 100, 115, 10526880);
  	 	if(message != null)
  	 		drawCenteredText(matrixStack, textRenderer, message, width / 2, 158, 16777215);
  	 	if(recievedMessage != null)
  	 	{
  	 		drawCenteredText(matrixStack, textRenderer, Formatting.YELLOW + "Recieved message from server:", width / 2, 183, 16777215);
  	 		drawCenteredText(matrixStack, textRenderer, recievedMessage, width / 2, 198, 16777215);
  	 	}
  	 	tokenField.render(matrixStack, mouseX, mouseY, partialTicks);
  	 	super.render(matrixStack, mouseX, mouseY, partialTicks);
	}
	
	private String getCurrentStatus()
	{
		String name = client.getSession().getUsername();
		switch(SessionManager.getAltType())
		{
			case LAUNCHER:
				return Formatting.GOLD + "No Token redeemed. Using launcher account " + Formatting.YELLOW + 
					name + Formatting.GOLD + " to login!";
			case EASYMC:
				return Formatting.GREEN + "EasyMC token active. Using " + 
				Formatting.AQUA + name + Formatting.GREEN + " to login!";
			case PREMIUM_ALT:
				return Formatting.GOLD + "No token redeemed. Using alt manager premium account " + Formatting.YELLOW + 
					name + Formatting.GOLD + " to login!";
			case CRACKED_ALT:
				return Formatting.GOLD + "No token redeemed. Using alt manager cracked account " + Formatting.GRAY + 
					name + Formatting.GOLD + " to login!";
		}
		return Formatting.RED + "Error: Unknown session state";
	}
	
	private RedeemResponse loadEasyMCSession()
	{
		if(!easymc.toFile().exists())
			return null;
		RedeemResponse response = null;
		try
		{
			try(BufferedReader reader = Files.newBufferedReader(easymc))
			{
				response = JsonUtils.GSON.fromJson(reader, RedeemResponse.class);
			}
		}catch(Exception e)
		{
			System.out.println("Failed to load " + easymc.getFileName());
			e.printStackTrace();
		}
		return response;
	}
	
	private void saveEasyMCSession(RedeemResponse redeemResponse)
	{
		try(BufferedWriter writer = Files.newBufferedWriter(easymc))
		{
			JsonUtils.PRETTY_GSON.toJson(redeemResponse, writer);
		}catch(IOException e)
		{
			System.out.println("Failed to save " + easymc.getFileName());
			e.printStackTrace();
		}
	}
}
