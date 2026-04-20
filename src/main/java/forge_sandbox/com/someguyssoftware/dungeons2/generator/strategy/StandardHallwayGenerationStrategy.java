package forge_sandbox.com.someguyssoftware.dungeons2.generator.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.AxisAlignedBB;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.AbstractRoomGenerationStrategy;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.Arrangement;
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

public class StandardHallwayGenerationStrategy extends AbstractRoomGenerationStrategy {
    
    private List<Room> rooms;
    private List<Hallway> hallways;
    
    // 缓存常用的 BlockState
    private final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();
    
    public StandardHallwayGenerationStrategy(IDungeonsBlockProvider blockProvider, List<Room> rooms,
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
        
        // 收集与走廊相交的房间列表
        List<Room> intersectRooms = getIntersectingRooms(hallway);
        
        // 使用 FAWE EditSession 批量生成
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            // 预计算所有需要设置的方块
            Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
            
            // 预计算边界框，避免重复计算
            List<AxisAlignedBB> doorBBs = getDoorBoundingBoxes(hallway);
            List<AxisAlignedBB> hallwayBBs = getHallwayBoundingBoxes();
            
            for (int y = 0; y < room.getHeight(); y++) {
                for (int z = 0; z < room.getDepth(); z++) {
                    for (int x = 0; x < room.getWidth(); x++) {
                        
                        ICoords indexCoords = new Coords(x, y, z);
                        ICoords worldCoords = room.getCoords().add(indexCoords);
                        
                        Arrangement arrangement = getBlockProvider().getArrangement(worldCoords, room, room.getLayout());
                        
                        // 检查是否需要后处理 - 使用 postProcessMap
                        if (isPostProcessed(arrangement, worldCoords, postProcessMap)) {
                            continue;
                        }
                        
                        BlockData blockData = getBlockProvider().getBlockState(random, worldCoords, room,
                                arrangement, theme, styleSheet, config);
                        
                        if (blockData == IDungeonsBlockProvider.NULL_BLOCK) {
                            continue;
                        }
                        
                        boolean shouldBuild = shouldBuildBlock(worldCoords, arrangement, 
                                doorBBs, intersectRooms, hallwayBBs);
                        
                        if (shouldBuild) {
                            BlockVector3 position = BlockVector3.at(
                                worldCoords.getX(),
                                worldCoords.getY(),
                                worldCoords.getZ()
                            );
                            BlockState blockState = getCachedBlockState(blockData);
                            blocksToSet.put(position, blockState);
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
        
        // 生成后处理方块 - 使用 postProcessMap
        postProcess(world, random, postProcessMap, room.getLayout(), theme, styleSheet, config);
    }
    
    private List<Room> getIntersectingRooms(Hallway hallway) {
        List<Room> intersectRooms = new ArrayList<>();
        for (Room otherRoom : getRooms()) {
            if (hallway.getBoundingBox().intersects(otherRoom.getBoundingBox())) {
                intersectRooms.add(otherRoom);
            }
        }
        return intersectRooms;
    }
    
    private List<AxisAlignedBB> getDoorBoundingBoxes(Hallway hallway) {
        List<AxisAlignedBB> doorBBs = new ArrayList<>();
        for (int i = 0; i < hallway.getDoors().size() && i < 2; i++) {
            if (hallway.getDoors().get(i) != null && hallway.getDoors().get(i).getRoom() != null) {
                doorBBs.add(hallway.getDoors().get(i).getRoom().getBoundingBox());
            }
        }
        return doorBBs;
    }
    
    private List<AxisAlignedBB> getHallwayBoundingBoxes() {
        List<AxisAlignedBB> hallwayBBs = new ArrayList<>();
        for (Room r : getHallways()) {
            hallwayBBs.add(r.getBoundingBox());
        }
        return hallwayBBs;
    }
    
    private boolean shouldBuildBlock(ICoords worldCoords, Arrangement arrangement,
                                    List<AxisAlignedBB> doorBBs, List<Room> intersectRooms,
                                    List<AxisAlignedBB> hallwayBBs) {
        if (arrangement.getElement() == DesignElement.AIR) {
            return true;
        }
        
        AxisAlignedBB box = new AxisAlignedBB(worldCoords.toPos());
        
        for (AxisAlignedBB doorBB : doorBBs) {
            if (box.intersects(doorBB)) {
                return false;
            }
        }
        
        for (Room r : intersectRooms) {
            if (box.intersects(r.getBoundingBox())) {
                return false;
            }
        }
        
        for (AxisAlignedBB hallwayBB : hallwayBBs) {
            if (box.intersects(hallwayBB)) {
                return false;
            }
        }
        
        return true;
    }
    
    protected BlockState getCachedBlockState(BlockData blockData) {
        Material material = blockData.getMaterial();
        return blockStateCache.computeIfAbsent(material, 
            m -> BukkitAdapter.adapt(blockData));
    }
    
    @Override
    @Deprecated
    public void generate(AsyncWorldEditor world, Random random, Room room, Theme theme, StyleSheet styleSheet,
            LevelConfig config) {
        generate(world, random, room, theme, styleSheet, (ILevelConfig) config);
    }
    
    public List<Hallway> getHallways() {
        return hallways;
    }
    
    public final void setHallways(List<Hallway> hallways) {
        this.hallways = hallways;
    }
    
    public List<Room> getRooms() {
        return rooms;
    }
    
    public final void setRooms(List<Room> rooms) {
        this.rooms = rooms;
    }
}