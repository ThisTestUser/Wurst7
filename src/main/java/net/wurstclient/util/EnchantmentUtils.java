/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum EnchantmentUtils
{
	;
	
	public static MutableText getShortName(Enchantment enchantment)
	{
		if(enchantment == Enchantments.PROTECTION)
			return Text.literal("PR");
		if(enchantment == Enchantments.FIRE_PROTECTION)
			return Text.literal("FP");
		if(enchantment == Enchantments.FEATHER_FALLING)
			return Text.literal("FF");
		if(enchantment == Enchantments.BLAST_PROTECTION)
			return Text.literal("BP");
		if(enchantment == Enchantments.PROJECTILE_PROTECTION)
			return Text.literal("PP");
		if(enchantment == Enchantments.RESPIRATION)
			return Text.literal("RS");
		if(enchantment == Enchantments.AQUA_AFFINITY)
			return Text.literal("AQ");
		if(enchantment == Enchantments.THORNS)
			return Text.literal("TH");
		if(enchantment == Enchantments.DEPTH_STRIDER)
			return Text.literal("DS");
		if(enchantment == Enchantments.FROST_WALKER)
			return Text.literal("FW");
		if(enchantment == Enchantments.BINDING_CURSE)
			return Text.literal("BI").formatted(Formatting.RED);
		if(enchantment == Enchantments.SOUL_SPEED)
			return Text.literal("SO");
		if(enchantment == Enchantments.SWIFT_SNEAK)
			return Text.literal("SN");
		if(enchantment == Enchantments.SHARPNESS)
			return Text.literal("SH");
		if(enchantment == Enchantments.SMITE)
			return Text.literal("SM");
		if(enchantment == Enchantments.BANE_OF_ARTHROPODS)
			return Text.literal("BA");
		if(enchantment == Enchantments.KNOCKBACK)
			return Text.literal("KN");
		if(enchantment == Enchantments.FIRE_ASPECT)
			return Text.literal("FI");
		if(enchantment == Enchantments.LOOTING)
			return Text.literal("LO");
		if(enchantment == Enchantments.SWEEPING_EDGE)
			return Text.literal("SW");
		if(enchantment == Enchantments.EFFICIENCY)
			return Text.literal("EF");
		if(enchantment == Enchantments.SILK_TOUCH)
			return Text.literal("SI");
		if(enchantment == Enchantments.UNBREAKING)
			return Text.literal("UN");
		if(enchantment == Enchantments.FORTUNE)
			return Text.literal("FO");
		if(enchantment == Enchantments.POWER)
			return Text.literal("PO");
		if(enchantment == Enchantments.PUNCH)
			return Text.literal("PU");
		if(enchantment == Enchantments.FLAME)
			return Text.literal("FL");
		if(enchantment == Enchantments.INFINITY)
			return Text.literal("IN");
		if(enchantment == Enchantments.LUCK_OF_THE_SEA)
			return Text.literal("LS");
		if(enchantment == Enchantments.LURE)
			return Text.literal("LU");
		if(enchantment == Enchantments.LOYALTY)
			return Text.literal("LY");
		if(enchantment == Enchantments.IMPALING)
			return Text.literal("IM");
		if(enchantment == Enchantments.RIPTIDE)
			return Text.literal("RI");
		if(enchantment == Enchantments.CHANNELING)
			return Text.literal("CH");
		if(enchantment == Enchantments.MULTISHOT)
			return Text.literal("MU");
		if(enchantment == Enchantments.QUICK_CHARGE)
			return Text.literal("QC");
		if(enchantment == Enchantments.PIERCING)
			return Text.literal("PI");
		if(enchantment == Enchantments.DENSITY)
			return Text.literal("DN");
		if(enchantment == Enchantments.BREACH)
			return Text.literal("BR");
		if(enchantment == Enchantments.WIND_BURST)
			return Text.literal("WB");
		if(enchantment == Enchantments.MENDING)
			return Text.literal("ME");
		if(enchantment == Enchantments.VANISHING_CURSE)
			return Text.literal("VA").formatted(Formatting.RED);
		return Text.literal("??");
	}
}
