/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
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
import java.util.function.Predicate;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext.FluidHandling;
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
	
	private final EnumSetting<Display> displayMode =
		new EnumSetting<>("Display Mode",
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
	
	private final CheckboxSetting otherPlayer = new CheckboxSetting(
		"Trajectories for other players", false);
	
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
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		matrixStack.push();
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(false);
		
		RenderUtils.applyCameraRotationOnly();
		
		List<AbstractClientPlayerEntity> players = otherPlayer.isChecked() ? MC.world.getPlayers()
			: Collections.singletonList(MC.player);
		boolean accurate = displayMode.getSelected() == Display.ACCURATE;
		for(AbstractClientPlayerEntity player : players)
		{
			List<Trajectory> trajectories = getTrajectories(player,
				partialTicks, player == MC.player ? accurate : true);
			for(Trajectory trajectory : trajectories)
			{
				ArrayList<Vec3d> path = trajectory.path;
				
				ColorSetting color;
				if(!trajectory.hit.isEmpty())
					color = entityHitColor;
				else if(trajectory.land)
					color = blockHitColor;
				else
					color = missColor;
				
				drawLine(matrixStack, path, color);
				
				if(!path.isEmpty() && trajectory.land)
				{
					Vec3d end = path.get(path.size() - 1);
					drawEndOfLine(matrixStack, end, color);
				}
				for(Entity e : trajectory.hit)
					drawEntityHit(matrixStack, e, partialTicks, color);
			}
		}
		
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDepthMask(true);
		matrixStack.pop();
	}
	
	private void drawLine(MatrixStack matrixStack, ArrayList<Vec3d> path,
		ColorSetting color)
	{
		Vec3d camPos = RenderUtils.getCameraPos();
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		RenderSystem.setShader(GameRenderer::getPositionProgram);
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP,
			VertexFormats.POSITION);
		float[] colorF = color.getColorF();
		RenderSystem.setShaderColor(colorF[0], colorF[1], colorF[2], 0.75F);
		
		for(Vec3d point : path)
			bufferBuilder
				.vertex(matrix, (float)(point.x - camPos.x),
					(float)(point.y - camPos.y), (float)(point.z - camPos.z))
				.next();
		
		tessellator.draw();
	}
	
	private void drawEndOfLine(MatrixStack matrixStack, Vec3d end,
		ColorSetting color)
	{
		Vec3d camPos = RenderUtils.getCameraPos();
		double renderX = end.x - camPos.x;
		double renderY = end.y - camPos.y;
		double renderZ = end.z - camPos.z;
		float[] colorF = color.getColorF();
		
		matrixStack.push();
		matrixStack.translate(renderX - 0.5, renderY - 0.5, renderZ - 0.5);
		
		RenderSystem.setShaderColor(colorF[0], colorF[1], colorF[2], 0.25F);
		RenderUtils.drawSolidBox(matrixStack);
		
		RenderSystem.setShaderColor(colorF[0], colorF[1], colorF[2], 0.75F);
		RenderUtils.drawOutlinedBox(matrixStack);
		
		matrixStack.pop();
	}
	
	private void drawEntityHit(MatrixStack matrixStack, Entity hit,
		float partialTicks, ColorSetting color)
	{
		GL11.glEnable(GL11.GL_CULL_FACE);
		
		matrixStack.push();
		RenderUtils.applyRegionalRenderOffset(matrixStack);
		
		BlockPos camPos = RenderUtils.getCameraBlockPos();
		int regionX = (camPos.getX() >> 9) * 512;
		int regionZ = (camPos.getZ() >> 9) * 512;
		
		matrixStack.translate(
			MathHelper.lerp(partialTicks, hit.prevX,
				hit.getX()) - regionX,
			MathHelper.lerp(partialTicks, hit.prevY,
				hit.getY()),
			MathHelper.lerp(partialTicks, hit.prevZ,
				hit.getZ()) - regionZ);
		
		Box box = new Box(BlockPos.ORIGIN);
		
		// set size
		float boxWidth = hit.getWidth() + 0.1f;
		float boxHeight = hit.getHeight() + 0.1f;
		matrixStack.scale(boxWidth, boxHeight, boxWidth);
		matrixStack.translate(-0.5, 0, -0.5);
		
		RenderSystem.setShader(GameRenderer::getPositionProgram);
		float[] colorF = color.getColorF();
		RenderSystem.setShaderColor(colorF[0], colorF[1], colorF[2], 0.2F);
		
		// draw box
		RenderUtils.drawSolidBox(box, matrixStack);
		
		matrixStack.pop();
	}
	
	private record Trajectory(ArrayList<Vec3d> path, HashSet<Entity> hit, boolean land)
	{}
	
	private List<Trajectory> getTrajectories(AbstractClientPlayerEntity player,
		float partialTicks, boolean accurate)
	{
		// find the hand with a throwable item
		ItemStack stack = player.getMainHandStack();
		if(!isThrowable(stack))
		{
			stack = player.getOffHandStack();
			
			// if neither hand has a throwable item, return empty path
			if(!isThrowable(stack))
				return new ArrayList<>();
		}
		
		if(stack.getItem() instanceof CrossbowItem && EnchantmentHelper.getLevel(Enchantments.MULTISHOT, stack) != 0)
		{
			return Arrays.asList(getTrajectory(player, stack, partialTicks, accurate, 0),
				getTrajectory(player, stack, partialTicks, accurate, -10),
				getTrajectory(player, stack, partialTicks, accurate, 10));
		}else
			return Arrays.asList(getTrajectory(player, stack, partialTicks, accurate, 0));
	}
	
	private Trajectory getTrajectory(AbstractClientPlayerEntity player, ItemStack stack,
		float partialTicks, boolean accurate, float divergence)
	{
		ArrayList<Vec3d> path = new ArrayList<>();
		HashSet<Entity> hit = new HashSet<>();
		boolean land = false;
		
		Item item = stack.getItem();
		boolean bow = item instanceof BowItem;
		boolean crossbow = item instanceof CrossbowItem;
		boolean fireworkBow = crossbow && stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null
			&& stack.get(DataComponentTypes.CHARGED_PROJECTILES).contains(Items.FIREWORK_ROCKET);
		boolean trident = item instanceof TridentItem;
		boolean riptide = trident && EnchantmentHelper.getRiptide(stack) > 0;
		boolean fishingRod = stack.getItem() instanceof FishingRodItem;
		boolean potion = item instanceof ThrowablePotionItem;
		boolean expBottle = item instanceof ExperienceBottleItem;
		
		int pierce = crossbow && !fireworkBow ? 1 + EnchantmentHelper.getLevel(Enchantments.PIERCING, stack) : 1;
		int fireworkSpan = fireworkBow ? 1 + stack.get(DataComponentTypes.CHARGED_PROJECTILES).getProjectiles().get(0)
			.get(DataComponentTypes.FIREWORKS).flightDuration() : 0;
		
		// calculate item-specific values
		double throwPower = getThrowPower(player, stack, item);
		double gravity = getProjectileGravity(stack, item);
		FluidHandling fluidHandling = getFluidHandling(item);
		
		// prepare yaw and pitch
		double yaw = Math.toRadians(player.getYaw());
		double pitch = Math.toRadians(player.getPitch());
		
		// calculate starting position
		Vec3d arrowPos = EntityUtils.getLerpedPos(player, partialTicks)
			.add(getHandOffset(player, fishingRod, fireworkBow, yaw, accurate));
		
		// calculate starting motion
		Vec3d arrowMotion = getStartingMotion(player, fishingRod, potion || expBottle,
			yaw, pitch, player.getPitch(), throwPower, divergence);
		
		// build the path
		for(int i = 0; i < 400; i++)
		{
			// add to path
			path.add(arrowPos);
			
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
				// replace last pos with the collision point
				path.set(path.size() - 1, bResult.getPos());
				arrowPos = bResult.getPos();
				land = true;
			}
			
			// check for mob collision
			double halfBB = bow || (crossbow && !fireworkBow) || trident ? 0.25 : 0.125;
			Box arrowBox = new Box(lastPos.x - halfBB, lastPos.y,
				lastPos.z - halfBB, lastPos.x + halfBB, lastPos.y + halfBB * 2, lastPos.z + halfBB);
			Predicate<Entity> predicate = e -> !e.isSpectator() && e.isAlive() && e.canHit() && !hit.contains(e);
			double maxDistSq = 64 * 64;
			while(true)
			{
				EntityHitResult eResult = ProjectileUtil.raycast(player,
					lastPos, arrowPos, arrowBox.stretch(arrowMotion).expand(1),
					predicate, maxDistSq);
				if(eResult == null)
					break;
				else
					hit.add(eResult.getEntity());
				if(hit.size() == pierce)
				{
					land = false;
					path.set(path.size() - 1, eResult.getPos());
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
				else if(fireworkLifespan.getSelected() == FireworkLifespan.MAXIMUM)
					lifetime += 11;
				
				if(i > lifetime)
					break;
				continue;
			}
			
			// apply drag
			if(BlockUtils.getState(new BlockPos(MathHelper.floor(arrowPos.x), MathHelper.floor(arrowPos.y), MathHelper.floor(arrowPos.z)))
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
				else if(riptide)
					arrowMotion = arrowMotion.multiply(0.91);
				else
					arrowMotion = arrowMotion.multiply(0.99);
			}
			
			// apply gravity
			if(!fishingRod)
				arrowMotion = arrowMotion.add(0, -gravity, 0);
		}
		
		return new Trajectory(path, hit, land);
	}
	
	private Vec3d getHandOffset(AbstractClientPlayerEntity player, boolean fishingRod, boolean fireworkBow,
		double yaw, boolean accurate)
	{
		double factor = accurate ? 0 : 0.16;
		double yOffset = fishingRod ? 0 : fireworkBow ? 0.15 : 0.1;
		
		double handOffsetX = -Math.cos(yaw) * factor;
		double handOffsetY = player.getStandingEyeHeight() - yOffset;
		double handOffsetZ = -Math.sin(yaw) * factor;
		
		if(accurate && fishingRod)
		{
			handOffsetX -= Math.sin(-yaw - Math.PI) * 0.3D;
			handOffsetZ -= Math.cos(-yaw - Math.PI) * 0.3D;
		}
		
		return new Vec3d(handOffsetX, handOffsetY, handOffsetZ);
	}
	
	private Vec3d getStartingMotion(AbstractClientPlayerEntity player, boolean fishingRod, boolean pitchDown,
		double yaw, double pitch, double pitchDeg, double throwPower, float divergence)
	{
		double cosOfPitch = Math.cos(pitch);
		
		double arrowMotionX;
		double arrowMotionY;
		double arrowMotionZ;
		
		if(fishingRod)
		{
			arrowMotionX = -Math.sin(-yaw - Math.PI);
			arrowMotionY = MathHelper.clamp(Math.sin(-pitch) / Math.cos(-pitch), -5, 5);
			arrowMotionZ = -Math.cos(-yaw - Math.PI);
			
			double arrowMotion = Math.sqrt(arrowMotionX * arrowMotionX
				+ arrowMotionY * arrowMotionY + arrowMotionZ * arrowMotionZ);
			arrowMotionX *= 0.6 / arrowMotion + 0.5;
			arrowMotionY *= 0.6 / arrowMotion + 0.5;
			arrowMotionZ *= 0.6 / arrowMotion + 0.5;
			
			return new Vec3d(arrowMotionX, arrowMotionY, arrowMotionZ).multiply(throwPower);
		}else
		{
			arrowMotionX = -Math.sin(yaw) * cosOfPitch;
			arrowMotionY = -Math.sin(pitchDown ? Math.toRadians(pitchDeg - 20) : pitch);
			arrowMotionZ = Math.cos(yaw) * cosOfPitch;
			
			if(divergence == 0)
				return new Vec3d(arrowMotionX, arrowMotionY, arrowMotionZ).normalize()
					.multiply(throwPower);
			
			Vec3d rotVec = player.getOppositeRotationVector(1.0f);
			Quaternionf quaternion = new Quaternionf().setAngleAxis(
				Math.toRadians(divergence), rotVec.x, rotVec.y, rotVec.z);
			Vector3f vec = new Vec3d(arrowMotionX, arrowMotionY, arrowMotionZ).toVector3f().rotate(quaternion);
			return new Vec3d(vec).normalize().multiply(throwPower);
		}
	}
	
	private double getThrowPower(AbstractClientPlayerEntity player, ItemStack stack, Item item)
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
				int riptide = EnchantmentHelper.getRiptide(stack);
				return riptide > 0 ? 3 * (1 + riptide) / 4 : 2.5;
			}
			
			if(item instanceof CrossbowItem)
			{
				if(stack.get(DataComponentTypes.CHARGED_PROJECTILES) != null
					&& stack.get(DataComponentTypes.CHARGED_PROJECTILES).contains(Items.FIREWORK_ROCKET))
					return 1.6;
				
				return 3.15;
			}
			
			return 1.5;
		}
		
		// calculate bow power
		float bowPower = (72000 - player.getItemUseTimeLeft()) / 20F;
		bowPower = bowPower * bowPower + bowPower * 2F;
		
		// clamp value if fully charged or not charged at all
		if(bowPower > 3 || bowPower <= 0.3F)
			bowPower = 3;
		
		return bowPower;
	}
	
	private double getProjectileGravity(ItemStack stack, Item item)
	{
		if(item instanceof TridentItem && EnchantmentHelper.getRiptide(stack) > 0)
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
