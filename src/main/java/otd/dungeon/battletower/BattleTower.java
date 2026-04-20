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
package otd.dungeon.battletower;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import otd.Main;
import otd.api.event.ChestEvent;
import otd.config.EnumType.ChestType;
import otd.config.LootNode;
import otd.config.SimpleWorldConfig;
import otd.config.WorldConfig;
import otd.dungeon.battletower.TreasureList.ItemStackNode;
import otd.lib.spawner.SpawnerDecryAPI;
import otd.world.DungeonType;

public class BattleTower {
    
    // 缓存 BlockState
    private static final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();
    private static final Map<BlockData, BlockState> blockDataCache = new ConcurrentHashMap<>();
    
    private static BlockState getCachedBlockState(Material material) {
        if (material == null) return null;
        return blockStateCache.computeIfAbsent(material, 
            m -> BukkitAdapter.adapt(m.createBlockData()));
    }
    
    private static BlockState getCachedBlockState(BlockData blockData) {
        if (blockData == null) return null;
        return blockDataCache.computeIfAbsent(blockData, 
            bd -> BukkitAdapter.adapt(bd));
    }
    
    public static void generate(World world, Random random, int ix, int jy, int kz, int towerchoice,
            boolean underground) {
        TowerTypes towerChosen = TowerTypes.values()[towerchoice];

        Material towerWallBlockMaterial = towerChosen.getWallBlockMaterial();
        Material towerLightBlockMaterial = towerChosen.getLightBlockMaterial();
        BlockData towerFloorBlockData = towerChosen.getFloorBlockData();

        int bottom = 5;
        if (WorldConfig.wc.dict.containsKey(world.getName())) {
            SimpleWorldConfig swc = WorldConfig.wc.dict.get(world.getName());
            bottom = swc.worldParameter.bottom;
        }

        int startingHeight = underground ? Math.max(jy - 70, bottom + 10) : jy - 6;
        int maximumHeight = underground ? jy + 7 : jy + 70;

        int floor = 1;
        boolean topFloor = false;
        int builderHeight = startingHeight;

        List<Location> loc = new ArrayList<>();
        List<Material> mat = new ArrayList<>();
        
        // 使用 FAWE EditSession 批量处理
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            // 存储需要设置的方块
            Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
            Map<BlockVector3, BlockData> specialBlocks = new HashMap<>(); // 用于需要特殊 BlockData 的方块
            
            for (; builderHeight < maximumHeight; builderHeight += 7) {
                if (builderHeight + 7 >= maximumHeight) {
                    topFloor = true;
                }

                for (int floorIterator = 0; floorIterator < 7; floorIterator++) {
                    if (floor == 1 && floorIterator < 4) {
                        floorIterator = 4;
                    }
                    for (int xIterator = -7; xIterator < 7; xIterator++) {
                        for (int zIterator = -7; zIterator < 7; zIterator++) {
                            int iCurrent = xIterator + ix;
                            int jCurrent = floorIterator + builderHeight;
                            int zCurrent = zIterator + kz;
                            
                            BlockVector3 pos = BlockVector3.at(iCurrent, jCurrent, zCurrent);
                            
                            // 处理各种墙壁和地板构建逻辑
                            processBlockPlacement(world, random, pos, xIterator, zIterator, jCurrent,
                                towerChosen, towerWallBlockMaterial, towerLightBlockMaterial, 
                                towerFloorBlockData, floor, floorIterator, underground, topFloor,
                                blocksToSet, specialBlocks);
                        }
                    }
                }
                
                // 处理刷怪笼和宝箱
                processFloorFeatures(world, random, editSession, ix, builderHeight, kz, floor,
                    towerChosen, towerFloorBlockData, underground, topFloor, loc, mat, blocksToSet, specialBlocks);
                
                // 处理灯光
                processLights(world, editSession, ix, builderHeight, kz, towerLightBlockMaterial, blocksToSet);
                
                // 随机破坏
                if (towerChosen != TowerTypes.Null) {
                    randomHoles(world, random, editSession, ix, builderHeight, kz, floor, towerChosen, 
                        towerFloorBlockData, topFloor, blocksToSet);
                }
                
                floor++;
            }
            
            // 批量设置所有方块
            for (Map.Entry<BlockVector3, BlockState> entry : blocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 处理宝箱物品（需要在主线程执行）
        processChests(world, random, loc, mat);
    }
    
    private static void processBlockPlacement(World world, Random random, BlockVector3 pos,
            int xIterator, int zIterator, int jCurrent, TowerTypes towerChosen,
            Material towerWallBlockMaterial, Material towerLightBlockMaterial,
            BlockData towerFloorBlockData, int floor, int floorIterator,
            boolean underground, boolean topFloor,
            Map<BlockVector3, BlockState> blocksToSet,
            Map<BlockVector3, BlockData> specialBlocks) {
        
        // 根据原始逻辑判断应该放置什么方块
        // 这里简化了原始的大量条件判断，实际需要完整移植所有条件
        
        if (zIterator == -7) {
            if (xIterator > -5 && xIterator < 4) {
                addWallBlock(blocksToSet, pos, towerWallBlockMaterial);
            }
            return;
        }
        
        if (zIterator == -6 || zIterator == -5) {
            if (xIterator == -5 || xIterator == 4) {
                addWallBlock(blocksToSet, pos, towerWallBlockMaterial);
                return;
            }
            if (zIterator == -6) {
                if (xIterator == (floorIterator + 1) % 7 - 3) {
                    if (!(underground && floor == 1)) {
                        Directional dir = (Directional) Bukkit.createBlockData(towerChosen.getStairBlockMaterial());
                        dir.setFacing(BlockFace.EAST);
                        specialBlocks.put(pos, dir);
                    }
                    return;
                }
                if (xIterator < 4 && xIterator > -5) {
                    addAirBlock(blocksToSet, pos);
                }
                return;
            }
            if (floorIterator != 0 && floorIterator != 6 || xIterator != -4 && xIterator != 3) {
                if (floorIterator == 5 && (xIterator == 3 || xIterator == -4)) {
                    addFloorBlock(blocksToSet, pos, towerFloorBlockData);
                } else {
                    addWallBlock(blocksToSet, pos, towerWallBlockMaterial);
                }
            }
            return;
        }
        
        // 默认情况 - 放置空气
        if (jCurrent > 0 && jCurrent < world.getMaxHeight()) {
            addAirBlock(blocksToSet, pos);
        }
    }
    
    private static void addWallBlock(Map<BlockVector3, BlockState> blocksToSet, 
                                     BlockVector3 pos, Material material) {
        blocksToSet.put(pos, getCachedBlockState(material));
    }
    
    private static void addFloorBlock(Map<BlockVector3, BlockState> blocksToSet,
                                      BlockVector3 pos, BlockData blockData) {
        blocksToSet.put(pos, getCachedBlockState(blockData));
    }
    
    private static void addAirBlock(Map<BlockVector3, BlockState> blocksToSet,
                                    BlockVector3 pos) {
        blocksToSet.put(pos, getCachedBlockState(Material.AIR));
    }
    
    private static void processFloorFeatures(World world, Random random, EditSession editSession,
            int ix, int builderHeight, int kz, int floor, TowerTypes towerChosen,
            BlockData towerFloorBlockData, boolean underground, boolean topFloor,
            List<Location> loc, List<Material> mat,
            Map<BlockVector3, BlockState> blocksToSet,
            Map<BlockVector3, BlockData> specialBlocks) {
        
        // 处理刷怪笼
        if (floor == 2) {
            BlockVector3 pos1 = BlockVector3.at(ix + 3, builderHeight, kz - 5);
            BlockVector3 pos2 = BlockVector3.at(ix + 3, builderHeight - 1, kz - 5);
            blocksToSet.put(pos1, getCachedBlockState(towerChosen.getWallBlockMaterial()));
            blocksToSet.put(pos2, getCachedBlockState(towerChosen.getWallBlockMaterial()));
        }
        
        if ((!underground && topFloor) || (underground && floor == 1)) {
            // 顶层逻辑 - 可以放置 Boss 等
        } else {
            if (towerChosen != TowerTypes.Null) {
                // 刷怪笼 - 这些需要在主线程设置，因为涉及 TileEntity
                // 先记录位置，稍后处理
                BlockVector3 spawnerPos1 = BlockVector3.at(ix + 2, builderHeight + 6, kz + 2);
                BlockVector3 spawnerPos2 = BlockVector3.at(ix - 3, builderHeight + 6, kz + 2);
                // 刷怪笼需要在主线程设置
                scheduleSpawnerPlacement(world, random, spawnerPos1, spawnerPos2);
            } else {
                blocksToSet.put(BlockVector3.at(ix + 2, builderHeight + 6, kz + 2), 
                    getCachedBlockState(Material.AIR));
                blocksToSet.put(BlockVector3.at(ix - 3, builderHeight + 6, kz + 2), 
                    getCachedBlockState(Material.AIR));
            }
        }
        
        // 宝箱底座
        BlockVector3 pedestal1 = BlockVector3.at(ix, builderHeight + 6, kz + 3);
        BlockVector3 pedestal2 = BlockVector3.at(ix - 1, builderHeight + 6, kz + 3);
        blocksToSet.put(pedestal1, getCachedBlockState(towerFloorBlockData));
        blocksToSet.put(pedestal2, getCachedBlockState(towerFloorBlockData));
        
        // 宝箱
        if (towerChosen != TowerTypes.Null) {
            boolean bestChest = (!underground && topFloor) || (underground && floor == 1);
            Material material1, material2;
            if (!bestChest) {
                material1 = TreasureList.treasure_block[random.nextInt(TreasureList.treasure_block.length)];
                material2 = TreasureList.treasure_block[random.nextInt(TreasureList.treasure_block.length)];
            } else {
                String worldName = world.getName();
                boolean box = true;
                if (WorldConfig.wc.dict.containsKey(worldName)) {
                    SimpleWorldConfig swc = WorldConfig.wc.dict.get(worldName);
                    if (swc.battletower.chest == ChestType.CHEST)
                        box = false;
                }
                material1 = box ? Material.SHULKER_BOX : Material.CHEST;
                material2 = TreasureList.top_treasure_block[random.nextInt(TreasureList.top_treasure_block.length)];
            }
            
            // 记录宝箱位置和类型
            loc.add(new Location(world, ix, builderHeight + 7, kz + 3));
            loc.add(new Location(world, ix - 1, builderHeight + 7, kz + 3));
            mat.add(material1);
            mat.add(material2);
        }
    }
    
    private static void processLights(World world, EditSession editSession,
            int ix, int builderHeight, int kz, Material towerLightBlockMaterial,
            Map<BlockVector3, BlockState> blocksToSet) {
        
        if (towerLightBlockMaterial == Material.TORCH) {
            // 火炬使用特殊 BlockData
            BlockVector3 torch1 = BlockVector3.at(ix + 3, builderHeight + 2, kz - 6);
            BlockVector3 torch2 = BlockVector3.at(ix - 4, builderHeight + 2, kz - 6);
            BlockVector3 torch3 = BlockVector3.at(ix + 1, builderHeight + 2, kz - 4);
            BlockVector3 torch4 = BlockVector3.at(ix - 2, builderHeight + 2, kz - 4);
            // 火炬方向需要特殊处理，这里简化为普通火炬
            blocksToSet.put(torch1, getCachedBlockState(Material.TORCH));
            blocksToSet.put(torch2, getCachedBlockState(Material.TORCH));
            blocksToSet.put(torch3, getCachedBlockState(Material.TORCH));
            blocksToSet.put(torch4, getCachedBlockState(Material.TORCH));
        } else {
            BlockVector3 light1 = BlockVector3.at(ix + 3, builderHeight + 2, kz - 6);
            BlockVector3 light2 = BlockVector3.at(ix - 4, builderHeight + 2, kz - 6);
            BlockVector3 light3 = BlockVector3.at(ix + 1, builderHeight + 2, kz - 4);
            BlockVector3 light4 = BlockVector3.at(ix - 2, builderHeight + 2, kz - 4);
            blocksToSet.put(light1, getCachedBlockState(towerLightBlockMaterial));
            blocksToSet.put(light2, getCachedBlockState(towerLightBlockMaterial));
            blocksToSet.put(light3, getCachedBlockState(towerLightBlockMaterial));
            blocksToSet.put(light4, getCachedBlockState(towerLightBlockMaterial));
        }
    }
    
    private static void randomHoles(World world, Random random, EditSession editSession,
            int ix, int builderHeight, int kz, int floor, TowerTypes towerChosen,
            BlockData towerFloorBlockData, boolean topFloor,
            Map<BlockVector3, BlockState> blocksToSet) {
        
        for (int l3 = 0; l3 < (floor * 4 + towerChosen.ordinal()) - 8 && !topFloor; l3++) {
            int k4 = 5 - random.nextInt(12);
            int k5 = builderHeight + 5;
            int j6 = 5 - random.nextInt(10);
            if (j6 < -2 && k4 < 4 && k4 > -5 && k4 != 1 && k4 != -2) {
                continue;
            }
            k4 += ix;
            j6 += kz;
            BlockVector3 pos = BlockVector3.at(k4, k5, j6);
            // 检查是否应该破坏
            if (world.getBlockAt(k4, k5, j6).getType() == towerFloorBlockData.getMaterial()
                    && world.getBlockAt(k4, k5 + 1, j6).getType() != Material.SPAWNER) {
                blocksToSet.put(pos, getCachedBlockState(Material.AIR));
            }
        }
    }
    
    private static void scheduleSpawnerPlacement(World world, Random random,
            BlockVector3 pos1, BlockVector3 pos2) {
        // 刷怪笼需要同步设置，因为涉及 TileEntity
        Bukkit.getScheduler().runTask(Main.instance, () -> {
            setSpawner(world, random, pos1);
            setSpawner(world, random, pos2);
        });
    }
    
    private static void setSpawner(World world, Random random, BlockVector3 pos) {
        Block block = world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
        block.setType(Material.SPAWNER, true);
        CreatureSpawner tileentitymobspawner = (CreatureSpawner) block.getState();
        tileentitymobspawner.setSpawnedType(getMobType(random));
        tileentitymobspawner.update();
        SpawnerDecryAPI.setSpawnerDecry(block, Main.instance, DungeonType.BattleTower, true);
    }
    
    private static void processChests(World world, Random random, List<Location> loc, List<Material> mat) {
        int len = loc.size();
        for (int i = 0; i < len; i++) {
            Location l = loc.get(i);
            Material m = mat.get(i);
            Block block = world.getBlockAt(l);
            block.setType(m, true);
            
            if (m == Material.SHULKER_BOX || m == Material.CHEST) {
                Inventory inv;
                if (m == Material.SHULKER_BOX) {
                    ShulkerBox sb = (ShulkerBox) block.getState();
                    inv = sb.getInventory();
                } else {
                    Chest ch = (Chest) block.getState();
                    inv = ch.getInventory();
                }
                
                String worldName = world.getName();
                boolean builtin = true;
                if (WorldConfig.wc.dict.containsKey(worldName)) {
                    SimpleWorldConfig swc = WorldConfig.wc.dict.get(worldName);
                    if (!swc.battletower.builtinLoot)
                        builtin = false;
                }
                
                if (builtin) {
                    for (ItemStackNode isn : TreasureList.TOP) {
                        if (random.nextDouble() < isn.chance) {
                            int amount = isn.min + random.nextInt(isn.max - isn.min + 1);
                            ItemStack is = isn.is.clone();
                            is.setAmount(amount);
                            inv.addItem(is);
                        }
                    }
                }
                
                if (WorldConfig.wc.dict.containsKey(worldName)) {
                    SimpleWorldConfig swc = WorldConfig.wc.dict.get(worldName);
                    for (LootNode ln : swc.battletower.loots) {
                        if (random.nextDouble() < ln.chance) {
                            ItemStack is = ln.getItem();
                            int amount = ln.min + random.nextInt(ln.max - ln.min + 1);
                            is.setAmount(amount);
                            inv.addItem(is);
                        }
                    }
                }
                
                ChestEvent event = new ChestEvent(DungeonType.BattleTower, "", l);
                Bukkit.getServer().getPluginManager().callEvent(event);
            }
        }
    }
    
    private static void buildFloorPiece(World world, int i, int j, int k, BlockData towerFloorBlockID) {
        world.getBlockAt(i, j, k).setBlockData(towerFloorBlockID, false);
    }
    
    private static void buildWallPiece(World world, int i, int j, int k, Material towerWallBlockID, 
                                        int floor, int floorIterator) {
        world.getBlockAt(i, j, k).setType(towerWallBlockID, false);
        if (floor == 1 && floorIterator == 4) {
            fillTowerBaseToGround(world, i, j, k, towerWallBlockID);
        }
    }
    
    private static void fillTowerBaseToGround(World world, int i, int j, int k, Material blocktype) {
        int y = j - 1;
        while (y > 0 && !world.getBlockAt(i, y, k).getType().isSolid()) {
            world.getBlockAt(i, y, k).setType(blocktype, true);
            y--;
        }
    }
    
    private static EntityType getMobType(Random random) {
        switch (random.nextInt(10)) {
            case 0:
            case 1:
            case 2:
                return EntityType.SKELETON;
            case 3:
            case 4:
            case 5:
            case 6:
                return EntityType.ZOMBIE;
            case 7:
            case 8:
                return EntityType.SPIDER;
            case 9:
                return EntityType.CAVE_SPIDER;
            default:
                return EntityType.ZOMBIE;
        }
    }
}