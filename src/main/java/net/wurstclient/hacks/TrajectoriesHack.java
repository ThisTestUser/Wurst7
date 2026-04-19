/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.RenderListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RenderUtils;

@SearchTags({"ArrowTrajectories", "ArrowPrediction", "aim assist",
	"arrow trajectories", "bow trajectories"})
public final class TrajectoriesHack extends Hack implements RenderListener
{
	private final ColorSetting missColor = new ColorSetting("Miss Color",
		"Color of the trajectory when it doesn't hit anything.", Color.GRAY);
	
	private final ColorSetting entityHitColor =
		new ColorSetting("Entity Hit Color",
			"Color of the trajectory when it hits an entity.", Color.RED);
	
	private final ColorSetting blockHitColor =
		new ColorSetting("Block Hit Color",
			"Color of the trajectory when it hits a block.", Color.GREEN);
	
	private final EnumSetting<Display> displayMode = new EnumSetting<>(
		"Display Mode",
		"\u00a7lFancy\u00a7r mode shows trajectories that look better,"
			+ " but with a slight inaccuracy.\n"
			+ "\u00a7lAccurate\u00a7r mode is slightly more accurate"
			+ " but is visually unappealing.",
		Display.values(), Display.FANCY);
	
	private final EnumSetting<FireworkLifespan> fireworkLifespan =
		new EnumSetting<>("Firework Lifespan",
			"Fireworks fired by crossbows have a bit of randomness in their lifespan.\n"
				+ "This option allows you to choose which lifespan to use.",
			FireworkLifespan.values(), FireworkLifespan.AVERAGE);
	
	private final CheckboxSetting otherPlayer =
		new CheckboxSetting("Trajectories for other players", false);
	
	public TrajectoriesHack()
	{
		super("Trajectories");
		setCategory(Category.RENDER);
		addSetting(missColor);
		addSetting(entityHitColor);
		addSetting(blockHitColor);
		addSetting(displayMode);
		addSetting(fireworkLifespan);
		addSetting(otherPlayer);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		RegistryAccess drm = MC.level.registryAccess();
		Registry<Enchantment> registry =
			drm.lookupOrThrow(Registries.ENCHANTMENT);
		
		List<AbstractClientPlayer> players = otherPlayer.isChecked()
			? MC.level.players() : Collections.singletonList(MC.player);
		boolean accurate = displayMode.getSelected() == Display.ACCURATE;
		for(AbstractClientPlayer player : players)
		{
			List<Trajectory> trajectories = getTrajectories(player,
				partialTicks, player == MC.player ? accurate : true, registry);
			for(Trajectory trajectory : trajectories)
			{
				ColorSetting color = getColor(trajectory);
				int lineColor = color.getColorI(0xC0);
				int quadColor = color.getColorI(0x40);
				
				ArrayList<Vec3> path = trajectory.path();
				
				for(Entity e : trajectory.hit)
				{
					AABB box = EntityUtils.getLerpedBox(e, partialTicks)
						.move(0, 0.05F, 0).inflate(0.05F);
					
					RenderUtils.drawSolidBox(matrixStack, box, quadColor,
						false);
				}
				
				if(trajectory.land)
				{
					AABB endBox = trajectory.getEndBox();
					RenderUtils.drawSolidBox(matrixStack, endBox, quadColor,
						false);
					RenderUtils.drawOutlinedBox(matrixStack, endBox, lineColor,
						false);
				}
				
				RenderUtils.drawCurvedLine(matrixStack, path, lineColor, false);
			}
		}
	}
	
	private List<Trajectory> getTrajectories(AbstractClientPlayer player,
		float partialTicks, boolean accurate, Registry<Enchantment> registry)
	{
		// Find the hand with a throwable item
		ItemStack stack = player.getMainHandItem();
		if(!isThrowable(stack))
		{
			stack = player.getOffhandItem();
			
			// If neither hand has a throwable item, return empty path
			if(!isThrowable(stack))
				return new ArrayList<>();
		}
		
		Optional<Reference<Enchantment>> multishot =
			registry.get(Enchantments.MULTISHOT);
		
		ItemStack stackFinal = stack;
		int multishotLvl = multishot.map(entry -> EnchantmentHelper
			.getItemEnchantmentLevel(entry, stackFinal)).orElse(0);
		if(stack.getItem() instanceof CrossbowItem && multishotLvl > 0)
		{
			int divergence = multishotLvl * 10;
			List<Trajectory> trajectories = new ArrayList<>();
			for(int i = -divergence; i <= divergence; i += 10)
				trajectories.add(getTrajectory(player, stack, partialTicks,
					accurate, i, registry));
			
			return trajectories;
		}else
			return Arrays.asList(getTrajectory(player, stack, partialTicks,
				accurate, 0, registry));
	}
	
	private Trajectory getTrajectory(AbstractClientPlayer player,
		ItemStack stack, float partialTicks, boolean accurate, float divergence,
		Registry<Enchantment> registry)
	{
		Optional<Reference<Enchantment>> riptide =
			registry.get(Enchantments.RIPTIDE);
		Optional<Reference<Enchantment>> piercing =
			registry.get(Enchantments.PIERCING);
		
		ArrayList<Vec3> path = new ArrayList<>();
		HashSet<Entity> hit = new HashSet<>();
		boolean land = false;
		
		Item item = stack.getItem();
		boolean bow = item instanceof BowItem;
		boolean crossbow = item instanceof CrossbowItem;
		boolean fireworkBow =
			crossbow && stack.get(DataComponents.CHARGED_PROJECTILES) != null
				&& stack.get(DataComponents.CHARGED_PROJECTILES)
					.contains(Items.FIREWORK_ROCKET);
		boolean trident = item instanceof TridentItem;
		boolean hasRiptide = trident && riptide.map(
			entry -> EnchantmentHelper.getItemEnchantmentLevel(entry, stack))
			.orElse(0) > 0;
		boolean fishingRod = stack.getItem() instanceof FishingRodItem;
		boolean potion = item instanceof ThrowablePotionItem;
		boolean expBottle = item instanceof ExperienceBottleItem;
		
		int pierce = crossbow && !fireworkBow ? 1 + piercing.map(
			entry -> EnchantmentHelper.getItemEnchantmentLevel(entry, stack))
			.orElse(0) : 1;
		int fireworkSpan = fireworkBow
			? 1 + stack.get(DataComponents.CHARGED_PROJECTILES).getItems()
				.get(0).get(DataComponents.FIREWORKS).flightDuration()
			: 0;
		
		// Calculate item-specific values
		double throwPower = getThrowPower(player, stack, item, riptide);
		double gravity = getProjectileGravity(stack, item, riptide);
		Fluid fluidHandling = getFluidHandling(item);
		
		// Prepare yaw and pitch
		double yaw = Math.toRadians(player.getYRot());
		double pitch = Math.toRadians(player.getXRot());
		
		// Calculate starting position
		Vec3 arrowPos = EntityUtils.getLerpedPos(player, partialTicks)
			.add(getHandOffset(player, fishingRod, fireworkBow, yaw, accurate));
		
		// Calculate starting motion
		Vec3 arrowMotion =
			getStartingMotion(player, fishingRod, potion || expBottle, yaw,
				pitch, player.getXRot(), throwPower, divergence);
		
		// Build the path
		for(int i = 0; i < 400; i++)
		{
			// Add to path
			path.add(arrowPos);
			
			// Gravity is applied first (fishing rods only)
			if(fishingRod)
				arrowMotion = arrowMotion.add(0, -gravity, 0);
			
			// Apply motion
			Vec3 lastPos = arrowPos;
			arrowPos = arrowPos.add(arrowMotion);
			
			// Check for block collision
			BlockHitResult bResult =
				BlockUtils.raycast(lastPos, arrowPos, fluidHandling);
			if(bResult.getType() != HitResult.Type.MISS)
			{
				// Replace last pos with the collision point
				path.set(path.size() - 1, bResult.getLocation());
				arrowPos = bResult.getLocation();
				land = true;
			}
			
			// Check for mob collision
			double halfBB =
				bow || (crossbow && !fireworkBow) || trident ? 0.25 : 0.125;
			AABB arrowBox = new AABB(lastPos.x - halfBB, lastPos.y,
				lastPos.z - halfBB, lastPos.x + halfBB, lastPos.y + halfBB * 2,
				lastPos.z + halfBB);
			Predicate<Entity> predicate = e -> !e.isSpectator() && e.isAlive()
				&& e.isPickable() && !hit.contains(e);
			double maxDistSq = 64 * 64;
			while(true)
			{
				EntityHitResult eResult =
					ProjectileUtil.getEntityHitResult(player, lastPos, arrowPos,
						arrowBox.expandTowards(arrowMotion).inflate(1),
						predicate, maxDistSq);
				if(eResult == null)
					break;
				else
					hit.add(eResult.getEntity());
				if(hit.size() == pierce)
				{
					land = false;
					path.set(path.size() - 1, eResult.getLocation());
					break;
				}
			}
			if(land || hit.size() == pierce)
				break;
			
			// Fireworks travel in a straight line
			if(fireworkBow)
			{
				// Account for firework lifespan
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
			
			// Apply drag
			if(BlockUtils
				.getState(new BlockPos(Mth.floor(arrowPos.x),
					Mth.floor(arrowPos.y), Mth.floor(arrowPos.z)))
				.getBlock() == Blocks.WATER && !trident)
			{
				if(bow)
					arrowMotion = arrowMotion.scale(0.6);
				else
					arrowMotion = arrowMotion.scale(0.8);
			}else
			{
				if(fishingRod)
					arrowMotion = arrowMotion.scale(0.92);
				else if(hasRiptide)
					arrowMotion = arrowMotion.scale(0.91);
				else
					arrowMotion = arrowMotion.scale(0.99);
			}
			
			// Apply gravity
			if(!fishingRod)
				arrowMotion = arrowMotion.add(0, -gravity, 0);
		}
		
		return new Trajectory(path, hit, land);
	}
	
	private boolean isThrowable(ItemStack stack)
	{
		if(stack.isEmpty())
			return false;
		
		Item item = stack.getItem();
		return item instanceof ProjectileWeaponItem
			|| item instanceof SnowballItem || item instanceof EggItem
			|| item instanceof EnderpearlItem
			|| item instanceof ThrowablePotionItem
			|| item instanceof ExperienceBottleItem
			|| item instanceof FishingRodItem || item instanceof TridentItem;
	}
	
	private double getThrowPower(AbstractClientPlayer player, ItemStack stack,
		Item item, Optional<Reference<Enchantment>> riptide)
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
				int riptideLvl = riptide.map(entry -> EnchantmentHelper
					.getItemEnchantmentLevel(entry, stack)).orElse(0);
				return riptideLvl > 0 ? 3 * (1 + riptideLvl) / 4 : 2.5;
			}
			
			if(item instanceof CrossbowItem)
			{
				if(stack.get(DataComponents.CHARGED_PROJECTILES) != null
					&& stack.get(DataComponents.CHARGED_PROJECTILES)
						.contains(Items.FIREWORK_ROCKET))
					return 1.6;
				
				return 3.15;
			}
			
			return 1.5;
		}
		
		// Calculate bow power
		float bowPower = (72000 - player.getUseItemRemainingTicks()) / 20F;
		bowPower = bowPower * bowPower + bowPower * 2F;
		
		// Clamp value if fully charged or not charged at all
		if(bowPower > 3 || bowPower <= 0.3F)
			bowPower = 3;
		
		return bowPower;
	}
	
	private double getProjectileGravity(ItemStack stack, Item item,
		Optional<Reference<Enchantment>> riptide)
	{
		if(item instanceof TridentItem && riptide.map(
			entry -> EnchantmentHelper.getItemEnchantmentLevel(entry, stack))
			.orElse(0) > 0)
			return 0.08;
		
		if(item instanceof BowItem || item instanceof CrossbowItem
			|| item instanceof TridentItem || item instanceof PotionItem)
			return 0.05;
		
		if(item instanceof ExperienceBottleItem)
			return 0.07;
		
		return 0.03;
	}
	
	private Fluid getFluidHandling(Item item)
	{
		if(item instanceof FishingRodItem)
			return Fluid.ANY;
		
		return Fluid.NONE;
	}
	
	private Vec3 getHandOffset(AbstractClientPlayer player, boolean fishingRod,
		boolean fireworkBow, double yaw, boolean accurate)
	{
		double factor = accurate ? 0 : 0.16;
		double yOffset = fishingRod ? 0 : fireworkBow ? 0.15 : 0.1;
		
		double handOffsetX = -Math.cos(yaw) * factor;
		double handOffsetY = player.getEyeHeight() - yOffset;
		double handOffsetZ = -Math.sin(yaw) * factor;
		
		if(accurate && fishingRod)
		{
			handOffsetX -= Math.sin(-yaw - Math.PI) * 0.3D;
			handOffsetZ -= Math.cos(-yaw - Math.PI) * 0.3D;
		}
		
		return new Vec3(handOffsetX, handOffsetY, handOffsetZ);
	}
	
	private Vec3 getStartingMotion(AbstractClientPlayer player,
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
				Mth.clamp(Math.sin(-pitch) / Math.cos(-pitch), -5, 5);
			arrowMotionZ = -Math.cos(-yaw - Math.PI);
			
			double arrowMotion = Math.sqrt(arrowMotionX * arrowMotionX
				+ arrowMotionY * arrowMotionY + arrowMotionZ * arrowMotionZ);
			arrowMotionX *= 0.6 / arrowMotion + 0.5;
			arrowMotionY *= 0.6 / arrowMotion + 0.5;
			arrowMotionZ *= 0.6 / arrowMotion + 0.5;
			
			return new Vec3(arrowMotionX, arrowMotionY, arrowMotionZ)
				.scale(throwPower);
		}else
		{
			arrowMotionX = -Math.sin(yaw) * cosOfPitch;
			arrowMotionY =
				-Math.sin(pitchDown ? Math.toRadians(pitchDeg - 20) : pitch);
			arrowMotionZ = Math.cos(yaw) * cosOfPitch;
			
			if(divergence == 0)
				return new Vec3(arrowMotionX, arrowMotionY, arrowMotionZ)
					.normalize().scale(throwPower);
			
			Vec3 rotVec = player.getUpVector(1.0f);
			Quaternionf quaternion = new Quaternionf().setAngleAxis(
				Math.toRadians(divergence), rotVec.x, rotVec.y, rotVec.z);
			Vector3f vec = new Vec3(arrowMotionX, arrowMotionY, arrowMotionZ)
				.toVector3f().rotate(quaternion);
			return new Vec3(vec).normalize().scale(throwPower);
		}
	}
	
	private ColorSetting getColor(Trajectory trajectory)
	{
		if(!trajectory.hit.isEmpty())
			return entityHitColor;
		
		if(trajectory.land)
			return blockHitColor;
		
		return missColor;
	}
	
	private record Trajectory(ArrayList<Vec3> path, HashSet<Entity> hit,
		boolean land)
	{
		public AABB getEndBox()
		{
			Vec3 end = path.get(path.size() - 1);
			return new AABB(end.subtract(0.5), end.add(0.5));
		}
	}
	
	private enum Display
	{
		FANCY("Fancy"),
		ACCURATE("Accurate");
		
		private final String name;
		
		private Display(String name)
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
}
