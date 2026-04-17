/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

public class AutoDrainHack extends Hack
	implements UpdateListener, PostMotionListener, PacketOutputListener
{
	private SliderSetting delay = new SliderSetting("Delay",
		"Delay between right click actions in milliseconds.", 100, 0, 2000, 50,
		ValueDisplay.INTEGER);
	private final SliderSetting range = new SliderSetting("Placement Range",
		"The range to attempt to right click surfaces.\n"
			+ "Ranges above 4.5 will most likely fail.",
		4, 1, 6, 0.25, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting lava = new CheckboxSetting("Drain Lava",
		"Seeks out lava source blocks to be drained", true);
	private final CheckboxSetting water =
		new CheckboxSetting("Drain Water",
			"Seeks out water source blocks to be drained.\n"
				+ "This will not work if the source is able to regenerate!",
			false);
	
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
		ItemStack stack = MC.player.getMainHandItem();
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
		BlockPos start = MC.player.blockPosition();
		int layer = reverse ? range.getValueI() : 0;
		
		while(reverse ? layer >= 0 : layer <= range.getValueI())
		{
			for(int x = start.getX() - layer; x <= start.getX() + layer; x++)
				for(int y = start.getY() - layer; y <= start.getY()
					+ layer; y++)
					for(int z = start.getZ() - layer; z <= start.getZ()
						+ layer; z++)
					{
						if(Math.abs(x - start.getX()) != layer
							&& Math.abs(y - start.getY()) != layer
							&& Math.abs(z - start.getZ()) != layer)
							continue;
						
						BlockPos pos = new BlockPos(x, y, z);
						FluidState state = MC.level.getFluidState(pos);
						
						if(state.isEmpty() || !state.isSource())
							continue;
						boolean isWater = state.is(Fluids.WATER);
						if((isWater && !water.isChecked())
							|| (!isWater && !lava.isChecked()))
							continue;
						
						if((item == Items.WATER_BUCKET && !isWater)
							|| (item == Items.LAVA_BUCKET && isWater))
							continue;
						
						double rangeSq = Math.pow(range.getValue(), 2);
						
						if(item == Items.BUCKET
							&& prepareToCollect(pos, rangeSq, prioFloating))
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
	
	private boolean prepareToCollect(BlockPos pos, double rangeSq,
		boolean prioFloating)
	{
		Vec3 eyesPos = RotationUtils.getEyesPos();
		Vec3 posVec = Vec3.atCenterOf(pos);
		double distanceSqPosVec = eyesPos.distanceToSqr(posVec);
		
		if(prioFloating)
			// check if lava is attached to a surface, if so, skip for now
			for(Direction side : Direction.values())
			{
				BlockPos neighbor = pos.relative(side);
				
				// check if neighbor can be right clicked
				if(BlockUtils.canBeClicked(neighbor))
					return false;
			}
		
		for(Direction side : Direction.values())
		{
			Vec3 hitVec = posVec
				.add(Vec3.atLowerCornerOf(side.getUnitVec3i()).scale(0.5));
			double distanceSqHitVec = eyesPos.distanceToSqr(hitVec);
			
			// check if hitVec is within range
			if(distanceSqHitVec > rangeSq)
				continue;
			
			// check if side is facing towards player
			if(distanceSqHitVec >= distanceSqPosVec)
				continue;
			
			// check line of sight
			Rotation rotation = RotationUtils.getNeededRotations(hitVec);
			BlockHitResult result = rayTrace(rotation.pitch(), rotation.yaw(),
				ClipContext.Fluid.SOURCE_ONLY);
			if(result.getType() == HitResult.Type.MISS
				|| !result.getBlockPos().equals(pos)
				|| result.getDirection() != side)
				continue;
			
			serverRot = rotation;
			WURST.getRotationFaker().faceVectorPacket(hitVec);
			return true;
		}
		return false;
	}
	
	private boolean prepareToPlace(BlockPos pos, double rangeSq)
	{
		Vec3 eyesPos = RotationUtils.getEyesPos();
		Vec3 posVec = Vec3.atCenterOf(pos);
		double distanceSqPosVec = eyesPos.distanceToSqr(posVec);
		
		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			
			// check if neighbor can be right clicked
			if(!BlockUtils.canBeClicked(neighbor) && (!(BlockUtils
				.getState(neighbor).getBlock() instanceof LiquidBlock)
				|| !WURST.getHax().liquidsHack.isEnabled()))
				continue;
			
			Vec3 dirVec = Vec3.atLowerCornerOf(side.getUnitVec3i());
			Vec3 hitVec = posVec.add(dirVec.scale(0.5));
			
			// check if hitVec is within range
			if(eyesPos.distanceToSqr(hitVec) > rangeSq)
				continue;
			
			// check if side is visible (facing away from player)
			if(distanceSqPosVec > eyesPos.distanceToSqr(posVec.add(dirVec)))
				continue;
			
			// check line of sight
			Rotation rotation = RotationUtils.getNeededRotations(hitVec);
			BlockHitResult result = rayTrace(rotation.pitch(), rotation.yaw(),
				ClipContext.Fluid.NONE);
			if(result.getType() == HitResult.Type.MISS
				|| !result.getBlockPos().equals(neighbor)
				|| result.getDirection() != side.getOpposite())
				continue;
			
			serverRot = rotation;
			WURST.getRotationFaker().faceVectorPacket(hitVec);
			return true;
		}
		
		return false;
	}
	
	private BlockHitResult rayTrace(float pitch, float yaw,
		ClipContext.Fluid fluidHandling)
	{
		LocalPlayer player = MC.player;
		Vec3 eyes = player.getEyePosition();
		Vec3 hitVec = eyes.add(
			MC.player.calculateViewVector(pitch, yaw).scale(range.getValue()));
		return MC.level.clip(new ClipContext(eyes, hitVec,
			ClipContext.Block.OUTLINE, fluidHandling, player));
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
		if(event.getPacket() instanceof ServerboundUseItemPacket packet
			&& useServerRot)
			event.setPacket(new ServerboundUseItemPacket(packet.getHand(),
				packet.getSequence(), serverRot.yaw(), serverRot.pitch()));
	}
	
	public boolean useServerRot()
	{
		return useServerRot;
	}
	
	public BlockHitResult rayTrace(ClipContext.Fluid fluidHandling)
	{
		return rayTrace(serverRot.pitch(), serverRot.yaw(), fluidHandling);
	}
}
