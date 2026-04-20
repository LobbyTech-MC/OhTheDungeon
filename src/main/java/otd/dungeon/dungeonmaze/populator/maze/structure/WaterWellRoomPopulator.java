/* 
 * Copyright (C) 2021 shadow
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package otd.dungeon.dungeonmaze.populator.maze.structure;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import otd.dungeon.dungeonmaze.populator.maze.MazeRoomBlockPopulator;
import otd.dungeon.dungeonmaze.populator.maze.MazeRoomBlockPopulatorArgs;
import otd.lib.async.AsyncWorldEditor;

public class WaterWellRoomPopulator extends MazeRoomBlockPopulator {

	    private static final int LAYER_MIN = 3;
	    private static final int LAYER_MAX = 7;
	    private static final float ROOM_CHANCE = .002f;
	    
	    // 预定义相对位置数组，避免运行时计算
	    private static final int[][] FLOOR_AREA = new int[64][2];
	    private static final int[][] FLOOR_UNDER_AREA = new int[64][2];
	    private static final int[][] WELL_AREA = new int[9][2];
	    
	    static {
	        // 预计算地板区域 (8x8 = 64个位置)
	        int idx = 0;
	        for (int x = 0; x <= 7; x++) {
	            for (int z = 0; z <= 7; z++) {
	                FLOOR_AREA[idx][0] = x;
	                FLOOR_AREA[idx][1] = z;
	                idx++;
	            }
	        }
	        
	        // 预计算水井区域 (3x3 = 9个位置)
	        idx = 0;
	        for (int x = 2; x <= 4; x++) {
	            for (int z = 2; z <= 4; z++) {
	                WELL_AREA[idx][0] = x;
	                WELL_AREA[idx][1] = z;
	                idx++;
	            }
	        }
	        
	        // 复制地板区域用于下方圆石
	        System.arraycopy(FLOOR_AREA, 0, FLOOR_UNDER_AREA, 0, FLOOR_AREA.length);
	    }
	    
	    public boolean const_room = true;

	    @Override
	    public boolean getConstRoom() {
	        return const_room;
	    }

	    private final static BlockData STEP2 = Bukkit.createBlockData("minecraft:petrified_oak_slab[type=bottom]");
	    private final static BlockData STAIRS0 = Bukkit
	            .createBlockData("minecraft:oak_stairs[half=bottom,shape=outer_right,facing=east]");
	    private final static BlockData STAIRS1 = Bukkit
	            .createBlockData("minecraft:oak_stairs[half=bottom,shape=outer_right,facing=west]");
	    private final static BlockData STAIRS2 = Bukkit
	            .createBlockData("minecraft:oak_stairs[half=bottom,shape=outer_right,facing=south]");
	    private final static BlockData STAIRS3 = Bukkit
	            .createBlockData("minecraft:oak_stairs[half=bottom,shape=outer_right,facing=north]");
	    
	    // FAWE BlockState 缓存
	    private static final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();
	    
	    private static BlockState getCachedBlockState(Material material) {
	        if (material == null) return null;
	        return blockStateCache.computeIfAbsent(material, 
	            m -> BukkitAdapter.adapt(m.createBlockData()));
	    }
	    
	    private static BlockState getCachedBlockState(BlockData blockData) {
	        return getCachedBlockState(blockData.getMaterial());
	    }

	    @Override
	    public void populateRoom(MazeRoomBlockPopulatorArgs args) {
	        final AsyncWorldEditor world = args.getWorld();
	        final int chunkx = args.getChunkX(), chunkz = args.getChunkZ();
	        world.setChunk(chunkx, chunkz);
	        final int x = args.getRoomChunkX();
	        final int yFloor = args.getFloorY();
	        final int z = args.getRoomChunkZ();

	        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
	                .world(BukkitAdapter.adapt(world.getWorld()))
	                .allowedRegionsEverywhere()
	                .limitUnlimited()
	                .changeSetNull()
	                .fastMode(true)
	                .build()) {
	            
	            Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
	            
	            BlockState stoneState = getCachedBlockState(Material.STONE);
	            BlockState cobblestoneState = getCachedBlockState(Material.COBBLESTONE);
	            BlockState stoneBricksState = getCachedBlockState(Material.STONE_BRICKS);
	            BlockState waterState = getCachedBlockState(Material.WATER);
	            BlockState oakFenceState = getCachedBlockState(Material.OAK_FENCE);
	            BlockState glowstoneState = getCachedBlockState(Material.GLOWSTONE);
	            
	            // 地板
	            for (int[] pos : FLOOR_AREA) {
	                BlockVector3 blockPos = BlockVector3.at(chunkx * 16 + x + pos[0], yFloor, chunkz * 16 + z + pos[1]);
	                blocksToSet.put(blockPos, stoneState);
	            }
	            
	            // 地板下方圆石
	            for (int[] pos : FLOOR_UNDER_AREA) {
	                BlockVector3 blockPos = BlockVector3.at(chunkx * 16 + x + pos[0], yFloor - 1, chunkz * 16 + z + pos[1]);
	                blocksToSet.put(blockPos, cobblestoneState);
	            }
	            
	            // 水井结构
	            for (int[] pos : WELL_AREA) {
	                BlockVector3 blockPos = BlockVector3.at(chunkx * 16 + x + pos[0], yFloor + 1, chunkz * 16 + z + pos[1]);
	                blocksToSet.put(blockPos, stoneBricksState);
	            }
	            
	            // 井水
	            BlockVector3 waterPos = BlockVector3.at(chunkx * 16 + x + 3, yFloor + 1, chunkz * 16 + z + 3);
	            blocksToSet.put(waterPos, waterState);
	            
	            // 柱子
	            int[][] fencePositions = {
	                {2, 2}, {2, 4}, {4, 2}, {4, 4}
	            };
	            for (int[] pos : fencePositions) {
	                BlockVector3 fencePos = BlockVector3.at(chunkx * 16 + x + pos[0], yFloor + 2, chunkz * 16 + z + pos[1]);
	                blocksToSet.put(fencePos, oakFenceState);
	            }
	            
	            // 屋顶
	            BlockVector3 roof1 = BlockVector3.at(chunkx * 16 + x + 2, yFloor + 3, chunkz * 16 + z + 2);
	            BlockVector3 roof2 = BlockVector3.at(chunkx * 16 + x + 2, yFloor + 3, chunkz * 16 + z + 3);
	            BlockVector3 roof3 = BlockVector3.at(chunkx * 16 + x + 2, yFloor + 3, chunkz * 16 + z + 4);
	            BlockVector3 roof4 = BlockVector3.at(chunkx * 16 + x + 3, yFloor + 3, chunkz * 16 + z + 2);
	            BlockVector3 roof5 = BlockVector3.at(chunkx * 16 + x + 3, yFloor + 3, chunkz * 16 + z + 3);
	            BlockVector3 roof6 = BlockVector3.at(chunkx * 16 + x + 3, yFloor + 3, chunkz * 16 + z + 4);
	            BlockVector3 roof7 = BlockVector3.at(chunkx * 16 + x + 4, yFloor + 3, chunkz * 16 + z + 2);
	            BlockVector3 roof8 = BlockVector3.at(chunkx * 16 + x + 4, yFloor + 3, chunkz * 16 + z + 3);
	            BlockVector3 roof9 = BlockVector3.at(chunkx * 16 + x + 4, yFloor + 3, chunkz * 16 + z + 4);
	            
	            blocksToSet.put(roof1, getCachedBlockState(STEP2));
	            blocksToSet.put(roof2, getCachedBlockState(STAIRS0));
	            blocksToSet.put(roof3, getCachedBlockState(STEP2));
	            blocksToSet.put(roof4, getCachedBlockState(STAIRS2));
	            blocksToSet.put(roof5, glowstoneState);
	            blocksToSet.put(roof6, getCachedBlockState(STAIRS3));
	            blocksToSet.put(roof7, getCachedBlockState(STEP2));
	            blocksToSet.put(roof8, getCachedBlockState(STAIRS1));
	            blocksToSet.put(roof9, getCachedBlockState(STEP2));
	            
	            // 批量设置
	            for (Map.Entry<BlockVector3, BlockState> entry : blocksToSet.entrySet()) {
	                editSession.setBlock(entry.getKey(), entry.getValue());
	            }
	            
	            editSession.flushQueue();
	            
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	    @Override
	    public float getRoomChance() {
	        return ROOM_CHANCE;
	    }

	    @Override
	    public int getMinimumLayer() {
	        return LAYER_MIN;
	    }

	    @Override
	    public int getMaximumLayer() {
	        return LAYER_MAX;
	    }
	}