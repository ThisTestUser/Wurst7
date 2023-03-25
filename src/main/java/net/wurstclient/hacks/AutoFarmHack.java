/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.block.*;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferBuilder.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Shader;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"auto farm", "AutoHarvest", "auto harvest"})
public final class AutoFarmHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting replant =
		new CheckboxSetting("Replant", true);
	
	private final CheckboxSetting harvestFirst = new CheckboxSetting(
		"Harvest first", "Harvest all crops first before replanting.",
		false);
	
	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"Check line of sight",
		"Makes sure that you don't reach through walls when breaking.",
		false);
	
	private final CheckboxSetting fortune = new CheckboxSetting(
		"Choose fortune tool",
		"Chooses a fortune tool to harvest crops.",
		false);
	
	private final CheckboxSetting silkTouch = new CheckboxSetting(
		"Choose silk touch tool",
		"Chooses a silk touch tool to harvest melons. Axes will be prioritized.",
		false);
	
	private final BlockListSetting excluded = new BlockListSetting("Excluded Crops",
		"List of crops that will be excluded from AutoFarm.");
	
	private final HashMap<BlockPos, Item> plants = new HashMap<>();
	
	private final ArrayDeque<Set<BlockPos>> prevBlocks = new ArrayDeque<>();
	private BlockPos currentBlock;
	private float progress;
	private float prevProgress;
	
	private VertexBuffer greenBuffer;
	private VertexBuffer cyanBuffer;
	private VertexBuffer redBuffer;
	
	private boolean busy;
	
	private final HashMap<Block, Item> seeds;
	private final HashSet<Block> fortuneBlocks;
	
	public AutoFarmHack()
	{
		super("AutoFarm");
		
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(replant);
		addSetting(harvestFirst);
		addSetting(checkLOS);
		addSetting(fortune);
		addSetting(silkTouch);
		addSetting(excluded);
		
		seeds = new HashMap<>();
		seeds.put(Blocks.WHEAT, Items.WHEAT_SEEDS);
		seeds.put(Blocks.CARROTS, Items.CARROT);
		seeds.put(Blocks.POTATOES, Items.POTATO);
		seeds.put(Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
		seeds.put(Blocks.PUMPKIN_STEM, Items.PUMPKIN_SEEDS);
		seeds.put(Blocks.MELON_STEM, Items.MELON_SEEDS);
		seeds.put(Blocks.NETHER_WART, Items.NETHER_WART);
		seeds.put(Blocks.COCOA, Items.COCOA_BEANS);
		
		fortuneBlocks = new HashSet<>();
		fortuneBlocks.add(Blocks.WHEAT);
		fortuneBlocks.add(Blocks.CARROTS);
		fortuneBlocks.add(Blocks.POTATOES);
		fortuneBlocks.add(Blocks.BEETROOTS);
		fortuneBlocks.add(Blocks.NETHER_WART);
		fortuneBlocks.add(Blocks.MELON);
	}
	
	@Override
	public void onEnable()
	{
		plants.clear();
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		if(currentBlock != null)
		{
			IMC.getInteractionManager().setBreakingBlock(true);
			MC.interactionManager.cancelBlockBreaking();
			currentBlock = null;
		}
		
		prevBlocks.clear();
		busy = false;
		
		Stream.of(greenBuffer, cyanBuffer, redBuffer).filter(Objects::nonNull)
			.forEach(VertexBuffer::close);
		greenBuffer = cyanBuffer = redBuffer = null;
	}
	
	@Override
	public void onUpdate()
	{
		currentBlock = null;
		Vec3d eyesVec = RotationUtils.getEyesPos().subtract(0.5, 0.5, 0.5);
		BlockPos eyesBlock = new BlockPos(RotationUtils.getEyesPos());
		double rangeSq = Math.pow(range.getValue(), 2);
		int blockRange = (int)Math.ceil(range.getValue());
		
		List<BlockPos> blocks = getBlockStream(eyesBlock, blockRange)
			.filter(pos -> eyesVec.squaredDistanceTo(Vec3d.of(pos)) <= rangeSq)
			.filter(BlockUtils::canBeClicked).collect(Collectors.toList());
		
		registerPlants(blocks);
		
		List<BlockPos> blocksToHarvest = new ArrayList<>();
		List<BlockPos> blocksToReplant = new ArrayList<>();
		
		if(!WURST.getHax().freecamHack.isEnabled())
		{
			blocksToHarvest = getBlocksToHarvest(eyesVec, blocks);
			
			if(replant.isChecked())
				blocksToReplant =
					getBlocksToReplant(eyesVec, eyesBlock,
						MC.player.getMainHandStack(), rangeSq, blockRange);
		}
		
		boolean harvesting = false;
		boolean replanting = false;
		
		if(harvestFirst.isChecked())
			harvesting = harvest(blocksToHarvest);
		
		if(!harvesting)
		{
			Iterator<BlockPos> replantItr = blocksToReplant.iterator();
			while(replantItr.hasNext())
			{
				BlockPos pos = replantItr.next();
				Item neededItem = plants.get(pos);
				if(tryToReplant(pos, neededItem))
				{
					replanting = true;
					break;
				}else
					replantItr.remove();
			}
		}
		
		if(!harvestFirst.isChecked() && !replanting)
			harvest(blocksToHarvest);
		
		busy = harvesting || replanting;
		updateVertexBuffers(blocksToHarvest, blocksToReplant);
	}
	
	private List<BlockPos> getBlocksToHarvest(Vec3d eyesVec,
		List<BlockPos> blocks)
	{
		return blocks.parallelStream().filter(this::shouldBeHarvested)
			.sorted(Comparator.comparingDouble(
				pos -> eyesVec.squaredDistanceTo(Vec3d.of(pos))))
			.collect(Collectors.toList());
	}
	
	private List<BlockPos> getBlocksToReplant(Vec3d eyesVec, BlockPos eyesBlock, ItemStack stack,
		double rangeSq, int blockRange)
	{
		return getBlockStream(eyesBlock, blockRange)
			.filter(pos -> eyesVec.squaredDistanceTo(Vec3d.of(pos)) <= rangeSq)
			.filter(
				pos -> BlockUtils.getState(pos).getMaterial().isReplaceable())
			.filter(pos -> plants.containsKey(pos)).filter(this::canBeReplanted)
			.sorted(Comparator.<BlockPos>comparingInt(
				pos -> stack.isEmpty() || stack.getItem() != plants.get(pos) ? 1 : 0).thenComparingDouble(
				pos -> eyesVec.squaredDistanceTo(Vec3d.of(pos))))
			.collect(Collectors.toList());
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(WurstClient.MC.getBlockEntityRenderDispatcher().camera == null)
			return;
		
		// GL settings
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_LINE_SMOOTH);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		matrixStack.push();
		RenderUtils.applyRegionalRenderOffset(matrixStack);
		
		BlockPos camPos = RenderUtils.getCameraBlockPos();
		int regionX = (camPos.getX() >> 9) * 512;
		int regionZ = (camPos.getZ() >> 9) * 512;
		
		RenderSystem.setShader(GameRenderer::getPositionShader);
		Matrix4f viewMatrix = matrixStack.peek().getPositionMatrix();
		Matrix4f projMatrix = RenderSystem.getProjectionMatrix();
		Shader shader = RenderSystem.getShader();
		
		if(greenBuffer != null)
		{
			RenderSystem.setShaderColor(0, 1, 0, 0.5F);
			greenBuffer.bind();
			greenBuffer.draw(viewMatrix, projMatrix, shader);
			VertexBuffer.unbind();
		}
		
		if(cyanBuffer != null)
		{
			RenderSystem.setShaderColor(0, 1, 1, 0.5F);
			cyanBuffer.bind();
			cyanBuffer.draw(viewMatrix, projMatrix, shader);
			VertexBuffer.unbind();
		}
		
		if(redBuffer != null)
		{
			RenderSystem.setShaderColor(1, 0, 0, 0.5F);
			redBuffer.bind();
			redBuffer.draw(viewMatrix, projMatrix, shader);
			VertexBuffer.unbind();
		}
		
		if(currentBlock != null)
		{
			matrixStack.push();
			
			Box box = new Box(BlockPos.ORIGIN);
			float p = prevProgress + (progress - prevProgress) * partialTicks;
			float red = p * 2F;
			float green = 2 - red;
			
			matrixStack.translate(currentBlock.getX() - regionX,
				currentBlock.getY(), currentBlock.getZ() - regionZ);
			if(p < 1)
			{
				matrixStack.translate(0.5, 0.5, 0.5);
				matrixStack.scale(p, p, p);
				matrixStack.translate(-0.5, -0.5, -0.5);
			}
			
			RenderSystem.setShaderColor(red, green, 0, 0.25F);
			RenderUtils.drawSolidBox(box, matrixStack);
			
			RenderSystem.setShaderColor(red, green, 0, 0.5F);
			RenderUtils.drawOutlinedBox(box, matrixStack);
			
			matrixStack.pop();
		}
		
		matrixStack.pop();
		
		// GL resets
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glDisable(GL11.GL_LINE_SMOOTH);
	}
	
	private Stream<BlockPos> getBlockStream(BlockPos center, int range)
	{
		BlockPos min = center.add(-range, -range, -range);
		BlockPos max = center.add(range, range, range);
		
		return BlockUtils.getAllInBox(min, max).stream();
	}
	
	private boolean shouldBeHarvested(BlockPos pos)
	{
		Block block = BlockUtils.getBlock(pos);
		BlockState state = BlockUtils.getState(pos);
		
		if(Collections.binarySearch(excluded.getBlockNames(), BlockUtils.getName(pos)) >= 0)
			return false;
		
		if(block instanceof CropBlock)
			return ((CropBlock)block).isMature(state);
		if(block instanceof GourdBlock)
			return true;
		if(block instanceof SugarCaneBlock)
			return BlockUtils.getBlock(pos.down()) instanceof SugarCaneBlock
				&& !(BlockUtils
					.getBlock(pos.down(2)) instanceof SugarCaneBlock);
		if(block instanceof CactusBlock)
			return BlockUtils.getBlock(pos.down()) instanceof CactusBlock
				&& !(BlockUtils.getBlock(pos.down(2)) instanceof CactusBlock);
		if(block instanceof KelpPlantBlock)
			return BlockUtils.getBlock(pos.down()) instanceof KelpPlantBlock
				&& !(BlockUtils
					.getBlock(pos.down(2)) instanceof KelpPlantBlock);
		if(block instanceof NetherWartBlock)
			return state.get(NetherWartBlock.AGE) >= 3;
		if(block instanceof BambooBlock)
			return BlockUtils.getBlock(pos.down()) instanceof BambooBlock
				&& !(BlockUtils.getBlock(pos.down(2)) instanceof BambooBlock);
		if(block instanceof CocoaBlock)
			return state.get(CocoaBlock.AGE) >= 2;
		
		return false;
	}
	
	private void registerPlants(List<BlockPos> blocks)
	{
		plants.putAll(blocks.parallelStream()
			.filter(pos -> seeds.containsKey(BlockUtils.getBlock(pos))
				&& Collections.binarySearch(excluded.getBlockNames(), BlockUtils.getName(pos)) < 0)
			.collect(Collectors.toMap(pos -> pos,
				pos -> seeds.get(BlockUtils.getBlock(pos)))));
	}
	
	private boolean canBeReplanted(BlockPos pos)
	{
		Item item = plants.get(pos);
		
		if(item == Items.WHEAT_SEEDS || item == Items.CARROT
			|| item == Items.POTATO || item == Items.BEETROOT_SEEDS
			|| item == Items.PUMPKIN_SEEDS || item == Items.MELON_SEEDS)
			return BlockUtils.getBlock(pos.down()) instanceof FarmlandBlock;
		
		if(item == Items.NETHER_WART)
			return BlockUtils.getBlock(pos.down()) instanceof SoulSandBlock;
		
		if(item == Items.COCOA_BEANS)
			return BlockUtils.getState(pos.north()).isIn(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.east()).isIn(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.south()).isIn(BlockTags.JUNGLE_LOGS)
				|| BlockUtils.getState(pos.west()).isIn(BlockTags.JUNGLE_LOGS);
		
		return false;
	}
	
	private void setSlot(ClientPlayerEntity player, int slot)
	{
		if(slot < 9)
			player.getInventory().selectedSlot = slot;
		else if(player.getInventory().getEmptySlot() < 9)
			IMC.getInteractionManager().windowClick_QUICK_MOVE(slot);
		else if(player.getInventory().getEmptySlot() != -1)
		{
			IMC.getInteractionManager().windowClick_QUICK_MOVE(
				player.getInventory().selectedSlot + 36);
			IMC.getInteractionManager().windowClick_QUICK_MOVE(slot);
		}else
		{
			IMC.getInteractionManager().windowClick_PICKUP(
				player.getInventory().selectedSlot + 36);
			IMC.getInteractionManager().windowClick_PICKUP(slot);
			IMC.getInteractionManager().windowClick_PICKUP(
				player.getInventory().selectedSlot + 36);
		}
	}
	
	/** 
	 * Returns true if a replanting action has succeeded or is waiting for cooldown or rotation.
	 */
	private boolean tryToReplant(BlockPos pos, Item neededItem)
	{
		ClientPlayerEntity player = MC.player;
		ItemStack heldItem = player.getMainHandStack();
		
		if(IMC.getItemUseCooldown() > 0)
			return true;
		
		if(!heldItem.isEmpty() && heldItem.getItem() == neededItem)
			return placeBlockSimple(pos);
		
		for(int slot = 0; slot < 36; slot++)
		{
			if(slot == player.getInventory().selectedSlot)
				continue;
			
			ItemStack stack = player.getInventory().getStack(slot);
			if(stack.isEmpty() || stack.getItem() != neededItem)
				continue;
			
			setSlot(player, slot);
			
			return true;
		}
		
		return false;
	}
	
	private boolean placeBlockSimple(BlockPos pos)
	{
		Direction side = null;
		Direction[] sides = Direction.values();
		
		Vec3d eyesPos = RotationUtils.getEyesPos();
		Vec3d posVec = Vec3d.ofCenter(pos);
		double distanceSqPosVec = eyesPos.squaredDistanceTo(posVec);
		
		Vec3d[] hitVecs = new Vec3d[sides.length];
		for(int i = 0; i < sides.length; i++)
			hitVecs[i] =
				posVec.add(Vec3d.of(sides[i].getVector()).multiply(0.5));
		
		for(int i = 0; i < sides.length; i++)
		{
			// check if neighbor can be right clicked
			BlockPos neighbor = pos.offset(sides[i]);
			if(!BlockUtils.canBeClicked(neighbor))
				continue;
			
			// check line of sight
			BlockState neighborState = BlockUtils.getState(neighbor);
			VoxelShape neighborShape =
				neighborState.getOutlineShape(MC.world, neighbor);
			if(MC.world.raycastBlock(eyesPos, hitVecs[i], neighbor,
				neighborShape, neighborState) != null)
				continue;
			
			side = sides[i];
			break;
		}
		
		if(side == null)
			for(int i = 0; i < sides.length; i++)
			{
				// check if neighbor can be right clicked
				if(!BlockUtils.canBeClicked(pos.offset(sides[i])))
					continue;
				
				// check if side is facing away from player
				if(distanceSqPosVec > eyesPos.squaredDistanceTo(hitVecs[i]))
					continue;
				
				side = sides[i];
				break;
			}
		
		if(side == null)
			return false;
		
		Vec3d hitVec = hitVecs[side.ordinal()];
		
		// face block
		WURST.getRotationFaker().faceVectorPacket(hitVec);
		if(RotationUtils.getAngleToLastReportedLookVec(hitVec) > 1)
			return true;
		
		// place block
		IMC.getInteractionManager().rightClickBlock(pos.offset(side),
			side.getOpposite(), hitVec);
		
		// swing arm
		MC.player.networkHandler
			.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
		
		// reset timer
		IMC.setItemUseCooldown(4);
		
		return true;
	}
	
	private boolean harvest(List<BlockPos> blocksToHarvest)
	{
		if(MC.player.getAbilities().creativeMode)
		{
			Stream<BlockPos> stream3 = blocksToHarvest.parallelStream();
			for(Set<BlockPos> set : prevBlocks)
				stream3 = stream3.filter(pos -> !set.contains(pos));
			List<BlockPos> blocksToHarvest2 =
				stream3.collect(Collectors.toList());
			
			prevBlocks.addLast(new HashSet<>(blocksToHarvest2));
			while(prevBlocks.size() > 5)
				prevBlocks.removeFirst();
			
			if(!blocksToHarvest2.isEmpty())
				currentBlock = blocksToHarvest2.get(0);
			
			MC.interactionManager.cancelBlockBreaking();
			progress = 1;
			prevProgress = 1;
			BlockBreaker.breakBlocksWithPacketSpam(blocksToHarvest2, checkLOS.isChecked());
			return !blocksToHarvest2.isEmpty();
		}
		
		ClientPlayerEntity player = MC.player;
		for(BlockPos pos : blocksToHarvest)
		{
			ItemStack held = player.getMainHandStack();
			boolean chooseSilkTouch = silkTouch.isChecked() && BlockUtils.getBlock(pos) == Blocks.MELON;
			if(chooseSilkTouch)
			{
				// prioritize silk touch axes
				boolean silkTouch = EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, held) > 0;
				boolean axe = held.getItem() instanceof AxeItem;
				if(!silkTouch || !axe)
				{
					int choose = -1;
					for(int slot = 0; slot < 36; slot++)
					{
						if(slot == player.getInventory().selectedSlot)
							continue;
	
						ItemStack stack = player.getInventory().getStack(slot);
						
						boolean curSilkTouch = EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, stack) > 0;
						boolean curAxe = stack.getItem() instanceof AxeItem;
						if((!silkTouch && curSilkTouch) || (!axe && curSilkTouch && curAxe))
						{
							choose = slot;
							silkTouch = curSilkTouch;
							axe = curAxe;
						}
					}
					if(choose != -1)
						setSlot(player, choose);
				}
			}
			if(!chooseSilkTouch && fortune.isChecked() && fortuneBlocks.contains(BlockUtils.getBlock(pos)))
			{
				int level = EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, held) > 0 ?
					0 : EnchantmentHelper.getLevel(Enchantments.FORTUNE, held);
				if(level == 0)
				{
					//prioritize fortune tools
					int choose = -1;
					for(int slot = 0; slot < 36; slot++)
					{
						if(slot == player.getInventory().selectedSlot)
							continue;
	
						ItemStack stack = player.getInventory().getStack(slot);
						
						int curLevel = EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, stack) > 0 ?
							0 : EnchantmentHelper.getLevel(Enchantments.FORTUNE, stack);
						if(curLevel > level)
						{
							choose = slot;
							level = curLevel;
						}
					}
					if(choose != -1)
						setSlot(player, choose);
				}
			}
			if(BlockBreaker.breakOneBlock(pos, checkLOS.isChecked()))
			{
				currentBlock = pos;
				break;
			}
		}
		
		if(currentBlock == null)
			MC.interactionManager.cancelBlockBreaking();
		
		if(currentBlock != null && BlockUtils.getHardness(currentBlock) < 1)
		{
			prevProgress = progress;
			progress = IMC.getInteractionManager().getCurrentBreakingProgress();
			
			if(progress < prevProgress)
				prevProgress = progress;
			
		}else
		{
			progress = 1;
			prevProgress = 1;
		}
		
		return currentBlock != null;
	}
	
	private void updateVertexBuffers(List<BlockPos> blocksToHarvest,
		List<BlockPos> blocksToReplant)
	{
		if(WurstClient.MC.getBlockEntityRenderDispatcher().camera == null)
			return;
		
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		
		BlockPos camPos = RenderUtils.getCameraBlockPos();
		int regionX = (camPos.getX() >> 9) * 512;
		int regionZ = (camPos.getZ() >> 9) * 512;
		
		if(greenBuffer != null)
			greenBuffer.close();
		
		greenBuffer = new VertexBuffer();
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		
		double boxMin = 1 / 16.0;
		double boxMax = 15 / 16.0;
		Box box = new Box(boxMin, boxMin, boxMin, boxMax, boxMax, boxMax);
		
		for(BlockPos pos : blocksToHarvest)
		{
			Box renderBox = box.offset(pos).offset(-regionX, 0, -regionZ);
			RenderUtils.drawOutlinedBox(renderBox, bufferBuilder);
		}
		
		BuiltBuffer buffer = bufferBuilder.end();
		greenBuffer.bind();
		greenBuffer.upload(buffer);
		VertexBuffer.unbind();
		
		if(cyanBuffer != null)
			cyanBuffer.close();
		
		cyanBuffer = new VertexBuffer();
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		
		Box node = new Box(0.25, 0.25, 0.25, 0.75, 0.75, 0.75);
		
		for(BlockPos pos : plants.keySet())
		{
			Box renderNode = node.offset(pos).offset(-regionX, 0, -regionZ);
			RenderUtils.drawNode(renderNode, bufferBuilder);
		}
		
		buffer = bufferBuilder.end();
		cyanBuffer.bind();
		cyanBuffer.upload(buffer);
		VertexBuffer.unbind();
		
		if(redBuffer != null)
			redBuffer.close();
		
		redBuffer = new VertexBuffer();
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		
		for(BlockPos pos : blocksToReplant)
		{
			Box renderBox = box.offset(pos).offset(-regionX, 0, -regionZ);
			RenderUtils.drawOutlinedBox(renderBox, bufferBuilder);
		}
		
		buffer = bufferBuilder.end();
		redBuffer.bind();
		redBuffer.upload(buffer);
		VertexBuffer.unbind();
	}
	
	/**
	 * Returns true if AutoFarm is currently harvesting or replanting something.
	 */
	public boolean isBusy()
	{
		return busy;
	}
}
