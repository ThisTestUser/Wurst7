/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.text.DecimalFormat;

import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.wurstclient.Category;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class LagIndicatorHack extends Hack implements GUIRenderListener, PacketInputListener
{
	private final SliderSetting lagOffset = new SliderSetting("Show lag after (s)", 2, 0.1, 10,
		0.1, ValueDisplay.DECIMAL);
	
	private static final DecimalFormat DF = new DecimalFormat("0.0");
	private long lastPacketMS;
	
	public LagIndicatorHack()
	{
		super("LagIndicator");
		setCategory(Category.RENDER);
		addSetting(lagOffset);
	}
	
	@Override
	public void onEnable()
	{
		lastPacketMS = System.currentTimeMillis();
		EVENTS.add(GUIRenderListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
	}
	
	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		lastPacketMS = System.currentTimeMillis();
	}

	@Override
	public void onRenderGUI(MatrixStack matrixStack, float partialTicks)
	{
		Window window = MC.getWindow();
		long delta = System.currentTimeMillis() - lastPacketMS;
		if(delta >= lagOffset.getValue() * 1000)
			MC.textRenderer.drawWithShadow(matrixStack, "Time since last packet: " +
				DF.format((double)delta / 1000) + " S", window.getScaledWidth() / 2 - 130 / 2,
				16, 16777215);
	}
}
