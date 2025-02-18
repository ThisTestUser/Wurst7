/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.settings.ChunkAreaSetting;

public abstract class AbstractChunkCoordinator implements PacketInputListener
{
	protected final HashMap<ChunkPos, ChunkSearcher> searchers =
		new HashMap<>();
	protected final ChunkAreaSetting area;
	private BiPredicate<BlockPos, BlockState> query;
	
	protected final Set<ChunkPos> chunksToUpdate =
		Collections.synchronizedSet(new HashSet<>());
	
	public AbstractChunkCoordinator(BiPredicate<BlockPos, BlockState> query,
		ChunkAreaSetting area)
	{
		this.query = Objects.requireNonNull(query);
		this.area = Objects.requireNonNull(area);
	}
	
	public boolean update()
	{
		DimensionType dimension = WurstClient.MC.world.getDimension();
		HashSet<ChunkPos> chunkUpdates = clearChunksToUpdate();
		boolean searchersChanged = false;
		
		// remove outdated ChunkSearchers
		for(ChunkSearcher searcher : new ArrayList<>(searchers.values()))
		{
			boolean remove = false;
			ChunkPos searcherPos = searcher.getPos();
			
			// wrong dimension
			if(dimension != searcher.getDimension())
				remove = true;
			
			// out of range
			else if(!area.isInRange(searcherPos))
				remove = true;
			
			// chunk update
			else if(chunkUpdates.contains(searcherPos))
				remove = true;
			
			if(remove)
			{
				searchers.remove(searcherPos);
				searcher.cancel();
				onRemove(searcher);
				searchersChanged = true;
			}
		}
		
		// add new ChunkSearchers
		for(Chunk chunk : area.getChunksInRange())
		{
			ChunkPos chunkPos = chunk.getPos();
			if(searchers.containsKey(chunkPos))
				continue;
			
			ChunkSearcher searcher = new ChunkSearcher(query, chunk, dimension);
			searchers.put(chunkPos, searcher);
			searcher.start();
			searchersChanged = true;
		}
		
		return searchersChanged;
	}
	
	protected void onRemove(ChunkSearcher searcher)
	{
		// Overridden in ChunkVertexBufferCoordinator
	}
	
	public void reset()
	{
		searchers.values().forEach(ChunkSearcher::cancel);
		searchers.clear();
		chunksToUpdate.clear();
	}
	
	public boolean isDone()
	{
		return searchers.values().stream().allMatch(ChunkSearcher::isDone);
	}
	
	public void setQuery(BiPredicate<BlockPos, BlockState> query)
	{
		this.query = Objects.requireNonNull(query);
		searchers.values().forEach(ChunkSearcher::cancel);
		searchers.clear();
	}
	
	public void setTargetBlock(Block block)
	{
		setQuery((pos, state) -> block == state.getBlock());
	}
	
	protected HashSet<ChunkPos> clearChunksToUpdate()
	{
		synchronized(chunksToUpdate)
		{
			HashSet<ChunkPos> chunks = new HashSet<>(chunksToUpdate);
			chunksToUpdate.clear();
			return chunks;
		}
	}
}
