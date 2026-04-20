/**
 * 
 */
package forge_sandbox.com.someguyssoftware.dungeons2.generator;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.Material;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.BlockPos;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.strategy.IRoomGenerationStrategy;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Door;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Hallway;
import forge_sandbox.com.someguyssoftware.dungeons2.model.LevelConfig;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Room;
import forge_sandbox.com.someguyssoftware.dungeons2.style.StyleSheet;
import forge_sandbox.com.someguyssoftware.dungeons2.style.Theme;
import forge_sandbox.com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import forge_sandbox.com.someguyssoftware.gottschcore.enums.Alignment;
import forge_sandbox.com.someguyssoftware.gottschcore.enums.Direction;
import forge_sandbox.com.someguyssoftware.gottschcore.positional.ICoords;
import otd.lib.async.AsyncWorldEditor;

/**
 * @author Mark Gottschling on Aug 28, 2016
 * @modified FAWE 2.15.1
 */
public class HallwayGenerator extends AbstractRoomGenerator {

    private IRoomGenerationStrategy roomGenerationStrategy;
    
    // 缓存空气 BlockState
    private BlockState cachedAirState;
    
    // 批处理大小
    private static final int BATCH_SIZE = 100;

    /**
     * Enforce that the room generator has to have a structure generator.
     * 
     * @param generator
     */
    public HallwayGenerator(IRoomGenerationStrategy generator) {
        setGenerationStrategy(generator);
        // 初始化缓存
        cachedAirState = BukkitAdapter.adapt(Material.AIR.createBlockData());
    }

    @Override
    public void generate(AsyncWorldEditor world, Random random, Room room, Theme theme, StyleSheet styleSheet,
            ILevelConfig config) {

        // 生成房间结构
        getGenerationStrategy().generate(world, random, room, theme, styleSheet, config);

        Hallway hw = (Hallway) room;
        
        // 使用 FAWE EditSession 批量处理门框
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            // 存储需要设置的空气方块
            Map<BlockVector3, BlockState> airBlocksToSet = new HashMap<>();
            
            // 构建所有门
            for (Door door : hw.getDoors()) {
                collectDoorwayBlocks(world, hw, door, airBlocksToSet);
            }
            
            // 批量设置空气方块
            for (Map.Entry<BlockVector3, BlockState> entry : airBlocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 收集门框需要设置为空气的方块
     */
    private void collectDoorwayBlocks(AsyncWorldEditor world, Hallway hw, Door door, 
                                      Map<BlockVector3, BlockState> airBlocksToSet) {
        if (hw.getAlignment() == Alignment.HORIZONTAL) {
            // 水平方向的走廊 (东西方向)
            if (door.getCoords().getX() == hw.getMinX()) {
                if (door.getCoords().getZ() == door.getRoom().getMinZ()) {
                    collectDoorwayBlocksWithDoubleSide(world, door.getCoords(), Direction.WEST, Direction.SOUTH, airBlocksToSet);
                } else if (door.getCoords().getZ() == door.getRoom().getMaxZ()) {
                    collectDoorwayBlocksWithDoubleSide(world, door.getCoords(), Direction.WEST, Direction.NORTH, airBlocksToSet);
                } else {
                    collectDoorwayBlocks(world, door.getCoords(), Direction.WEST, airBlocksToSet);
                }
            }
            if (door.getCoords().getX() == hw.getMaxX()) {
                if (door.getCoords().getZ() == door.getRoom().getMinZ()) {
                    collectDoorwayBlocksWithDoubleSide(world, door.getCoords(), Direction.EAST, Direction.SOUTH, airBlocksToSet);
                } else if (door.getCoords().getZ() == door.getRoom().getMaxZ()) {
                    collectDoorwayBlocksWithDoubleSide(world, door.getCoords(), Direction.EAST, Direction.NORTH, airBlocksToSet);
                } else {
                    collectDoorwayBlocks(world, door.getCoords(), Direction.EAST, airBlocksToSet);
                }
            }
        } else {
            // 垂直方向的走廊 (南北方向)
            if (door.getCoords().getZ() == hw.getMinZ()) {
                if (door.getCoords().getX() == door.getRoom().getMinX()) {
                    collectDoorwayBlocksWithDoubleSide(world, door.getCoords(), Direction.NORTH, Direction.EAST, airBlocksToSet);
                } else if (door.getCoords().getX() == door.getRoom().getMaxX()) {
                    collectDoorwayBlocksWithDoubleSide(world, door.getCoords(), Direction.NORTH, Direction.WEST, airBlocksToSet);
                } else {
                    collectDoorwayBlocks(world, door.getCoords(), Direction.NORTH, airBlocksToSet);
                }
            }
            if (door.getCoords().getZ() == hw.getMaxZ()) {
                if (door.getCoords().getX() == door.getRoom().getMinX()) {
                    collectDoorwayBlocksWithDoubleSide(world, door.getCoords(), Direction.SOUTH, Direction.EAST, airBlocksToSet);
                } else if (door.getCoords().getX() == door.getRoom().getMaxX()) {
                    collectDoorwayBlocksWithDoubleSide(world, door.getCoords(), Direction.SOUTH, Direction.WEST, airBlocksToSet);
                } else {
                    collectDoorwayBlocks(world, door.getCoords(), Direction.SOUTH, airBlocksToSet);
                }
            }
        }
    }

    /**
     * 收集普通门框的方块
     */
    protected void collectDoorwayBlocks(AsyncWorldEditor world, ICoords coords, Direction direction,
                                        Map<BlockVector3, BlockState> airBlocksToSet) {
        int x = 0;
        int z = 0;
        int failSafe = 0;
        int touching;

        do {
            touching = 0;
            
            // 添加门框方块
            addAirBlock(airBlocksToSet, coords.add(x, 1, z));
            addAirBlock(airBlocksToSet, coords.add(x, 2, z));
            
            // 检查相邻空气方块数量
            BlockPos checkPos = coords.add(x, 1, z).toPos();
            if (world.getBlockState(checkPos.north()) == Material.AIR) touching++;
            if (world.getBlockState(checkPos.south()) == Material.AIR) touching++;
            if (world.getBlockState(checkPos.east()) == Material.AIR) touching++;
            if (world.getBlockState(checkPos.west()) == Material.AIR) touching++;
            
            // 移动到下一个位置
            switch (direction) {
                case NORTH: z--; break;
                case EAST:  x++; break;
                case SOUTH: z++; break;
                case WEST:  x--; break;
                default: break;
            }
            failSafe++;
        } while (touching < 3 && failSafe < 5);
    }

    /**
     * 收集带双侧扩展的门框方块
     */
    protected void collectDoorwayBlocksWithDoubleSide(AsyncWorldEditor world, ICoords coords, 
                                                      Direction direction, Direction doubleSide,
                                                      Map<BlockVector3, BlockState> airBlocksToSet) {
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = 0;
        int failSafe = 0;
        int touching;

        // 设置双侧扩展的方向偏移
        switch (doubleSide) {
            case NORTH: dz--; break;
            case EAST:  dx++; break;
            case SOUTH: dz++; break;
            case WEST:  dx--; break;
            default: break;
        }

        do {
            touching = 0;
            
            // 添加普通门框方块
            addAirBlock(airBlocksToSet, coords.add(x, 1, z));
            addAirBlock(airBlocksToSet, coords.add(x, 2, z));
            
            // 添加双侧扩展门框方块
            addAirBlock(airBlocksToSet, coords.add(dx, 1, dz));
            addAirBlock(airBlocksToSet, coords.add(dx, 2, dz));
            
            // 检查相邻空气方块数量
            BlockPos pos = coords.add(dx, 1, dz).toPos();
            if (world.getBlockState(pos.north()) == Material.AIR) touching++;
            if (world.getBlockState(pos.south()) == Material.AIR) touching++;
            if (world.getBlockState(pos.east()) == Material.AIR) touching++;
            if (world.getBlockState(pos.west()) == Material.AIR) touching++;
            
            // 移动到下一个位置
            switch (direction) {
                case NORTH: z--; dz--; break;
                case EAST:  x++; dx++; break;
                case SOUTH: z++; dz++; break;
                case WEST:  x--; dx--; break;
                default: break;
            }
            failSafe++;
        } while (touching < 3 && failSafe < 5);
    }
    
    /**
     * 添加空气方块到待设置集合
     */
    private void addAirBlock(Map<BlockVector3, BlockState> airBlocksToSet, ICoords coords) {
        BlockVector3 position = BlockVector3.at(coords.getX(), coords.getY(), coords.getZ());
        airBlocksToSet.put(position, cachedAirState);
    }

    @Deprecated
    @Override
    public void generate(AsyncWorldEditor world, Random random, Room room, Theme theme, StyleSheet styleSheet,
            LevelConfig config) {
        generate(world, random, room, theme, styleSheet, (ILevelConfig) config);
    }

    /**
     * 原始的单方块设置方法（保留用于兼容）
     * @deprecated 使用 FAWE 批量版本
     */
    @Deprecated
    protected void buildDoorway(AsyncWorldEditor world, ICoords coords, Direction direction, Direction doubleSide) {
        int touching;
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = 0;
        int failSafe = 0;

        switch (doubleSide) {
            case NORTH: dz--; break;
            case EAST:  dx++; break;
            case SOUTH: dz++; break;
            case WEST:  dx--; break;
            default: break;
        }

        do {
            touching = 0;
            
            world.setBlockState(coords.add(x, 1, z).toPos(), Material.AIR, 3);
            world.setBlockState(coords.add(x, 2, z).toPos(), Material.AIR, 3);
            world.setBlockState(coords.add(dx, 1, dz).toPos(), Material.AIR, 3);
            world.setBlockState(coords.add(dx, 2, dz).toPos(), Material.AIR, 3);

            BlockPos pos = coords.add(dx, 1, dz).toPos();
            if (world.getBlockState(pos.north()) == Material.AIR) touching++;
            if (world.getBlockState(pos.south()) == Material.AIR) touching++;
            if (world.getBlockState(pos.east()) == Material.AIR) touching++;
            if (world.getBlockState(pos.west()) == Material.AIR) touching++;

            switch (direction) {
                case NORTH: z--; dz--; break;
                case EAST:  x++; dx++; break;
                case SOUTH: z++; dz++; break;
                case WEST:  x--; dx--; break;
                default: break;
            }
            failSafe++;
        } while (touching < 3 && failSafe < 5);
    }
    
    @Deprecated
    protected void buildDoorway(AsyncWorldEditor world, ICoords coords, Direction direction) {
        int touching;
        int x = 0;
        int z = 0;
        int failSafe = 0;

        do {
            touching = 0;
            
            world.setBlockState(coords.add(x, 1, z).toPos(), Material.AIR, 3);
            world.setBlockState(coords.add(x, 2, z).toPos(), Material.AIR, 3);

            BlockPos pos = coords.add(x, 1, z).toPos();
            if (world.getBlockState(pos.north()) == Material.AIR) touching++;
            if (world.getBlockState(pos.south()) == Material.AIR) touching++;
            if (world.getBlockState(pos.east()) == Material.AIR) touching++;
            if (world.getBlockState(pos.west()) == Material.AIR) touching++;

            switch (direction) {
                case NORTH: z--; break;
                case EAST:  x++; break;
                case SOUTH: z++; break;
                case WEST:  x--; break;
                default: break;
            }
            failSafe++;
        } while (touching < 3 && failSafe < 5);
    }

    /**
     * @return the roomGenerationStrategy
     */
    @Override
    public final IRoomGenerationStrategy getGenerationStrategy() {
        return roomGenerationStrategy;
    }

    /**
     * @param roomGenerationStrategy the roomGenerationStrategy to set
     */
    public final void setGenerationStrategy(IRoomGenerationStrategy roomGenerationStrategy) {
        this.roomGenerationStrategy = roomGenerationStrategy;
    }
}