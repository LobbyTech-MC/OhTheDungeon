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
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.block.Biome;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.BlockPos;
import otd.dungeon.dungeonmaze.populator.ChunkBlockPopulator;
import otd.dungeon.dungeonmaze.populator.ChunkBlockPopulatorArgs;
import otd.lib.BiomeDictionary;
import otd.lib.async.AsyncWorldEditor;
import otd.lib.async.later.smoofy.Tree_Later;

public class OasisChunkPopulator extends ChunkBlockPopulator {

    /** General populator constants. */
    private static final float CHUNK_CHANCE = .003f;

    /** Populator constants. */
    private static final int CHANCE_CLAYINDIRT = 10;
    
    // FAWE BlockState 缓存
    private static final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();
    private static final Map<Material, BlockState> airBlockStateCache = new ConcurrentHashMap<>();
    
    private static BlockState getCachedBlockState(Material material) {
        if (material == null) return null;
        if (material == Material.AIR) {
            return airBlockStateCache.computeIfAbsent(material, 
                m -> BukkitAdapter.adapt(m.createBlockData()));
        }
        return blockStateCache.computeIfAbsent(material, 
            m -> BukkitAdapter.adapt(m.createBlockData()));
    }

    public void apply_glass(int ymax, AsyncWorldEditor world, int x, int z, Biome b) {
        Set<BiomeDictionary.Type> set = BiomeDictionary.getTypes(b);
        if (set.contains(BiomeDictionary.Type.BEACH) || set.contains(BiomeDictionary.Type.OCEAN)) {
            int chunkx = x / 16, chunkz = z / 16;
            
            // 使用 FAWE 批量设置玻璃
            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(world.getWorld()))
                    .allowedRegionsEverywhere()
                    .limitUnlimited()
                    .changeSetNull()
                    .fastMode(true)
                    .build()) {
                
                Map<BlockVector3, BlockState> glassBlocks = new HashMap<>();
                BlockState glassState = getCachedBlockState(Material.GLASS);
                
                for (int i = 0; i < 16; i++) {
                    for (int j = 0; j < 16; j++) {
                        BlockVector3 pos = BlockVector3.at(chunkx * 16 + i, ymax, chunkz * 16 + j);
                        glassBlocks.put(pos, glassState);
                    }
                }
                
                for (Map.Entry<BlockVector3, BlockState> entry : glassBlocks.entrySet()) {
                    editSession.setBlock(entry.getKey(), entry.getValue());
                }
                editSession.flushQueue();
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void populateChunk(ChunkBlockPopulatorArgs args) {
        final AsyncWorldEditor world = args.getWorld();
        final Random rand = args.getRandom();
        final int chunkx = args.getChunkX(), chunkz = args.getChunkZ();
        world.setChunk(chunkx, chunkz);
        
        // Set this chunk as custom
        args.custom.add(Integer.toString(chunkx) + "," + Integer.toString(chunkz));
        
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
            
            BlockState dirtState = getCachedBlockState(Material.DIRT);
            BlockState clayState = getCachedBlockState(Material.CLAY);
            BlockState grassState = getCachedBlockState(Material.GRASS_BLOCK);
            BlockState airState = getCachedBlockState(Material.AIR);
            BlockState shortGrassState = getCachedBlockState(Material.SHORT_GRASS);
            BlockState waterState = getCachedBlockState(Material.WATER);
            BlockState sugarCaneState = getCachedBlockState(Material.SUGAR_CANE);
            
            // 生成泥土层 (Y=29)
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockVector3 pos = BlockVector3.at(chunkx * 16 + x, 29, chunkz * 16 + z);
                    blocksToSet.put(pos, dirtState);
                }
            }
            
            // 在泥土层中随机生成粘土
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (rand.nextInt(100) < CHANCE_CLAYINDIRT) {
                        BlockVector3 pos = BlockVector3.at(chunkx * 16 + x, 29, chunkz * 16 + z);
                        blocksToSet.put(pos, clayState);
                    }
                }
            }
            
            // 生成草皮层 (Y=30)
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockVector3 pos = BlockVector3.at(chunkx * 16 + x, 30, chunkz * 16 + z);
                    blocksToSet.put(pos, grassState);
                }
            }
            
            // 移除草皮层以上的所有石头 (Y=31 到 100)
            for (int y = 31; y <= 100; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockVector3 pos = BlockVector3.at(chunkx * 16 + x, y, chunkz * 16 + z);
                        blocksToSet.put(pos, airState);
                    }
                }
            }
            
            // 生成一些高草
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (rand.nextInt(100) < CHANCE_CLAYINDIRT) {
                        BlockVector3 pos = BlockVector3.at(chunkx * 16 + x, 31, chunkz * 16 + z);
                        blocksToSet.put(pos, shortGrassState);
                    }
                }
            }
            
            // 随机树偏移量 (0 或 1)
            int treeOffsetX = rand.nextInt(2);
            int treeOffsetZ = rand.nextInt(2);
            
            // 生成树周围的水
            // 顶部行
            for (int x = 5; x <= 11; x++) {
                BlockVector3 pos = BlockVector3.at(chunkx * 16 + x + treeOffsetX, 30, chunkz * 16 + 5 + treeOffsetZ);
                blocksToSet.put(pos, waterState);
            }
            // 左侧列
            for (int z = 5; z <= 11; z++) {
                BlockVector3 pos = BlockVector3.at(chunkx * 16 + 5 + treeOffsetX, 30, chunkz * 16 + z + treeOffsetZ);
                blocksToSet.put(pos, waterState);
            }
            // 底部行
            for (int x = 5; x <= 11; x++) {
                BlockVector3 pos = BlockVector3.at(chunkx * 16 + x + treeOffsetX, 30, chunkz * 16 + 11 + treeOffsetZ);
                blocksToSet.put(pos, waterState);
            }
            // 右侧列
            for (int z = 5; z <= 11; z++) {
                BlockVector3 pos = BlockVector3.at(chunkx * 16 + 11 + treeOffsetX, 30, chunkz * 16 + z + treeOffsetZ);
                blocksToSet.put(pos, waterState);
            }
            
            // 生成甘蔗
            BlockVector3 sugarCane1 = BlockVector3.at(chunkx * 16 + 6 + treeOffsetX, 31, chunkz * 16 + 6 + treeOffsetZ);
            BlockVector3 sugarCane2 = BlockVector3.at(chunkx * 16 + 6 + treeOffsetX, 31, chunkz * 16 + 10 + treeOffsetZ);
            BlockVector3 sugarCane3 = BlockVector3.at(chunkx * 16 + 10 + treeOffsetX, 31, chunkz * 16 + 6 + treeOffsetZ);
            BlockVector3 sugarCane4 = BlockVector3.at(chunkx * 16 + 10 + treeOffsetX, 31, chunkz * 16 + 10 + treeOffsetZ);
            blocksToSet.put(sugarCane1, sugarCaneState);
            blocksToSet.put(sugarCane2, sugarCaneState);
            blocksToSet.put(sugarCane3, sugarCaneState);
            blocksToSet.put(sugarCane4, sugarCaneState);
            
            // 批量设置所有方块
            for (Map.Entry<BlockVector3, BlockState> entry : blocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 随机树类型和生成树（需要在主线程执行）
        TreeType treeType = getRandomTreeType(rand);
        BlockPos treeLocation = new BlockPos(chunkx * 16 + 8 + rand.nextInt(2), 31, chunkz * 16 + 8 + rand.nextInt(2));
        world.addLater(new Tree_Later(treeLocation, treeType));
    }
    
    private TreeType getRandomTreeType(Random rand) {
        switch (rand.nextInt(5)) {
            case 0: return TreeType.BIG_TREE;
            case 1: return TreeType.BIRCH;
            case 2: return TreeType.REDWOOD;
            case 3: return TreeType.TALL_REDWOOD;
            case 4: return TreeType.TREE;
            default: return TreeType.TREE;
        }
    }

    @Override
    public float getChunkIterationsChance() {
        return CHUNK_CHANCE;
    }
}