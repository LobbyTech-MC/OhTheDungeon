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
package otd.world;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

/**
 * @author shadow
 * @modified FAWE 2.15.1
 */
public class DungeonWorldChunkGenerator extends ChunkGenerator {
    
    // FAWE BlockState 缓存
    private static BlockState cachedBedrockState = null;
    private static BlockState cachedStoneState = null;
    private static BlockState cachedDirtState = null;
    private static BlockState cachedGrassState = null;
    
    private static BlockState getCachedBedrockState() {
        if (cachedBedrockState == null) {
            cachedBedrockState = BukkitAdapter.adapt(Material.BEDROCK.createBlockData());
        }
        return cachedBedrockState;
    }
    
    private static BlockState getCachedStoneState() {
        if (cachedStoneState == null) {
            cachedStoneState = BukkitAdapter.adapt(Material.STONE.createBlockData());
        }
        return cachedStoneState;
    }
    
    private static BlockState getCachedDirtState() {
        if (cachedDirtState == null) {
            cachedDirtState = BukkitAdapter.adapt(Material.DIRT.createBlockData());
        }
        return cachedDirtState;
    }
    
    private static BlockState getCachedGrassState() {
        if (cachedGrassState == null) {
            cachedGrassState = BukkitAdapter.adapt(Material.GRASS_BLOCK.createBlockData());
        }
        return cachedGrassState;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData chunk = Bukkit.createChunkData(world);

        String chunkPos = chunkX + "," + chunkZ;
        
        // 设置生物群系
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                biome.setBiome(i, j, Biome.PLAINS);
            }
        }
        
        // 如果区块在列表中，使用 FAWE 生成地形
        if (ChunkList.chunks.containsKey(chunkPos)) {
            // 使用 FAWE 批量生成地形
            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(world))
                    .allowedRegionsEverywhere()
                    .limitUnlimited()
                    .changeSetNull()
                    .fastMode(true)
                    .build()) {
                
                int startX = chunkX * 16;
                int startZ = chunkZ * 16;
                
                // 预计算所有需要设置的方块
                BlockState bedrockState = getCachedBedrockState();
                BlockState stoneState = getCachedStoneState();
                BlockState dirtState = getCachedDirtState();
                BlockState grassState = getCachedGrassState();
                
                // 批量设置基岩层 (Y=0)
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockVector3 pos = BlockVector3.at(startX + x, 0, startZ + z);
                        editSession.setBlock(pos, bedrockState);
                    }
                }
                
                // 批量设置石头层 (Y=1 到 60)
                for (int y = 1; y <= 60; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockVector3 pos = BlockVector3.at(startX + x, y, startZ + z);
                            editSession.setBlock(pos, stoneState);
                        }
                    }
                }
                
                // 批量设置泥土层 (Y=61, 62, 63)
                for (int y = 61; y <= 63; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockVector3 pos = BlockVector3.at(startX + x, y, startZ + z);
                            editSession.setBlock(pos, dirtState);
                        }
                    }
                }
                
                // 批量设置草方块层 (Y=64)
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockVector3 pos = BlockVector3.at(startX + x, 64, startZ + z);
                        editSession.setBlock(pos, grassState);
                    }
                }
                
                editSession.flushQueue();
                
            } catch (Exception e) {
                e.printStackTrace();
                // 回退到 ChunkData 方式
                fallbackToChunkData(chunk, random, chunkX, chunkZ);
            }
        }

        return chunk;
    }
    
    /**
     * 回退方法：使用传统的 ChunkData 方式生成地形
     */
    private void fallbackToChunkData(ChunkData chunk, Random random, int chunkX, int chunkZ) {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                chunk.setBlock(i, 0, j, Material.BEDROCK);
                for (int k = 1; k <= 60; k++) {
                    chunk.setBlock(i, k, j, Material.STONE);
                }
                chunk.setBlock(i, 61, j, Material.DIRT);
                chunk.setBlock(i, 62, j, Material.DIRT);
                chunk.setBlock(i, 63, j, Material.DIRT);
                chunk.setBlock(i, 64, j, Material.GRASS_BLOCK);
            }
        }
    }
}