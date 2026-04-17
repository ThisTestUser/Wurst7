/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
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
		if(MC.player == null || MC.level == null)
			return;
		
		if(event.getPacket() instanceof ClientboundPlayerPositionPacket packet)
		{
			packet.relatives().remove(Relative.X_ROT);
			packet.relatives().remove(Relative.Y_ROT);
			PositionMoveRotation newPosition = new PositionMoveRotation(
				packet.change().position(), packet.change().deltaMovement(),
				MC.player.getYRot(), MC.player.getXRot());
			event.setPacket(ClientboundPlayerPositionPacket.of(packet.id(),
				newPosition, packet.relatives()));
		}
	}
}
