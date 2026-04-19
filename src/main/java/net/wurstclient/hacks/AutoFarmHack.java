/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autofarm.AutoFarmPlantType;
import net.wurstclient.hacks.autofarm.AutoFarmPlantTypeManager;
import net.wurstclient.hacks.autofarm.AutoFarmRenderer;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FaceTargetSetting;
import net.wurstclient.settings.FaceTargetSetting.FaceTarget;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.*;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;

@SearchTags({"auto farm", "AutoHarvest", "auto harvest"})
public final class AutoFarmHack extends Hack
	implements UpdateListener, RenderListener
{
	private final SliderSetting range =
		new SliderSetting("Range", 5, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting checkLOS =
		new CheckboxSetting("Check line of sight",
			"description.wurst.setting.autofarm.check_line_of_sight", false);
	
	private final FaceTargetSetting faceTarget =
		FaceTargetSetting.withoutPacketSpam(this, FaceTarget.SERVER);
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.SERVER);
	
	private final CheckboxSetting fortune =
		new CheckboxSetting("Choose fortune tool",
			"Chooses a fortune tool to harvest crops.", false);
	
	private final CheckboxSetting silkTouch = new CheckboxSetting(
		"Choose silk touch tool",
		"Chooses a silk touch tool to harvest melons. Axes will be prioritized.",
		false);
	
	private final AutoFarmPlantTypeManager plantTypes =
		new AutoFarmPlantTypeManager();
	
	private final HashMap<BlockPos, AutoFarmPlantType> replantingSpots =
		new HashMap<>();
	private final BlockBreakingCache cache = new BlockBreakingCache();
	private BlockPos currentlyMining;
	
	private final AutoFarmRenderer renderer = new AutoFarmRenderer();
	private final OverlayRenderer overlay = new OverlayRenderer();
	
	private final HashSet<Block> fortuneBlocks =
		new HashSet<>(List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES,
			Blocks.BEETROOTS, Blocks.NETHER_WART, Blocks.MELON));
	
	private boolean busy;
	
	public AutoFarmHack()
	{
		super("AutoFarm");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(checkLOS);
		addSetting(faceTarget);
		addSetting(swingHand);
		addSetting(fortune);
		addSetting(silkTouch);
		renderer.getSettings().forEach(this::addSetting);
		plantTypes.getSettings().forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().autoMineHack.setEnabled(false);
		replantingSpots.clear();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		if(currentlyMining != null)
		{
			MC.gameMode.isDestroying = true;
			MC.gameMode.stopDestroyBlock();
			currentlyMining = null;
		}
		
		cache.reset();
		overlay.resetProgress();
		busy = false;
	}
	
	@Override
	public void onUpdate()
	{
		currentlyMining = null;
		Vec3 eyesVec = RotationUtils.getEyesPos();
		BlockPos eyesBlock = BlockPos.containing(eyesVec);
		double rangeSq = range.getValueSq();
		int blockRange = range.getValueCeil();
		
		List<BlockPos> nonEmptyBlocks =
			BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
				.filter(pos -> pos.distToCenterSqr(eyesVec) <= rangeSq)
				.filter(BlockUtils::canBeClicked).toList();
		
		for(BlockPos pos : nonEmptyBlocks)
		{
			AutoFarmPlantType plantType = plantTypes.getReplantingSpotType(pos);
			if(plantType != null)
				replantingSpots.put(pos, plantType);
		}
		
		List<BlockPos> blocksToMine = List.of();
		List<BlockPos> blocksToInteract = List.of();
		List<BlockPos> blocksToReplant = List.of();
		
		blocksToMine = nonEmptyBlocks.stream()
			.filter(plantTypes::shouldHarvestByMining)
			.sorted(
				Comparator.comparingDouble(pos -> pos.distToCenterSqr(eyesVec)))
			.toList();
		
		blocksToInteract = nonEmptyBlocks.stream()
			.filter(plantTypes::shouldHarvestByInteracting)
			.sorted(
				Comparator.comparingDouble(pos -> pos.distToCenterSqr(eyesVec)))
			.toList();
		
		blocksToReplant = BlockUtils.getAllInBoxStream(eyesBlock, blockRange)
			.filter(pos -> pos.distToCenterSqr(eyesVec) <= rangeSq)
			.filter(pos -> BlockUtils.getState(pos).canBeReplaced())
			.filter(pos -> {
				AutoFarmPlantType plantType = replantingSpots.get(pos);
				return plantType != null && plantType.isReplantingEnabled()
					&& plantType.hasPlantingSurface(pos);
			})
			.sorted(
				Comparator.comparingDouble(pos -> pos.distToCenterSqr(eyesVec)))
			.toList();
		
		boolean replanting = replant(blocksToReplant);
		boolean interacting =
			replanting ? false : harvestByInteracting(blocksToInteract);
		if(!interacting && !replanting)
			harvestByMining(blocksToMine);
		
		busy = replanting || interacting || currentlyMining != null;
		
		List<BlockPos> blocksToHarvest = Stream
			.of(blocksToMine, blocksToInteract).flatMap(List::stream).toList();
		renderer.update(replantingSpots.keySet(), blocksToHarvest,
			blocksToReplant);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		renderer.render(matrixStack);
		
		if(renderer.drawBlocksToHarvest.isChecked())
			overlay.render(matrixStack, partialTicks, currentlyMining);
	}
	
	/**
	 * Returns true if AutoFarm is currently harvesting or replanting something.
	 */
	public boolean isBusy()
	{
		return busy;
	}
	
	private boolean replant(List<BlockPos> blocksToReplant)
	{
		if(MC.rightClickDelay > 0)
			return false;
		
		if(MC.gameMode.isDestroying() || MC.player.isHandsBusy())
			return false;
		
		Optional<Item> heldSeed =
			blocksToReplant.stream().map(replantingSpots::get)
				.filter(Objects::nonNull).map(AutoFarmPlantType::getSeedItem)
				.distinct().filter(MC.player::isHolding).findFirst();
		
		if(heldSeed.isPresent())
		{
			Item item = heldSeed.get();
			InteractionHand hand = MC.player.getMainHandItem().is(item)
				? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
			
			for(BlockPos pos : blocksToReplant)
			{
				AutoFarmPlantType plantType = replantingSpots.get(pos);
				if(plantType == null || plantType.getSeedItem() != item)
					continue;
				
				BlockPlacingParams params =
					BlockPlacer.getBlockPlacingParams(pos);
				if(params == null || params.distanceSq() > range.getValueSq()
					|| params.requiresSneaking())
					continue;
				
				if(checkLOS.isChecked() && !params.lineOfSight())
					continue;
				
				MC.rightClickDelay = 4;
				faceTarget.face(params.hitVec());
				InteractionSimulator.rightClickBlock(params.toHitResult(), hand,
					swingHand.getSelected());
				return true;
			}
		}
		
		for(BlockPos pos : blocksToReplant)
		{
			BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(pos);
			if(params == null || params.distanceSq() > range.getValueSq()
				|| params.requiresSneaking())
				continue;
			
			AutoFarmPlantType plantType = replantingSpots.get(pos);
			if(plantType == null)
				continue;
			
			if(InventoryUtils.selectItem(plantType.getSeedItem())
				|| InventoryUtils.giveCreativeItem(plantType.getSeedItem()))
				return true;
		}
		
		return false;
	}
	
	private boolean harvestByInteracting(List<BlockPos> blocksToInteract)
	{
		if(MC.rightClickDelay > 0)
			return false;
		
		if(MC.gameMode.isDestroying() || MC.player.isHandsBusy())
			return false;
		
		for(BlockPos pos : blocksToInteract)
		{
			BlockBreakingParams params =
				BlockBreaker.getBlockBreakingParams(pos);
			if(params == null || params.distanceSq() > range.getValueSq())
				continue;
			
			if(checkLOS.isChecked() && !params.lineOfSight())
				continue;
			
			if(MC.player.getMainHandItem().is(Items.BONE_MEAL))
				return InventoryUtils.selectItem(s -> !s.is(Items.BONE_MEAL));
			
			MC.rightClickDelay = 4;
			faceTarget.face(params.hitVec());
			InteractionSimulator.rightClickBlock(params.toHitResult(),
				swingHand.getSelected());
			return true;
		}
		
		return false;
	}
	
	private void harvestByMining(List<BlockPos> blocksToMine)
	{
		double rangeSq = range.getValueSq();
		Stream<BlockBreakingParams> stream = blocksToMine.stream()
			.map(BlockBreaker::getBlockBreakingParams).filter(Objects::nonNull)
			.filter(params -> params.distanceSq() <= rangeSq);
		
		if(checkLOS.isChecked())
			stream = stream.filter(BlockBreakingParams::lineOfSight);
		
		stream = stream.sorted(BlockBreaker.comparingParams());
		
		// Break all blocks in creative mode
		if(MC.player.getAbilities().instabuild
			&& faceTarget.getSelected() == FaceTarget.OFF)
		{
			MC.gameMode.stopDestroyBlock();
			overlay.resetProgress();
			
			List<BlockPos> blocks = cache
				.filterOutRecentBlocks(stream.map(BlockBreakingParams::pos));
			if(blocks.isEmpty())
				return;
			
			currentlyMining = blocks.get(0);
			BlockBreaker.breakBlocksWithPacketSpam(blocks);
			swingHand.swing(InteractionHand.MAIN_HAND);
			return;
		}
		
		// Break the first valid block in survival mode
		currentlyMining = stream.filter(this::breakOneBlock)
			.map(BlockBreakingParams::pos).findFirst().orElse(null);
		
		if(currentlyMining == null)
		{
			MC.gameMode.stopDestroyBlock();
			overlay.resetProgress();
			return;
		}
		
		overlay.updateProgress();
	}
	
	private boolean breakOneBlock(BlockBreakingParams params)
	{
		faceTarget.face(params.hitVec());
		selectTool(params);
		
		if(!MC.gameMode.continueDestroyBlock(params.pos(), params.side()))
			return false;
		
		swingHand.swing(InteractionHand.MAIN_HAND);
		return true;
	}
	
	private boolean selectTool(BlockBreakingParams params)
	{
		BlockPos pos = params.pos();
		boolean findSilkTouch =
			silkTouch.isChecked() && BlockUtils.getBlock(pos) == Blocks.MELON;
		boolean findFortune = fortune.isChecked()
			&& fortuneBlocks.contains(BlockUtils.getBlock(pos));
		
		RegistryAccess drm = MC.level.registryAccess();
		Registry<Enchantment> registry =
			drm.lookupOrThrow(Registries.ENCHANTMENT);
		Optional<Reference<Enchantment>> silkTouch =
			registry.get(Enchantments.SILK_TOUCH);
		Optional<Reference<Enchantment>> fortune =
			registry.get(Enchantments.FORTUNE);
		
		ItemStack held = MC.player.getMainHandItem();
		if(findSilkTouch)
		{
			if(silkTouch.map(
				entry -> EnchantmentHelper.getItemEnchantmentLevel(entry, held))
				.orElse(0) == 0 || !(held.getItem() instanceof AxeItem))
			{
				int slot = InventoryUtils.indexOf(
					stack -> stack.getItem() instanceof AxeItem && silkTouch
						.map(entry -> EnchantmentHelper
							.getItemEnchantmentLevel(entry, stack))
						.orElse(0) > 0);
				if(slot == -1)
					slot = InventoryUtils.indexOf(stack -> silkTouch
						.map(entry -> EnchantmentHelper
							.getItemEnchantmentLevel(entry, stack))
						.orElse(0) > 0);
				return InventoryUtils.selectItem(slot);
			}
		}else if(findFortune)
		{
			int[] slots =
				InventoryUtils.indicesOf(stack -> silkTouch
					.map(entry -> EnchantmentHelper
						.getItemEnchantmentLevel(entry, stack))
					.orElse(0) == 0
					&& fortune
						.map(entry -> EnchantmentHelper
							.getItemEnchantmentLevel(entry, stack))
						.orElse(0) > 0,
					36, false);
			
			int selected = -1;
			int level = silkTouch.map(
				entry -> EnchantmentHelper.getItemEnchantmentLevel(entry, held))
				.orElse(0) > 0 ? 0
					: fortune.map(entry -> EnchantmentHelper
						.getItemEnchantmentLevel(entry, held)).orElse(0);
			for(int slot : slots)
			{
				int curLevel = fortune
					.map(entry -> EnchantmentHelper.getItemEnchantmentLevel(
						entry, MC.player.getInventory().getItem(slot)))
					.orElse(0);
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
