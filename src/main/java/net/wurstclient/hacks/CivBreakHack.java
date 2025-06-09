/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.block.BlockState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PostMotionListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;

@SearchTags({"civ break", "insta mine"})
public final class CivBreakHack extends Hack
	implements UpdateListener, PostMotionListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private BlockPos currentBlock;
	private Direction facing;
	
	public CivBreakHack()
	{
		super("CivBreak");
		setCategory(Category.BLOCKS);
		addSetting(range);
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().excavatorHack.setEnabled(false);
		WURST.getHax().nukerHack.setEnabled(false);
		WURST.getHax().nukerLegitHack.setEnabled(false);
		WURST.getHax().speedNukerHack.setEnabled(false);
		WURST.getHax().tunnellerHack.setEnabled(false);
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PostMotionListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PostMotionListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		currentBlock = null;
		facing = null;
	}
	
	@Override
	public void onUpdate()
	{
		if(currentBlock == null)
			return;
		
		BlockState state = BlockUtils.getState(currentBlock);
		if(state.isAir())
			return;
		
		BlockBreakingParams params =
			BlockBreaker.getBlockBreakingParams(currentBlock);
		if(params.distanceSq() > range.getValueSq())
			return;
		
		// face block
		WURST.getRotationFaker().faceVectorPacket(params.hitVec());
		facing = params.side();
	}
	
	@Override
	public void onPostMotion()
	{
		if(facing == null)
			return;
		
		MC.player.swingHand(Hand.MAIN_HAND);
		IMC.getInteractionManager().sendPlayerActionC2SPacket(
			PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, currentBlock,
			facing);
		facing = null;
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(currentBlock == null)
			return;
		
		// Get colors
		float[] rgb = {1, 1, 0};
		int quadColor = RenderUtils.toIntColor(rgb, 0.25F);
		int lineColor = RenderUtils.toIntColor(rgb, 0.5F);
		
		// Draw box
		Box box = new Box(currentBlock);
		RenderUtils.drawSolidBox(matrixStack, box, quadColor, false);
		RenderUtils.drawOutlinedBox(matrixStack, box, lineColor, false);
	}
	
	public void updateBlock(BlockPos pos)
	{
		currentBlock = pos;
	}
}
