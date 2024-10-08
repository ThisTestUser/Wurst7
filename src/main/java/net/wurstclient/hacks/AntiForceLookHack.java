/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.hack.Hack;

@SearchTags({"anti force look", "no rotate"})
public final class AntiForceLookHack extends Hack implements PacketInputListener
{
	public AntiForceLookHack()
	{
		super("AntiForceLook");
		setCategory(Category.MOVEMENT);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(PacketInputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PacketInputListener.class, this);
	}
	
	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(event.getPacket() instanceof PlayerPositionLookS2CPacket packet)
			event.setPacket(
				new PlayerPositionLookS2CPacket(packet.getX(), packet.getY(),
					packet.getZ(), MC.player.getYaw(), MC.player.getPitch(),
					packet.getFlags(), packet.getTeleportId()));
	}
}
