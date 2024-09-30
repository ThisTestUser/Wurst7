/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerMoveListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"EasyElytra", "extra elytra", "easy elytra"})
public final class ExtraElytraHack extends Hack implements PlayerMoveListener, UpdateListener
{
	private final CheckboxSetting instantFly = new CheckboxSetting(
		"Instant fly", "Jump to fly, no weird double-jump needed!", true);
	
	private final CheckboxSetting motionCtrl = new CheckboxSetting(
		"Motion control", "Move around freely like flying in Creative Mode.\n"
			+ "No fireworks needed!",
		true);
	
	public final SliderSetting base =
		new SliderSetting("Base speed", "Speed to move using WASD with motion control.",
			4, 0, 15, 0.05, ValueDisplay.DECIMAL);
	
	private final SliderSetting up =
		new SliderSetting("Up speed", "Speed to move up with motion control.",
			1, 0, 3, 0.02, ValueDisplay.DECIMAL);
	
	private final SliderSetting down =
		new SliderSetting("Down speed", "Speed to move down with motion control.",
			1, 0, 3, 0.02, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting ignorePitch =
		new CheckboxSetting("Ignore Pitch",
			"You will not be able to dive down or up\n"
			+ "by changing your pitch.", false);
	
	private final CheckboxSetting idleLock = new CheckboxSetting("Idle lock",
		"Freezes your position at idle when flying.", false);
	
	private final CheckboxSetting stopInWater =
		new CheckboxSetting("Stop flying in water", true);
	
	private final CheckboxSetting hover =
		new CheckboxSetting("Hover mode",
			"The player will not be allowed to touch the ground unless the sneak key is long pressed.\n"
			+ "You must not be looking down for this to work, unless you have fake pitch enabled.", false);
	
	private final CheckboxSetting fakePitch =
		new CheckboxSetting("Fake Pitch",
			"Prevents the player from touching the ground with hover mode enabled by faking your pitch.", false);
	
	private int jumpTimer;
	private int waterTimer;
	private int sneakPressTime;
	
	public ExtraElytraHack()
	{
		super("ExtraElytra");
		setCategory(Category.MOVEMENT);
		addSetting(instantFly);
		addSetting(motionCtrl);
		addSetting(base);
		addSetting(up);
		addSetting(down);
		addSetting(ignorePitch);
		
		addSetting(idleLock);
		addSetting(stopInWater);
		addSetting(hover);
		addSetting(fakePitch);
	}
	
	@Override
	public void onEnable()
	{
		EVENTS.add(PlayerMoveListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		sneakPressTime = 0;
		jumpTimer = 0;
		waterTimer = 0;
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(PlayerMoveListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onPlayerMove(PlayerMoveEvent event)
	{
		ItemStack chest = MC.player.getEquippedStack(EquipmentSlot.CHEST);
		if(chest.getItem() != Items.ELYTRA || !MC.player.isFallFlying())
			return;
		
		if(idleLock.isChecked() && !MC.options.sneakKey.isPressed()
			&& !MC.options.jumpKey.isPressed()
			&& !MC.options.forwardKey.isPressed()
			&& !MC.options.backKey.isPressed()
			&& !MC.options.leftKey.isPressed()
			&& !MC.options.rightKey.isPressed())
			event.setOffset(new Vec3d(0, 0, 0));
		
		if(ignorePitch.isChecked() && !MC.options.sneakKey.isPressed()
			&& !MC.options.jumpKey.isPressed())
		{
			Vec3d offset = event.getOffset();
			event.setOffset(new Vec3d(offset.x, 0, offset.z));
		}
		
		forceHover(event);
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.options.sneakKey.isPressed())
			sneakPressTime++;
		else
			sneakPressTime = 0;
		
		if(jumpTimer > 0)
			jumpTimer--;
		if(MC.player.isTouchingWater())
			waterTimer = 20;
		else if(waterTimer > 0)
			waterTimer--;
		
		ItemStack chest = MC.player.getEquippedStack(EquipmentSlot.CHEST);
		if(chest.getItem() != Items.ELYTRA)
			return;
		
		if(MC.player.isFallFlying())
		{
			if(stopInWater.isChecked() && MC.player.isTouchingWater())
			{
				sendStartStopPacket();
				return;
			}
			
			controlMotion();
			
			if(fakePitch.isChecked() && !WURST.getRotationFaker().isFakeRotation())
				WURST.getRotationFaker().setServerRotation(MC.player.getYaw(), -10);
			return;
		}
		
		if(ElytraItem.isUsable(chest) && MC.options.jumpKey.isPressed())
			doInstantFly();
	}
	
	private void sendStartStopPacket()
	{
		ClientCommandC2SPacket packet = new ClientCommandC2SPacket(MC.player,
			ClientCommandC2SPacket.Mode.START_FALL_FLYING);
		MC.player.networkHandler.sendPacket(packet);
	}
	
	private void controlMotion()
	{
		if(!motionCtrl.isChecked())
			return;
		
		double baseSpeed = 0.2873 * base.getValue();
		double forward = MC.player.forwardSpeed;
		double strafe = MC.player.sidewaysSpeed;
		float yaw = MC.player.getYaw();
		float pitch = ignorePitch.isChecked() ? 0 : MC.player.getPitch();
		if(forward == 0 && strafe == 0)
			MC.player.setVelocity(0, 0, 0);
		else
		{
			if(forward != 0)
			{
				if(strafe > 0)
					yaw += forward > 0 ? -45 : 45;
				else if(strafe < 0)
					yaw += forward > 0 ? 45 : -45;
				forward = forward > 0 ? 1 : -1;
				strafe = 0;
			}
			double strafeX = Math.sin(Math.toRadians(yaw + 90));
			double strafeZ = Math.cos(Math.toRadians(yaw + 90));
			
			double yawRad = Math.toRadians(yaw);
			double pitchRad = Math.toRadians(pitch);
			double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad);
			double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad);
			double vertical = -Math.sin(pitchRad);
			MC.player.setVelocity(forward * baseSpeed * forwardX + strafe * baseSpeed * strafeX,
				forward * baseSpeed * vertical,
				forward * baseSpeed * forwardZ - strafe * baseSpeed * strafeZ);
		}
		Vec3d velocity = MC.player.getVelocity();
		if(MC.options.jumpKey.isPressed())
			MC.player.setVelocity(velocity.x, velocity.y + up.getValue(),
				velocity.z);
		else if(MC.options.sneakKey.isPressed())
			MC.player.setVelocity(velocity.x, velocity.y - down.getValue(),
				velocity.z);
	}
	
	private void doInstantFly()
	{
		if(!instantFly.isChecked())
			return;
		
		if(stopInWater.isChecked() && MC.player.isTouchingWater())
			return;
		
		if(jumpTimer <= 0)
		{
			jumpTimer = 20;
			if(waterTimer <= 0)
				MC.player.setVelocity(0, -0.2, 0);
			else
			{
				MC.player.setJumping(false);
				MC.player.setSprinting(true);
				MC.player.jump();
			}
		}
		
		sendStartStopPacket();
	}
	
	private void forceHover(PlayerMoveEvent event)
	{
		if(!hover.isChecked() || sneakPressTime > 15)
			return;
		
		Vec3d velocity = MC.player.getVelocity();
		Vec3d move = event.getOffset();
		
		double offset = -3;
		Iterable<VoxelShape> boxes =
			MC.world.getBlockCollisions(MC.player,
				MC.player.getBoundingBox().expand(velocity.x,
					offset, velocity.z));
		double closest = VoxelShapes.calculateMaxOffset(Direction.Axis.Y,
			MC.player.getBoundingBox(), boxes, offset);
		
		// Force player to hover 0.3 blocks above
		if(Math.abs(closest) < Math.abs(offset))
			event.setOffset(new Vec3d(move.x, Math.max(move.y, closest - offset / 10), move.z));
	}
}
