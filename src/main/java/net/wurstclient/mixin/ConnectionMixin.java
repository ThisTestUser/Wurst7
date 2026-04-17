/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.ConnectionPacketOutputListener.ConnectionPacketOutputEvent;
import net.wurstclient.events.PacketInputListener.PacketInputEvent;

@Mixin(Connection.class)
public abstract class ConnectionMixin
	extends SimpleChannelInboundHandler<Packet<?>>
{
	private ConcurrentLinkedQueue<ConnectionPacketOutputEvent> events =
		new ConcurrentLinkedQueue<>();
	
	@WrapOperation(
		method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V",
			ordinal = 0))
	private void onPacketReceived(Packet<?> packet, PacketListener listener,
		Operation<Void> original)
	{
		if(packet instanceof ClientboundBundlePacket bundle)
		{
			List<Packet<? super ClientGamePacketListener>> packets =
				new ArrayList<>();
			bundle.subPackets().forEach(p -> {
				PacketInputEvent event = new PacketInputEvent(p);
				EventManager.fire(event);
				
				if(!event.isCancelled())
					packets.add((Packet<? super ClientGamePacketListener>)event
						.getPacket());
			});
			original.call(new ClientboundBundlePacket(packets), listener);
			return;
		}
		
		PacketInputEvent event = new PacketInputEvent(packet);
		EventManager.fire(event);
		
		if(!event.isCancelled())
			original.call(event.getPacket(), listener);
	}
	
	// These mixins target the second "send" method. The one with two arguments.
	
	@ModifyVariable(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
		at = @At("HEAD"))
	public Packet<?> modifyPacket(Packet<?> packet)
	{
		ConnectionPacketOutputEvent event =
			new ConnectionPacketOutputEvent(packet);
		events.add(event);
		EventManager.fire(event);
		return event.getPacket();
	}
	
	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
		at = @At("HEAD"),
		cancellable = true)
	private void onSend(Packet<?> packet,
		@Nullable ChannelFutureListener callback, CallbackInfo ci)
	{
		ConnectionPacketOutputEvent event = getEvent(packet);
		if(event == null)
			return;
		
		if(event.isCancelled())
			ci.cancel();
		
		events.remove(event);
	}
	
	private ConnectionPacketOutputEvent getEvent(Packet<?> packet)
	{
		for(ConnectionPacketOutputEvent event : events)
			if(event.getPacket() == packet)
				return event;
			
		return null;
	}
	
	@Inject(
		method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V",
		at = @At("HEAD"))
	private void onDisconnect(DisconnectionDetails info, CallbackInfo ci)
	{
		WurstClient.INSTANCE.getCmds().visitorDetectorCmd.removeListeners();
	}
}
