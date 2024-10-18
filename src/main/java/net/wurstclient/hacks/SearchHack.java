/*
 * Copyright (c) 2014-2024 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ChunkAreaSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.BlockVertexCompiler;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RegionPos;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;
import net.wurstclient.util.chunk.ChunkSearcher;
import net.wurstclient.util.chunk.ChunkSearcherCoordinator;

@SearchTags({"BlockESP", "block esp"})
public final class SearchHack extends Hack implements UpdateListener,
	RenderListener, CameraTransformViewBobbingListener
{
	private final BlockListSetting blocks = new BlockListSetting("Blocks",
		"The types of blocks to search for.", "minecraft:diamond_ore");
	private List<String> lastBlocks;
	
	private final ChunkAreaSetting area = new ChunkAreaSetting("Area",
		"The area around the player to search in.\n"
			+ "Higher values require a faster computer.");
	
	private final SliderSetting limit = new SliderSetting("Limit",
		"The maximum number of blocks to display.\n"
			+ "Higher values require a faster computer.",
		4, 3, 6, 1, ValueDisplay.LOGARITHMIC);
	
	private final CheckboxSetting counter = new CheckboxSetting("Counter",
		"Displays the number of blocks found.", false);
	
	private final CheckboxSetting tracers = new CheckboxSetting("Tracers",
		"Draw tracer lines to blocks found.", false);
	
	private final CheckboxSetting alert = new CheckboxSetting("Alert", false);
	
	private boolean alertState;
	private int foundBlocks;
	private int prevLimit;
	private boolean notify;
	
	private final ChunkSearcherCoordinator coordinator =
		new ChunkSearcherCoordinator(area);
	
	private ForkJoinPool forkJoinPool;
	private ForkJoinTask<HashSet<BlockPos>> getMatchingBlocksTask;
	private ForkJoinTask<ArrayList<int[]>> compileVerticesTask;
	
	private VertexBuffer vertexBuffer;
	private RegionPos bufferRegion;
	private boolean bufferUpToDate;
	
	private HashSet<BlockPos> matchingBlocks;
	
	public SearchHack()
	{
		super("Search");
		setCategory(Category.RENDER);
		addSetting(blocks);
		addSetting(area);
		addSetting(limit);
		
		addSetting(counter);
		addSetting(tracers);
		addSetting(alert);
	}
	
	@Override
	public String getRenderName()
	{
		String name;
		if(blocks.getBlockNames().size() == 0)
			name = getName() + " [None]";
		else if(blocks.getBlockNames().size() == 1)
			name = getName() + " ["
				+ blocks.getBlockNames().get(0).replace("minecraft:", "") + "]";
		else
			name = getName() + " [Multi]";
		if(counter.isChecked())
		{
			boolean exceed =
				foundBlocks >= (int)Math.pow(10, limit.getValueI());
			name += " (" + (exceed ? ">" : "") + foundBlocks + " found)";
		}
		return name;
	}
	
	@Override
	protected void onEnable()
	{
		alertState = false;
		foundBlocks = 0;
		lastBlocks = new ArrayList<>(blocks.getBlockNames());
		coordinator
			.setQuery((pos, state) -> Collections.binarySearch(lastBlocks,
				BlockUtils.getName(state.getBlock())) >= 0);
		prevLimit = limit.getValueI();
		notify = true;
		
		forkJoinPool = new ForkJoinPool();
		
		bufferUpToDate = false;
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, coordinator);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, coordinator);
		EVENTS.remove(RenderListener.class, this);
		
		stopBuildingBuffer();
		coordinator.reset();
		forkJoinPool.shutdownNow();
		
		if(vertexBuffer != null)
			vertexBuffer.close();
		vertexBuffer = null;
		bufferRegion = null;
		matchingBlocks = null;
	}
	
	@Override
	public void onCameraTransformViewBobbing(
		CameraTransformViewBobbingEvent event)
	{
		if(tracers.isChecked())
			event.cancel();
	}
	
	@Override
	public void onUpdate()
	{
		boolean searchersChanged = false;
		
		// clear ChunkSearchers if blocks have changed
		List<String> currentBlocks = new ArrayList<>(blocks.getBlockNames());
		if(!currentBlocks.equals(lastBlocks))
		{
			lastBlocks = currentBlocks;
			coordinator
				.setQuery((pos, state) -> Collections.binarySearch(lastBlocks,
					BlockUtils.getName(state.getBlock())) >= 0);
			searchersChanged = true;
		}
		
		if(coordinator.update())
			searchersChanged = true;
		
		if(searchersChanged)
			stopBuildingBuffer();
		
		if(!coordinator.isDone())
			return;
		
		// check if limit has changed
		if(limit.getValueI() != prevLimit)
		{
			stopBuildingBuffer();
			prevLimit = limit.getValueI();
			notify = true;
		}
		
		// build the buffer
		
		if(getMatchingBlocksTask == null)
			startGetMatchingBlocksTask();
		
		if(!getMatchingBlocksTask.isDone())
			return;
		
		if(compileVerticesTask == null)
			startCompileVerticesTask();
		
		if(!compileVerticesTask.isDone())
			return;
		
		if(!bufferUpToDate)
			setBufferFromTask();
		
		if(alert.isChecked() && foundBlocks > 0 && !alertState)
		{
			ChatUtils
				.message("Alert: Search has found blocks near your location of "
					+ MC.player.getPos());
			alertState = true;
		}
		
		if(alert.isChecked() && foundBlocks == 0 && alertState)
			alertState = false;
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		if(vertexBuffer == null || bufferRegion == null)
			return;
		
		// GL settings
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		matrixStack.push();
		RenderUtils.applyRegionalRenderOffset(matrixStack, bufferRegion);
		
		float[] rainbow = RenderUtils.getRainbowColor();
		RenderUtils.setShaderColor(rainbow, 0.5F);
		
		RenderSystem.setShader(GameRenderer::getPositionProgram);
		
		Matrix4f viewMatrix = matrixStack.peek().getPositionMatrix();
		Matrix4f projMatrix = RenderSystem.getProjectionMatrix();
		ShaderProgram shader = RenderSystem.getShader();
		vertexBuffer.bind();
		vertexBuffer.draw(viewMatrix, projMatrix, shader);
		VertexBuffer.unbind();
		
		if(tracers.isChecked() && matchingBlocks != null
			&& !matchingBlocks.isEmpty())
		{
			BlockPos camPos = RenderUtils.getCameraBlockPos();
			int regionX = (camPos.getX() >> 9) * 512;
			int regionZ = (camPos.getZ() >> 9) * 512;
			
			Matrix4f matrix = matrixStack.peek().getPositionMatrix();
			Tessellator tessellator = RenderSystem.renderThreadTesselator();
			BufferBuilder bufferBuilder = tessellator.begin(
				VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION);
			
			Vec3d start = RotationUtils.getClientLookVec(partialTicks)
				.add(RenderUtils.getCameraPos()).subtract(regionX, 0, regionZ);
			
			for(BlockPos pos : matchingBlocks)
			{
				Vec3d center = BlockUtils.canBeClicked(pos)
					? BlockUtils.getBoundingBox(pos).getCenter() : new Vec3d(
						pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
				
				Vec3d end = center.subtract(regionX, 0, regionZ);
				
				bufferBuilder.vertex(matrix, (float)start.x, (float)start.y,
					(float)start.z);
				
				bufferBuilder.vertex(matrix, (float)end.x, (float)end.y,
					(float)end.z);
			}
			
			BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
		}
		
		matrixStack.pop();
		
		// GL resets
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
	}
	
	private void stopBuildingBuffer()
	{
		if(getMatchingBlocksTask != null)
			getMatchingBlocksTask.cancel(true);
		getMatchingBlocksTask = null;
		
		if(compileVerticesTask != null)
			compileVerticesTask.cancel(true);
		compileVerticesTask = null;
		
		bufferUpToDate = false;
	}
	
	private void startGetMatchingBlocksTask()
	{
		BlockPos eyesPos = BlockPos.ofFloored(RotationUtils.getEyesPos());
		Comparator<BlockPos> comparator =
			Comparator.comparingInt(pos -> eyesPos.getManhattanDistance(pos));
		
		getMatchingBlocksTask = forkJoinPool.submit(() -> coordinator
			.getMatches().parallel().map(ChunkSearcher.Result::pos)
			.sorted(comparator).limit(limit.getValueLog())
			.collect(Collectors.toCollection(HashSet::new)));
	}
	
	private void startCompileVerticesTask()
	{
		matchingBlocks = getMatchingBlocksTask.join();
		
		foundBlocks = matchingBlocks.size();
		if(foundBlocks < limit.getValueLog())
			notify = true;
		else if(notify)
		{
			ChatUtils.warning("Search found \u00a7lA LOT\u00a7r of blocks!"
				+ " To prevent lag, it will only show the closest \u00a76"
				+ limit.getValueString() + "\u00a7r results.");
			notify = false;
		}
		
		compileVerticesTask = forkJoinPool
			.submit(() -> BlockVertexCompiler.compile(matchingBlocks));
	}
	
	private void setBufferFromTask()
	{
		ArrayList<int[]> vertices = compileVerticesTask.join();
		RegionPos region = RenderUtils.getCameraRegion();
		if(vertexBuffer != null)
		{
			vertexBuffer.close();
			vertexBuffer = null;
		}
		
		if(!vertices.isEmpty())
		{
			Tessellator tessellator = RenderSystem.renderThreadTesselator();
			BufferBuilder bufferBuilder = tessellator
				.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
			
			for(int[] vertex : vertices)
				bufferBuilder.vertex(vertex[0] - region.x(), vertex[1],
					vertex[2] - region.z());
			
			BuiltBuffer buffer = bufferBuilder.endNullable();
			
			vertexBuffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
			vertexBuffer.bind();
			vertexBuffer.upload(buffer);
			VertexBuffer.unbind();
		}
		
		bufferUpToDate = true;
		bufferRegion = region;
	}
}
