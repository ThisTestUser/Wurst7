/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
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

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.wurstclient.WurstClient;

@Mixin(BucketItem.class)
public class BucketItemMixin
{
	@WrapOperation(
		method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/BucketItem;getPlayerPOVHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;"))
	private BlockHitResult onUse(Level level, Player player,
		ClipContext.Fluid fluidHandling, Operation<BlockHitResult> original)
	{
		if(level.isClientSide()
			&& WurstClient.INSTANCE.getHax().autoDrainHack.useServerRot())
			return WurstClient.INSTANCE.getHax().autoDrainHack
				.rayTrace(fluidHandling);
		return original.call(level, player, fluidHandling);
	}
}
