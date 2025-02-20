/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
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

import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
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
		"How LiquidFill should swing your hand when placing blocks.",
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
			|| !isCorrectItem(MC.player.getMainHandStack()))
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
			&& !MC.player.getMainHandStack().isEmpty())
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
		PlayerInventory inventory = MC.player.getInventory();
		IntStream stream = IntStream.range(0, 36);
		stream = IntStream.concat(stream, IntStream.of(40));
		
		for(int slot : stream.toArray())
		{
			ItemStack stack = inventory.getStack(slot);
			if(stack.isEmpty() || stack.getItem() != refillItem)
				continue;
			
			if(slot < 9)
				inventory.selectedSlot = slot;
			else
			{
				IClientPlayerInteractionManager im =
					IMC.getInteractionManager();
				im.windowClick_SWAP(InventoryUtils.toNetworkSlot(slot),
					inventory.selectedSlot);
			}
			
			break;
		}
	}
	
	private boolean checkSurroundingLiquids(boolean sources)
	{
		BlockPos playerPos = BlockPos.ofFloored(MC.player.getPos());
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
					if(MC.player.getBoundingBox().intersects(new Box(pos)))
						continue;
					
					FluidState state = MC.world.getFluidState(pos);
					if(state.isEmpty())
						continue;
					
					boolean isWater = state.isOf(Fluids.WATER)
						|| state.isOf(Fluids.FLOWING_WATER);
					if((isWater && !water.isChecked())
						|| (!isWater && !lava.isChecked()))
						continue;
					if(sources && !state.isStill())
						continue;
					if(!sources && state.isStill())
						continue;
					
					if(topCheck.isChecked() && !sources)
					{
						FluidState topState = MC.world.getFluidState(pos.up());
						if(!topState.isEmpty() && topState.getFluid()
							.matchesType(state.getFluid()))
							continue;
					}
					
					if(placeBlock(pos))
						return true;
				}
		return false;
	}
	
	private boolean placeBlock(BlockPos pos)
	{
		Vec3d eyesPos = RotationUtils.getEyesPos();
		Vec3d posVec = Vec3d.ofCenter(pos);
		double rangeSq = Math.pow(range.getValue(), 2);
		
		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.offset(side);
			
			if(minPlaceDelay.getValue() > 0 && placements.containsKey(neighbor))
				continue;
			
			// check if neighbor can be right clicked
			boolean allowLiquidPlace = liquids.isChecked() && (BlockUtils
				.getState(neighbor).getBlock() instanceof FluidBlock
				|| BlockUtils.getState(neighbor)
					.getBlock() instanceof AirBlock);
			if(!allowLiquidPlace && (!BlockUtils.canBeClicked(neighbor)
				|| BlockUtils.getState(neighbor).isReplaceable()))
				continue;
			
			// fix for liquid placements
			if(allowLiquidPlace)
				neighbor = pos;
			
			Vec3d dirVec = Vec3d.of(side.getVector());
			Vec3d hitVec = posVec.add(dirVec.multiply(0.5));
			
			// check if hitVec is within range
			if(eyesPos.squaredDistanceTo(hitVec) > rangeSq)
				continue;
			
			// sneak
			if(isInteractable(BlockUtils.getBlock(neighbor))
				&& !MC.player.isSneaking())
			{
				MC.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
					MC.player, Mode.PRESS_SHIFT_KEY));
				unsneak = true;
			}
			
			refillItem = MC.player.getMainHandStack().getItem();
			
			// place block
			IMC.getInteractionManager().rightClickBlock(neighbor,
				side.getOpposite(), hitVec);
			swingHand.swing(Hand.MAIN_HAND);
			
			// unsneak
			if(unsneak)
			{
				MC.player.networkHandler.sendPacket(new ClientCommandC2SPacket(
					MC.player, Mode.RELEASE_SHIFT_KEY));
				unsneak = false;
			}
			
			if(MC.player.getMainHandStack().isEmpty() && refill.isChecked())
				refillTimer = refillDelay.getValueI();
			
			if(minPlaceDelay.getValue() > 0)
				placements.put(pos, System.currentTimeMillis());
			return true;
		}
		return false;
	}
	
	private boolean isInteractable(Block block)
	{
		// all blocks here have ActionResult.CONSUME or ActionResult.success
		return block instanceof AbstractFurnaceBlock
			|| block instanceof AnvilBlock || block instanceof BarrelBlock
			|| block instanceof BeaconBlock || block instanceof BedBlock
			|| block instanceof BellBlock || block instanceof BrewingStandBlock
			|| block instanceof ButtonBlock || block instanceof CakeBlock
			|| block instanceof CandleBlock || block instanceof CandleCakeBlock
			|| block instanceof CartographyTableBlock
			|| block instanceof CaveVines || block instanceof ChestBlock
			|| block instanceof ChiseledBookshelfBlock
			|| block instanceof CommandBlock || block instanceof ComparatorBlock
			|| block instanceof ComposterBlock || block instanceof CrafterBlock
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
			|| block instanceof NoteBlock || block instanceof RepeaterBlock
			|| block instanceof RespawnAnchorBlock
			|| block instanceof ShulkerBoxBlock
			|| block instanceof SmithingTableBlock
			|| block instanceof StonecutterBlock
			|| block instanceof StructureBlock
			|| block instanceof SweetBerryBushBlock
			|| block instanceof TrapdoorBlock || block instanceof VaultBlock
			|| block instanceof PistonExtensionBlock;
	}
	
	private boolean isCorrectItem(ItemStack stack)
	{
		String itemName = Registries.ITEM.getId(stack.getItem()).toString();
		if(blacklist.getItemNames().contains(itemName))
			return false;
		if(whitelist.getItemNames().contains(itemName))
			return true;
		return stack.getItem() instanceof BlockItem;
	}
}
