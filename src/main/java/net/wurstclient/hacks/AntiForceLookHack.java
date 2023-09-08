/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixin.PlayerPositionLookS2CPacketAccessor;

@SearchTags({"anti force look", "no rotate"})
public final class AntiForceLookHack extends Hack implements PacketOutputListener
{
	public AntiForceLookHack()
	{
		super("AntiForceLook");
		setCategory(Category.MOVEMENT);
	}
	
	@Override
	public void onEnable()
	{
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(PacketOutputListener.class, this);
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(event.getPacket() instanceof PlayerPositionLookS2CPacket)
		{
			PlayerPositionLookS2CPacketAccessor packet = (PlayerPositionLookS2CPacketAccessor)event.getPacket();
			packet.setYaw(MC.player.getYaw());
			packet.setPitch(MC.player.getPitch());
		}
	}
}
