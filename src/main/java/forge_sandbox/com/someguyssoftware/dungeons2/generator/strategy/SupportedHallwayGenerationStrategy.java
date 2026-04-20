/**
 * 
 */
package forge_sandbox.com.someguyssoftware.dungeons2.generator.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.google.common.collect.Multimap;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.AxisAlignedBB;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.AbstractRoomGenerationStrategy;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.Arrangement;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.ISupportedBlock;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.SupportedBlock;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.SupportedBlockProcessor;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Hallway;
import forge_sandbox.com.someguyssoftware.dungeons2.model.LevelConfig;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Room;
import forge_sandbox.com.someguyssoftware.dungeons2.style.DesignElement;
import forge_sandbox.com.someguyssoftware.dungeons2.style.StyleSheet;
import forge_sandbox.com.someguyssoftware.dungeons2.style.Theme;
import forge_sandbox.com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import forge_sandbox.com.someguyssoftware.gottschcore.positional.Coords;
import forge_sandbox.com.someguyssoftware.gottschcore.positional.ICoords;
import otd.lib.async.AsyncWorldEditor;

/**
 * @author Mark Gottschling on Sep 9, 2016
 * @modified FAWE 2.15.1
 */
public class SupportedHallwayGenerationStrategy extends AbstractRoomGenerationStrategy {
    
    /*
     * a list of all the rooms in the level
     */
    private List<Room> rooms;
    
    /*
     * a list of generated hallways
     */
    private List<Hallway> hallways;
    
    // 缓存常用的 BlockState
    private final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();
    
    /**
     * 
     * @param blockProvider
     * @param rooms
     * @param hallways
     */
    public SupportedHallwayGenerationStrategy(IDungeonsBlockProvider blockProvider, List<Room> rooms,
            List<Hallway> hallways) {
        super(blockProvider);
        setRooms(rooms);
        setHallways(hallways);
    }

    @Override
    public void generate(AsyncWorldEditor world, Random random, Room room, Theme theme, StyleSheet styleSheet,
            ILevelConfig config) {
        Hallway hallway = (Hallway) room;
        Map<ICoords, Arrangement> postProcessMap = new HashMap<>();
        Multimap<DesignElement, ICoords> blueprint = room.getFloorMap();

        SupportedBlockProcessor supportProcessor = new SupportedBlockProcessor(getBlockProvider(), room);
        ISupportedBlock supportedBlock;

        // 收集相交房间
        List<Room> intersectRooms = getIntersectingRooms(hallway);
        
        // 预计算边界框
        List<AxisAlignedBB> doorBBs = getDoorBoundingBoxes(hallway);
        List<AxisAlignedBB> hallwayBBs = getHallwayBoundingBoxes();

        // 使用 FAWE EditSession
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            // 存储需要设置的方块
            Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
            
            // 第一遍：正向遍历
            for (int y = 0; y < room.getHeight(); y++) {
                for (int z = 0; z < room.getDepth(); z++) {
                    for (int x = 0; x < room.getWidth(); x++) {

                        ICoords indexCoords = new Coords(x, y, z);
                        ICoords worldCoords = room.getCoords().add(indexCoords);

                        Arrangement arrangement = getBlockProvider().getArrangement(worldCoords, room, room.getLayout());

                        if (isPostProcessed(arrangement, worldCoords, postProcessMap)) {
                            continue;
                        }

                        BlockData blockData = getBlockProvider().getBlockState(random, worldCoords, room,
                                arrangement, theme, styleSheet, config);

                        // 处理空气和支持计算
                        if (blockData == null || arrangement.getElement() == DesignElement.AIR
                                || blockData.getMaterial() == Material.AIR
                                || blockData == IDungeonsBlockProvider.NULL_BLOCK) {
                            supportedBlock = new SupportedBlock(blockData, 100);
                            if (blockData != null && blockData.getMaterial() == Material.AIR) {
                                BlockVector3 pos = BlockVector3.at(
                                    worldCoords.getX(), worldCoords.getY(), worldCoords.getZ()
                                );
                                blocksToSet.put(pos, getCachedBlockState(blockData));
                                if (worldCoords.getY() == room.getMinY() + 1) {
                                    blueprint.put(arrangement.getElement(), worldCoords);
                                }
                            }
                        } else {
                            boolean buildBlock = isBlockBuildable(worldCoords, doorBBs, intersectRooms, hallwayBBs);
                            
                            if (buildBlock && blockData != IDungeonsBlockProvider.NULL_BLOCK) {
                                int amount = supportProcessor.applySupportRulesPass1(world, indexCoords, worldCoords,
                                        arrangement.getElement());
                                if (amount >= 100) {
                                    supportedBlock = new SupportedBlock(blockData, 100);
                                    BlockVector3 pos = BlockVector3.at(
                                        worldCoords.getX(), worldCoords.getY(), worldCoords.getZ()
                                    );
                                    blocksToSet.put(pos, getCachedBlockState(blockData));
                                } else {
                                    supportedBlock = new SupportedBlock(blockData, amount);
                                }
                            } else {
                                supportedBlock = new SupportedBlock(IDungeonsBlockProvider.NULL_BLOCK, 100);
                            }
                        }
                        supportProcessor.getSupportMatrix()[y][z][x] = supportedBlock;
                    }
                }

                // 第二遍：反向遍历
                for (int z = room.getDepth() - 1; z >= 0; z--) {
                    for (int x = room.getWidth() - 1; x >= 0; x--) {
                        supportedBlock = supportProcessor.getSupportMatrix()[y][z][x];
                        if (supportedBlock == null || supportedBlock.getAmount() < 100) {

                            ICoords indexCoords = new Coords(x, y, z);
                            ICoords worldCoords = room.getCoords().add(indexCoords);

                            Arrangement arrangement = getBlockProvider().getArrangement(worldCoords, room,
                                    room.getLayout());

                            BlockData blockData;
                            if (arrangement.getElement() != DesignElement.AIR) {
                                blockData = getBlockProvider().getBlockState(random, worldCoords, room,
                                        arrangement, theme, styleSheet, config);
                            } else {
                                blockData = Bukkit.createBlockData(Material.AIR);
                            }

                            if (blockData == null || blockData.getMaterial() == Material.AIR) {
                                BlockVector3 pos = BlockVector3.at(
                                    worldCoords.getX(), worldCoords.getY(), worldCoords.getZ()
                                );
                                blocksToSet.put(pos, getCachedBlockState(blockData));
                            } else {
                                supportedBlock = new SupportedBlock(blockData, 0);
                                boolean buildBlock = isBlockBuildable(worldCoords, doorBBs, intersectRooms, hallwayBBs);

                                if (buildBlock && blockData != IDungeonsBlockProvider.NULL_BLOCK) {
                                    int amount = supportProcessor.applySupportRulesPass2(world, indexCoords, worldCoords,
                                            arrangement.getElement());
                                    supportedBlock.setAmount(supportedBlock.getAmount() + amount);

                                    if (supportedBlock.getAmount() >= 100) {
                                        BlockVector3 pos = BlockVector3.at(
                                            worldCoords.getX(), worldCoords.getY(), worldCoords.getZ()
                                        );
                                        blocksToSet.put(pos, getCachedBlockState(blockData));
                                        if (worldCoords.getY() == room.getMinY() + 1) {
                                            blueprint.put(arrangement.getElement(), worldCoords);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 批量设置所有方块
            for (Map.Entry<BlockVector3, BlockState> entry : blocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 生成后处理方块
        postProcess(world, random, postProcessMap, room.getLayout(), theme, styleSheet, config);
    }

    /**
     * 获取相交的房间列表
     */
    private List<Room> getIntersectingRooms(Hallway hallway) {
        List<Room> intersectRooms = new ArrayList<>();
        for (Room otherRoom : getRooms()) {
            if (hallway.getBoundingBox().intersects(otherRoom.getBoundingBox())) {
                intersectRooms.add(otherRoom);
            }
        }
        return intersectRooms;
    }
    
    /**
     * 获取门连接的房间边界框
     */
    private List<AxisAlignedBB> getDoorBoundingBoxes(Hallway hallway) {
        List<AxisAlignedBB> doorBBs = new ArrayList<>();
        for (int i = 0; i < hallway.getDoors().size() && i < 2; i++) {
            if (hallway.getDoors().get(i) != null && hallway.getDoors().get(i).getRoom() != null) {
                doorBBs.add(hallway.getDoors().get(i).getRoom().getBoundingBox());
            }
        }
        return doorBBs;
    }
    
    /**
     * 获取所有走廊的边界框
     */
    private List<AxisAlignedBB> getHallwayBoundingBoxes() {
        List<AxisAlignedBB> hallwayBBs = new ArrayList<>();
        for (Room r : getHallways()) {
            hallwayBBs.add(r.getBoundingBox());
        }
        return hallwayBBs;
    }
    
    /**
     * 检查方块是否可构建
     */
    public boolean isBlockBuildable(ICoords worldCoords, List<AxisAlignedBB> doorBBs,
                                    List<Room> intersectRooms, List<AxisAlignedBB> hallwayBBs) {
        AxisAlignedBB box = new AxisAlignedBB(worldCoords.toPos());
        
        // 检查门连接的房间
        for (AxisAlignedBB doorBB : doorBBs) {
            if (box.intersects(doorBB)) {
                return false;
            }
        }
        
        // 检查相交的房间
        for (Room r : intersectRooms) {
            if (box.intersects(r.getBoundingBox())) {
                return false;
            }
        }
        
        // 检查其他走廊
        for (AxisAlignedBB hallwayBB : hallwayBBs) {
            if (box.intersects(hallwayBB)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 获取缓存的 BlockState
     */
    protected BlockState getCachedBlockState(BlockData blockData) {
        if (blockData == null) {
            return null;
        }
        Material material = blockData.getMaterial();
        return blockStateCache.computeIfAbsent(material, 
            m -> BukkitAdapter.adapt(blockData));
    }
    
    /**
     * @deprecated 使用新的 ILevelConfig 版本
     */
    @Deprecated
    @Override
    public void generate(AsyncWorldEditor world, Random random, Room room, Theme theme, StyleSheet styleSheet,
            LevelConfig config) {
        generate(world, random, room, theme, styleSheet, (ILevelConfig) config);
    }

    /**
     * @return the rooms
     */
    public List<Room> getRooms() {
        return rooms;
    }

    /**
     * @param rooms the rooms to set
     */
    public final void setRooms(List<Room> rooms) {
        this.rooms = rooms;
    }

    /**
     * @return the hallways
     */
    public List<Hallway> getHallways() {
        return hallways;
    }

    /**
     * @param hallways the hallways to set
     */
    public final void setHallways(List<Hallway> hallways) {
        this.hallways = hallways;
    }
}