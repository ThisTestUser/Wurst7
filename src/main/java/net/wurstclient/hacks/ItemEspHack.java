/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.awt.Color;
import java.util.ArrayList;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ColorSetting;
import net.wurstclient.settings.EspBoxSizeSetting;
import net.wurstclient.settings.EspStyleSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"item esp", "ItemTracers", "item tracers"})
public final class ItemEspHack extends Hack implements UpdateListener,
	CameraTransformViewBobbingListener, RenderListener
{
	private final CheckboxSetting names =
		new CheckboxSetting("Show item names", true);
	
	private final SliderSetting range = new SliderSetting("Item name range",
		"Items names will be shown if less than this distance.\n"
			+ "200 = always display item names",
		30, 5, 200, 1, ValueDisplay.DECIMAL.withLabel(200, "always show"));
	
	private final EspStyleSetting style = new EspStyleSetting();
	
	private final EspBoxSizeSetting boxSize = new EspBoxSizeSetting(
		"\u00a7lAccurate\u00a7r mode shows the exact hitbox of each item.\n"
			+ "\u00a7lFancy\u00a7r mode shows larger boxes that look better.");
	
	private final ColorSetting color = new ColorSetting("Color",
		"Items will be highlighted in this color.", Color.YELLOW);
	
	private final ArrayList<ItemEntity> items = new ArrayList<>();
	
	public ItemEspHack()
	{
		super("ItemESP");
		setCategory(Category.RENDER);
		addSetting(names);
		addSetting(range);
		addSetting(style);
		addSetting(boxSize);
		addSetting(color);
	}
	
	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(RenderListener.class, this);
	}
	
	@Override
	public void onUpdate()
	{
		items.clear();
		for(Entity entity : MC.world.getEntities())
			if(entity instanceof ItemEntity)
				items.add((ItemEntity)entity);
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(style.hasLines())
			event.cancel();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		RenderSystem.depthFunc(GlConst.GL_ALWAYS);
		
		VertexConsumerProvider.Immediate vcp =
			MC.getBufferBuilders().getEntityVertexConsumers();
		
		matrixStack.push();
		
		RegionPos region = RenderUtils.getCameraRegion();
		RenderUtils.applyRegionalRenderOffset(matrixStack, region);
		
		if(style.hasBoxes())
			renderBoxes(matrixStack, vcp, partialTicks, region);
		
		if(style.hasLines())
			renderTracers(matrixStack, vcp, partialTicks, region);
		
		matrixStack.pop();
		
		if(names.isChecked())
			renderItemNames(matrixStack, vcp, partialTicks);
		
		vcp.draw(WurstRenderLayers.ESP_LINES);
	}
	
	private void renderBoxes(MatrixStack matrixStack,
		VertexConsumerProvider vcp, float partialTicks, RegionPos region)
	{
		double extraSize = boxSize.getExtraSize() / 2;
		Vec3d offset = region.negate().toVec3d().add(0, extraSize, 0);
		VertexConsumer buffer = vcp.getBuffer(WurstRenderLayers.ESP_LINES);
		
		for(ItemEntity e : items)
		{
			Box box = EntityUtils.getLerpedBox(e, partialTicks).offset(offset)
				.expand(extraSize);
			
			RenderUtils.drawOutlinedBox(matrixStack, buffer, box,
				color.getColorI(0x80));
		}
	}
	
	private void renderTracers(MatrixStack matrixStack,
		VertexConsumerProvider vcp, float partialTicks, RegionPos region)
	{
		VertexConsumer buffer = vcp.getBuffer(WurstRenderLayers.ESP_LINES);
		
		Vec3d regionVec = region.toVec3d();
		Vec3d start = RotationUtils.getClientLookVec(partialTicks)
			.add(RenderUtils.getCameraPos()).subtract(regionVec);
		
		for(ItemEntity e : items)
		{
			Vec3d end = EntityUtils.getLerpedBox(e, partialTicks).getCenter()
				.subtract(regionVec);
			
			RenderUtils.drawLine(matrixStack, buffer, start, end,
				color.getColorI(0x80));
		}
	}
	
	private void renderItemNames(MatrixStack matrixStack,
		VertexConsumerProvider vcp, float partialTicks)
	{
		for(ItemEntity e : items)
			if(range.getValue() >= 200
				|| e.squaredDistanceTo(MC.player) < range.getValueSq())
			{
				ItemStack stack = e.getStack();
				Text name = Text.empty().append(stack.getName())
					.formatted(stack.getRarity().getFormatting());
				Text text = Text.literal(stack.getCount() + "x ").append(name);
				matrixStack.scale(0.5F, 0.5F, 0.5F);
				RenderUtils.renderTag(matrixStack, text, e, vcp, 0xffffff, 0.6F,
					0.3, partialTicks);
			}
	}
}
