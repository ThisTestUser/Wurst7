/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
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

import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.ConnectionPacketOutputListener.ConnectionPacketOutputEvent;
import net.wurstclient.events.PacketInputListener.PacketInputEvent;

@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin
	extends SimpleChannelInboundHandler<Packet<?>>
{
	private ConcurrentLinkedQueue<ConnectionPacketOutputEvent> events =
		new ConcurrentLinkedQueue<>();
	
	@WrapOperation(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V",
		ordinal = 0),
		method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V")
	private void onPacketReceived(Packet<?> packet, PacketListener listener,
		Operation<Void> original)
	{
		if(packet instanceof BundleS2CPacket bundle)
		{
			List<Packet<? super ClientPlayPacketListener>> packets =
				new ArrayList<>();
			bundle.getPackets().forEach(p -> {
				PacketInputEvent event = new PacketInputEvent(p);
				EventManager.fire(event);
				
				if(!event.isCancelled())
					packets.add((Packet<? super ClientPlayPacketListener>)event
						.getPacket());
			});
			original.call(new BundleS2CPacket(packets), listener);
			return;
		}
		
		PacketInputEvent event = new PacketInputEvent(packet);
		EventManager.fire(event);
		
		if(!event.isCancelled())
			original.call(event.getPacket(), listener);
	}
	
	@ModifyVariable(at = @At("HEAD"),
		method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V")
	public Packet<?> modifyPacket(Packet<?> packet)
	{
		ConnectionPacketOutputEvent event =
			new ConnectionPacketOutputEvent(packet);
		events.add(event);
		EventManager.fire(event);
		return event.getPacket();
	}
	
	@Inject(at = @At("HEAD"),
		method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
		cancellable = true)
	private void onSend(Packet<?> packet, @Nullable PacketCallbacks callback,
		CallbackInfo ci)
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
	
	@Inject(at = @At("HEAD"),
		method = "disconnect(Lnet/minecraft/network/DisconnectionInfo;)V")
	private void onDisconnect(DisconnectionInfo info, CallbackInfo ci)
	{
		WurstClient.INSTANCE.getCmds().visitorDetectorCmd.removeListeners();
	}
}
