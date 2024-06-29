/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.wurstclient.WurstClient;

@Mixin(BucketItem.class)
public class BucketItemMixin
{
	@Redirect(method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/TypedActionResult;",
		at = @At(value = "INVOKE",
		target = "Lnet/minecraft/item/BucketItem;raycast(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/RaycastContext$FluidHandling;)Lnet/minecraft/util/hit/BlockHitResult;"))	
	private BlockHitResult onUse(World world,
		PlayerEntity player, RaycastContext.FluidHandling fluidHandling)
	{
		if(world.isClient && WurstClient.INSTANCE.getHax().autoDrainHack.useServerRot())
			return WurstClient.INSTANCE.getHax().autoDrainHack.rayTrace(fluidHandling == RaycastContext.FluidHandling.SOURCE_ONLY);
		return raycast(world, player, fluidHandling);
	}
	
    private BlockHitResult raycast(World world, PlayerEntity player, RaycastContext.FluidHandling fluidHandling)
    {
    	float pitch = player.getPitch();
    	float yaw = player.getYaw();
    	Vec3d vec3d = player.getEyePos();
    	float h = MathHelper.cos(-yaw * ((float)Math.PI / 180) - (float)Math.PI);
        float i = MathHelper.sin(-yaw * ((float)Math.PI / 180) - (float)Math.PI);
        float j = -MathHelper.cos(-pitch * ((float)Math.PI / 180));
        float k = MathHelper.sin(-pitch * ((float)Math.PI / 180));
        float l = i * j;
        float m = k;
        float n = h * j;
        double d = 5.0;
        Vec3d vec3d2 = vec3d.add((double)l * d, (double)m * d, (double)n * d);
        return world.raycast(new RaycastContext(vec3d, vec3d2, RaycastContext.ShapeType.OUTLINE, fluidHandling, player));
    }
}
