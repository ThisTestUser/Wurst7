/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.Color;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.*;
import net.wurstclient.hack.DontSaveState;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.FakePlayerEntity;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@DontSaveState
@SearchTags({"free camera", "spectator"})
public final class FreecamHack extends Hack implements UpdateListener,
	PacketOutputListener, IsPlayerInWaterListener, AirStrafingSpeedListener,
	IsPlayerInLavaListener, CameraTransformViewBobbingListener,
	IsNormalCubeListener, SetOpaqueCubeListener, RenderListener
{
	private final SliderSetting speed =
		new SliderSetting("Speed", 1, 0.05, 10, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting tracer = new CheckboxSetting("Tracer",
		"Draws a line to your character's actual position.", false);
	
	private final ColorSetting color =
		new ColorSetting("Tracer color", Color.WHITE);
	
	private final CheckboxSetting motionPackets = new CheckboxSetting(
		"Continue motion updates",
		"Continue sending motion packets using your actual position.", true);
	
	private FakePlayerEntity fakePlayer;
	private int lastPositionTicks;
	
	public FreecamHack()
	{
		super("Freecam");
		setCategory(Category.RENDER);
		addSetting(speed);
		addSetting(tracer);
		addSetting(color);
		addSetting(motionPackets);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
		EVENTS.add(IsPlayerInWaterListener.class, this);
		EVENTS.add(IsPlayerInLavaListener.class, this);
		EVENTS.add(AirStrafingSpeedListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(IsNormalCubeListener.class, this);
		EVENTS.add(SetOpaqueCubeListener.class, this);
		EVENTS.add(RenderListener.class, this);
		
		fakePlayer = new FakePlayerEntity();
		fakePlayer.setOnGround(MC.player.isOnGround());
		fakePlayer.horizontalCollision = MC.player.horizontalCollision;
		
		GameOptions opt = MC.options;
		KeyBinding[] bindings = {opt.forwardKey, opt.backKey, opt.leftKey,
			opt.rightKey, opt.jumpKey, opt.sneakKey};
		
		for(KeyBinding binding : bindings)
			IKeyBinding.get(binding).resetPressedState();
		
		lastPositionTicks = 0;
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		EVENTS.remove(IsPlayerInWaterListener.class, this);
		EVENTS.remove(IsPlayerInLavaListener.class, this);
		EVENTS.remove(AirStrafingSpeedListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(IsNormalCubeListener.class, this);
		EVENTS.remove(SetOpaqueCubeListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		fakePlayer.resetPlayerPosition();
		fakePlayer.despawn();
		
		ClientPlayerEntity player = MC.player;
		player.setVelocity(Vec3d.ZERO);
		
		MC.worldRenderer.reload();
	}
	
	@Override
	public void onUpdate()
	{
		ClientPlayerEntity player = MC.player;
		player.setVelocity(Vec3d.ZERO);
		player.getAbilities().flying = false;
		
		player.setOnGround(false);
		Vec3d velocity = player.getVelocity();
		
		if(MC.options.jumpKey.isPressed())
			player.setVelocity(velocity.add(0, speed.getValue(), 0));
		
		if(MC.options.sneakKey.isPressed())
			player.setVelocity(velocity.subtract(0, speed.getValue(), 0));
	}
	
	@Override
	public void onGetAirStrafingSpeed(AirStrafingSpeedEvent event)
	{
		event.setSpeed(speed.getValueF());
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(event.getPacket() instanceof PlayerMoveC2SPacket)
		{
			if(motionPackets.isChecked())
				lastPositionTicks++;
			
			if(lastPositionTicks >= 20)
			{
				event.setPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
					fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(),
					fakePlayer.isOnGround(), fakePlayer.horizontalCollision));
				lastPositionTicks = 0;
			}else
				event.cancel();
		}
	}
	
	@Override
	public void onIsPlayerInWater(IsPlayerInWaterEvent event)
	{
		event.setInWater(false);
	}
	
	@Override
	public void onIsPlayerInLava(IsPlayerInLavaEvent event)
	{
		event.setInLava(false);
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(tracer.isChecked())
			event.cancel();
	}
	
	@Override
	public void onIsNormalCube(IsNormalCubeEvent event)
	{
		event.cancel();
	}
	
	@Override
	public void onSetOpaqueCube(SetOpaqueCubeEvent event)
	{
		event.cancel();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(fakePlayer == null || !tracer.isChecked())
			return;
		
		RenderSystem.depthFunc(GlConst.GL_ALWAYS);
		
		VertexConsumerProvider.Immediate vcp =
			MC.getBufferBuilders().getEntityVertexConsumers();
		VertexConsumer buffer = vcp.getBuffer(WurstRenderLayers.ESP_LINES);
		
		matrixStack.push();
		
		RegionPos region = RenderUtils.getCameraRegion();
		RenderUtils.applyRegionalRenderOffset(matrixStack, region);
		
		Vec3d regionVec = region.toVec3d();
		int colorI = color.getColorI(0x80);
		
		// box
		double extraSize = 0.05;
		Vec3d offset = regionVec.negate().add(0, extraSize, 0);
		Box box = fakePlayer.getBoundingBox().offset(offset).expand(extraSize);
		RenderUtils.drawOutlinedBox(matrixStack, buffer, box, colorI);
		
		// line
		Vec3d start = RotationUtils.getClientLookVec(partialTicks).multiply(2)
			.add(RenderUtils.getCameraPos()).subtract(regionVec);
		Vec3d end = fakePlayer.getBoundingBox().getCenter().subtract(regionVec);
		RenderUtils.drawLine(matrixStack, buffer, start, end, colorI);
		
		matrixStack.pop();
		
		vcp.draw(WurstRenderLayers.ESP_LINES);
	}
}
