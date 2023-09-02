/*
 * Copyright (c) 2014-2023 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferBuilder.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.wurstclient.Category;
import net.wurstclient.events.CameraTransformViewBobbingListener;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.search.SearchArea;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.BlockVertexCompiler;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.ChunkSearcher;
import net.wurstclient.util.MinPriorityThreadFactory;
import net.wurstclient.util.RenderUtils;
import net.wurstclient.util.RotationUtils;

public final class SearchHack extends Hack
	implements UpdateListener, PacketInputListener, RenderListener, CameraTransformViewBobbingListener
{
	private final BlockListSetting blocks = new BlockListSetting("Blocks",
		"The types of blocks to search for.", "minecraft:diamond_ore");
	
	private final EnumSetting<SearchArea> area = new EnumSetting<>("Area",
		"The area around the player to search in.\n"
			+ "Higher values require a faster computer.",
		SearchArea.values(), SearchArea.D11);
	
	private final SliderSetting limit = new SliderSetting("Limit",
		"The maximum number of blocks to display.\n"
			+ "Higher values require a faster computer.",
		4, 3, 6, 1, ValueDisplay.LOGARITHMIC);
	
	private final CheckboxSetting yLevel = new CheckboxSetting("Adjust Y-level",
		"Allows you to adjust the y levels to search.", false);
	private final SliderSetting minY = new SliderSetting("Min Y",
		"The lowest Y-level to search. Requires ticking \"Adjust Y-level\".",
		-64, -64, 320, 1, ValueDisplay.INTEGER);
	private final SliderSetting maxY = new SliderSetting("Max Y",
		"The highest Y-level to search. Requires ticking \"Adjust Y-level\".",
		320, -64, 320, 1, ValueDisplay.INTEGER);
	
	private final CheckboxSetting sourcesOnly = new CheckboxSetting("Sources only",
		"For liquids, show the source block only.", false);
	
	private final CheckboxSetting counter = new CheckboxSetting("Counter",
		"Displays the number of blocks found.", false);
	
	private final CheckboxSetting tracers = new CheckboxSetting("Tracers",
		"Draw tracer lines to blocks found.", false);
	
	private int foundBlocks;
	
	private int prevLimit;
	private boolean notify;
	
	private final HashMap<ChunkPos, ChunkSearcher> searchers = new HashMap<>();
	private final Set<ChunkPos> chunksToUpdate =
		Collections.synchronizedSet(new HashSet<>());
	private ExecutorService pool1;
	
	private ForkJoinPool pool2;
	private ForkJoinTask<HashSet<BlockPos>> getMatchingBlocksTask;
	private ForkJoinTask<ArrayList<int[]>> compileVerticesTask;
	
	private VertexBuffer vertexBuffer;
	private boolean bufferUpToDate;
	
	private HashSet<BlockPos> matchingBlocks;
	
	public SearchHack()
	{
		super("Search");
		setCategory(Category.RENDER);
		addSetting(blocks);
		addSetting(area);
		addSetting(limit);
		addSetting(yLevel);
		addSetting(minY);
		addSetting(maxY);
		addSetting(sourcesOnly);
		
		addSetting(counter);
		addSetting(tracers);
	}
	
	@Override
	public String getRenderName()
	{
		String name;
		if(blocks.getBlockNames().size() == 0)
			name = getName() + " [None]";
		else if(blocks.getBlockNames().size() == 1)
			name = getName() + " [" + blocks.getBlockNames().get(0).replace("minecraft:", "")
			+ "]";
		else
			name = getName() + " [Multi]";
		if(counter.isChecked())
		{
			boolean exceed = foundBlocks >= (int)Math.pow(10, limit.getValueI());
			name += " (" + (exceed ? ">" : "") + foundBlocks + " found)";
		}
		return name;
	}
	
	@Override
	public void onEnable()
	{
		foundBlocks = 0;
		
		prevLimit = limit.getValueI();
		notify = true;
		
		pool1 = MinPriorityThreadFactory.newFixedThreadPool();
		pool2 = new ForkJoinPool();
		
		bufferUpToDate = false;
		
		EVENTS.add(CameraTransformViewBobbingListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		EVENTS.add(RenderListener.class, this);
	}
	
	@Override
	public void onDisable()
	{
		EVENTS.remove(CameraTransformViewBobbingListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		
		stopPool2Tasks();
		pool1.shutdownNow();
		pool2.shutdownNow();
		
		if(vertexBuffer != null)
		{
			vertexBuffer.close();
			vertexBuffer = null;
		}
		
		chunksToUpdate.clear();
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
	public void onReceivedPacket(PacketInputEvent event)
	{
		ClientPlayerEntity player = MC.player;
		ClientWorld world = MC.world;
		if(player == null || world == null)
			return;
		
		Packet<?> packet = event.getPacket();
		ChunkPos chunkPos;
		
		if(packet instanceof BlockUpdateS2CPacket change)
		{
			BlockPos pos = change.getPos();
			chunkPos = new ChunkPos(pos);
			
		}else if(packet instanceof ChunkDeltaUpdateS2CPacket change)
		{
			ArrayList<BlockPos> changedBlocks = new ArrayList<>();
			change.visitUpdates((pos, state) -> changedBlocks.add(pos));
			if(changedBlocks.isEmpty())
				return;
			
			chunkPos = new ChunkPos(changedBlocks.get(0));
			
		}else if(packet instanceof ChunkDataS2CPacket chunkData)
			chunkPos = new ChunkPos(chunkData.getX(), chunkData.getZ());
		else
			return;
		
		chunksToUpdate.add(chunkPos);
	}
	
	@Override
	public void onUpdate()
	{
		List<String> currentBlocks = new ArrayList<>(blocks.getBlockNames());
		BlockPos eyesPos = BlockPos.ofFloored(RotationUtils.getEyesPos());
		
		ChunkPos center = MC.player.getChunkPos();
		int dimensionId = MC.world.getRegistryKey().toString().hashCode();
		
		addSearchersInRange(center, currentBlocks, dimensionId);
		removeSearchersOutOfRange(center);
		replaceSearchersWithDifferences(currentBlocks, dimensionId);
		replaceSearchersWithChunkUpdate(currentBlocks, dimensionId);
		
		if(!areAllChunkSearchersDone())
			return;
		
		checkIfLimitChanged();
		
		if(getMatchingBlocksTask == null)
			startGetMatchingBlocksTask(eyesPos);
		
		if(!getMatchingBlocksTask.isDone())
			return;
		
		if(compileVerticesTask == null)
			startCompileVerticesTask();
		
		if(!compileVerticesTask.isDone())
			return;
		
		if(!bufferUpToDate)
			setBufferFromTask();
	}
	
	@Override
	public void onRender(MatrixStack matrixStack, float partialTicks)
	{
		// GL settings
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glDisable(GL11.GL_DEPTH_TEST);
		
		matrixStack.push();
		RenderUtils.applyRegionalRenderOffset(matrixStack);
		
		float[] rainbow = RenderUtils.getRainbowColor();
		RenderSystem.setShaderColor(rainbow[0], rainbow[1], rainbow[2], 0.5F);
		
		RenderSystem.setShader(GameRenderer::getPositionProgram);
		
		if(vertexBuffer != null)
		{
			Matrix4f viewMatrix = matrixStack.peek().getPositionMatrix();
			Matrix4f projMatrix = RenderSystem.getProjectionMatrix();
			ShaderProgram shader = RenderSystem.getShader();
			vertexBuffer.bind();
			vertexBuffer.draw(viewMatrix, projMatrix, shader);
			VertexBuffer.unbind();
		}
		
		if(tracers.isChecked() && matchingBlocks != null)
		{
			BlockPos camPos = RenderUtils.getCameraBlockPos();
			int regionX = (camPos.getX() >> 9) * 512;
			int regionZ = (camPos.getZ() >> 9) * 512;
			
			Matrix4f matrix = matrixStack.peek().getPositionMatrix();
			Tessellator tessellator = RenderSystem.renderThreadTesselator();
			BufferBuilder bufferBuilder = tessellator.getBuffer();
			
			bufferBuilder.begin(VertexFormat.DrawMode.DEBUG_LINES,
				VertexFormats.POSITION);
			
			Vec3d start = RotationUtils.getClientLookVec(partialTicks)
				.add(RenderUtils.getCameraPos()).subtract(regionX, 0, regionZ);
			
			for(BlockPos pos : matchingBlocks)
			{
				Vec3d center = BlockUtils.canBeClicked(pos) ? BlockUtils.getBoundingBox(pos).getCenter()
					: new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
				
				Vec3d end = center.subtract(regionX, 0, regionZ);
				
				bufferBuilder.vertex(matrix,
					(float)start.x, (float)start.y, (float)start.z).next();
			
				bufferBuilder.vertex(matrix,
					(float)end.x, (float)end.y, (float)end.z).next();
			}
			
			tessellator.draw();
		}
		
		matrixStack.pop();
		
		// GL resets
		RenderSystem.setShaderColor(1, 1, 1, 1);
		GL11.glEnable(GL11.GL_DEPTH_TEST);
		GL11.glDisable(GL11.GL_BLEND);
	}
	
	private void addSearchersInRange(ChunkPos center, List<String> currentBlocks,
		int dimensionId)
	{
		ArrayList<ChunkPos> chunksInRange =
			area.getSelected().getChunksInRange(center);
		
		for(ChunkPos chunkPos : chunksInRange)
		{
			if(searchers.containsKey(chunkPos))
				continue;
			
			addSearcher(chunkPos, currentBlocks, dimensionId);
		}
	}
	
	private void removeSearchersOutOfRange(ChunkPos center)
	{
		for(ChunkSearcher searcher : new ArrayList<>(searchers.values()))
		{
			ChunkPos searcherPos = searcher.getChunkPos();
			if(area.getSelected().isInRange(searcherPos, center))
				continue;
			
			removeSearcher(searcher);
		}
	}
	
	private void replaceSearchersWithDifferences(List<String> currentBlocks,
		int dimensionId)
	{
		int minY = !yLevel.isChecked() ? Integer.MIN_VALUE : this.minY.getValueI();
		int maxY = !yLevel.isChecked() ? Integer.MAX_VALUE : this.maxY.getValueI();
		for(ChunkSearcher oldSearcher : new ArrayList<>(searchers.values()))
		{
			if(currentBlocks.equals(oldSearcher.getBlocks())
				&& dimensionId == oldSearcher.getDimensionId()
				&& minY == oldSearcher.getMinY()
				&& maxY == oldSearcher.getMaxY()
				&& sourcesOnly.isChecked() == oldSearcher.isSourcesOnly())
				continue;
			
			removeSearcher(oldSearcher);
			addSearcher(oldSearcher.getChunkPos(), currentBlocks, dimensionId);
		}
	}
	
	private void replaceSearchersWithChunkUpdate(List<String> currentBlocks,
		int dimensionId)
	{
		synchronized(chunksToUpdate)
		{
			if(chunksToUpdate.isEmpty())
				return;
			
			for(Iterator<ChunkPos> itr = chunksToUpdate.iterator(); itr.hasNext();)
			{
				ChunkPos chunkPos = itr.next();
				
				ChunkSearcher oldSearcher = searchers.get(chunkPos);
				if(oldSearcher == null)
					continue;
				
				removeSearcher(oldSearcher);
				addSearcher(chunkPos, currentBlocks, dimensionId);
				itr.remove();
			}
		}
	}
	
	private void addSearcher(ChunkPos chunkPos, List<String> blocks, int dimensionId)
	{
		stopPool2Tasks();
		
		int minY = !yLevel.isChecked() ? Integer.MIN_VALUE : this.minY.getValueI();
		int maxY = !yLevel.isChecked() ? Integer.MAX_VALUE : this.maxY.getValueI();
		ChunkSearcher searcher = new ChunkSearcher(chunkPos, blocks, minY, maxY,
			sourcesOnly.isChecked(), dimensionId);
		searchers.put(chunkPos, searcher);
		searcher.startSearching(pool1);
	}
	
	private void removeSearcher(ChunkSearcher searcher)
	{
		stopPool2Tasks();
		
		searchers.remove(searcher.getChunkPos());
		searcher.cancelSearching();
	}
	
	private void stopPool2Tasks()
	{
		if(getMatchingBlocksTask != null)
		{
			getMatchingBlocksTask.cancel(true);
			getMatchingBlocksTask = null;
		}
		
		if(compileVerticesTask != null)
		{
			compileVerticesTask.cancel(true);
			compileVerticesTask = null;
		}
		
		bufferUpToDate = false;
	}
	
	private boolean areAllChunkSearchersDone()
	{
		for(ChunkSearcher searcher : searchers.values())
			if(searcher.getStatus() != ChunkSearcher.Status.DONE)
				return false;
			
		return true;
	}
	
	private void checkIfLimitChanged()
	{
		if(limit.getValueI() != prevLimit)
		{
			stopPool2Tasks();
			notify = true;
			prevLimit = limit.getValueI();
		}
	}
	
	private void startGetMatchingBlocksTask(BlockPos eyesPos)
	{
		int maxBlocks = (int)Math.pow(10, limit.getValueI());
		
		Callable<HashSet<BlockPos>> task = () -> searchers.values()
			.parallelStream()
			.flatMap(searcher -> searcher.getMatchingBlocks().stream())
			.sorted(Comparator
				.comparingInt(pos -> eyesPos.getManhattanDistance(pos)))
			.limit(maxBlocks).collect(Collectors.toCollection(HashSet::new));
		
		getMatchingBlocksTask = pool2.submit(task);
	}
	
	private HashSet<BlockPos> getMatchingBlocksFromTask()
	{
		HashSet<BlockPos> matchingBlocks = new HashSet<>();
		
		try
		{
			matchingBlocks = getMatchingBlocksTask.get();
			
		}catch(InterruptedException | ExecutionException e)
		{
			throw new RuntimeException(e);
		}
		
		int maxBlocks = (int)Math.pow(10, limit.getValueI());
		
		foundBlocks = matchingBlocks.size();
		if(foundBlocks < maxBlocks)
			notify = true;
		else if(notify)
		{
			ChatUtils.warning("Search found \u00a7lA LOT\u00a7r of blocks!"
				+ " To prevent lag, it will only show the closest \u00a76"
				+ limit.getValueString() + "\u00a7r results.");
			notify = false;
		}
		
		return matchingBlocks;
	}
	
	private void startCompileVerticesTask()
	{
		HashSet<BlockPos> matchingBlocks = getMatchingBlocksFromTask();
		this.matchingBlocks = matchingBlocks;
		
		BlockPos camPos = RenderUtils.getCameraBlockPos();
		int regionX = (camPos.getX() >> 9) * 512;
		int regionZ = (camPos.getZ() >> 9) * 512;
		
		Callable<ArrayList<int[]>> task =
			() -> BlockVertexCompiler.compile(matchingBlocks, regionX, regionZ);
		
		compileVerticesTask = pool2.submit(task);
	}
	
	private void setBufferFromTask()
	{
		ArrayList<int[]> vertices = getVerticesFromTask();
		
		if(vertexBuffer != null)
			vertexBuffer.close();
		
		vertexBuffer = new VertexBuffer();
		
		Tessellator tessellator = RenderSystem.renderThreadTesselator();
		BufferBuilder bufferBuilder = tessellator.getBuffer();
		bufferBuilder.begin(VertexFormat.DrawMode.QUADS,
			VertexFormats.POSITION);
		
		for(int[] vertex : vertices)
			bufferBuilder.vertex(vertex[0], vertex[1], vertex[2]).next();
		
		BuiltBuffer buffer = bufferBuilder.end();
		
		vertexBuffer.bind();
		vertexBuffer.upload(buffer);
		VertexBuffer.unbind();
		
		bufferUpToDate = true;
	}
	
	private ArrayList<int[]> getVerticesFromTask()
	{
		try
		{
			return compileVerticesTask.get();
			
		}catch(InterruptedException | ExecutionException e)
		{
			throw new RuntimeException(e);
		}
	}
}
