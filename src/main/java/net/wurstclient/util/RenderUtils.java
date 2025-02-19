/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.ArrayList;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;

import net.minecraft.block.Blocks;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.wurstclient.WurstClient;
import net.wurstclient.WurstRenderLayers;
import net.wurstclient.hacks.NameTagsHack;

public enum RenderUtils
{
	;
	
	private static final Box DEFAULT_BOX = new Box(0, 0, 0, 1, 1, 1);
	
	public static void applyRegionalRenderOffset(MatrixStack matrixStack)
	{
		applyRegionalRenderOffset(matrixStack, getCameraRegion());
	}
	
	public static void applyRegionalRenderOffset(MatrixStack matrixStack,
		Chunk chunk)
	{
		applyRegionalRenderOffset(matrixStack, RegionPos.of(chunk.getPos()));
	}
	
	public static void applyRegionalRenderOffset(MatrixStack matrixStack,
		RegionPos region)
	{
		Vec3d offset = region.toVec3d().subtract(getCameraPos());
		matrixStack.translate(offset.x, offset.y, offset.z);
	}
	
	public static void applyRenderOffset(MatrixStack matrixStack)
	{
		Vec3d camPos = getCameraPos();
		matrixStack.translate(-camPos.x, -camPos.y, -camPos.z);
	}
	
	public static Vec3d getCameraPos()
	{
		Camera camera = WurstClient.MC.getBlockEntityRenderDispatcher().camera;
		if(camera == null)
			return Vec3d.ZERO;
		
		return camera.getPos();
	}
	
	public static BlockPos getCameraBlockPos()
	{
		Camera camera = WurstClient.MC.getBlockEntityRenderDispatcher().camera;
		if(camera == null)
			return BlockPos.ORIGIN;
		
		return camera.getBlockPos();
	}
	
	public static RegionPos getCameraRegion()
	{
		return RegionPos.of(getCameraBlockPos());
	}
	
	public static float[] getRainbowColor()
	{
		float x = System.currentTimeMillis() % 2000 / 1000F;
		float pi = (float)Math.PI;
		
		float[] rainbow = new float[3];
		rainbow[0] = 0.5F + 0.5F * MathHelper.sin(x * pi);
		rainbow[1] = 0.5F + 0.5F * MathHelper.sin((x + 4F / 3F) * pi);
		rainbow[2] = 0.5F + 0.5F * MathHelper.sin((x + 8F / 3F) * pi);
		return rainbow;
	}
	
	public static void setShaderColor(float[] rgb, float opacity)
	{
		RenderSystem.setShaderColor(rgb[0], rgb[1], rgb[2], opacity);
	}
	
	public static int toIntColor(float[] rgb, float opacity)
	{
		return (int)(MathHelper.clamp(opacity, 0, 1) * 255) << 24
			| (int)(MathHelper.clamp(rgb[0], 0, 1) * 255) << 16
			| (int)(MathHelper.clamp(rgb[1], 0, 1) * 255) << 8
			| (int)(MathHelper.clamp(rgb[2], 0, 1) * 255);
	}
	
	public static void drawLine(MatrixStack matrices, VertexConsumer buffer,
		Vec3d start, Vec3d end, int color)
	{
		drawLine(matrices, buffer, start.toVector3f(), end.toVector3f(), color);
	}
	
	public static void drawLine(MatrixStack matrices, VertexConsumer buffer,
		Vector3f start, Vector3f end, int color)
	{
		MatrixStack.Entry entry = matrices.peek();
		Vector3f normal = new Vector3f(end).sub(start).normalize();
		
		buffer.vertex(entry, start).color(color).normal(entry, normal);
		buffer.vertex(entry, end).color(color).normal(entry, normal);
	}
	
	public static void drawCurvedLine(MatrixStack matrices,
		VertexConsumer buffer, ArrayList<Vec3d> points, int color)
	{
		if(points.size() < 2)
			return;
		
		MatrixStack.Entry entry = matrices.peek();
		Vector3f first = points.get(0).toVector3f();
		Vector3f second = points.get(1).toVector3f();
		Vector3f normal = new Vector3f(first).sub(second).normalize();
		buffer.vertex(entry, first).color(color).normal(entry, normal);
		
		for(int i = 1; i < points.size(); i++)
		{
			Vector3f prev = points.get(i - 1).toVector3f();
			Vector3f current = points.get(i).toVector3f();
			normal = new Vector3f(current).sub(prev).normalize();
			buffer.vertex(entry, current).color(color).normal(entry, normal);
		}
	}
	
	public static void drawSolidBox(MatrixStack matrices, VertexConsumer buffer,
		Box box, int color)
	{
		MatrixStack.Entry entry = matrices.peek();
		float x1 = (float)box.minX;
		float y1 = (float)box.minY;
		float z1 = (float)box.minZ;
		float x2 = (float)box.maxX;
		float y2 = (float)box.maxY;
		float z2 = (float)box.maxZ;
		
		buffer.vertex(entry, x1, y1, z1).color(color);
		buffer.vertex(entry, x2, y1, z1).color(color);
		buffer.vertex(entry, x2, y1, z2).color(color);
		buffer.vertex(entry, x1, y1, z2).color(color);
		
		buffer.vertex(entry, x1, y2, z1).color(color);
		buffer.vertex(entry, x1, y2, z2).color(color);
		buffer.vertex(entry, x2, y2, z2).color(color);
		buffer.vertex(entry, x2, y2, z1).color(color);
		
		buffer.vertex(entry, x1, y1, z1).color(color);
		buffer.vertex(entry, x1, y2, z1).color(color);
		buffer.vertex(entry, x2, y2, z1).color(color);
		buffer.vertex(entry, x2, y1, z1).color(color);
		
		buffer.vertex(entry, x2, y1, z1).color(color);
		buffer.vertex(entry, x2, y2, z1).color(color);
		buffer.vertex(entry, x2, y2, z2).color(color);
		buffer.vertex(entry, x2, y1, z2).color(color);
		
		buffer.vertex(entry, x1, y1, z2).color(color);
		buffer.vertex(entry, x2, y1, z2).color(color);
		buffer.vertex(entry, x2, y2, z2).color(color);
		buffer.vertex(entry, x1, y2, z2).color(color);
		
		buffer.vertex(entry, x1, y1, z1).color(color);
		buffer.vertex(entry, x1, y1, z2).color(color);
		buffer.vertex(entry, x1, y2, z2).color(color);
		buffer.vertex(entry, x1, y2, z1).color(color);
	}
	
	public static void drawSolidBox(MatrixStack matrixStack)
	{
		drawSolidBox(DEFAULT_BOX, matrixStack);
	}
	
	public static void drawSolidBox(Box bb, MatrixStack matrixStack)
	{
		float minX = (float)bb.minX;
		float minY = (float)bb.minY;
		float minZ = (float)bb.minZ;
		float maxX = (float)bb.maxX;
		float maxY = (float)bb.maxY;
		float maxZ = (float)bb.maxZ;
		
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		RenderSystem.setShader(ShaderProgramKeys.POSITION);
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
		
		bufferBuilder.vertex(matrix, minX, minY, minZ);
		bufferBuilder.vertex(matrix, maxX, minY, minZ);
		bufferBuilder.vertex(matrix, maxX, minY, maxZ);
		bufferBuilder.vertex(matrix, minX, minY, maxZ);
		
		bufferBuilder.vertex(matrix, minX, maxY, minZ);
		bufferBuilder.vertex(matrix, minX, maxY, maxZ);
		bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
		bufferBuilder.vertex(matrix, maxX, maxY, minZ);
		
		bufferBuilder.vertex(matrix, minX, minY, minZ);
		bufferBuilder.vertex(matrix, minX, maxY, minZ);
		bufferBuilder.vertex(matrix, maxX, maxY, minZ);
		bufferBuilder.vertex(matrix, maxX, minY, minZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, minZ);
		bufferBuilder.vertex(matrix, maxX, maxY, minZ);
		bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
		bufferBuilder.vertex(matrix, maxX, minY, maxZ);
		
		bufferBuilder.vertex(matrix, minX, minY, maxZ);
		bufferBuilder.vertex(matrix, maxX, minY, maxZ);
		bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
		bufferBuilder.vertex(matrix, minX, maxY, maxZ);
		
		bufferBuilder.vertex(matrix, minX, minY, minZ);
		bufferBuilder.vertex(matrix, minX, minY, maxZ);
		bufferBuilder.vertex(matrix, minX, maxY, maxZ);
		bufferBuilder.vertex(matrix, minX, maxY, minZ);
		
		BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
	}
	
	public static void drawSolidBox(Box bb, VertexBuffer vertexBuffer)
	{
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
		drawSolidBox(bb, bufferBuilder);
		BuiltBuffer buffer = bufferBuilder.end();
		
		vertexBuffer.bind();
		vertexBuffer.upload(buffer);
		VertexBuffer.unbind();
	}
	
	public static void drawSolidBox(Box bb, BufferBuilder bufferBuilder)
	{
		float minX = (float)bb.minX;
		float minY = (float)bb.minY;
		float minZ = (float)bb.minZ;
		float maxX = (float)bb.maxX;
		float maxY = (float)bb.maxY;
		float maxZ = (float)bb.maxZ;
		
		bufferBuilder.vertex(minX, minY, minZ);
		bufferBuilder.vertex(maxX, minY, minZ);
		bufferBuilder.vertex(maxX, minY, maxZ);
		bufferBuilder.vertex(minX, minY, maxZ);
		
		bufferBuilder.vertex(minX, maxY, minZ);
		bufferBuilder.vertex(minX, maxY, maxZ);
		bufferBuilder.vertex(maxX, maxY, maxZ);
		bufferBuilder.vertex(maxX, maxY, minZ);
		
		bufferBuilder.vertex(minX, minY, minZ);
		bufferBuilder.vertex(minX, maxY, minZ);
		bufferBuilder.vertex(maxX, maxY, minZ);
		bufferBuilder.vertex(maxX, minY, minZ);
		
		bufferBuilder.vertex(maxX, minY, minZ);
		bufferBuilder.vertex(maxX, maxY, minZ);
		bufferBuilder.vertex(maxX, maxY, maxZ);
		bufferBuilder.vertex(maxX, minY, maxZ);
		
		bufferBuilder.vertex(minX, minY, maxZ);
		bufferBuilder.vertex(maxX, minY, maxZ);
		bufferBuilder.vertex(maxX, maxY, maxZ);
		bufferBuilder.vertex(minX, maxY, maxZ);
		
		bufferBuilder.vertex(minX, minY, minZ);
		bufferBuilder.vertex(minX, minY, maxZ);
		bufferBuilder.vertex(minX, maxY, maxZ);
		bufferBuilder.vertex(minX, maxY, minZ);
	}
	
	public static void drawOutlinedBox(MatrixStack matrices,
		VertexConsumer buffer, Box box, int color)
	{
		MatrixStack.Entry entry = matrices.peek();
		float x1 = (float)box.minX;
		float y1 = (float)box.minY;
		float z1 = (float)box.minZ;
		float x2 = (float)box.maxX;
		float y2 = (float)box.maxY;
		float z2 = (float)box.maxZ;
		
		// bottom lines
		buffer.vertex(entry, x1, y1, z1).color(color).normal(entry, 1, 0, 0);
		buffer.vertex(entry, x2, y1, z1).color(color).normal(entry, 1, 0, 0);
		buffer.vertex(entry, x1, y1, z1).color(color).normal(entry, 0, 0, 1);
		buffer.vertex(entry, x1, y1, z2).color(color).normal(entry, 0, 0, 1);
		buffer.vertex(entry, x2, y1, z1).color(color).normal(entry, 0, 0, 1);
		buffer.vertex(entry, x2, y1, z2).color(color).normal(entry, 0, 0, 1);
		buffer.vertex(entry, x1, y1, z2).color(color).normal(entry, 1, 0, 0);
		buffer.vertex(entry, x2, y1, z2).color(color).normal(entry, 1, 0, 0);
		
		// top lines
		buffer.vertex(entry, x1, y2, z1).color(color).normal(entry, 1, 0, 0);
		buffer.vertex(entry, x2, y2, z1).color(color).normal(entry, 1, 0, 0);
		buffer.vertex(entry, x1, y2, z1).color(color).normal(entry, 0, 0, 1);
		buffer.vertex(entry, x1, y2, z2).color(color).normal(entry, 0, 0, 1);
		buffer.vertex(entry, x2, y2, z1).color(color).normal(entry, 0, 0, 1);
		buffer.vertex(entry, x2, y2, z2).color(color).normal(entry, 0, 0, 1);
		buffer.vertex(entry, x1, y2, z2).color(color).normal(entry, 1, 0, 0);
		buffer.vertex(entry, x2, y2, z2).color(color).normal(entry, 1, 0, 0);
		
		// side lines
		buffer.vertex(entry, x1, y1, z1).color(color).normal(entry, 0, 1, 0);
		buffer.vertex(entry, x1, y2, z1).color(color).normal(entry, 0, 1, 0);
		buffer.vertex(entry, x2, y1, z1).color(color).normal(entry, 0, 1, 0);
		buffer.vertex(entry, x2, y2, z1).color(color).normal(entry, 0, 1, 0);
		buffer.vertex(entry, x1, y1, z2).color(color).normal(entry, 0, 1, 0);
		buffer.vertex(entry, x1, y2, z2).color(color).normal(entry, 0, 1, 0);
		buffer.vertex(entry, x2, y1, z2).color(color).normal(entry, 0, 1, 0);
		buffer.vertex(entry, x2, y2, z2).color(color).normal(entry, 0, 1, 0);
	}
	
	public static void drawOutlinedBox(MatrixStack matrixStack)
	{
		drawOutlinedBox(DEFAULT_BOX, matrixStack);
	}
	
	public static void drawOutlinedBox(Box bb, MatrixStack matrixStack)
	{
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		RenderSystem.setShader(ShaderProgramKeys.POSITION);
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
		
		float minX = (float)bb.minX;
		float minY = (float)bb.minY;
		float minZ = (float)bb.minZ;
		float maxX = (float)bb.maxX;
		float maxY = (float)bb.maxY;
		float maxZ = (float)bb.maxZ;
		
		bufferBuilder.vertex(matrix, minX, minY, minZ);
		bufferBuilder.vertex(matrix, maxX, minY, minZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, minZ);
		bufferBuilder.vertex(matrix, maxX, minY, maxZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, maxZ);
		bufferBuilder.vertex(matrix, minX, minY, maxZ);
		
		bufferBuilder.vertex(matrix, minX, minY, maxZ);
		bufferBuilder.vertex(matrix, minX, minY, minZ);
		
		bufferBuilder.vertex(matrix, minX, minY, minZ);
		bufferBuilder.vertex(matrix, minX, maxY, minZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, minZ);
		bufferBuilder.vertex(matrix, maxX, maxY, minZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, maxZ);
		bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
		
		bufferBuilder.vertex(matrix, minX, minY, maxZ);
		bufferBuilder.vertex(matrix, minX, maxY, maxZ);
		
		bufferBuilder.vertex(matrix, minX, maxY, minZ);
		bufferBuilder.vertex(matrix, maxX, maxY, minZ);
		
		bufferBuilder.vertex(matrix, maxX, maxY, minZ);
		bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
		
		bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
		bufferBuilder.vertex(matrix, minX, maxY, maxZ);
		
		bufferBuilder.vertex(matrix, minX, maxY, maxZ);
		bufferBuilder.vertex(matrix, minX, maxY, minZ);
		
		BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
	}
	
	public static void drawOutlinedBox(Box bb, VertexBuffer vertexBuffer)
	{
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
		drawOutlinedBox(bb, bufferBuilder);
		BuiltBuffer buffer = bufferBuilder.end();
		
		vertexBuffer.bind();
		vertexBuffer.upload(buffer);
		VertexBuffer.unbind();
	}
	
	public static void drawOutlinedBox(Box bb, BufferBuilder bufferBuilder)
	{
		float minX = (float)bb.minX;
		float minY = (float)bb.minY;
		float minZ = (float)bb.minZ;
		float maxX = (float)bb.maxX;
		float maxY = (float)bb.maxY;
		float maxZ = (float)bb.maxZ;
		
		bufferBuilder.vertex(minX, minY, minZ);
		bufferBuilder.vertex(maxX, minY, minZ);
		
		bufferBuilder.vertex(maxX, minY, minZ);
		bufferBuilder.vertex(maxX, minY, maxZ);
		
		bufferBuilder.vertex(maxX, minY, maxZ);
		bufferBuilder.vertex(minX, minY, maxZ);
		
		bufferBuilder.vertex(minX, minY, maxZ);
		bufferBuilder.vertex(minX, minY, minZ);
		
		bufferBuilder.vertex(minX, minY, minZ);
		bufferBuilder.vertex(minX, maxY, minZ);
		
		bufferBuilder.vertex(maxX, minY, minZ);
		bufferBuilder.vertex(maxX, maxY, minZ);
		
		bufferBuilder.vertex(maxX, minY, maxZ);
		bufferBuilder.vertex(maxX, maxY, maxZ);
		
		bufferBuilder.vertex(minX, minY, maxZ);
		bufferBuilder.vertex(minX, maxY, maxZ);
		
		bufferBuilder.vertex(minX, maxY, minZ);
		bufferBuilder.vertex(maxX, maxY, minZ);
		
		bufferBuilder.vertex(maxX, maxY, minZ);
		bufferBuilder.vertex(maxX, maxY, maxZ);
		
		bufferBuilder.vertex(maxX, maxY, maxZ);
		bufferBuilder.vertex(minX, maxY, maxZ);
		
		bufferBuilder.vertex(minX, maxY, maxZ);
		bufferBuilder.vertex(minX, maxY, minZ);
	}
	
	public static void drawCrossBox(MatrixStack matrices, VertexConsumer buffer,
		Box box, int color)
	{
		MatrixStack.Entry entry = matrices.peek();
		float x1 = (float)box.minX;
		float y1 = (float)box.minY;
		float z1 = (float)box.minZ;
		float x2 = (float)box.maxX;
		float y2 = (float)box.maxY;
		float z2 = (float)box.maxZ;
		
		// back
		buffer.vertex(entry, x1, y1, z1).color(color).normal(entry, 1, 1, 0);
		buffer.vertex(entry, x2, y2, z1).color(color).normal(entry, 1, 1, 0);
		buffer.vertex(entry, x2, y1, z1).color(color).normal(entry, -1, 1, 0);
		buffer.vertex(entry, x1, y2, z1).color(color).normal(entry, -1, 1, 0);
		
		// left
		buffer.vertex(entry, x2, y1, z1).color(color).normal(entry, 0, 1, 1);
		buffer.vertex(entry, x2, y2, z2).color(color).normal(entry, 0, 1, 1);
		buffer.vertex(entry, x2, y1, z2).color(color).normal(entry, 0, 1, -1);
		buffer.vertex(entry, x2, y2, z1).color(color).normal(entry, 0, 1, -1);
		
		// front
		buffer.vertex(entry, x2, y1, z2).color(color).normal(entry, -1, 1, 0);
		buffer.vertex(entry, x1, y2, z2).color(color).normal(entry, -1, 1, 0);
		buffer.vertex(entry, x1, y1, z2).color(color).normal(entry, 1, 1, 0);
		buffer.vertex(entry, x2, y2, z2).color(color).normal(entry, 1, 1, 0);
		
		// right
		buffer.vertex(entry, x1, y1, z2).color(color).normal(entry, 0, 1, -1);
		buffer.vertex(entry, x1, y2, z1).color(color).normal(entry, 0, 1, -1);
		buffer.vertex(entry, x1, y1, z1).color(color).normal(entry, 0, 1, 1);
		buffer.vertex(entry, x1, y2, z2).color(color).normal(entry, 0, 1, 1);
		
		// top
		buffer.vertex(entry, x1, y2, z2).color(color).normal(entry, 1, 0, -1);
		buffer.vertex(entry, x2, y2, z1).color(color).normal(entry, 1, 0, -1);
		buffer.vertex(entry, x1, y2, z1).color(color).normal(entry, 1, 0, 1);
		buffer.vertex(entry, x2, y2, z2).color(color).normal(entry, 1, 0, 1);
		
		// bottom
		buffer.vertex(entry, x2, y1, z1).color(color).normal(entry, -1, 0, 1);
		buffer.vertex(entry, x1, y1, z2).color(color).normal(entry, -1, 0, 1);
		buffer.vertex(entry, x1, y1, z1).color(color).normal(entry, 1, 0, 1);
		buffer.vertex(entry, x2, y1, z2).color(color).normal(entry, 1, 0, 1);
	}
	
	public static void drawCrossBox(Box bb, MatrixStack matrixStack)
	{
		float minX = (float)bb.minX;
		float minY = (float)bb.minY;
		float minZ = (float)bb.minZ;
		float maxX = (float)bb.maxX;
		float maxY = (float)bb.maxY;
		float maxZ = (float)bb.maxZ;
		
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
		
		bufferBuilder.vertex(matrix, minX, minY, minZ);
		bufferBuilder.vertex(matrix, maxX, maxY, minZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, minZ);
		bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, maxZ);
		bufferBuilder.vertex(matrix, minX, maxY, maxZ);
		
		bufferBuilder.vertex(matrix, minX, minY, maxZ);
		bufferBuilder.vertex(matrix, minX, maxY, minZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, minZ);
		bufferBuilder.vertex(matrix, minX, maxY, minZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, maxZ);
		bufferBuilder.vertex(matrix, maxX, maxY, minZ);
		
		bufferBuilder.vertex(matrix, minX, maxY, maxZ);
		bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
		
		bufferBuilder.vertex(matrix, minX, minY, minZ);
		bufferBuilder.vertex(matrix, minX, maxY, maxZ);
		
		bufferBuilder.vertex(matrix, minX, maxY, minZ);
		bufferBuilder.vertex(matrix, maxX, maxY, minZ);
		
		bufferBuilder.vertex(matrix, minX, maxY, maxZ);
		bufferBuilder.vertex(matrix, maxX, maxY, maxZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, minZ);
		bufferBuilder.vertex(matrix, minX, minY, maxZ);
		
		bufferBuilder.vertex(matrix, maxX, minY, maxZ);
		bufferBuilder.vertex(matrix, maxX, minY, minZ);
		
		BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
	}
	
	public static void drawCrossBox(Box bb, VertexBuffer vertexBuffer)
	{
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
		drawCrossBox(bb, bufferBuilder);
		BuiltBuffer buffer = bufferBuilder.end();
		
		vertexBuffer.bind();
		vertexBuffer.upload(buffer);
		VertexBuffer.unbind();
	}
	
	public static void drawCrossBox(Box bb, BufferBuilder bufferBuilder)
	{
		float minX = (float)bb.minX;
		float minY = (float)bb.minY;
		float minZ = (float)bb.minZ;
		float maxX = (float)bb.maxX;
		float maxY = (float)bb.maxY;
		float maxZ = (float)bb.maxZ;
		
		bufferBuilder.vertex(minX, minY, minZ);
		bufferBuilder.vertex(maxX, maxY, minZ);
		
		bufferBuilder.vertex(maxX, minY, minZ);
		bufferBuilder.vertex(maxX, maxY, maxZ);
		
		bufferBuilder.vertex(maxX, minY, maxZ);
		bufferBuilder.vertex(minX, maxY, maxZ);
		
		bufferBuilder.vertex(minX, minY, maxZ);
		bufferBuilder.vertex(minX, maxY, minZ);
		
		bufferBuilder.vertex(maxX, minY, minZ);
		bufferBuilder.vertex(minX, maxY, minZ);
		
		bufferBuilder.vertex(maxX, minY, maxZ);
		bufferBuilder.vertex(maxX, maxY, minZ);
		
		bufferBuilder.vertex(minX, minY, maxZ);
		bufferBuilder.vertex(maxX, maxY, maxZ);
		
		bufferBuilder.vertex(minX, minY, minZ);
		bufferBuilder.vertex(minX, maxY, maxZ);
		
		bufferBuilder.vertex(minX, maxY, minZ);
		bufferBuilder.vertex(maxX, maxY, maxZ);
		
		bufferBuilder.vertex(maxX, maxY, minZ);
		bufferBuilder.vertex(minX, maxY, maxZ);
		
		bufferBuilder.vertex(maxX, minY, minZ);
		bufferBuilder.vertex(minX, minY, maxZ);
		
		bufferBuilder.vertex(maxX, minY, maxZ);
		bufferBuilder.vertex(minX, minY, minZ);
	}
	
	public static void drawNode(Box bb, MatrixStack matrixStack)
	{
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		RenderSystem.setShader(ShaderProgramKeys.POSITION);
		
		double midX = (bb.minX + bb.maxX) / 2;
		double midY = (bb.minY + bb.maxY) / 2;
		double midZ = (bb.minZ + bb.maxZ) / 2;
		
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.maxZ);
		bufferBuilder.vertex(matrix, (float)bb.minX, (float)midY, (float)midZ);
		
		bufferBuilder.vertex(matrix, (float)bb.minX, (float)midY, (float)midZ);
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.minZ);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.minZ);
		bufferBuilder.vertex(matrix, (float)bb.maxX, (float)midY, (float)midZ);
		
		bufferBuilder.vertex(matrix, (float)bb.maxX, (float)midY, (float)midZ);
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.maxZ);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.maxY, (float)midZ);
		bufferBuilder.vertex(matrix, (float)bb.maxX, (float)midY, (float)midZ);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.maxY, (float)midZ);
		bufferBuilder.vertex(matrix, (float)bb.minX, (float)midY, (float)midZ);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.maxY, (float)midZ);
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.minZ);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.maxY, (float)midZ);
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.maxZ);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.minY, (float)midZ);
		bufferBuilder.vertex(matrix, (float)bb.maxX, (float)midY, (float)midZ);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.minY, (float)midZ);
		bufferBuilder.vertex(matrix, (float)bb.minX, (float)midY, (float)midZ);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.minY, (float)midZ);
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.minZ);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.minY, (float)midZ);
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.maxZ);
		
		BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
	}
	
	public static void drawNode(Box bb, VertexBuffer vertexBuffer)
	{
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
		drawNode(bb, bufferBuilder);
		BuiltBuffer buffer = bufferBuilder.end();
		
		vertexBuffer.bind();
		vertexBuffer.upload(buffer);
		VertexBuffer.unbind();
	}
	
	public static void drawNode(Box bb, BufferBuilder bufferBuilder)
	{
		float minX = (float)bb.minX;
		float minY = (float)bb.minY;
		float minZ = (float)bb.minZ;
		float maxX = (float)bb.maxX;
		float maxY = (float)bb.maxY;
		float maxZ = (float)bb.maxZ;
		float midX = (minX + maxX) / 2F;
		float midY = (minY + maxY) / 2F;
		float midZ = (minZ + maxZ) / 2F;
		
		bufferBuilder.vertex(midX, midY, maxZ);
		bufferBuilder.vertex(minX, midY, midZ);
		
		bufferBuilder.vertex(minX, midY, midZ);
		bufferBuilder.vertex(midX, midY, minZ);
		
		bufferBuilder.vertex(midX, midY, minZ);
		bufferBuilder.vertex(maxX, midY, midZ);
		
		bufferBuilder.vertex(maxX, midY, midZ);
		bufferBuilder.vertex(midX, midY, maxZ);
		
		bufferBuilder.vertex(midX, maxY, midZ);
		bufferBuilder.vertex(maxX, midY, midZ);
		
		bufferBuilder.vertex(midX, maxY, midZ);
		bufferBuilder.vertex(minX, midY, midZ);
		
		bufferBuilder.vertex(midX, maxY, midZ);
		bufferBuilder.vertex(midX, midY, minZ);
		
		bufferBuilder.vertex(midX, maxY, midZ);
		bufferBuilder.vertex(midX, midY, maxZ);
		
		bufferBuilder.vertex(midX, minY, midZ);
		bufferBuilder.vertex(maxX, midY, midZ);
		
		bufferBuilder.vertex(midX, minY, midZ);
		bufferBuilder.vertex(minX, midY, midZ);
		
		bufferBuilder.vertex(midX, minY, midZ);
		bufferBuilder.vertex(midX, midY, minZ);
		
		bufferBuilder.vertex(midX, minY, midZ);
		bufferBuilder.vertex(midX, midY, maxZ);
	}
	
	public static void drawArrow(MatrixStack matrices, VertexConsumer buffer,
		Vec3d from, Vec3d to, int color, float headSize)
	{
		matrices.push();
		MatrixStack.Entry entry = matrices.peek();
		Matrix4f matrix = entry.getPositionMatrix();
		
		// main line
		Vector3f fromJoml = from.toVector3f();
		Vector3f toJoml = to.toVector3f();
		Vector3f normal = to.subtract(from).normalize().toVector3f();
		buffer.vertex(entry, fromJoml).color(color).normal(entry, normal);
		buffer.vertex(entry, toJoml).color(color).normal(entry, normal);
		
		matrices.translate(to);
		matrices.scale(headSize, headSize, headSize);
		
		double xDiff = to.x - from.x;
		double yDiff = to.y - from.y;
		double zDiff = to.z - from.z;
		
		float xAngle = (float)(Math.atan2(yDiff, -zDiff) + Math.toRadians(90));
		matrix.rotate(xAngle, new Vector3f(1, 0, 0));
		
		double yzDiff = Math.sqrt(yDiff * yDiff + zDiff * zDiff);
		float zAngle = (float)Math.atan2(xDiff, yzDiff);
		matrix.rotate(zAngle, new Vector3f(0, 0, 1));
		
		// arrow head
		buffer.vertex(matrix, 0, 2, 1).color(color).normal(-1, 0, -1);
		buffer.vertex(matrix, -1, 2, 0).color(color).normal(-1, 0, -1);
		
		buffer.vertex(matrix, -1, 2, 0).color(color).normal(1, 0, -1);
		buffer.vertex(matrix, 0, 2, -1).color(color).normal(1, 0, -1);
		
		buffer.vertex(matrix, 0, 2, -1).color(color).normal(1, 0, 1);
		buffer.vertex(matrix, 1, 2, 0).color(color).normal(1, 0, 1);
		
		buffer.vertex(matrix, 1, 2, 0).color(color).normal(-1, 0, 1);
		buffer.vertex(matrix, 0, 2, 1).color(color).normal(-1, 0, 1);
		
		buffer.vertex(matrix, 1, 2, 0).color(color).normal(-1, 0, 0);
		buffer.vertex(matrix, -1, 2, 0).color(color).normal(-1, 0, 0);
		
		buffer.vertex(matrix, 0, 2, 1).color(color).normal(0, 0, -1);
		buffer.vertex(matrix, 0, 2, -1).color(color).normal(0, 0, 1);
		
		buffer.vertex(matrix, 0, 0, 0).color(color).normal(1, 2, 0);
		buffer.vertex(matrix, 1, 2, 0).color(color).normal(1, 2, 0);
		
		buffer.vertex(matrix, 0, 0, 0).color(color).normal(-1, 2, 0);
		buffer.vertex(matrix, -1, 2, 0).color(color).normal(-1, 2, 0);
		
		buffer.vertex(matrix, 0, 0, 0).color(color).normal(0, 2, -1);
		buffer.vertex(matrix, 0, 2, -1).color(color).normal(0, 2, -1);
		
		buffer.vertex(matrix, 0, 0, 0).color(color).normal(0, 2, 1);
		buffer.vertex(matrix, 0, 2, 1).color(color).normal(0, 2, 1);
		
		matrices.pop();
	}
	
	public static void drawArrow(Vec3d from, Vec3d to, MatrixStack matrixStack)
	{
		RenderSystem.setShader(ShaderProgramKeys.POSITION);
		
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
		
		double startX = from.x;
		double startY = from.y;
		double startZ = from.z;
		
		double endX = to.x;
		double endY = to.y;
		double endZ = to.z;
		
		matrixStack.push();
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		
		bufferBuilder.vertex(matrix, (float)startX, (float)startY,
			(float)startZ);
		bufferBuilder.vertex(matrix, (float)endX, (float)endY, (float)endZ);
		
		matrixStack.translate(endX, endY, endZ);
		matrixStack.scale(0.1F, 0.1F, 0.1F);
		
		double xDiff = endX - startX;
		double yDiff = endY - startY;
		double zDiff = endZ - startZ;
		
		float xAngle = (float)(Math.atan2(yDiff, -zDiff) + Math.toRadians(90));
		matrix.rotate(xAngle, new Vector3f(1, 0, 0));
		
		double yzDiff = Math.sqrt(yDiff * yDiff + zDiff * zDiff);
		float zAngle = (float)Math.atan2(xDiff, yzDiff);
		matrix.rotate(zAngle, new Vector3f(0, 0, 1));
		
		bufferBuilder.vertex(matrix, 0, 2, 1);
		bufferBuilder.vertex(matrix, -1, 2, 0);
		
		bufferBuilder.vertex(matrix, -1, 2, 0);
		bufferBuilder.vertex(matrix, 0, 2, -1);
		
		bufferBuilder.vertex(matrix, 0, 2, -1);
		bufferBuilder.vertex(matrix, 1, 2, 0);
		
		bufferBuilder.vertex(matrix, 1, 2, 0);
		bufferBuilder.vertex(matrix, 0, 2, 1);
		
		bufferBuilder.vertex(matrix, 1, 2, 0);
		bufferBuilder.vertex(matrix, -1, 2, 0);
		
		bufferBuilder.vertex(matrix, 0, 2, 1);
		bufferBuilder.vertex(matrix, 0, 2, -1);
		
		bufferBuilder.vertex(matrix, 0, 0, 0);
		bufferBuilder.vertex(matrix, 1, 2, 0);
		
		bufferBuilder.vertex(matrix, 0, 0, 0);
		bufferBuilder.vertex(matrix, -1, 2, 0);
		
		bufferBuilder.vertex(matrix, 0, 0, 0);
		bufferBuilder.vertex(matrix, 0, 2, -1);
		
		bufferBuilder.vertex(matrix, 0, 0, 0);
		bufferBuilder.vertex(matrix, 0, 2, 1);
		
		matrixStack.pop();
		
		BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
	}
	
	public static void drawArrow(Vec3d from, Vec3d to,
		VertexBuffer vertexBuffer)
	{
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator
			.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
		drawArrow(from, to, bufferBuilder);
		BuiltBuffer buffer = bufferBuilder.end();
		
		vertexBuffer.bind();
		vertexBuffer.upload(buffer);
		VertexBuffer.unbind();
	}
	
	public static void drawArrow(Vec3d from, Vec3d to,
		BufferBuilder bufferBuilder)
	{
		double startX = from.x;
		double startY = from.y;
		double startZ = from.z;
		
		double endX = to.x;
		double endY = to.y;
		double endZ = to.z;
		
		Matrix4f matrix = new Matrix4f();
		matrix.identity();
		
		bufferBuilder.vertex(matrix, (float)startX, (float)startY,
			(float)startZ);
		bufferBuilder.vertex(matrix, (float)endX, (float)endY, (float)endZ);
		
		matrix.translate((float)endX, (float)endY, (float)endZ);
		matrix.scale(0.1F, 0.1F, 0.1F);
		
		double xDiff = endX - startX;
		double yDiff = endY - startY;
		double zDiff = endZ - startZ;
		
		float xAngle = (float)(Math.atan2(yDiff, -zDiff) + Math.toRadians(90));
		matrix.rotate(xAngle, new Vector3f(1, 0, 0));
		
		double yzDiff = Math.sqrt(yDiff * yDiff + zDiff * zDiff);
		float zAngle = (float)Math.atan2(xDiff, yzDiff);
		matrix.rotate(zAngle, new Vector3f(0, 0, 1));
		
		bufferBuilder.vertex(matrix, 0, 2, 1);
		bufferBuilder.vertex(matrix, -1, 2, 0);
		
		bufferBuilder.vertex(matrix, -1, 2, 0);
		bufferBuilder.vertex(matrix, 0, 2, -1);
		
		bufferBuilder.vertex(matrix, 0, 2, -1);
		bufferBuilder.vertex(matrix, 1, 2, 0);
		
		bufferBuilder.vertex(matrix, 1, 2, 0);
		bufferBuilder.vertex(matrix, 0, 2, 1);
		
		bufferBuilder.vertex(matrix, 1, 2, 0);
		bufferBuilder.vertex(matrix, -1, 2, 0);
		
		bufferBuilder.vertex(matrix, 0, 2, 1);
		bufferBuilder.vertex(matrix, 0, 2, -1);
		
		bufferBuilder.vertex(matrix, 0, 0, 0);
		bufferBuilder.vertex(matrix, 1, 2, 0);
		
		bufferBuilder.vertex(matrix, 0, 0, 0);
		bufferBuilder.vertex(matrix, -1, 2, 0);
		
		bufferBuilder.vertex(matrix, 0, 0, 0);
		bufferBuilder.vertex(matrix, 0, 2, -1);
		
		bufferBuilder.vertex(matrix, 0, 0, 0);
		bufferBuilder.vertex(matrix, 0, 2, 1);
	}
	
	public static void drawItem(DrawContext context, ItemStack stack, int x,
		int y, boolean large)
	{
		MatrixStack matrixStack = context.getMatrices();
		
		matrixStack.push();
		matrixStack.translate(x, y, 0);
		if(large)
			matrixStack.scale(1.5F, 1.5F, 1.5F);
		else
			matrixStack.scale(0.75F, 0.75F, 0.75F);
		
		ItemStack renderStack = stack.isEmpty() || stack.getItem() == null
			? new ItemStack(Blocks.GRASS_BLOCK) : stack;
		
		DiffuseLighting.enableGuiDepthLighting();
		context.drawItem(renderStack, 0, 0);
		DiffuseLighting.disableGuiDepthLighting();
		
		matrixStack.pop();
		
		if(stack.isEmpty())
		{
			matrixStack.push();
			matrixStack.translate(x, y, 250);
			if(large)
				matrixStack.scale(2, 2, 2);
			
			TextRenderer tr = WurstClient.MC.textRenderer;
			context.drawText(tr, "?", 3, 2, 0xf0f0f0, true);
			
			matrixStack.pop();
		}
		
		RenderSystem.setShaderColor(1, 1, 1, 1);
	}
	
	/**
	 * Similar to {@link DrawContext#fill(int, int, int, int, int)}, but uses
	 * floating-point coordinates instead of integers.
	 */
	public static void fill2D(DrawContext context, float x1, float y1, float x2,
		float y2, int color)
	{
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		context.draw(consumers -> {
			VertexConsumer buffer = consumers.getBuffer(RenderLayer.getGui());
			buffer.vertex(matrix, x1, y1, 0).color(color);
			buffer.vertex(matrix, x1, y2, 0).color(color);
			buffer.vertex(matrix, x2, y2, 0).color(color);
			buffer.vertex(matrix, x2, y1, 0).color(color);
		});
	}
	
	/**
	 * Renders the given vertices in QUADS draw mode.
	 *
	 * @apiNote Due to back-face culling, quads will be invisible if their
	 *          vertices are not supplied in counter-clockwise order.
	 */
	public static void fillQuads2D(DrawContext context, float[][] vertices,
		int color)
	{
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		context.draw(consumers -> {
			VertexConsumer buffer = consumers.getBuffer(RenderLayer.getGui());
			for(float[] vertex : vertices)
				buffer.vertex(matrix, vertex[0], vertex[1], 0).color(color);
		});
	}
	
	/**
	 * Renders the given vertices in TRIANGLE_STRIP draw mode.
	 *
	 * @apiNote Due to back-face culling, triangles will be invisible if their
	 *          vertices are not supplied in counter-clockwise order.
	 */
	public static void fillTriangle2D(DrawContext context, float[][] vertices,
		int color)
	{
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		context.draw(consumers -> {
			VertexConsumer buffer =
				consumers.getBuffer(RenderLayer.getDebugFilledBox());
			for(float[] vertex : vertices)
				buffer.vertex(matrix, vertex[0], vertex[1], 0).color(color);
		});
	}
	
	/**
	 * Similar to {@link DrawContext#drawHorizontalLine(int, int, int, int)} and
	 * {@link DrawContext#drawVerticalLine(int, int, int, int)}, but supports
	 * diagonal lines, uses floating-point coordinates instead of integers, is
	 * one actual pixel wide instead of one scaled pixel, uses fewer draw calls
	 * than the vanilla method, and uses a z value of 1 to ensure that lines
	 * show up above fills.
	 */
	public static void drawLine2D(DrawContext context, float x1, float y1,
		float x2, float y2, int color)
	{
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		context.draw(consumers -> {
			VertexConsumer buffer =
				consumers.getBuffer(WurstRenderLayers.ONE_PIXEL_LINES);
			buffer.vertex(matrix, x1, y1, 1).color(color);
			buffer.vertex(matrix, x2, y2, 1).color(color);
		});
	}
	
	/**
	 * Similar to {@link DrawContext#drawBorder(int, int, int, int, int)}, but
	 * uses floating-point coordinates instead of integers, is one actual pixel
	 * wide instead of one scaled pixel, uses fewer draw calls than the vanilla
	 * method, and uses a z value of 1 to ensure that lines show up above fills.
	 */
	public static void drawBorder2D(DrawContext context, float x1, float y1,
		float x2, float y2, int color)
	{
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		context.draw(consumers -> {
			VertexConsumer buffer =
				consumers.getBuffer(WurstRenderLayers.ONE_PIXEL_LINE_STRIP);
			buffer.vertex(matrix, x1, y1, 1).color(color);
			buffer.vertex(matrix, x2, y1, 1).color(color);
			buffer.vertex(matrix, x2, y2, 1).color(color);
			buffer.vertex(matrix, x1, y2, 1).color(color);
			buffer.vertex(matrix, x1, y1, 1).color(color);
		});
	}
	
	/**
	 * Draws a 1px border around the given polygon.
	 */
	public static void drawLineStrip2D(DrawContext context, float[][] vertices,
		int color)
	{
		Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		context.draw(consumers -> {
			VertexConsumer buffer =
				consumers.getBuffer(WurstRenderLayers.ONE_PIXEL_LINE_STRIP);
			for(float[] vertex : vertices)
				buffer.vertex(matrix, vertex[0], vertex[1], 1).color(color);
			buffer.vertex(matrix, vertices[0][0], vertices[0][1], 1)
				.color(color);
		});
	}
	
	/**
	 * Draws a box shadow around the given rectangle.
	 */
	public static void drawBoxShadow2D(DrawContext context, int x1, int y1,
		int x2, int y2)
	{
		float[] acColor = WurstClient.INSTANCE.getGui().getAcColor();
		
		// outline
		float xo1 = x1 - 0.1F;
		float xo2 = x2 + 0.1F;
		float yo1 = y1 - 0.1F;
		float yo2 = y2 + 0.1F;
		
		int outlineColor = toIntColor(acColor, 0.5F);
		drawBorder2D(context, xo1, yo1, xo2, yo2, outlineColor);
		
		// shadow
		float xs1 = x1 - 1;
		float xs2 = x2 + 1;
		float ys1 = y1 - 1;
		float ys2 = y2 + 1;
		
		int shadowColor1 = toIntColor(acColor, 0.75F);
		int shadowColor2 = 0x00000000;
		
		MatrixStack matrixStack = context.getMatrices();
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		
		context.draw(consumers -> {
			VertexConsumer buffer = consumers.getBuffer(RenderLayer.getGui());
			
			// top
			buffer.vertex(matrix, x1, y1, 0).color(shadowColor1);
			buffer.vertex(matrix, x2, y1, 0).color(shadowColor1);
			buffer.vertex(matrix, xs2, ys1, 0).color(shadowColor2);
			buffer.vertex(matrix, xs1, ys1, 0).color(shadowColor2);
			
			// left
			buffer.vertex(matrix, xs1, ys1, 0).color(shadowColor2);
			buffer.vertex(matrix, xs1, ys2, 0).color(shadowColor2);
			buffer.vertex(matrix, x1, y2, 0).color(shadowColor1);
			buffer.vertex(matrix, x1, y1, 0).color(shadowColor1);
			
			// right
			buffer.vertex(matrix, x2, y1, 0).color(shadowColor1);
			buffer.vertex(matrix, x2, y2, 0).color(shadowColor1);
			buffer.vertex(matrix, xs2, ys2, 0).color(shadowColor2);
			buffer.vertex(matrix, xs2, ys1, 0).color(shadowColor2);
			
			// bottom
			buffer.vertex(matrix, x2, y2, 0).color(shadowColor1);
			buffer.vertex(matrix, x1, y2, 0).color(shadowColor1);
			buffer.vertex(matrix, xs1, ys2, 0).color(shadowColor2);
			buffer.vertex(matrix, xs2, ys2, 0).color(shadowColor2);
		});
	}
	
	public static void renderTag(MatrixStack matrixStack, Text text,
		Entity entity, VertexConsumerProvider provider, int color,
		double vOffset, float partialTicks)
	{
		NameTagsHack nameTags = WurstClient.INSTANCE.getHax().nameTagsHack;
		
		EntityRenderDispatcher dispatcher =
			WurstClient.MC.getEntityRenderDispatcher();
		double dist = dispatcher.getSquaredDistanceToCamera(entity);
		if(dist > 4096 && !nameTags.isUnlimitedRange())
			return;
		
		matrixStack.push();
		
		Vec3d camPos = RenderUtils.getCameraPos();
		Vec3d tagPos = EntityUtils.getLerpedPos(entity, partialTicks)
			.subtract(camPos).add(0, entity.getHeight() + vOffset, 0);
		matrixStack.translate(tagPos.x, tagPos.y, tagPos.z);
		
		matrixStack.multiply(dispatcher.getRotation().rotateY((float)Math.PI,
			new Quaternionf()));
		
		float scale = 0.025F;
		if(nameTags.isEnabled())
		{
			double distance = WurstClient.MC.player.distanceTo(entity);
			if(distance > 10)
				scale *= distance / 10;
		}
		matrixStack.scale(-scale, -scale, scale);
		
		float bgOpacity =
			WurstClient.MC.options.getTextBackgroundOpacity(0.25f);
		int bgColor = (int)(bgOpacity * 255F) << 24;
		
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		TextRenderer tr = WurstClient.MC.textRenderer;
		int labelX = -tr.getWidth(text) / 2;
		
		tr.draw(text, labelX, 0, color, false, matrix, provider,
			TextLayerType.NORMAL, bgColor, 15728880);
		
		tr.draw(text, labelX, 0, -1, false, matrix, provider,
			TextLayerType.SEE_THROUGH, 0, 15728880);
		
		matrixStack.pop();
	}
	
	public static void renderArmor(MatrixStack matrixStack, ItemStack stack,
		Entity entity, int armorId, boolean showEnchants, boolean impossible,
		float multiplier, double vOffset, float partialTicks)
	{
		NameTagsHack nameTags = WurstClient.INSTANCE.getHax().nameTagsHack;
		
		EntityRenderDispatcher dispatcher =
			WurstClient.MC.getEntityRenderDispatcher();
		double dist = dispatcher.getSquaredDistanceToCamera(entity);
		if(dist > 4096 && !nameTags.isUnlimitedRange())
			return;
		
		Vec3d camPos = RenderUtils.getCameraPos();
		Vec3d tagPos = EntityUtils.getLerpedPos(entity, partialTicks)
			.subtract(camPos).add(0, entity.getHeight() + vOffset, 0);
		
		float scale = 0.025F * multiplier;
		double distance = WurstClient.MC.player.distanceTo(entity);
		if(distance > 10)
			scale *= distance / 10;
		
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		TextRenderer tr = WurstClient.MC.textRenderer;
		
		// offset model view stack for armor
		Matrix4fStack viewMatrix = RenderSystem.getModelViewStack();
		viewMatrix.pushMatrix();
		
		// camera rotation
		viewMatrix.mul(matrixStack.peek().getPositionMatrix());
		
		viewMatrix.translate((float)tagPos.x, (float)tagPos.y, (float)tagPos.z);
		viewMatrix.rotate(dispatcher.getRotation().rotateY((float)Math.PI,
			new Quaternionf()));
		viewMatrix.scale(-scale, -scale, scale);
		
		// render item icon
		DrawContext context = new DrawContext(WurstClient.MC,
			WurstClient.MC.getBufferBuilders().getEntityVertexConsumers());
		context.getMatrices().translate(0, 0, -149);
		context.drawItem(stack, -50 + armorId * 20, -20);
		context.getMatrices().translate(0, 0, 149);
		context.drawStackOverlay(tr, stack, -50 + armorId * 20, -20);
		context.draw();
		
		viewMatrix.popMatrix();
		
		// offset matrixStack for rendering text
		matrixStack.push();
		matrixStack.translate(tagPos.x, tagPos.y, tagPos.z);
		matrixStack.multiply(dispatcher.getRotation().rotateY((float)Math.PI,
			new Quaternionf()));
		matrixStack.scale(-scale, -scale, scale);
		
		// render enchants
		if(showEnchants && stack.hasEnchantments())
		{
			RenderSystem.disableBlend();
			matrixStack.scale(0.5F, 0.5F, 0.5F);
			int index = 0;
			VertexConsumerProvider.Immediate immediate =
				WurstClient.MC.getBufferBuilders().getEntityVertexConsumers();
			Matrix4f matrix = matrixStack.peek().getPositionMatrix();
			for(Entry<RegistryEntry<Enchantment>> entry : EnchantmentHelper
				.getEnchantments(stack).getEnchantmentEntries())
			{
				Enchantment enchantment = entry.getKey().value();
				if(impossible && !enchantment.isAcceptableItem(stack))
					continue;
				index++;
				Text text = EnchantmentUtils
					.getShortName(entry.getKey().getIdAsString(),
						entry.getKey().isIn(EnchantmentTags.CURSE))
					.append(Integer.toString(entry.getIntValue()));
				
				tr.draw(text, -95 + armorId * 40 - tr.getWidth(text),
					-60 + tr.fontHeight * index, 0xffffff, false, matrix,
					immediate, TextLayerType.NORMAL, 0, 15728880);
				tr.draw(text, -95 + armorId * 40 - tr.getWidth(text),
					-60 + tr.fontHeight * index, -1, false, matrix, immediate,
					TextLayerType.SEE_THROUGH, 0, 15728880);
			}
			immediate.draw();
		}
		
		RenderSystem.disableBlend();
		matrixStack.pop();
		
		// reset lighting
		if(WurstClient.MC.world.getDimensionEffects().isDarkened())
			DiffuseLighting.enableForLevel();
		else
			DiffuseLighting.disableForLevel();
	}
}
