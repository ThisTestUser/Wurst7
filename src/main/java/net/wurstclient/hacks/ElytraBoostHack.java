/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.function.Predicate;

import net.minecraft.client.option.GameOptions;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.events.PlayerMoveListener;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public class ElytraBoostHack extends Hack
	implements PlayerMoveListener, PreMotionListener
{
	private final EnumSetting<BoostMode> mode = new EnumSetting<>("Boost Mode",
		BoostMode.values(), BoostMode.FORWARD_KEY);
	
	private final SliderSetting acceleration = new SliderSetting("Acceleration",
		"Your velocity will be increased by this value every tick,"
			+ " when you are under the maximum speed.",
		0.1, 0, 0.3, 0.01, ValueDisplay.DECIMAL);
	
	private final SliderSetting deceleration = new SliderSetting("Deceleration",
		"Your velocity will be decreased by this value every tick,"
			+ " when you are over the maximum speed, or when you are pressing the back key.",
		0.1, 0, 0.3, 0.01, ValueDisplay.DECIMAL);
	
	private final SliderSetting maxHorizSpeed = new SliderSetting(
		"Max Horizontal Speed",
		"The maximum horizontal speed in blocks per second before the boost stops.",
		35, 0, 200, 1, ValueDisplay.DECIMAL);
	
	private final SliderSetting pitch = new SliderSetting("Minimum Pitch",
		"Your pitch must be above this value to get the boost.", -4, -20, 0,
		-0.1, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting sneakDown = new CheckboxSetting("Sneak Down",
		"Press the sneak key to stop moving horizontally.", true);
	
	private final SliderSetting sneakDownSpeed =
		new SliderSetting("Sneak Down Speed",
			"The speed you will move down when the sneak key is pressed.\n"
				+ "Set to 0 to move down at the default speed.",
			0, 0, 2, 0.01, ValueDisplay.DECIMAL.withLabel(0, "default speed"));
	
	public ElytraBoostHack()
	{
		super("ElytraBoost");
		setCategory(Category.MOVEMENT);
		addSetting(mode);
		addSetting(acceleration);
		addSetting(deceleration);
		addSetting(maxHorizSpeed);
		addSetting(pitch);
		addSetting(sneakDown);
		addSetting(sneakDownSpeed);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(PlayerMoveListener.class, this);
		EVENTS.add(PreMotionListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerMoveListener.class, this);
		EVENTS.remove(PreMotionListener.class, this);
	}
	
	@Override
	public void onPlayerMove(PlayerMoveEvent event)
	{
		if(sneakDown.isChecked() && MC.player.isFallFlying()
			&& MC.options.sneakKey.isPressed() && !MC.player.isOnGround())
		{
			double y = sneakDownSpeed.getValue() == 0
				? MC.player.getVelocity().y : -sneakDownSpeed.getValue();
			MC.player.setVelocity(new Vec3d(0, y, 0));
		}
	}
	
	@Override
	public void onPreMotion()
	{
		if(!MC.player.isFallFlying())
			return;
		
		Vec3d speed = new Vec3d(MC.player.getX() - MC.player.prevX,
			MC.player.getY() - MC.player.prevY,
			MC.player.getZ() - MC.player.prevZ).multiply(20);
		float yawRad = MC.player.getYaw() * MathHelper.RADIANS_PER_DEGREE;
		
		if(speed.horizontalLength() > maxHorizSpeed.getValueF()
			|| MC.options.backKey.isPressed())
			MC.player.addVelocity(
				MathHelper.sin(-yawRad) * -deceleration.getValueF(), 0,
				MathHelper.cos(yawRad) * -deceleration.getValueF());
		else if(speed.horizontalLength() < maxHorizSpeed.getValueF()
			&& MC.player.getPitch() > pitch.getValueF()
			&& mode.getSelected().condition.test(MC.options))
			MC.player.addVelocity(
				MathHelper.sin(-yawRad) * acceleration.getValueF(), 0,
				MathHelper.cos(yawRad) * acceleration.getValueF());
	}
	
	private enum BoostMode
	{
		FORWARD_KEY("Forward key pressed", o -> o.forwardKey.isPressed()),
		ALWAYS("Always", o -> true);
		
		private final String name;
		private final Predicate<GameOptions> condition;
		
		private BoostMode(String name, Predicate<GameOptions> condition)
		{
			this.name = name;
			this.condition = condition;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
