/**
 * 
 */
package forge_sandbox.com.someguyssoftware.dungeons2.style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.BlockPos;
import forge_sandbox.GroupHelper;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Dungeon;
import forge_sandbox.com.someguyssoftware.dungeons2.model.LevelConfig;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Room;
import forge_sandbox.com.someguyssoftware.dungeons2.spawner.SpawnSheet;
import forge_sandbox.com.someguyssoftware.dungeonsengine.chest.ILootLoader;
import forge_sandbox.com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import forge_sandbox.com.someguyssoftware.gottschcore.positional.ICoords;
import otd.lib.async.AsyncWorldEditor;

/**
 * @author Mark Gottschling on Feb 15, 2017
 * @modified FAWE 2.15.1
 */
public class LibraryRoomDecorator extends RoomDecorator {
    
    private static final int CARPET_PERCENT_CHANCE = 85;
    
    // 缓存 BlockState
    private final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();
    
    // 缓存橡木原木的 BlockState
    private BlockState cachedOakLogState;
    private BlockState cachedOakPlanksState;
    
    /**
     * 
     * @param loader
     * @param spawnSheet
     */
    public LibraryRoomDecorator(ILootLoader loader, SpawnSheet spawnSheet) {
        super(loader, spawnSheet);
        // 初始化缓存
        cachedOakLogState = getCachedBlockState(GroupHelper.OAK_LOG_Y.clone());
        cachedOakPlanksState = getCachedBlockState(Material.OAK_PLANKS.createBlockData());
    }

    @Override
    public void decorate(AsyncWorldEditor world, Random random, Dungeon dungeon, IDungeonsBlockProvider provider,
            Room room, ILevelConfig config) {
        
        List<Entry<DesignElement, ICoords>> surfaceAirZone = room.getFloorMap().entries().stream()
                .filter(x -> x.getKey().getFamily() == DesignElement.SURFACE_AIR).collect(Collectors.toList());

        if (surfaceAirZone == null || surfaceAirZone.isEmpty())
            return;

        List<ICoords> wallZone = (List<ICoords>) room.getFloorMap().get(DesignElement.WALL_AIR);
        List<ICoords> floorZone = (List<ICoords>) room.getFloorMap().get(DesignElement.FLOOR_AIR);

        if (floorZone == null) floorZone = new ArrayList<>();
        if (wallZone == null) wallZone = new ArrayList<>();

        List<ICoords> removeFloorZones = new ArrayList<>();
        List<ICoords> removeWallZones = new ArrayList<>();

        // 使用 FAWE EditSession 批量处理
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            // 存储需要设置的方块
            Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
            
            Material carpet = GroupHelper.CARPETS.get(random.nextInt(GroupHelper.CARPETS.size()));
            BlockState carpetState = getCachedBlockState(carpet.createBlockData());
            BlockState bookshelfState = getCachedBlockState(Material.BOOKSHELF.createBlockData());
            
            // 处理地板区域
            processFloorZone(world, random, room, provider, floorZone, removeFloorZones,
                    carpet, carpetState, bookshelfState, blocksToSet);
            
            // 处理墙壁区域
            processWallZone(world, room, provider, wallZone, removeWallZones,
                    bookshelfState, blocksToSet);
            
            // 批量设置所有方块
            for (Map.Entry<BlockVector3, BlockState> entry : blocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 从 FloorMap 中移除已处理的区域
        for (ICoords c : removeFloorZones) {
            room.getFloorMap().remove(DesignElement.FLOOR_AIR, c);
        }
        for (ICoords c : removeWallZones) {
            room.getFloorMap().remove(DesignElement.WALL_AIR, c);
        }

        floorZone.removeAll(removeFloorZones);
        wallZone.removeAll(removeWallZones);

        // 正常装饰
        super.decorate(world, random, dungeon, provider, room, config);
    }
    
    /**
     * 处理地板区域
     */
    private void processFloorZone(AsyncWorldEditor world, Random random, Room room, IDungeonsBlockProvider provider,
                                  List<ICoords> floorZone, List<ICoords> removeFloorZones,
                                  Material carpet, BlockState carpetState, BlockState bookshelfState,
                                  Map<BlockVector3, BlockState> blocksToSet) {
        
        for (ICoords coords : floorZone) {
            BlockPos floorPos = coords.toPos().down();
            
            // 获取相对于房间的索引
            int xIndex = coords.getX() - room.getCoords().getX();
            int zIndex = coords.getZ() - room.getCoords().getZ();
            
            BlockVector3 position = BlockVector3.at(coords.getX(), coords.getY(), coords.getZ());
            BlockVector3 floorPosition = BlockVector3.at(floorPos.getX(), floorPos.getY(), floorPos.getZ());
            
            // 检查是否靠墙
            boolean againstWall = coords.getX() == room.getMinX() + 1 || coords.getX() == room.getMaxX() - 1
                    || coords.getZ() == room.getMinZ() + 1 || coords.getZ() == room.getMaxZ() - 1;
            
            if (againstWall) {
                if (hasSupport(world, coords, DesignElement.FLOOR_AIR,
                        provider.getLocation(coords, room, room.getLayout()))) {
                    
                    // 检查是否是第4个方块（柱子位置）
                    boolean isFourthBlock = ((coords.getX() == room.getMinX() + 1 || coords.getX() == room.getMaxX() - 1)
                            && Math.abs(zIndex) % 4 == 0)
                            || ((coords.getZ() == room.getMinZ() + 1 || coords.getZ() == room.getMaxZ() - 1)
                                    && Math.abs(xIndex) % 4 == 0);
                    
                    if (isFourthBlock) {
                        blocksToSet.put(position, cachedOakLogState);
                    } else {
                        blocksToSet.put(position, bookshelfState);
                    }
                    removeFloorZones.add(coords);
                }
            } else {
                // 添加地毯
                if (random.nextInt(100) < CARPET_PERCENT_CHANCE) {
                    if (world.getBlockState(floorPos).isSolid()) {
                        blocksToSet.put(position, carpetState);
                    }
                }
            }
            
            // 替换地板方块为橡木木板
            if (world.getBlockState(floorPos).isSolid()) {
                blocksToSet.put(floorPosition, cachedOakPlanksState);
            }
        }
    }
    
    /**
     * 处理墙壁区域
     */
    private void processWallZone(AsyncWorldEditor world, Room room, IDungeonsBlockProvider provider,
                                 List<ICoords> wallZone, List<ICoords> removeWallZones,
                                 BlockState bookshelfState, Map<BlockVector3, BlockState> blocksToSet) {
        
        for (ICoords coords : wallZone) {
            if (hasSupport(world, coords, DesignElement.WALL_AIR,
                    provider.getLocation(coords, room, room.getLayout()))) {
                
                // 获取相对于房间的索引
                int xIndex = coords.getX() - room.getCoords().getX();
                int zIndex = coords.getZ() - room.getCoords().getZ();
                
                BlockVector3 position = BlockVector3.at(coords.getX(), coords.getY(), coords.getZ());
                
                // 检查是否是第4个方块（柱子位置）
                boolean isFourthBlock = ((coords.getX() == room.getMinX() + 1 || coords.getX() == room.getMaxX() - 1)
                        && Math.abs(zIndex) % 4 == 0)
                        || ((coords.getZ() == room.getMinZ() + 1 || coords.getZ() == room.getMaxZ() - 1)
                                && Math.abs(xIndex) % 4 == 0);
                
                if (isFourthBlock) {
                    blocksToSet.put(position, cachedOakLogState);
                } else {
                    blocksToSet.put(position, bookshelfState);
                }
                removeWallZones.add(coords);
            }
        }
    }
    
    /**
     * 获取缓存的 BlockState
     */
    private BlockState getCachedBlockState(BlockData blockData) {
        if (blockData == null) return null;
        Material material = blockData.getMaterial();
        return blockStateCache.computeIfAbsent(material, 
            m -> BukkitAdapter.adapt(blockData));
    }

    @Deprecated
    @Override
    public void decorate(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
            LevelConfig config) {
        // 委托给新版本
        Dungeon tempDungeon = new Dungeon();
        decorate(world, random, tempDungeon, provider, room, (ILevelConfig) config);
    }
}