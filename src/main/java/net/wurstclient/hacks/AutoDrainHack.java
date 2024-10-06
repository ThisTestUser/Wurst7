/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.block.FluidBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BucketItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.wurstclient.Category;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.PostMotionListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

public class AutoDrainHack extends Hack implements UpdateListener, PostMotionListener, PacketOutputListener
{
	private SliderSetting delay = new SliderSetting("Delay",
		"Delay between right click actions in milliseconds.",
		100, 0, 2000, 50, ValueDisplay.INTEGER);
	private final SliderSetting range =
		new SliderSetting("Placement Range", "The range to attempt to right click surfaces.\n"
			+ "Ranges above 4.5 will most likely fail.",
			4, 1, 6, 0.25, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting lava =
		new CheckboxSetting("Drain Lava", "Seeks out lava source blocks to be drained", true);
	private final CheckboxSetting water =
		new CheckboxSetting("Drain Water", "Seeks out water source blocks to be drained.\n"
			+ "This will not work if the source is able to regenerate!", false);
	
	private boolean rightClick;
	private boolean useServerRot;
	private Rotation serverRot;
	
	private long lastRun;
	
	public AutoDrainHack()
	{
		super("AutoDrain");
		setCategory(Category.BLOCKS);
		addSetting(delay);
		addSetting(range);
		addSetting(lava);
		addSetting(water);
	}
	
	@Override
	protected void onEnable()
	{
		rightClick = false;
		useServerRot = false;
		serverRot = null;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PostMotionListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PostMotionListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		ItemStack stack = MC.player.getMainHandStack();
		if(!(stack.getItem() instanceof BucketItem))
			return;
		
		if(System.currentTimeMillis() < lastRun + delay.getValueI())
			return;
		
		lastRun = System.currentTimeMillis();
		
		BucketItem bucket = (BucketItem)stack.getItem();
		if(bucket == Items.BUCKET)
		{
			// prioritize floating (non-attached lava)
			if(faceLiquid(false, bucket, true))
				return;
			faceLiquid(false, bucket, false);
		}else if(bucket == Items.LAVA_BUCKET || bucket == Items.WATER_BUCKET)
			faceLiquid(true, bucket, false);
	}
	
	private boolean faceLiquid(boolean reverse, Item item, boolean prioFloating)
	{
		BlockPos start = MC.player.getBlockPos();
		int layer = reverse ? range.getValueI() : 0;
		
		while(reverse ? layer >= 0 : layer <= range.getValueI())
		{
			for(int x = start.getX() - layer; x <= start.getX() + layer; x++)
				for(int y = start.getY() - layer; y <= start.getY() + layer; y++)
					for(int z = start.getZ() - layer; z <= start.getZ() + layer; z++)
					{
						if(Math.abs(x - start.getX()) != layer
							&& Math.abs(y - start.getY()) != layer && Math.abs(z - start.getZ()) != layer)
							continue;
						
						BlockPos pos = new BlockPos(x, y, z);
						FluidState state = MC.world.getFluidState(pos);
						
						if(state.isEmpty() || !state.isStill())
							continue;
						boolean isWater = state.isOf(Fluids.WATER);
						if((isWater && !water.isChecked()) || (!isWater && !lava.isChecked()))
							continue;
						
						if((item == Items.WATER_BUCKET && !isWater)
							|| (item == Items.LAVA_BUCKET && isWater))
							continue;
						
						double rangeSq = Math.pow(range.getValue(), 2);
						
						if(item == Items.BUCKET && prepareToCollect(pos, rangeSq, prioFloating))
						{
							rightClick = true;
							return true;
						}
						
						if(item != Items.BUCKET && prepareToPlace(pos, rangeSq))
						{
							rightClick = true;
							return true;
						}
					}
			if(reverse)
				layer--;
			else
				layer++;
		}
		return false;
	}
	
	private boolean prepareToCollect(BlockPos pos, double rangeSq, boolean prioFloating)
	{
		Vec3d eyesPos = RotationUtils.getEyesPos();
		Vec3d posVec = Vec3d.ofCenter(pos);
		double distanceSqPosVec = eyesPos.squaredDistanceTo(posVec);
		
		if(prioFloating)
			// check if lava is attached to a surface, if so, skip for now
			for(Direction side : Direction.values())
			{
				BlockPos neighbor = pos.offset(side);
				
				// check if neighbor can be right clicked
				if(BlockUtils.canBeClicked(neighbor))
					return false;
			}
		
		for(Direction side : Direction.values())
		{
			Vec3d hitVec = posVec.add(Vec3d.of(side.getVector()).multiply(0.5));
			double distanceSqHitVec = eyesPos.squaredDistanceTo(hitVec);
			
			// check if hitVec is within range
			if(distanceSqHitVec > rangeSq)
				continue;
			
			// check if side is facing towards player
			if(distanceSqHitVec >= distanceSqPosVec)
				continue;
			
			// check line of sight
			Rotation rotation = RotationUtils.getNeededRotations(hitVec);
			BlockHitResult result = rayTrace(rotation.pitch(), rotation.yaw(), RaycastContext.FluidHandling.SOURCE_ONLY);
			if(result.getType() == HitResult.Type.MISS
				|| !result.getBlockPos().equals(pos) || result.getSide() != side)
				continue;
			
			serverRot = rotation;
			WURST.getRotationFaker().faceVectorPacket(hitVec);
			return true;
		}
		return false;
	}
	
	private boolean prepareToPlace(BlockPos pos, double rangeSq)
	{
		Vec3d eyesPos = RotationUtils.getEyesPos();
		Vec3d posVec = Vec3d.ofCenter(pos);
		double distanceSqPosVec = eyesPos.squaredDistanceTo(posVec);
		
		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.offset(side);
			
			// check if neighbor can be right clicked
			if(!BlockUtils.canBeClicked(neighbor) && (!(BlockUtils.getState(neighbor).getBlock() instanceof FluidBlock)
				|| !WURST.getHax().liquidsHack.isEnabled()))
				continue;
			
			Vec3d dirVec = Vec3d.of(side.getVector());
			Vec3d hitVec = posVec.add(dirVec.multiply(0.5));
			
			// check if hitVec is within range
			if(eyesPos.squaredDistanceTo(hitVec) > rangeSq)
				continue;
			
			// check if side is visible (facing away from player)
			if(distanceSqPosVec > eyesPos.squaredDistanceTo(posVec.add(dirVec)))
				continue;
			
			// check line of sight
			Rotation rotation = RotationUtils.getNeededRotations(hitVec);
			BlockHitResult result = rayTrace(rotation.pitch(), rotation.yaw(), RaycastContext.FluidHandling.NONE);
			if(result.getType() == HitResult.Type.MISS
				|| !result.getBlockPos().equals(neighbor) || result.getSide() != side.getOpposite())
				continue;
			
			serverRot = rotation;
			WURST.getRotationFaker().faceVectorPacket(hitVec);
			return true;
		}
		
		return false;
	}
	
	private BlockHitResult rayTrace(float pitch, float yaw, RaycastContext.FluidHandling fluidHandling)
	{
		ClientPlayerEntity player = MC.player;
		Vec3d eyes = player.getEyePos();
		Vec3d hitVec = eyes.add(MC.player.getRotationVector(pitch, yaw).multiply(range.getValue()));
		return MC.world.raycast(new RaycastContext(eyes, hitVec, RaycastContext.ShapeType.OUTLINE,
			fluidHandling, player));
	}
	
	@Override
	public void onPostMotion()
	{
		if(rightClick)
		{
			useServerRot = true;
			IMC.getInteractionManager().rightClickItem();
			useServerRot = false;
			rightClick = false;
		}
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(event.getPacket() instanceof PlayerMoveC2SPacket.Full packet && useServerRot)
			event.setPacket(new PlayerMoveC2SPacket.Full(packet.getX(0), packet.getY(0), packet.getZ(0),
				serverRot.yaw(), serverRot.pitch(), packet.isOnGround()));
	}
	
	public boolean useServerRot()
	{
		return useServerRot;
	}
	
	public BlockHitResult rayTrace(RaycastContext.FluidHandling fluidHandling)
	{
		return rayTrace(serverRot.pitch(), serverRot.yaw(), fluidHandling);
	}
}
