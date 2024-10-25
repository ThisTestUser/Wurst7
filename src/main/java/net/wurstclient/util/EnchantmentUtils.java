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
	
	public static MutableText getShortName(String enchantment, boolean curse)
	{
		if(enchantment.contains(":"))
			enchantment = enchantment.substring(enchantment.indexOf(":") + 1);
		
		String name;
		if(enchantment.equals("respiration"))
			name = "RS";
		else if(enchantment.equals("aqua_affinity"))
			name = "AQ";
		else if(enchantment.equals("binding_curse"))
			name = "BI";
		else if(enchantment.equals("soul_speed"))
			name = "SO";
		else if(enchantment.equals("swift_sneak"))
			name = "SN";
		else if(enchantment.equals("sweeping_edge"))
			name = "SW";
		else if(enchantment.equals("loyalty"))
			name = "LY";
		else if(enchantment.equals("density"))
			name = "DN";
		else if(enchantment.equals("vanishing_curse"))
			name = "VA";
		else
			name = getShortNameSimple(enchantment).toUpperCase();
		
		MutableText text = Text.literal(name);
		if(curse)
			text.formatted(Formatting.RED);
		
		return text;
	}
	
	private static String getShortNameSimple(String enchantmentName)
	{
		if(enchantmentName.contains("_"))
		{
			String[] words = enchantmentName.split("_");
			if(words.length >= 2 && words[0].length() > 0
				&& words[words.length - 1].length() > 0)
				return words[0].substring(0, 1)
					+ words[words.length - 1].substring(0, 1);
		}
		
		return enchantmentName.length() >= 2 ? enchantmentName.substring(0, 2)
			: enchantmentName;
	}
}
