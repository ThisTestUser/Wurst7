/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.wurstclient.WurstClient;

@Mixin(BucketItem.class)
public class BucketItemMixin
{
	@WrapOperation(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/item/BucketItem;raycast(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/RaycastContext$FluidHandling;)Lnet/minecraft/util/hit/BlockHitResult;"),
		method = "use(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;")
	private BlockHitResult onUse(World world, PlayerEntity player,
		RaycastContext.FluidHandling fluidHandling,
		Operation<BlockHitResult> original)
	{
		if(world.isClient
			&& WurstClient.INSTANCE.getHax().autoDrainHack.useServerRot())
			return WurstClient.INSTANCE.getHax().autoDrainHack
				.rayTrace(fluidHandling);
		return original.call(world, player, fluidHandling);
	}
}
