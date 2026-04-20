/**
 * 
 */
package forge_sandbox.com.someguyssoftware.dungeons2.style;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.BlockPos;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.Location;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Dungeon;
import forge_sandbox.com.someguyssoftware.dungeons2.model.LevelConfig;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Room;
import forge_sandbox.com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import forge_sandbox.com.someguyssoftware.gottschcore.Quantity;
import forge_sandbox.com.someguyssoftware.gottschcore.positional.ICoords;
import forge_sandbox.com.someguyssoftware.gottschcore.random.RandomHelper;
import otd.lib.async.AsyncWorldEditor;

/**
 * @author Mark Gottschling on Jan 11, 2017
 * @modified FAWE 2.15.1
 */
@SuppressWarnings("deprecation")
public interface IRoomDecorator {

    /**
     * 缓存 BlockState 的辅助类
     */
    class BlockStateCache {
        private static final Map<Material, BlockState> cache = new ConcurrentHashMap<>();
        
        public static BlockState get(Material material) {
            if (material == null) return null;
            return cache.computeIfAbsent(material, 
                m -> BukkitAdapter.adapt(m.createBlockData()));
        }
        
        public static BlockState get(BlockData blockData) {
            if (blockData == null) return null;
            return get(blockData.getMaterial());
        }
        
        public static void clear() {
            cache.clear();
        }
    }

    /**
     * 
     * @param world
     * @param random
     * @param provider
     * @param room
     * @param config
     */
    void decorate(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
            LevelConfig config);

    void decorate(AsyncWorldEditor world, Random random, Dungeon dungeon, IDungeonsBlockProvider provider, Room room,
            ILevelConfig config);

    /**
     * 使用 FAWE 批量添加装饰方块
     */
    default public void addBlock(final AsyncWorldEditor world, Random random, final IDungeonsBlockProvider provider,
            final Room room, final List<Entry<DesignElement, ICoords>> zone, final BlockData[] states,
            final Quantity frequency, final Quantity number, final ILevelConfig config) {

        if (zone.isEmpty()) return;
        
        double freq = RandomHelper.randomDouble(random, frequency.getMin(), frequency.getMax());
        int scaledNum = scaleNumForSizeOfRoom(room,
                RandomHelper.randomInt(random, number.getMinInt(), number.getMaxInt()), config);

        if (scaledNum <= 0) return;
        
        // 使用 FAWE EditSession 批量设置
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
            int placedCount = 0;
            
            for (int i = 0; i < scaledNum && !zone.isEmpty(); i++) {
                double n = random.nextDouble() * 100;
                if (n < freq) {
                    int zoneIndex = random.nextInt(zone.size());
                    Entry<DesignElement, ICoords> entry = zone.get(zoneIndex);
                    DesignElement elem = entry.getKey();
                    ICoords coords = entry.getValue();
                    
                    if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
                        BlockData state = (states.length == 1) ? states[0] : states[random.nextInt(states.length)];
                        BlockVector3 position = BlockVector3.at(coords.getX(), coords.getY(), coords.getZ());
                        blocksToSet.put(position, BlockStateCache.get(state));
                        zone.remove(entry);
                        placedCount++;
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
    }

    /**
     * NOTE This is a STATELESS method ie blocks that don't have a specific state to
     * be in, like WEB. This won't work with Blocks that use FACING etc. Adds a
     * random number of specified blocks as decorations to the room.
     * 
     * @deprecated 使用新的 ILevelConfig 版本
     */
    @Deprecated
    default public void addBlock(final AsyncWorldEditor world, Random random, final IDungeonsBlockProvider provider,
            final Room room, final List<Entry<DesignElement, ICoords>> zone, final BlockData[] states,
            final Quantity frequency, final Quantity number, final LevelConfig config) {

        if (zone.isEmpty()) return;
        
        double freq = RandomHelper.randomDouble(random, frequency.getMin(), frequency.getMax());
        int scaledNum = scaleNumForSizeOfRoom(room,
                RandomHelper.randomInt(random, number.getMinInt(), number.getMaxInt()), config);

        if (scaledNum <= 0) return;
        
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
            
            for (int i = 0; i < scaledNum && !zone.isEmpty(); i++) {
                double n = random.nextDouble() * 100;
                if (n < freq) {
                    int zoneIndex = random.nextInt(zone.size());
                    Entry<DesignElement, ICoords> entry = zone.get(zoneIndex);
                    DesignElement elem = entry.getKey();
                    ICoords coords = entry.getValue();
                    
                    if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
                        BlockData state = (states.length == 1) ? states[0] : states[random.nextInt(states.length)];
                        BlockVector3 position = BlockVector3.at(coords.getX(), coords.getY(), coords.getZ());
                        blocksToSet.put(position, BlockStateCache.get(state));
                        zone.remove(entry);
                    }
                }
            }
            
            for (Map.Entry<BlockVector3, BlockState> entry : blocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 检查是否有支撑
     */
    default public boolean hasSupport(AsyncWorldEditor world, ICoords coords, DesignElement elem, Location location) {
        BlockPos pos = coords.toPos();
        Material blockState = null;
        
        switch (elem) {
            case FLOOR_AIR:
                blockState = world.getBlockState(pos.add(0, -1, 0));
                break;
            case CEILING_AIR:
                blockState = world.getBlockState(pos.add(0, 1, 0));
                break;
            case WALL_AIR:
                switch (location) {
                    case NORTH_SIDE:
                        blockState = world.getBlockState(pos.add(0, 0, -1));
                        break;
                    case EAST_SIDE:
                        blockState = world.getBlockState(pos.add(1, 0, 0));
                        break;
                    case SOUTH_SIDE:
                        blockState = world.getBlockState(pos.add(0, 0, 1));
                        break;
                    case WEST_SIDE:
                        blockState = world.getBlockState(pos.add(-1, 0, 0));
                        break;
                    default:
                        break;
                }
                break;
            default:
                break;
        }

        return blockState != null && blockState.isSolid();
    }

    /**
     * 根据房间大小缩放装饰数量
     */
    default public int scaleNumForSizeOfRoom(Room room, int numDecorations, ILevelConfig config) {
        int size = (room.getWidth() - 2) * (room.getDepth() - 2) * (room.getHeight() - 2);
        int halfOfMax = ((config.getWidth().getMaxInt() - 2) * (config.getDepth().getMaxInt() - 2)
                * (config.getHeight().getMaxInt() - 2)) / 2;
        float factor = 1F;

        if (size <= 27) {
            factor = 0.25F;
        } else if (size < halfOfMax) {
            factor = 0.5F;
        }

        return (int) (numDecorations * factor);
    }

    /**
     * 根据房间大小缩放装饰数量（旧版）
     * @deprecated 使用新的 ILevelConfig 版本
     */
    @Deprecated
    default public int scaleNumForSizeOfRoom(Room room, int numDecorations, LevelConfig config) {
        int size = (room.getWidth() - 2) * (room.getDepth() - 2) * (room.getHeight() - 2);
        int halfOfMax = ((config.getWidth().getMaxInt() - 2) * (config.getDepth().getMaxInt() - 2)
                * (config.getHeight().getMaxInt() - 2)) / 2;
        float factor = 1F;

        if (size <= 27) {
            factor = 0.25F;
        } else if (size < halfOfMax) {
            factor = 0.5F;
        }

        return (int) (numDecorations * factor);
    }

    /**
     * 根据位置获取宝箱朝向
     */
    default public BlockFace orientChest(Location location) {
        switch (location) {
            case NORTH_SIDE:
                return BlockFace.SOUTH;
            case SOUTH_SIDE:
                return BlockFace.NORTH;
            case EAST_SIDE:
                return BlockFace.WEST;
            case WEST_SIDE:
                return BlockFace.EAST;
            default:
                return BlockFace.NORTH;
        }
    }
}