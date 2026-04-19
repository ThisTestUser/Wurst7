/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.IntStream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.text.WText;

@SearchTags({"LavaFill", "WaterFill"})
public class LiquidFillHack extends Hack implements UpdateListener
{
	private final CheckboxSetting lava = new CheckboxSetting("Fill lava", true);
	
	private final CheckboxSetting water =
		new CheckboxSetting("Fill water", false);
	
	private final CheckboxSetting sourceOnly =
		new CheckboxSetting("Fill source blocks only", true);
	
	private final SliderSetting range = new SliderSetting("Placement Range",
		"The range to search for placement locations with automatic mode enabled.",
		4, 1, 6, 0.25, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting liquids =
		new CheckboxSetting("Place on liquids",
			"Allow placement against liquids, not just solid blocks.", true);
	
	private final SliderSetting times = new SliderSetting(
		"Placements Per Interval", "Amount of times to place per interval.", 1,
		1, 20, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting minPlaceDelay =
		new SliderSetting("Minimum Placement Delay",
			"After placing a block in a location, how long in MS to wait"
				+ " before using that location to place another block.",
			0, 0, 2000, 10, ValueDisplay.INTEGER);
	
	private final SliderSetting delay = new SliderSetting("Placement Delay",
		"Delay in MS between block placements.", 300, 0, 2000, 50,
		ValueDisplay.INTEGER);
	
	private final CheckboxSetting topCheck = new CheckboxSetting("Top Check",
		"Only place on non-source liquids if it is the top block.", false);
	
	private ItemListSetting blacklist = new ItemListSetting("Blacklist",
		"Items in this category will not be used for filling.");
	
	private ItemListSetting whitelist = new ItemListSetting("Whitelist",
		"Items in this category will be used for filling.\n"
			+ "This is used for including items that are not blocks.");
	
	private final CheckboxSetting refill = new CheckboxSetting("Refill",
		"Automatically moves blocks of the same type"
			+ " to your hand when your held stack is used up.",
		false);
	
	private final SliderSetting refillDelay = new SliderSetting("Refill Delay",
		"Delay in ticks before replenishing your held block.", 4, 1, 20, 1,
		ValueDisplay.INTEGER);
	
	private final SwingHandSetting swingHand = new SwingHandSetting(
		WText.literal(
			"How LiquidFill should swing your hand when placing blocks."),
		SwingHand.CLIENT);
	
	private Item refillItem;
	private int refillTimer;
	private boolean unsneak;
	
	private Map<BlockPos, Long> placements = new HashMap<>();
	private long lastPlacement;
	
	public LiquidFillHack()
	{
		super("LiquidFill");
		setCategory(Category.BLOCKS);
		addSetting(lava);
		addSetting(water);
		addSetting(sourceOnly);
		
		addSetting(range);
		addSetting(liquids);
		addSetting(times);
		addSetting(minPlaceDelay);
		addSetting(delay);
		addSetting(topCheck);
		
		addSetting(blacklist);
		addSetting(whitelist);
		
		addSetting(refill);
		addSetting(refillDelay);
		addSetting(swingHand);
	}
	
	@Override
	protected void onEnable()
	{
		refillItem = null;
		refillTimer = 0;
		unsneak = false;
		placements.clear();
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		if(refillTimer > 0)
		{
			refillTimer--;
			if(refillTimer == 0)
				refillStack();
			return;
		}
		
		if(System.currentTimeMillis() < lastPlacement + delay.getValue()
			|| !isCorrectItem(MC.player.getMainHandItem()))
			return;
		
		Iterator<Entry<BlockPos, Long>> iterator =
			placements.entrySet().iterator();
		long removeThres =
			System.currentTimeMillis() - (long)minPlaceDelay.getValue();
		while(iterator.hasNext())
		{
			Entry<BlockPos, Long> entry = iterator.next();
			if(entry.getValue() > removeThres)
				continue;
			iterator.remove();
		}
		
		int placements = 0;
		while(placements < times.getValueI()
			&& !MC.player.getMainHandItem().isEmpty())
		{
			if(checkSurroundingLiquids(true))
			{
				placements++;
				continue;
			}
			
			if(!sourceOnly.isChecked() && checkSurroundingLiquids(false))
			{
				placements++;
				continue;
			}
			break;
		}
		
		if(placements > 0)
			lastPlacement = System.currentTimeMillis();
	}
	
	private void refillStack()
	{
		Inventory inventory = MC.player.getInventory();
		IntStream stream = IntStream.range(0, 36);
		stream = IntStream.concat(stream, IntStream.of(40));
		
		for(int slot : stream.toArray())
		{
			ItemStack stack = inventory.getItem(slot);
			if(stack.isEmpty() || stack.getItem() != refillItem)
				continue;
			
			if(slot < 9)
				inventory.setSelectedSlot(slot);
			else
			{
				IClientPlayerInteractionManager im =
					IMC.getInteractionManager();
				im.windowClick_SWAP(InventoryUtils.toNetworkSlot(slot),
					inventory.getSelectedSlot());
			}
			
			break;
		}
	}
	
	private boolean checkSurroundingLiquids(boolean sources)
	{
		BlockPos playerPos = BlockPos.containing(MC.player.position());
		int searchRange = (int)(range.getValueI() + 2);
		for(int y = playerPos.getY() + searchRange; y >= playerPos.getY()
			- searchRange; y--)
			for(int x = 0; x <= range.getValueI() * 2 + 4; x++)
				for(int z = 0; z <= range.getValueI() * 2 + 4; z++)
				{
					int realX =
						playerPos.getX() + (x % 2 == 1 ? (-x - 1) / 2 : x / 2);
					int realZ =
						playerPos.getZ() + (z % 2 == 1 ? (-z - 1) / 2 : z / 2);
					BlockPos pos = new BlockPos(realX, y, realZ);
					if(MC.player.getBoundingBox().intersects(new AABB(pos)))
						continue;
					
					FluidState state = MC.level.getFluidState(pos);
					if(state.isEmpty())
						continue;
					
					boolean isWater = state.is(Fluids.WATER)
						|| state.is(Fluids.FLOWING_WATER);
					if((isWater && !water.isChecked())
						|| (!isWater && !lava.isChecked()))
						continue;
					if(sources && !state.isSource())
						continue;
					if(!sources && state.isSource())
						continue;
					
					if(topCheck.isChecked() && !sources)
					{
						FluidState topState =
							MC.level.getFluidState(pos.above());
						if(!topState.isEmpty()
							&& topState.getType().isSame(state.getType()))
							continue;
					}
					
					if(placeBlock(pos))
						return true;
				}
		return false;
	}
	
	private boolean placeBlock(BlockPos pos)
	{
		Vec3 eyesPos = RotationUtils.getEyesPos();
		Vec3 posVec = Vec3.atCenterOf(pos);
		double rangeSq = Math.pow(range.getValue(), 2);
		
		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			
			if(minPlaceDelay.getValue() > 0 && placements.containsKey(neighbor))
				continue;
			
			// check if neighbor can be right clicked
			boolean allowLiquidPlace = liquids.isChecked() && (BlockUtils
				.getState(neighbor).getBlock() instanceof LiquidBlock
				|| BlockUtils.getState(neighbor)
					.getBlock() instanceof AirBlock);
			if(!allowLiquidPlace && (!BlockUtils.canBeClicked(neighbor)
				|| BlockUtils.getState(neighbor).canBeReplaced()))
				continue;
			
			// fix for liquid placements
			if(allowLiquidPlace)
				neighbor = pos;
			
			Vec3 dirVec = Vec3.atLowerCornerOf(side.getUnitVec3i());
			Vec3 hitVec = posVec.add(dirVec.scale(0.5));
			
			// check if hitVec is within range
			if(eyesPos.distanceToSqr(hitVec) > rangeSq)
				continue;
			
			// sneak
			if(isInteractable(BlockUtils.getBlock(neighbor))
				&& !MC.player.isShiftKeyDown())
			{
				Input playerInput = MC.player.input.keyPresses;
				Input input =
					new Input(playerInput.forward(), playerInput.backward(),
						playerInput.left(), playerInput.right(),
						playerInput.jump(), true, playerInput.sprint());
				
				MC.player.connection
					.send(new ServerboundPlayerInputPacket(input));
				unsneak = true;
			}
			
			refillItem = MC.player.getMainHandItem().getItem();
			
			// place block
			IMC.getInteractionManager().rightClickBlock(neighbor,
				side.getOpposite(), hitVec);
			swingHand.swing(InteractionHand.MAIN_HAND);
			
			// unsneak
			if(unsneak)
			{
				Input playerInput = MC.player.input.keyPresses;
				Input input =
					new Input(playerInput.forward(), playerInput.backward(),
						playerInput.left(), playerInput.right(),
						playerInput.jump(), false, playerInput.sprint());
				
				MC.player.connection
					.send(new ServerboundPlayerInputPacket(input));
				unsneak = false;
			}
			
			if(MC.player.getMainHandItem().isEmpty() && refill.isChecked())
				refillTimer = refillDelay.getValueI();
			
			if(minPlaceDelay.getValue() > 0)
				placements.put(pos, System.currentTimeMillis());
			return true;
		}
		return false;
	}
	
	private boolean isInteractable(Block block)
	{
		// all blocks here have InteractionResult.CONSUME or
		// InteractionResult.SUCCESS
		return block instanceof AbstractFurnaceBlock
			|| block instanceof AnvilBlock || block instanceof BarrelBlock
			|| block instanceof BeaconBlock || block instanceof BedBlock
			|| block instanceof BellBlock || block instanceof BrewingStandBlock
			|| block instanceof ButtonBlock || block instanceof CakeBlock
			|| block instanceof CandleBlock || block instanceof CandleCakeBlock
			|| block instanceof CartographyTableBlock
			|| block instanceof CaveVines || block instanceof ChestBlock
			|| block instanceof ChiseledBookShelfBlock
			|| block instanceof CommandBlock || block instanceof ComparatorBlock
			|| block instanceof ComposterBlock
			|| block instanceof CopperGolemStatueBlock
			|| block instanceof CrafterBlock
			|| block instanceof CraftingTableBlock
			|| block instanceof DaylightDetectorBlock
			|| block instanceof DecoratedPotBlock
			|| block instanceof DispenserBlock || block instanceof DoorBlock
			|| block instanceof DragonEggBlock
			|| block instanceof EnchantingTableBlock
			|| block instanceof EnderChestBlock
			|| block instanceof FenceGateBlock
			|| block instanceof FlowerPotBlock
			|| block instanceof GrindstoneBlock || block instanceof HopperBlock
			|| block instanceof JigsawBlock || block instanceof JukeboxBlock
			|| block instanceof LecternBlock || block instanceof LeverBlock
			|| block instanceof LightBlock || block instanceof LoomBlock
			|| block instanceof MovingPistonBlock || block instanceof NoteBlock
			|| block instanceof RepeaterBlock
			|| block instanceof RespawnAnchorBlock
			|| block instanceof ShelfBlock || block instanceof ShulkerBoxBlock
			|| block instanceof SignBlock || block instanceof SmithingTableBlock
			|| block instanceof StonecutterBlock
			|| block instanceof StructureBlock
			|| block instanceof SweetBerryBushBlock
			|| block instanceof TrapDoorBlock || block instanceof VaultBlock;
	}
	
	private boolean isCorrectItem(ItemStack stack)
	{
		String itemName =
			BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		if(blacklist.getItemNames().contains(itemName))
			return false;
		if(whitelist.getItemNames().contains(itemName))
			return true;
		return stack.getItem() instanceof BlockItem;
	}
}
