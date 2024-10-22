/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;

import net.minecraft.block.Blocks;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.BufferBuilder.BuiltBuffer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.NameTagsHack;

public enum RenderUtils
{
	;
	
	private static final Box DEFAULT_BOX = new Box(0, 0, 0, 1, 1, 1);
	
	public static void scissorBox(int startX, int startY, int endX, int endY)
	{
		int width = endX - startX;
		int height = endY - startY;
		int bottomY = WurstClient.MC.currentScreen.height - endY;
		double factor = WurstClient.MC.getWindow().getScaleFactor();
		
		int scissorX = (int)(startX * factor);
		int scissorY = (int)(bottomY * factor);
		int scissorWidth = (int)(width * factor);
		int scissorHeight = (int)(height * factor);
		GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
	}
	
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
	
	public static void applyCameraRotationOnly()
	{
		// no longer necessary for some reason
		
		// Camera camera =
		// WurstClient.MC.getBlockEntityRenderDispatcher().camera;
		// GL11.glRotated(MathHelper.wrapDegrees(camera.getPitch()), 1, 0, 0);
		// GL11.glRotated(MathHelper.wrapDegrees(camera.getYaw() + 180.0), 0, 1,
		// 0);
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
	
	public static void drawSolidBox(MatrixStack matrixStack)
	{
		drawSolidBox(DEFAULT_BOX, matrixStack);
	}
	
	public static void drawSolidBox(Box bb, MatrixStack matrixStack)
	{
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		RenderSystem.setShader(GameRenderer::getPositionProgram);
		
		bufferBuilder.begin(VertexFormat.DrawMode.QUADS,
			VertexFormats.POSITION);
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ)
			.next();
		tessellator.draw();
	}
	
	public static void drawSolidBox(Box bb, VertexBuffer vertexBuffer)
	{
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		
		bufferBuilder.begin(VertexFormat.DrawMode.QUADS,
			VertexFormats.POSITION);
		drawSolidBox(bb, bufferBuilder);
		BuiltBuffer buffer = bufferBuilder.end();
		
		vertexBuffer.bind();
		vertexBuffer.upload(buffer);
		VertexBuffer.unbind();
	}
	
	public static void drawSolidBox(Box bb, BufferBuilder bufferBuilder)
	{
		bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).next();
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).next();
	}
	
	public static void drawOutlinedBox(MatrixStack matrixStack)
	{
		drawOutlinedBox(DEFAULT_BOX, matrixStack);
	}
	
	public static void drawOutlinedBox(Box bb, MatrixStack matrixStack)
	{
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		RenderSystem.setShader(GameRenderer::getPositionProgram);
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ)
			.next();
		tessellator.draw();
	}
	
	public static void drawOutlinedBox(Box bb, VertexBuffer vertexBuffer)
	{
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		drawOutlinedBox(bb, bufferBuilder);
		BuiltBuffer buffer = bufferBuilder.end();
		
		vertexBuffer.bind();
		vertexBuffer.upload(buffer);
		VertexBuffer.unbind();
	}
	
	public static void drawOutlinedBox(Box bb, BufferBuilder bufferBuilder)
	{
		bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).next();
	}
	
	public static void drawCrossBox(Box bb, MatrixStack matrixStack)
	{
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.maxY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.maxY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.minZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.maxZ)
			.next();
		
		bufferBuilder
			.vertex(matrix, (float)bb.maxX, (float)bb.minY, (float)bb.maxZ)
			.next();
		bufferBuilder
			.vertex(matrix, (float)bb.minX, (float)bb.minY, (float)bb.minZ)
			.next();
		tessellator.draw();
	}
	
	public static void drawCrossBox(Box bb, VertexBuffer vertexBuffer)
	{
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		drawCrossBox(bb, bufferBuilder);
		BuiltBuffer buffer = bufferBuilder.end();
		
		vertexBuffer.bind();
		vertexBuffer.upload(buffer);
		VertexBuffer.unbind();
	}
	
	public static void drawCrossBox(Box bb, BufferBuilder bufferBuilder)
	{
		bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.maxY, bb.minZ).next();
		bufferBuilder.vertex(bb.minX, bb.maxY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.minZ).next();
		bufferBuilder.vertex(bb.minX, bb.minY, bb.maxZ).next();
		
		bufferBuilder.vertex(bb.maxX, bb.minY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, bb.minY, bb.minZ).next();
	}
	
	public static void drawNode(Box bb, MatrixStack matrixStack)
	{
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		RenderSystem.setShader(GameRenderer::getPositionProgram);
		
		double midX = (bb.minX + bb.maxX) / 2;
		double midY = (bb.minY + bb.maxY) / 2;
		double midZ = (bb.minZ + bb.maxZ) / 2;
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.maxZ)
			.next();
		bufferBuilder.vertex(matrix, (float)bb.minX, (float)midY, (float)midZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)bb.minX, (float)midY, (float)midZ)
			.next();
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.minZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.minZ)
			.next();
		bufferBuilder.vertex(matrix, (float)bb.maxX, (float)midY, (float)midZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)bb.maxX, (float)midY, (float)midZ)
			.next();
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.maxZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.maxY, (float)midZ)
			.next();
		bufferBuilder.vertex(matrix, (float)bb.maxX, (float)midY, (float)midZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.maxY, (float)midZ)
			.next();
		bufferBuilder.vertex(matrix, (float)bb.minX, (float)midY, (float)midZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.maxY, (float)midZ)
			.next();
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.minZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.maxY, (float)midZ)
			.next();
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.maxZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.minY, (float)midZ)
			.next();
		bufferBuilder.vertex(matrix, (float)bb.maxX, (float)midY, (float)midZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.minY, (float)midZ)
			.next();
		bufferBuilder.vertex(matrix, (float)bb.minX, (float)midY, (float)midZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.minY, (float)midZ)
			.next();
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.minZ)
			.next();
		
		bufferBuilder.vertex(matrix, (float)midX, (float)bb.minY, (float)midZ)
			.next();
		bufferBuilder.vertex(matrix, (float)midX, (float)midY, (float)bb.maxZ)
			.next();
		
		tessellator.draw();
	}
	
	public static void drawNode(Box bb, VertexBuffer vertexBuffer)
	{
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		drawNode(bb, bufferBuilder);
		BuiltBuffer buffer = bufferBuilder.end();
		
		vertexBuffer.bind();
		vertexBuffer.upload(buffer);
		VertexBuffer.unbind();
	}
	
	public static void drawNode(Box bb, BufferBuilder bufferBuilder)
	{
		double midX = (bb.minX + bb.maxX) / 2;
		double midY = (bb.minY + bb.maxY) / 2;
		double midZ = (bb.minZ + bb.maxZ) / 2;
		
		bufferBuilder.vertex(midX, midY, bb.maxZ).next();
		bufferBuilder.vertex(bb.minX, midY, midZ).next();
		
		bufferBuilder.vertex(bb.minX, midY, midZ).next();
		bufferBuilder.vertex(midX, midY, bb.minZ).next();
		
		bufferBuilder.vertex(midX, midY, bb.minZ).next();
		bufferBuilder.vertex(bb.maxX, midY, midZ).next();
		
		bufferBuilder.vertex(bb.maxX, midY, midZ).next();
		bufferBuilder.vertex(midX, midY, bb.maxZ).next();
		
		bufferBuilder.vertex(midX, bb.maxY, midZ).next();
		bufferBuilder.vertex(bb.maxX, midY, midZ).next();
		
		bufferBuilder.vertex(midX, bb.maxY, midZ).next();
		bufferBuilder.vertex(bb.minX, midY, midZ).next();
		
		bufferBuilder.vertex(midX, bb.maxY, midZ).next();
		bufferBuilder.vertex(midX, midY, bb.minZ).next();
		
		bufferBuilder.vertex(midX, bb.maxY, midZ).next();
		bufferBuilder.vertex(midX, midY, bb.maxZ).next();
		
		bufferBuilder.vertex(midX, bb.minY, midZ).next();
		bufferBuilder.vertex(bb.maxX, midY, midZ).next();
		
		bufferBuilder.vertex(midX, bb.minY, midZ).next();
		bufferBuilder.vertex(bb.minX, midY, midZ).next();
		
		bufferBuilder.vertex(midX, bb.minY, midZ).next();
		bufferBuilder.vertex(midX, midY, bb.minZ).next();
		
		bufferBuilder.vertex(midX, bb.minY, midZ).next();
		bufferBuilder.vertex(midX, midY, bb.maxZ).next();
	}
	
	public static void drawArrow(Vec3d from, Vec3d to, MatrixStack matrixStack)
	{
		RenderSystem.setShader(GameRenderer::getPositionProgram);
		
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
		
		double startX = from.x;
		double startY = from.y;
		double startZ = from.z;
		
		double endX = to.x;
		double endY = to.y;
		double endZ = to.z;
		
		matrixStack.push();
		Matrix4f matrix = matrixStack.peek().getPositionMatrix();
		
		bufferBuilder
			.vertex(matrix, (float)startX, (float)startY, (float)startZ).next();
		bufferBuilder.vertex(matrix, (float)endX, (float)endY, (float)endZ)
			.next();
		
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
		
		bufferBuilder.vertex(matrix, 0, 2, 1).next();
		bufferBuilder.vertex(matrix, -1, 2, 0).next();
		
		bufferBuilder.vertex(matrix, -1, 2, 0).next();
		bufferBuilder.vertex(matrix, 0, 2, -1).next();
		
		bufferBuilder.vertex(matrix, 0, 2, -1).next();
		bufferBuilder.vertex(matrix, 1, 2, 0).next();
		
		bufferBuilder.vertex(matrix, 1, 2, 0).next();
		bufferBuilder.vertex(matrix, 0, 2, 1).next();
		
		bufferBuilder.vertex(matrix, 1, 2, 0).next();
		bufferBuilder.vertex(matrix, -1, 2, 0).next();
		
		bufferBuilder.vertex(matrix, 0, 2, 1).next();
		bufferBuilder.vertex(matrix, 0, 2, -1).next();
		
		bufferBuilder.vertex(matrix, 0, 0, 0).next();
		bufferBuilder.vertex(matrix, 1, 2, 0).next();
		
		bufferBuilder.vertex(matrix, 0, 0, 0).next();
		bufferBuilder.vertex(matrix, -1, 2, 0).next();
		
		bufferBuilder.vertex(matrix, 0, 0, 0).next();
		bufferBuilder.vertex(matrix, 0, 2, -1).next();
		
		bufferBuilder.vertex(matrix, 0, 0, 0).next();
		bufferBuilder.vertex(matrix, 0, 2, 1).next();
		
		matrixStack.pop();
		
		tessellator.draw();
	}
	
	public static void drawArrow(Vec3d from, Vec3d to,
		VertexBuffer vertexBuffer)
	{
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		
		bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
			VertexFormats.POSITION);
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
		
		bufferBuilder
			.vertex(matrix, (float)startX, (float)startY, (float)startZ).next();
		bufferBuilder.vertex(matrix, (float)endX, (float)endY, (float)endZ)
			.next();
		
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
		
		bufferBuilder.vertex(matrix, 0, 2, 1).next();
		bufferBuilder.vertex(matrix, -1, 2, 0).next();
		
		bufferBuilder.vertex(matrix, -1, 2, 0).next();
		bufferBuilder.vertex(matrix, 0, 2, -1).next();
		
		bufferBuilder.vertex(matrix, 0, 2, -1).next();
		bufferBuilder.vertex(matrix, 1, 2, 0).next();
		
		bufferBuilder.vertex(matrix, 1, 2, 0).next();
		bufferBuilder.vertex(matrix, 0, 2, 1).next();
		
		bufferBuilder.vertex(matrix, 1, 2, 0).next();
		bufferBuilder.vertex(matrix, -1, 2, 0).next();
		
		bufferBuilder.vertex(matrix, 0, 2, 1).next();
		bufferBuilder.vertex(matrix, 0, 2, -1).next();
		
		bufferBuilder.vertex(matrix, 0, 0, 0).next();
		bufferBuilder.vertex(matrix, 1, 2, 0).next();
		
		bufferBuilder.vertex(matrix, 0, 0, 0).next();
		bufferBuilder.vertex(matrix, -1, 2, 0).next();
		
		bufferBuilder.vertex(matrix, 0, 0, 0).next();
		bufferBuilder.vertex(matrix, 0, 2, -1).next();
		
		bufferBuilder.vertex(matrix, 0, 0, 0).next();
		bufferBuilder.vertex(matrix, 0, 2, 1).next();
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
		
		matrixStack.multiply(dispatcher.getRotation());
		
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
		viewMatrix.rotate(dispatcher.getRotation());
		viewMatrix.scale(-scale, -scale, scale);
		RenderSystem.applyModelViewMatrix();
		
		// render item icon
		DrawContext context = new DrawContext(WurstClient.MC,
			WurstClient.MC.getBufferBuilders().getEntityVertexConsumers());
		context.getMatrices().translate(0, 0, -149);
		context.drawItem(stack, -50 + armorId * 20, -20);
		context.getMatrices().translate(0, 0, 149);
		context.drawItemInSlot(tr, stack, -50 + armorId * 20, -20);
		context.draw();
		
		viewMatrix.popMatrix();
		RenderSystem.applyModelViewMatrix();
		
		// offset matrixStack for rendering text
		matrixStack.push();
		matrixStack.translate(tagPos.x, tagPos.y, tagPos.z);
		matrixStack.multiply(dispatcher.getRotation());
		matrixStack.scale(-scale, -scale, scale);
		
		// render enchants
		if(showEnchants && stack.hasEnchantments())
		{
			RenderSystem.disableBlend();
			matrixStack.scale(0.5F, 0.5F, 0.5F);
			int index = 0;
			VertexConsumerProvider.Immediate immediate = VertexConsumerProvider
				.immediate(Tessellator.getInstance().getBuffer());
			Matrix4f matrix = matrixStack.peek().getPositionMatrix();
			for(Entry<RegistryEntry<Enchantment>> entry : EnchantmentHelper
				.getEnchantments(stack).getEnchantmentsMap())
			{
				Enchantment enchantment = entry.getKey().value();
				if(impossible && !enchantment.isAcceptableItem(stack))
					continue;
				index++;
				Text text = EnchantmentUtils.getShortName(enchantment)
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
