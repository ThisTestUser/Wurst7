/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.events;

import java.util.ArrayList;

import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.event.CancellableEvent;
import net.wurstclient.event.Listener;

public interface PlayerMoveListener extends Listener
{
	public void onPlayerMove(PlayerMoveEvent event);
	
	public static class PlayerMoveEvent extends CancellableEvent<PlayerMoveListener>
	{
		private final MovementType type;
		private Vec3d offset;
		
		public PlayerMoveEvent(MovementType type, Vec3d offset)
		{
			this.type = type;
			this.offset = offset;
		}
		
		public MovementType getType()
		{
			return type;
		}
		
		public Vec3d getOffset()
		{
			return offset;
		}
		
		public void setOffset(Vec3d offset)
		{
			this.offset = offset;
		}
		
		@Override
		public void fire(ArrayList<PlayerMoveListener> listeners)
		{
			for(PlayerMoveListener listener : listeners)
				listener.onPlayerMove(this);
		}
		
		@Override
		public Class<PlayerMoveListener> getListenerType()
		{
			return PlayerMoveListener.class;
		}
	}
}
