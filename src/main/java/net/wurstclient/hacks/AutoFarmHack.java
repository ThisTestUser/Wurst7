/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.block.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstClient;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autofarm.AutoFarmRenderer;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockBreakingCache;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.OverlayRenderer;
import net.wurstclient.util.RotationUtils;

@SearchTags({"auto farm", "AutoHarvest", "auto harvest"})
public final class AutoFarmHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting replant =
		new CheckboxSetting("Replant", true);
	
	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"Check line of sight",
		"Makes sure that you don't reach through walls when breaking or replanting.",
		false);
	
	private final CheckboxSetting fortune =
		new CheckboxSetting("Choose fortune tool",
			"Chooses a fortune tool to harvest crops.", false);
	
	private final CheckboxSetting silkTouch = new CheckboxSetting(
		"Choose silk touch tool",
		"Chooses a silk touch tool to harvest melons. Axes will be prioritized.",
		false);
	
	private final BlockListSetting excluded = new BlockListSetting(
		"Excluded Crops", "List of crops that will not be harvested.");
	
	private final HashMap<Block, Item> seeds = new HashMap<>();
	{
		seeds.put(Blocks.WHEAT, Items.WHEAT_SEEDS);
		seeds.put(Blocks.CARROTS, Items.CARROT);
		seeds.put(Blocks.POTATOES, Items.POTATO);
		seeds.put(Blocks.BEETROOTS, Items.BEETROOT_SEEDS);
		seeds.put(Blocks.PUMPKIN_STEM, Items.PUMPKIN_SEEDS);
		seeds.put(Blocks.MELON_STEM, Items.MELON_SEEDS);
		seeds.put(Blocks.NETHER_WART, Items.NETHER_WART);
		seeds.put(Blocks.COCOA, Items.COCOA_BEANS);
	}
	
	private final HashSet<Block> fortuneBlocks = new HashSet<>();
	{
		fortuneBlocks.add(Blocks.WHEAT);
		fortuneBlocks.add(Blocks.CARROTS);
		fortuneBlocks.add(Blocks.POTATOES);
		fortuneBlocks.add(Blocks.BEETROOTS);
		fortuneBlocks.add(Blocks.NETHER_WART);
		fortuneBlocks.add(Blocks.MELON);
	}
	
	private final HashMap<BlockPos, Item> plants = new HashMap<>();
	private final BlockBreakingCache cache = new BlockBreakingCache();
	private BlockPos currentlyHarvesting;
	
	private final AutoFarmRenderer renderer = new AutoFarmRenderer();
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	private boolean busy;
	
	public AutoFarmHack()
	{
		super("AutoFarm");
		
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(replant);
		addSetting(checkLOS);
		addSetting(excluded);
		addSetting(fortune);
		addSetting(silkTouch);
	}
	
	@Override
	protected void onEnable()
	{
		plants.clear();
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		if(currentlyHarvesting != null)
		{
			MC.interactionManager.breakingBlock = true;
			MC.interactionManager.cancelBlockBreaking();
			currentlyHarvesting = null;
		}
		
		cache.reset();
		overlay.resetProgress();
		busy = false;
		
		renderer.reset();
	}
	
	@Override
	public void onUpdate()
	{
		currentlyHarvesting = null;
		Vec3d eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.ofFloored(eyesVec);
		double rangeSq = range.getValueSq();
		int blockRange = range.getValueCeil();
		
		// get nearby, non-empty blocks
		ArrayList<BlockPos> blocks =
			BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
				.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
				.filter(BlockUtils::canBeClicked)
				.collect(Collectors.toCollection(ArrayList::new));
		
		// check for any new plants and add them to the map
		updatePlants(blocks);
		
		ArrayList<BlockPos> blocksToHarvest = new ArrayList<>();
		ArrayList<BlockPos> blocksToReplant = new ArrayList<>();
		
		// don't place or break any blocks while Freecam is enabled
		if(!WURST.getHax().freecamHack.isEnabled())
		{
			// check which of the nearby blocks can be harvested
			blocksToHarvest = getBlocksToHarvest(eyesVec, blocks);
			
			// do a new search to find empty blocks that can be replanted
			if(replant.isChecked())
				blocksToReplant =
					getBlocksToReplant(eyesVec, eyesBlock, rangeSq, blockRange);
		}
		
		// first, try to replant
		boolean replanting = replant(blocksToReplant);
		
		// if we can't replant, harvest instead
		if(!replanting)
			harvest(blocksToHarvest.stream());
		
		// update busy state
		busy = replanting || currentlyHarvesting != null;
		
		// update renderer
		renderer.updateVertexBuffers(blocksToHarvest, plants.keySet(),
			blocksToReplant);
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		renderer.render(matrixStack);
		overlay.render(matrixStack, partialTicks, currentlyHarvesting);
	}
	
	/**
	 * Returns true if AutoFarm is currently harvesting or replanting something.
	 */
	public boolean isBusy()
	{
		return busy;
	}
	
	private void updatePlants(List<BlockPos> blocks)
	{
		for(BlockPos pos : blocks)
		{
			Item seed = seeds.get(BlockUtils.getBlock(pos));
			if(seed == null)
				continue;
			
			plants.put(pos, seed);
		}
	}
	
	private ArrayList<BlockPos> getBlocksToHarvest(Vec3d eyesVec,
		ArrayList<BlockPos> blocks)
	{
		return blocks.parallelStream().filter(this::shouldBeHarvested)
			.sorted(Comparator
				.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private boolean shouldBeHarvested(BlockPos pos)
	{
		Block block = BlockUtils.getBlock(pos);
		BlockState state = BlockUtils.getState(pos);
		
		if(Collections.binarySearch(excluded.getBlockNames(),
			BlockUtils.getName(pos)) >= 0)
			return false;
		
		if(block instanceof CropBlock)
			return ((CropBlock)block).isMature(state);
		
		if(block instanceof NetherWartBlock)
			return state.get(NetherWartBlock.AGE) >= 3;
		
		if(block instanceof CocoaBlock)
			return state.get(CocoaBlock.AGE) >= 2;
		
		if(block == Blocks.PUMPKIN || block == Blocks.MELON)
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
		
		if(block instanceof BambooBlock)
			return BlockUtils.getBlock(pos.down()) instanceof BambooBlock
				&& !(BlockUtils.getBlock(pos.down(2)) instanceof BambooBlock);
		
		return false;
	}
	
	private ArrayList<BlockPos> getBlocksToReplant(Vec3d eyesVec,
		BlockPos eyesBlock, double rangeSq, int blockRange)
	{
		return BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
			.filter(pos -> pos.getSquaredDistance(eyesVec) <= rangeSq)
			.filter(pos -> BlockUtils.getState(pos).isReplaceable())
			.filter(pos -> plants.containsKey(pos)).filter(this::canBeReplanted)
			.sorted(Comparator
				.comparingDouble(pos -> pos.getSquaredDistance(eyesVec)))
			.collect(Collectors.toCollection(ArrayList::new));
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
	
	private boolean replant(List<BlockPos> blocksToReplant)
	{
		// check cooldown
		if(MC.itemUseCooldown > 0)
			return false;
		
		// check if already holding one of the seeds needed for blocksToReplant
		Optional<Item> heldSeed = blocksToReplant.stream().map(plants::get)
			.distinct().filter(item -> MC.player.isHolding(item)).findFirst();
		
		// if so, try to replant the blocks that need that seed
		if(heldSeed.isPresent())
		{
			// get the seed and the hand that is holding it
			Item item = heldSeed.get();
			Hand hand = MC.player.getMainHandStack().isOf(item) ? Hand.MAIN_HAND
				: Hand.OFF_HAND;
			
			// filter out blocks that need a different seed
			ArrayList<BlockPos> blocksToReplantWithHeldSeed =
				blocksToReplant.stream().filter(pos -> plants.get(pos) == item)
					.collect(Collectors.toCollection(ArrayList::new));
			
			for(BlockPos pos : blocksToReplantWithHeldSeed)
			{
				// skip over blocks that we can't reach
				BlockPlacingParams params =
					BlockPlacer.getBlockPlacingParams(pos);
				if(params == null || params.distanceSq() > range.getValueSq())
					continue;
				
				if(checkLOS.isChecked() && !params.lineOfSight())
					continue;
				
				// face block
				WURST.getRotationFaker().faceVectorPacket(params.hitVec());
				
				// place seed
				ActionResult result = MC.interactionManager
					.interactBlock(MC.player, hand, params.toHitResult());
				
				// swing arm
				// Note: All SwingHand types correspond to SwingSource.CLIENT
				if(result instanceof ActionResult.Success success
					&& success.swingSource() == ActionResult.SwingSource.CLIENT)
					SwingHand.SERVER.swing(hand); // intentional use of SERVER
					
				// reset cooldown
				MC.itemUseCooldown = 4;
				return true;
			}
		}
		
		// otherwise, find a block that we can reach and have seeds for
		for(BlockPos pos : blocksToReplant)
		{
			// skip over blocks that we can't reach
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			if(params == null || params.distanceSq() > range.getValueSq())
				continue;
			
			// try to select the seed (returns false if we don't have it)
			Item item = plants.get(pos);
			if(InventoryUtils.selectItem(item))
				return true;
		}
		
		// if we couldn't replant anything, return false
		return false;
	}
	
	private void harvest(Stream<BlockPos> stream)
	{
		// Break all blocks in creative mode
		if(MC.player.getAbilities().creativeMode)
		{
			MC.interactionManager.cancelBlockBreaking();
			overlay.resetProgress();
			
			ArrayList<BlockPos> blocks = cache.filterOutRecentBlocks(stream);
			if(blocks.isEmpty())
				return;
			
			currentlyHarvesting = blocks.get(0);
			BlockBreaker.breakBlocksWithPacketSpam(blocks);
			return;
		}
		
		// Break the first valid block in survival mode
		currentlyHarvesting =
			stream
				.filter(pos -> BlockBreaker.breakOneBlock(pos,
					checkLOS.isChecked(), p -> selectTool(p)))
				.findFirst().orElse(null);
		
		if(currentlyHarvesting == null)
		{
			MC.interactionManager.cancelBlockBreaking();
			overlay.resetProgress();
			return;
		}
		
		overlay.updateProgress();
	}
	
	private boolean selectTool(BlockBreakingParams params)
	{
		BlockPos pos = params.pos();
		boolean findSilkTouch =
			silkTouch.isChecked() && BlockUtils.getBlock(pos) == Blocks.MELON;
		boolean findFortune = fortune.isChecked()
			&& fortuneBlocks.contains(BlockUtils.getBlock(pos));
		
		DynamicRegistryManager drm = WurstClient.MC.world.getRegistryManager();
		Registry<Enchantment> registry =
			drm.getOrThrow(RegistryKeys.ENCHANTMENT);
		Optional<Reference<Enchantment>> silkTouch =
			registry.getOptional(Enchantments.SILK_TOUCH);
		Optional<Reference<Enchantment>> fortune =
			registry.getOptional(Enchantments.FORTUNE);
		
		ItemStack held = MC.player.getMainHandStack();
		if(findSilkTouch)
		{
			if(silkTouch.map(entry -> EnchantmentHelper.getLevel(entry, held))
				.orElse(0) == 0 || !(held.getItem() instanceof AxeItem))
			{
				int slot = InventoryUtils.indexOf(
					stack -> stack.getItem() instanceof AxeItem && silkTouch
						.map(entry -> EnchantmentHelper.getLevel(entry, stack))
						.orElse(0) > 0);
				if(slot == -1)
					slot = InventoryUtils.indexOf(stack -> silkTouch
						.map(entry -> EnchantmentHelper.getLevel(entry, stack))
						.orElse(0) > 0);
				return InventoryUtils.selectItem(slot);
			}
		}else if(findFortune)
		{
			int[] slots = InventoryUtils.indicesOf(stack -> silkTouch
				.map(entry -> EnchantmentHelper.getLevel(entry, stack))
				.orElse(0) == 0
				&& fortune
					.map(entry -> EnchantmentHelper.getLevel(entry, stack))
					.orElse(0) > 0,
				36, false);
			
			int selected = -1;
			int level =
				silkTouch.map(entry -> EnchantmentHelper.getLevel(entry, held))
					.orElse(0) > 0
						? 0
						: fortune.map(
							entry -> EnchantmentHelper.getLevel(entry, held))
							.orElse(0);
			for(int slot : slots)
			{
				int curLevel =
					fortune.map(entry -> EnchantmentHelper.getLevel(entry,
						MC.player.getInventory().getStack(slot))).orElse(0);
				if(curLevel > level)
				{
					selected = slot;
					level = curLevel;
				}
			}
			return InventoryUtils.selectItem(selected);
		}
		
		return false;
	}
}
