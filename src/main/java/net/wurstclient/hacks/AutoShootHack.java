/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.item.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyBinding;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.BlockUtils;

public final class AutoShootHack extends Hack implements UpdateListener
{
	private final CheckboxSetting bows = new CheckboxSetting("Bows",
		"Automatically shoots bows and crossbows with arrows.", true);
	
	private final CheckboxSetting fireworkBows =
		new CheckboxSetting("Firework Crossbows",
			"Automatically shoots crossbows with fireworks.", false);
	
	private final CheckboxSetting tridents =
		new CheckboxSetting("Tridents", "Automatically shoots tridents.", true);
	
	private final CheckboxSetting throwable = new CheckboxSetting(
		"Throwable Items", "Automatically throws eggs and snowballs.", false);
	
	private final CheckboxSetting potions =
		new CheckboxSetting("Potions", "Automatically throws potions.", false);
	
	private final CheckboxSetting fishingRods = new CheckboxSetting(
		"Fishing Rods", "Automatically casts fishing rods.", false);
	
	private final SliderSetting minPower = new SliderSetting("Minimum Power",
		"The minimum power needed when shooting a bow.", 0.8, 0, 1, 0.01,
		ValueDisplay.PERCENTAGE);
	
	private final SliderSetting waitTime = new SliderSetting(
		"Fully Charged Wait Time",
		"Time in ticks to wait after a bow or trident is ready to fire before firing.\n"
			+ "This is needed on laggy servers.",
		0, 0, 50, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting pressTime = new SliderSetting("Press Time",
		"The number of ticks to wait after a click or release.\n"
			+ "If this number is too low, the shot may not be fired.",
		3, 1, 20, 1, ValueDisplay.INTEGER);
	
	private final SliderSetting cooldown = new SliderSetting("Cooldown",
		"Minimum delay between consecutive shots (MS).\n"
			+ "There will still be a delay from the release/click mechanism.",
		400, 0, 3000, 50, ValueDisplay.INTEGER);
	
	private final EnumSetting<MultiTargetOption> multiTargetOption =
		new EnumSetting<>("Multi-Target Mode",
			"Determines how multiple targets should be handled for multishot and piercing crossbows.\n"
				+ "\u00a7lAny Filtered Entity\u00a7r - Any entity hit can be valid.\n"
				+ "\u00a7lOnly Filtered Entities\u00a7r - All entities hit must be valid.",
			MultiTargetOption.values(), MultiTargetOption.ANY_FILTERED);
	
	private final EnumSetting<FireworkLifespan> fireworkLifespan =
		new EnumSetting<>("Firework Lifespan",
			"Fireworks fired by crossbows have a bit of randomness in their lifespan.\n"
				+ "This option allows you to choose which lifespan to use.",
			FireworkLifespan.values(), FireworkLifespan.AVERAGE);
	
	private final SliderSetting predictMovement =
		new SliderSetting("Predict movement",
			"Controls the strength of the prediction algorithm.", 0.2, 0, 3,
			0.01, ValueDisplay.PERCENTAGE);
	
	private final EntityFilterList entityFilters =
		EntityFilterList.genericCombat();
	
	private long shootTime;
	private ProjectileReleaser releaser;
	
	public AutoShootHack()
	{
		super("AutoShoot");
		setCategory(Category.COMBAT);
		addSetting(bows);
		addSetting(fireworkBows);
		addSetting(tridents);
		addSetting(throwable);
		addSetting(potions);
		addSetting(fishingRods);
		
		addSetting(minPower);
		addSetting(waitTime);
		addSetting(pressTime);
		addSetting(cooldown);
		
		addSetting(multiTargetOption);
		addSetting(fireworkLifespan);
		addSetting(predictMovement);
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		if(releaser != null)
		{
			releaser.tick(null, -999);
			releaser = null;
		}
		EVENTS.remove(UpdateListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.world == null)
			return;
		
		// find the hand with a throwable item
		ItemStack stack = MC.player.getMainHandStack();
		int slot = MC.player.getInventory().selectedSlot;
		if(!isThrowable(stack))
		{
			stack = MC.player.getOffHandStack();
			slot = -1;
		}
		
		if(releaser != null)
		{
			if(releaser.tick(stack, slot))
				releaser = null;
			else
				return;
		}
		
		if(!isThrowable(stack))
			return;
		
		if(System.currentTimeMillis() < shootTime + cooldown.getValueI())
			return;
		
		// check if item is valid
		if(!isValid(stack))
			return;
		
		DynamicRegistryManager drm = MC.world.getRegistryManager();
		Registry<Enchantment> registry =
			drm.getOrThrow(RegistryKeys.ENCHANTMENT);
		
		// get entities hit by the item
		Set<Entity> entities = getEntitiesHit(stack, registry);
		
		// check if we should fire
		List<Entity> filtered =
			entityFilters.applyTo(entities.stream()).toList();
		if(multiTargetOption.getSelected() == MultiTargetOption.ANY_FILTERED
			&& filtered.isEmpty())
			return;
		
		if(multiTargetOption.getSelected() == MultiTargetOption.ONLY_FILTERED
			&& (filtered.isEmpty() || filtered.size() < entities.size()))
			return;
		
		shootTime = System.currentTimeMillis();
		releaser = new ProjectileReleaser(stack, slot, pressTime.getValueI());
	}
	
	private Set<Entity> getEntitiesHit(ItemStack stack,
		Registry<Enchantment> registry)
	{
		Optional<Reference<Enchantment>> multishot =
			registry.getOptional(Enchantments.MULTISHOT);
		
		int multishotLvl = multishot
			.map(entry -> EnchantmentHelper.getLevel(entry, stack)).orElse(0);
		if(stack.getItem() instanceof CrossbowItem && multishotLvl > 0)
		{
			int divergence = multishotLvl * 10;
			Set<Entity> entities = new HashSet<>();
			for(int i = -divergence; i <= divergence; i += 10)
				entities.addAll(
					getHitsForTrajectory(MC.player, stack, i, registry));
			
			return entities;
		}else
			return getHitsForTrajectory(MC.player, stack, 0, registry);
	}
	
	private boolean isValid(ItemStack stack)
	{
		if(bows.isChecked() && stack.getItem() instanceof BowItem)
		{
			if(MC.player.getItemUseTimeLeft() == 0)
				return false;
			float bowPower =
				(72000 - MC.player.getItemUseTimeLeft() - waitTime.getValueI())
					/ 20F;
			if(bowPower < 0)
				return false;
			bowPower = bowPower * bowPower + bowPower * 2F;
			if(bowPower >= minPower.getValueF() * 3)
				return true;
			return false;
		}
		
		if(bows.isChecked() && stack.getItem() instanceof CrossbowItem
			&& stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null
			&& stack.get(DataComponentTypes.CHARGED_PROJECTILES)
				.getProjectiles().stream()
				.filter(s -> RangedWeaponItem.BOW_PROJECTILES.test(s))
				.count() > 0)
			return true;
		
		if(fireworkBows.isChecked() && stack.getItem() instanceof CrossbowItem
			&& stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null
			&& stack.get(DataComponentTypes.CHARGED_PROJECTILES)
				.contains(Items.FIREWORK_ROCKET))
			return true;
		
		if(tridents.isChecked() && stack.getItem() instanceof TridentItem)
		{
			if(72000 - MC.player.getItemUseTimeLeft()
				- waitTime.getValueI() >= 10)
				return true;
			return false;
		}
		
		if(throwable.isChecked() && (stack.getItem() instanceof SnowballItem
			|| stack.getItem() instanceof EggItem))
			return true;
		
		if(potions.isChecked()
			&& stack.getItem() instanceof ThrowablePotionItem)
			return true;
		
		if(fishingRods.isChecked() && stack.getItem() instanceof FishingRodItem)
		{
			if(MC.player.fishHook == null)
				return true;
			return false;
		}
		
		return false;
	}
	
	private HashSet<Entity> getHitsForTrajectory(
		AbstractClientPlayerEntity player, ItemStack stack, float divergence,
		Registry<Enchantment> registry)
	{
		Optional<Reference<Enchantment>> riptide =
			registry.getOptional(Enchantments.RIPTIDE);
		Optional<Reference<Enchantment>> piercing =
			registry.getOptional(Enchantments.PIERCING);
		
		HashSet<Entity> hit = new HashSet<>();
		boolean land = false;
		
		Item item = stack.getItem();
		boolean bow = item instanceof BowItem;
		boolean crossbow = item instanceof CrossbowItem;
		boolean fireworkBow = crossbow
			&& stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null
			&& stack.get(DataComponentTypes.CHARGED_PROJECTILES)
				.contains(Items.FIREWORK_ROCKET);
		boolean trident = item instanceof TridentItem;
		boolean hasRiptide = trident
			&& riptide.map(entry -> EnchantmentHelper.getLevel(entry, stack))
				.orElse(0) > 0;
		boolean fishingRod = stack.getItem() instanceof FishingRodItem;
		boolean potion = item instanceof ThrowablePotionItem;
		boolean expBottle = item instanceof ExperienceBottleItem;
		
		int pierce = crossbow && !fireworkBow ? 1 + piercing
			.map(entry -> EnchantmentHelper.getLevel(entry, stack)).orElse(0)
			: 1;
		int fireworkSpan = fireworkBow ? 1
			+ stack.get(DataComponentTypes.CHARGED_PROJECTILES).getProjectiles()
				.get(0).get(DataComponentTypes.FIREWORKS).flightDuration()
			: 0;
		
		// calculate item-specific values
		double throwPower = getThrowPower(player, stack, item, riptide);
		double gravity = getProjectileGravity(stack, item, riptide);
		FluidHandling fluidHandling = getFluidHandling(item);
		
		// prepare yaw and pitch
		double yaw = Math.toRadians(player.getYaw());
		double pitch = Math.toRadians(player.getPitch());
		
		// calculate starting position
		Vec3d arrowPos = player.getPos()
			.add(getHandOffset(player, fishingRod, fireworkBow, yaw));
		
		// calculate starting motion
		Vec3d arrowMotion =
			getStartingMotion(player, fishingRod, potion || expBottle, yaw,
				pitch, player.getPitch(), throwPower, divergence);
		
		// build the path
		for(int i = 0; i < 400; i++)
		{
			// gravity is applied first (fishing rods only)
			if(fishingRod)
				arrowMotion = arrowMotion.add(0, -gravity, 0);
			
			// apply motion
			Vec3d lastPos = arrowPos;
			arrowPos = arrowPos.add(arrowMotion);
			
			// check for block collision
			BlockHitResult bResult =
				BlockUtils.raycast(lastPos, arrowPos, fluidHandling);
			if(bResult.getType() != HitResult.Type.MISS)
			{
				arrowPos = bResult.getPos();
				land = true;
			}
			
			// check for mob collision
			double halfBB =
				bow || (crossbow && !fireworkBow) || trident ? 0.25 : 0.125;
			Box arrowBox = new Box(lastPos.x - halfBB, lastPos.y,
				lastPos.z - halfBB, lastPos.x + halfBB, lastPos.y + halfBB * 2,
				lastPos.z + halfBB);
			Predicate<Entity> predicate = e -> !e.isSpectator() && e.isAlive()
				&& e.canHit() && !hit.contains(e);
			double maxDistSq = 64 * 64;
			while(true)
			{
				EntityHitResult eResult = raycastEntity(player, lastPos,
					arrowPos, arrowBox.stretch(arrowMotion).expand(1),
					predicate, maxDistSq, predictMovement.getValue());
				if(eResult == null)
					break;
				else
					hit.add(eResult.getEntity());
				if(hit.size() == pierce)
				{
					land = false;
					break;
				}
			}
			if(land || hit.size() == pierce)
				break;
			
			// fireworks travel in a straight line
			if(fireworkBow)
			{
				// account for firework lifespan
				int lifetime = 10 * fireworkSpan;
				if(fireworkLifespan.getSelected() == FireworkLifespan.AVERAGE)
					lifetime += 5;
				else if(fireworkLifespan
					.getSelected() == FireworkLifespan.MAXIMUM)
					lifetime += 11;
				
				if(i > lifetime)
					break;
				continue;
			}
			
			// apply drag
			if(BlockUtils
				.getState(new BlockPos(MathHelper.floor(arrowPos.x),
					MathHelper.floor(arrowPos.y), MathHelper.floor(arrowPos.z)))
				.getBlock() == Blocks.WATER && !trident)
			{
				if(bow)
					arrowMotion = arrowMotion.multiply(0.6);
				else
					arrowMotion = arrowMotion.multiply(0.8);
			}else
			{
				if(fishingRod)
					arrowMotion = arrowMotion.multiply(0.92);
				else if(hasRiptide)
					arrowMotion = arrowMotion.multiply(0.91);
				else
					arrowMotion = arrowMotion.multiply(0.99);
			}
			
			// apply gravity
			if(!fishingRod)
				arrowMotion = arrowMotion.add(0, -gravity, 0);
		}
		
		return hit;
	}
	
	private EntityHitResult raycastEntity(Entity entity, Vec3d min, Vec3d max,
		Box box, Predicate<Entity> predicate, double maxDistance,
		double predictionStrength)
	{
		// copied from ProjectileUtil.raycast()
		World world = entity.getWorld();
		double d = maxDistance;
		Entity entity2 = null;
		Vec3d vec3d = null;
		
		for(Entity entity3 : world.getOtherEntities(entity, box, predicate))
		{
			Box box2 = entity3.getBoundingBox()
				.expand((double)entity3.getTargetingMargin()).offset(
					(entity3.getX() - entity3.lastRenderX) * predictionStrength,
					(entity3.getY() - entity3.lastRenderY) * predictionStrength,
					(entity3.getZ() - entity3.lastRenderZ)
						* predictionStrength);
			Optional<Vec3d> optional = box2.raycast(min, max);
			if(box2.contains(min))
			{
				if(d >= 0.0)
				{
					entity2 = entity3;
					vec3d = (Vec3d)optional.orElse(min);
					d = 0.0;
				}
			}else if(optional.isPresent())
			{
				Vec3d vec3d2 = (Vec3d)optional.get();
				double e = min.squaredDistanceTo(vec3d2);
				if(e < d || d == 0.0)
				{
					if(entity3.getRootVehicle() == entity.getRootVehicle())
					{
						if(d == 0.0)
						{
							entity2 = entity3;
							vec3d = vec3d2;
						}
					}else
					{
						entity2 = entity3;
						vec3d = vec3d2;
						d = e;
					}
				}
			}
		}
		
		return entity2 == null ? null : new EntityHitResult(entity2, vec3d);
	}
	
	private Vec3d getHandOffset(AbstractClientPlayerEntity player,
		boolean fishingRod, boolean fireworkBow, double yaw)
	{
		double yOffset = fishingRod ? 0 : fireworkBow ? 0.15 : 0.1;
		
		double handOffsetX = 0;
		double handOffsetY = player.getStandingEyeHeight() - yOffset;
		double handOffsetZ = 0;
		
		if(fishingRod)
		{
			handOffsetX -= Math.sin(-yaw - Math.PI) * 0.3D;
			handOffsetZ -= Math.cos(-yaw - Math.PI) * 0.3D;
		}
		
		return new Vec3d(handOffsetX, handOffsetY, handOffsetZ);
	}
	
	private Vec3d getStartingMotion(AbstractClientPlayerEntity player,
		boolean fishingRod, boolean pitchDown, double yaw, double pitch,
		double pitchDeg, double throwPower, float divergence)
	{
		double cosOfPitch = Math.cos(pitch);
		
		double arrowMotionX;
		double arrowMotionY;
		double arrowMotionZ;
		
		if(fishingRod)
		{
			arrowMotionX = -Math.sin(-yaw - Math.PI);
			arrowMotionY =
				MathHelper.clamp(Math.sin(-pitch) / Math.cos(-pitch), -5, 5);
			arrowMotionZ = -Math.cos(-yaw - Math.PI);
			
			double arrowMotion = Math.sqrt(arrowMotionX * arrowMotionX
				+ arrowMotionY * arrowMotionY + arrowMotionZ * arrowMotionZ);
			arrowMotionX *= 0.6 / arrowMotion + 0.5;
			arrowMotionY *= 0.6 / arrowMotion + 0.5;
			arrowMotionZ *= 0.6 / arrowMotion + 0.5;
			
			return new Vec3d(arrowMotionX, arrowMotionY, arrowMotionZ)
				.multiply(throwPower);
		}else
		{
			arrowMotionX = -Math.sin(yaw) * cosOfPitch;
			arrowMotionY =
				-Math.sin(pitchDown ? Math.toRadians(pitchDeg - 20) : pitch);
			arrowMotionZ = Math.cos(yaw) * cosOfPitch;
			
			if(divergence == 0)
				return new Vec3d(arrowMotionX, arrowMotionY, arrowMotionZ)
					.normalize().multiply(throwPower);
			
			Vec3d rotVec = player.getOppositeRotationVector(1.0f);
			Quaternionf quaternion = new Quaternionf().setAngleAxis(
				Math.toRadians(divergence), rotVec.x, rotVec.y, rotVec.z);
			Vector3f vec = new Vec3d(arrowMotionX, arrowMotionY, arrowMotionZ)
				.toVector3f().rotate(quaternion);
			return new Vec3d(vec).normalize().multiply(throwPower);
		}
	}
	
	private double getThrowPower(AbstractClientPlayerEntity player,
		ItemStack stack, Item item, Optional<Reference<Enchantment>> riptide)
	{
		if(!(item instanceof BowItem))
		{
			if(item instanceof FishingRodItem)
				return 1;
			
			if(item instanceof ThrowablePotionItem)
				return 0.5;
			
			if(item instanceof ExperienceBottleItem)
				return 0.7;
			
			if(item instanceof TridentItem)
			{
				int riptideLvl = riptide
					.map(entry -> EnchantmentHelper.getLevel(entry, stack))
					.orElse(0);
				return riptideLvl > 0 ? 3 * (1 + riptideLvl) / 4 : 2.5;
			}
			
			if(item instanceof CrossbowItem)
			{
				if(stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null
					&& stack.get(DataComponentTypes.CHARGED_PROJECTILES)
						.contains(Items.FIREWORK_ROCKET))
					return 1.6;
				
				return 3.15;
			}
			
			return 1.5;
		}
		
		// calculate bow power
		float bowPower = (72000 - player.getItemUseTimeLeft()) / 20F;
		bowPower = bowPower * bowPower + bowPower * 2F;
		
		// clamp value if fully charged
		if(bowPower > 3)
			bowPower = 3;
		
		return bowPower;
	}
	
	private double getProjectileGravity(ItemStack stack, Item item,
		Optional<Reference<Enchantment>> riptide)
	{
		if(item instanceof TridentItem
			&& riptide.map(entry -> EnchantmentHelper.getLevel(entry, stack))
				.orElse(0) > 0)
			return 0.08;
		
		if(item instanceof BowItem || item instanceof CrossbowItem
			|| item instanceof TridentItem || item instanceof PotionItem)
			return 0.05;
		
		if(item instanceof ExperienceBottleItem)
			return 0.07;
		
		return 0.03;
	}
	
	private FluidHandling getFluidHandling(Item item)
	{
		if(item instanceof FishingRodItem)
			return FluidHandling.ANY;
		
		return FluidHandling.NONE;
	}
	
	public static boolean isThrowable(ItemStack stack)
	{
		if(stack.isEmpty())
			return false;
		
		Item item = stack.getItem();
		return item instanceof RangedWeaponItem || item instanceof SnowballItem
			|| item instanceof EggItem || item instanceof EnderPearlItem
			|| item instanceof ThrowablePotionItem
			|| item instanceof ExperienceBottleItem
			|| item instanceof FishingRodItem || item instanceof TridentItem;
	}
	
	private enum MultiTargetOption
	{
		ANY_FILTERED("Any Filtered Entity"),
		ONLY_FILTERED("Only Filtered Entities");
		
		private final String name;
		
		private MultiTargetOption(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
	
	private enum FireworkLifespan
	{
		MINIMUM("Minimum"),
		AVERAGE("Average"),
		MAXIMUM("Maximum");
		
		private final String name;
		
		private FireworkLifespan(String name)
		{
			this.name = name;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
	
	private class ProjectileReleaser
	{
		private final ItemStack stack;
		private final int slot;
		private final int pressTicks;
		private int ticks;
		private State state;
		
		public ProjectileReleaser(ItemStack stack, int slot, int pressTicks)
		{
			this.stack = stack;
			this.slot = slot;
			this.pressTicks = pressTicks;
			ticks = 0;
			if(stack.getItem() instanceof BowItem
				|| stack.getItem() instanceof TridentItem)
			{
				state = State.RELEASING;
				MC.options.useKey.setPressed(false);
			}else
			{
				state = State.PRESSING;
				MC.options.useKey.setPressed(true);
			}
		}
		
		public boolean tick(ItemStack currentStack, int currentSlot)
		{
			if(currentStack != stack || currentSlot != slot)
			{
				state = State.FINISHED;
				IKeyBinding.get(MC.options.useKey).resetPressedState();
				return true;
			}
			
			ticks++;
			if(ticks == pressTicks)
			{
				if(state == State.PRESSING)
				{
					ticks = 0;
					state = State.RELEASING;
					MC.options.useKey.setPressed(false);
				}else
				{
					state = State.FINISHED;
					IKeyBinding.get(MC.options.useKey).resetPressedState();
				}
			}
			
			return state == State.FINISHED;
		}
		
		private enum State
		{
			PRESSING,
			RELEASING,
			FINISHED;
		}
	}
}
