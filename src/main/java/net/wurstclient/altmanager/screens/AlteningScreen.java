/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.altmanager.screens;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.exceptions.MinecraftClientException;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.session.Session;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.wurstclient.WurstClient;
import net.wurstclient.altmanager.SessionManager;
import net.wurstclient.util.AlteningUtils;
import net.wurstclient.util.AlteningUtils.AlteningSession;
import net.wurstclient.util.AlteningUtils.AuthenticationReponse;
import net.wurstclient.util.json.JsonUtils;

public class AlteningScreen extends Screen
{
	private final Path alteningFolder =
		WurstClient.INSTANCE.getWurstFolder().resolve("altening");
	
	private final Screen prevScreen;
	
	private TextFieldWidget tokenBox;
	private TextFieldWidget nameBox;
	private TextFieldWidget uuidBox;
	private TextFieldWidget sessionBox;
	
	private ButtonWidget redeemButton;
	private ButtonWidget loginButton;
	private ButtonWidget restoreButton;
	private ButtonWidget loadButton;
	
	private Text redeemMessage = Text.literal("");
	private Text fileMessage = Text.literal("");
	
	public AlteningScreen(Screen prevScreen)
	{
		super(Text.literal("Altening"));
		this.prevScreen = prevScreen;
	}
	
	@Override
	public void init()
	{
		tokenBox = new TextFieldWidget(textRenderer, width / 2 - 100, 90, 200,
			20, Text.literal(""));
		tokenBox.setMaxLength(48);
		tokenBox.setFocused(true);
		addSelectableChild(tokenBox);
		
		addDrawableChild(redeemButton =
			ButtonWidget.builder(Text.literal("Redeem"), b -> redeemToken())
				.dimensions(width / 2 - 100, 115, 200, 20).build());
		
		nameBox = new TextFieldWidget(textRenderer, width / 2 - 100, 140, 200,
			20, Text.literal(""));
		nameBox.setMaxLength(48);
		addSelectableChild(nameBox);
		
		uuidBox = new TextFieldWidget(textRenderer, width / 2 - 100, 165, 200,
			20, Text.literal(""));
		uuidBox.setMaxLength(75);
		addSelectableChild(uuidBox);
		
		sessionBox = new TextFieldWidget(textRenderer, width / 2 - 100, 190,
			200, 20, Text.literal(""));
		sessionBox.setMaxLength(75);
		addSelectableChild(sessionBox);
		
		addDrawableChild(loginButton =
			ButtonWidget.builder(Text.literal("Login"), b -> login())
				.dimensions(width / 2 - 100, 215, 200, 20).build());
		
		addDrawableChild(loadButton = ButtonWidget
			.builder(Text.literal("Load Session from File"),
				b -> loadFromFile())
			.dimensions(width / 2 - 100, 240, 200, 20).build());
		
		addDrawableChild(restoreButton = ButtonWidget
			.builder(Text.literal("Restore Launcher Session"),
				b -> restoreSession(b))
			.dimensions(width / 2 - 100, 265, 200, 20).build());
		restoreButton.active = false;
		
		addDrawableChild(ButtonWidget
			.builder(Text.literal("Back"), b -> client.setScreen(prevScreen))
			.dimensions(width / 2 - 100, 290, 200, 20).build());
		
		if(SessionManager.isAltening())
		{
			nameBox.setText(client.getSession().getUsername());
			uuidBox.setText(client.getSession().getUuidOrNull().toString());
			sessionBox.setText(client.getSession().getAccessToken());
		}
		
		setFocused(tokenBox);
	}
	
	private void redeemToken()
	{
		AuthenticationReponse response = null;
		try
		{
			response = AlteningUtils.redeem(tokenBox.getText());
		}catch(MalformedURLException e)
		{
			redeemMessage = Text.literal("Redeem failed: Malformed URL")
				.formatted(Formatting.RED);
			return;
		}catch(MinecraftClientException e)
		{
			redeemMessage = Text.literal("Redeem failed: " + e.getMessage())
				.formatted(Formatting.RED);
			return;
		}
		if(response == null)
		{
			redeemMessage = Text.literal("Redeem failed: Null response")
				.formatted(Formatting.RED);
			return;
		}
		
		nameBox.setText(response.selectedProfile().getName());
		uuidBox.setText(response.selectedProfile().getId().toString());
		sessionBox.setText(response.accessToken());
		redeemMessage = Text
			.literal("Redeem successful! Press \"Login\" to use the session.")
			.formatted(Formatting.GREEN);
		
		saveToFile();
	}
	
	private void login()
	{
		UUID uuid = null;
		try
		{
			uuid = UUID.fromString(uuidBox.getText());
		}catch(IllegalArgumentException e)
		{
			redeemMessage = Text.literal("Error: Could not parse UUID")
				.formatted(Formatting.RED);
			return;
		}
		SessionManager.setSessionType(SessionManager.Type.ALTENING);
		WurstClient.IMC.setSession(new Session(nameBox.getText(), uuid,
			sessionBox.getText(), Optional.empty(), Optional.empty(),
			Session.AccountType.MOJANG));
		redeemMessage =
			Text.literal("Login successful!").formatted(Formatting.GREEN);
	}
	
	private void saveToFile()
	{
		AlteningSession session = new AlteningSession(tokenBox.getText(),
			nameBox.getText(), uuidBox.getText(), sessionBox.getText());
		alteningFolder.toFile().mkdirs();
		try(BufferedWriter writer = Files.newBufferedWriter(
			alteningFolder.resolve(session.username() + ".json")))
		{
			JsonUtils.PRETTY_GSON.toJson(session, writer);
			fileMessage = Text.literal(
				"Session information saved to " + session.username() + ".json")
				.formatted(Formatting.GREEN);
		}catch(IOException e)
		{
			fileMessage = Text.literal("Could not save to " + session.username()
				+ ".json: " + e.getClass().getSimpleName())
				.formatted(Formatting.RED);
		}
	}
	
	private void loadFromFile()
	{
		Path path = alteningFolder.resolve(nameBox.getText() + ".json");
		if(!path.toFile().exists())
		{
			fileMessage = Text.literal(nameBox.getText() + ".json not found!")
				.formatted(Formatting.RED);
			return;
		}
		
		try(BufferedReader reader = Files.newBufferedReader(path))
		{
			AlteningSession easymc =
				JsonUtils.PRETTY_GSON.fromJson(reader, AlteningSession.class);
			tokenBox.setText(easymc.token());
			nameBox.setText(easymc.username());
			uuidBox.setText(easymc.uuid());
			sessionBox.setText(easymc.session());
			redeemMessage = Text.literal("");
			fileMessage = Text.literal("Session information loaded from "
				+ easymc.username() + ".json").formatted(Formatting.GREEN);
		}catch(IOException e)
		{
			fileMessage = Text.literal("Could not load from "
				+ nameBox.getText() + ".json: " + e.getClass().getSimpleName())
				.formatted(Formatting.RED);
		}
	}
	
	private void restoreSession(ButtonWidget b)
	{
		SessionManager.loadOrigSession();
		b.active = false;
	}
	
	@Override
	public void tick()
	{
		redeemButton.active = !tokenBox.getText().isEmpty();
		loginButton.active = !nameBox.getText().isEmpty()
			&& !uuidBox.getText().isEmpty() && !sessionBox.getText().isEmpty();
		restoreButton.active = SessionManager.isAltActive();
		loadButton.active = !nameBox.getText().isEmpty();
	}
	
	@Override
	public boolean mouseClicked(double x, double y, int button)
	{
		tokenBox.mouseClicked(x, y, button);
		nameBox.mouseClicked(x, y, button);
		uuidBox.mouseClicked(x, y, button);
		sessionBox.mouseClicked(x, y, button);
		
		return super.mouseClicked(x, y, button);
	}
	
	@Override
	public void render(DrawContext context, int mouseX, int mouseY,
		float partialTicks)
	{
		renderBackground(context, mouseX, mouseY, partialTicks);
		
		// title
		context.drawCenteredTextWithShadow(textRenderer, "Altening Login",
			width / 2, 15, 16777215);
		
		// status
		context.drawCenteredTextWithShadow(textRenderer, getCurrentStatus(),
			width / 2, 30, 16777215);
		context.drawCenteredTextWithShadow(textRenderer, redeemMessage,
			width / 2, 45, 16777215);
		context.drawCenteredTextWithShadow(textRenderer, fileMessage, width / 2,
			60, 16777215);
		
		// labels
		context.drawTextWithShadow(textRenderer, "Token:", width / 2 - 100, 77,
			10526880);
		context.drawTextWithShadow(textRenderer, "Name:",
			width / 2 - 105 - textRenderer.getWidth("Name:"),
			140 + textRenderer.fontHeight / 2, 10526880);
		context.drawTextWithShadow(textRenderer, "UUID:",
			width / 2 - 105 - textRenderer.getWidth("UUID:"),
			165 + textRenderer.fontHeight / 2, 10526880);
		context.drawTextWithShadow(textRenderer, "Session:",
			width / 2 - 105 - textRenderer.getWidth("Session:"),
			190 + textRenderer.fontHeight / 2, 10526880);
		
		// text boxes
		tokenBox.render(context, mouseX, mouseY, partialTicks);
		nameBox.render(context, mouseX, mouseY, partialTicks);
		uuidBox.render(context, mouseX, mouseY, partialTicks);
		sessionBox.render(context, mouseX, mouseY, partialTicks);
		
		for(Drawable drawable : drawables)
			drawable.render(context, mouseX, mouseY, partialTicks);
	}
	
	private Text getCurrentStatus()
	{
		MutableText status = Text.literal("Logged in as ")
			.append(Text.literal(client.getSession().getUsername())
				.formatted(Formatting.GOLD))
			.append(" (");
		
		switch(SessionManager.getSessionType())
		{
			case ALTENING:
			return status
				.append(Text.literal("altening").formatted(Formatting.GREEN))
				.append(" account)");
			case PREMIUM_ALT:
			return status
				.append(
					Text.literal("premium alt").formatted(Formatting.YELLOW))
				.append(" account)");
			case CRACKED_ALT:
			return status
				.append(Text.literal("cracked alt").formatted(Formatting.GRAY))
				.append(" account)");
			case LAUNCHER:
			default:
			return status
				.append(Text.literal("launcher").formatted(Formatting.GOLD))
				.append(" account)");
		}
	}
	
	@Override
	public final void close()
	{
		client.setScreen(prevScreen);
	}
}
