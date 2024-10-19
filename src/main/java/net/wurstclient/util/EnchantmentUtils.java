/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public enum EnchantmentUtils
{
	;
	
	public static MutableText getShortName(String enchantment)
	{
		if(enchantment.equals("minecraft:protection"))
			return Text.literal("PR");
		if(enchantment.equals("minecraft:fire_protection"))
			return Text.literal("FP");
		if(enchantment.equals("minecraft:feather_falling"))
			return Text.literal("FF");
		if(enchantment.equals("minecraft:blast_protection"))
			return Text.literal("BP");
		if(enchantment.equals("minecraft:projectile_protection"))
			return Text.literal("PP");
		if(enchantment.equals("minecraft:respiration"))
			return Text.literal("RS");
		if(enchantment.equals("minecraft:aqua_affinity"))
			return Text.literal("AQ");
		if(enchantment.equals("minecraft:thorns"))
			return Text.literal("TH");
		if(enchantment.equals("minecraft:depth_strider"))
			return Text.literal("DS");
		if(enchantment.equals("minecraft:frost_walker"))
			return Text.literal("FW");
		if(enchantment.equals("minecraft:binding_curse"))
			return Text.literal("BI").formatted(Formatting.RED);
		if(enchantment.equals("minecraft:soul_speed"))
			return Text.literal("SO");
		if(enchantment.equals("minecraft:swift_sneak"))
			return Text.literal("SN");
		if(enchantment.equals("minecraft:sharpness"))
			return Text.literal("SH");
		if(enchantment.equals("minecraft:smite"))
			return Text.literal("SM");
		if(enchantment.equals("minecraft:bane_of_arthropods"))
			return Text.literal("BA");
		if(enchantment.equals("minecraft:knockback"))
			return Text.literal("KN");
		if(enchantment.equals("minecraft:fire_aspect"))
			return Text.literal("FI");
		if(enchantment.equals("minecraft:looting"))
			return Text.literal("LO");
		if(enchantment.equals("minecraft:sweeping_edge"))
			return Text.literal("SW");
		if(enchantment.equals("minecraft:efficiency"))
			return Text.literal("EF");
		if(enchantment.equals("minecraft:silk_touch"))
			return Text.literal("SI");
		if(enchantment.equals("minecraft:unbreaking"))
			return Text.literal("UN");
		if(enchantment.equals("minecraft:fortune"))
			return Text.literal("FO");
		if(enchantment.equals("minecraft:power"))
			return Text.literal("PO");
		if(enchantment.equals("minecraft:punch"))
			return Text.literal("PU");
		if(enchantment.equals("minecraft:flame"))
			return Text.literal("FL");
		if(enchantment.equals("minecraft:infinity"))
			return Text.literal("IN");
		if(enchantment.equals("minecraft:luck_of_the_sea"))
			return Text.literal("LS");
		if(enchantment.equals("minecraft:lure"))
			return Text.literal("LU");
		if(enchantment.equals("minecraft:loyalty"))
			return Text.literal("LY");
		if(enchantment.equals("minecraft:impaling"))
			return Text.literal("IM");
		if(enchantment.equals("minecraft:riptide"))
			return Text.literal("RI");
		if(enchantment.equals("minecraft:channeling"))
			return Text.literal("CH");
		if(enchantment.equals("minecraft:multishot"))
			return Text.literal("MU");
		if(enchantment.equals("minecraft:quick_charge"))
			return Text.literal("QC");
		if(enchantment.equals("minecraft:piercing"))
			return Text.literal("PI");
		if(enchantment.equals("minecraft:density"))
			return Text.literal("DN");
		if(enchantment.equals("minecraft:breach"))
			return Text.literal("BR");
		if(enchantment.equals("minecraft:wind_burst"))
			return Text.literal("WB");
		if(enchantment.equals("minecraft:mending"))
			return Text.literal("ME");
		if(enchantment.equals("minecraft:vanishing_curse"))
			return Text.literal("VA").formatted(Formatting.RED);
		return Text.literal("??");
	}
}
