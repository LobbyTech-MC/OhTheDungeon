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
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.GroupHelper;
import forge_sandbox.com.someguyssoftware.dungeons2.Dungeons2;
import forge_sandbox.com.someguyssoftware.dungeons2.config.ModConfig;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.Location;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Dungeon;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Level;
import forge_sandbox.com.someguyssoftware.dungeons2.model.LevelConfig;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Room;
import forge_sandbox.com.someguyssoftware.dungeonsengine.chest.ILootLoader;
import forge_sandbox.com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import forge_sandbox.com.someguyssoftware.gottschcore.enums.Rarity;
import forge_sandbox.com.someguyssoftware.gottschcore.positional.ICoords;
import forge_sandbox.com.someguyssoftware.gottschcore.random.RandomHelper;
import otd.lib.async.AsyncWorldEditor;
import otd.lib.async.later.dungeons2.Chest_Later;

/**
 * @author Mark Gottschling on Jan 11, 2017
 * @modified FAWE 2.15.1
 */
public class BossRoomDecorator extends RoomDecorator {

    private static final int CARPET_PERCENT_CHANCE = 75;
    private ILootLoader lootLoader;
    
    // 缓存 BlockState
    private final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();

    /**
     * @param chestSheet
     */
    public BossRoomDecorator() {
//        this.chestPopulator = new ChestPopulator(chestSheet);
    }

    /**
     * 
     * @param loader
     */
    public BossRoomDecorator(ILootLoader loader) {
        setLootLoader(loader);
    }

    /**
     * 
     */
    @Override
    public void decorate(AsyncWorldEditor world, Random random, Dungeon dungeon, IDungeonsBlockProvider provider,
            Room room, ILevelConfig config) {
        Dungeons2.log.debug("In Boos Room Decorator.");
        List<Entry<DesignElement, ICoords>> surfaceAirZone = room.getFloorMap().entries().stream()
                .filter(x -> x.getKey().getFamily() == DesignElement.SURFACE_AIR).collect(Collectors.toList());
        if (surfaceAirZone == null || surfaceAirZone.isEmpty())
            return;

        // 获取地板区域
        List<Entry<DesignElement, ICoords>> floorZone = surfaceAirZone.stream()
                .filter(f -> f.getKey() == DesignElement.FLOOR_AIR)
                .collect(Collectors.toList());

        // 使用 FAWE 批量设置地毯
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            Map<BlockVector3, BlockState> carpetBlocksToSet = new HashMap<>();
            
            Material carpet = GroupHelper.CARPETS.get(random.nextInt(GroupHelper.CARPETS.size()));
            BlockState carpetState = getCachedBlockState(carpet.createBlockData());
            
            for (Entry<DesignElement, ICoords> entry : floorZone) {
                if (random.nextInt(100) < CARPET_PERCENT_CHANCE) {
                    DesignElement elem = entry.getKey();
                    ICoords coords = entry.getValue();
                    // 检查支撑
                    if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
                        BlockVector3 position = BlockVector3.at(
                            coords.getX(), coords.getY(), coords.getZ()
                        );
                        carpetBlocksToSet.put(position, carpetState);
                    }
                }
            }
            
            // 批量设置地毯
            for (Map.Entry<BlockVector3, BlockState> entry : carpetBlocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 添加宝箱
        addChest(world, random, dungeon, provider, room, floorZone, config);
    }

    /**
     * 添加宝箱
     */
    protected ICoords addChest(AsyncWorldEditor world, Random random, Dungeon dungeon, IDungeonsBlockProvider provider,
            Room room, List<Entry<DesignElement, ICoords>> floorZone, ILevelConfig config) {
        if (floorZone.isEmpty()) {
            return null;
        }
        
        // 选择随机位置
        Entry<DesignElement, ICoords> floorEntry = floorZone.get(random.nextInt(floorZone.size()));
        DesignElement elem = floorEntry.getKey();
        ICoords chestCoords = floorEntry.getValue();
        
        // 检查支撑
        Location location = provider.getLocation(chestCoords, room, room.getLayout());
        if (!hasSupport(world, chestCoords, elem, location)) {
            Dungeons2.log.debug("Boss Chest has no floor support");
            return null;
        }
        
        BlockFace facing = orientChest(location);
        BlockData chestBlockData = GroupHelper.CHEST.get(facing);
        boolean isChestPlaced = false;
        
        // 尝试使用 Treasure2 集成
        if (ModConfig.enableTreasure2Integration
                && RandomHelper.checkProbability(random, ModConfig.treasure2ChestProbability)) {
            Dungeons2.log.debug("boss room adding Treasure2 chest @ " + chestCoords.toShortString());
            
            // 计算稀有度
            Rarity rarity = calculateRarity(dungeon);
            Dungeons2.log.debug("boss room using rarity -> " + rarity);
            
            Chest_Later.generate_later(world, random, chestCoords, rarity, chestBlockData);
            isChestPlaced = true;
        }
        
        // 默认操作
        if (!isChestPlaced) {
            Dungeons2.log.debug("boss room, treasure2 chest was NOT generated, using default.");
            Chest_Later.generate_later(world, random, chestCoords, Rarity.EPIC, chestBlockData);
            Dungeons2.log.debug("Adding boss chest @ " + chestCoords.toShortString());
        }
        
        // 从列表中移除
        floorZone.remove(floorEntry);
        
        return chestCoords;
    }
    
    /**
     * 根据地牢大小计算稀有度
     */
    private Rarity calculateRarity(Dungeon dungeon) {
        int rooms = 0;
        for (Level level : dungeon.getLevels()) {
            rooms += level.getRooms().size();
        }
        int levels = dungeon.getLevels().size();
        
        if (levels > 8 || rooms > 260) {
            return Rarity.EPIC;
        } else if (levels > 5 || rooms > 180) {
            return Rarity.RARE;
        } else if (levels > 2 || rooms > 100) {
            return Rarity.SCARCE;
        }
        return Rarity.UNCOMMON;
    }
    
    /**
     * 获取缓存的 BlockState
     */
    private BlockState getCachedBlockState(BlockData blockData) {
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
    public void decorate(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
            LevelConfig config) {
        Dungeons2.log.debug("In Boos Room Decorator.");
        List<Entry<DesignElement, ICoords>> surfaceAirZone = room.getFloorMap().entries().stream()
                .filter(x -> x.getKey().getFamily() == DesignElement.SURFACE_AIR).collect(Collectors.toList());
        if (surfaceAirZone == null || surfaceAirZone.isEmpty())
            return;

        List<Entry<DesignElement, ICoords>> floorZone = surfaceAirZone.stream()
                .filter(f -> f.getKey() == DesignElement.FLOOR_AIR)
                .collect(Collectors.toList());

        // 使用 FAWE 批量设置地毯
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            Map<BlockVector3, BlockState> carpetBlocksToSet = new HashMap<>();
            
            Material carpet = GroupHelper.CARPETS.get(random.nextInt(GroupHelper.CARPETS.size()));
            BlockState carpetState = getCachedBlockState(carpet.createBlockData());
            
            for (Entry<DesignElement, ICoords> entry : floorZone) {
                if (random.nextInt(100) < CARPET_PERCENT_CHANCE) {
                    DesignElement elem = entry.getKey();
                    ICoords coords = entry.getValue();
                    if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
                        BlockVector3 position = BlockVector3.at(
                            coords.getX(), coords.getY(), coords.getZ()
                        );
                        carpetBlocksToSet.put(position, carpetState);
                    }
                }
            }
            
            for (Map.Entry<BlockVector3, BlockState> entry : carpetBlocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 添加宝箱
        addChestLegacy(world, random, provider, room, floorZone);
    }
    
    /**
     * 旧版宝箱添加方法
     */
    private void addChestLegacy(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider,
                                Room room, List<Entry<DesignElement, ICoords>> floorZone) {
        if (floorZone.isEmpty()) return;
        
        Entry<DesignElement, ICoords> floorEntry = floorZone.get(random.nextInt(floorZone.size()));
        DesignElement elem = floorEntry.getKey();
        ICoords chestCoords = floorEntry.getValue();
        
        Location location = provider.getLocation(chestCoords, room, room.getLayout());
        if (hasSupport(world, chestCoords, elem, location)) {
            BlockFace facing = orientChest(location);
            Chest_Later.generate_later(world, random, chestCoords, Rarity.EPIC, 
                                       GroupHelper.CHEST.get(facing));
            floorZone.remove(floorEntry);
        } else {
            Dungeons2.log.debug("Boss Chest has no floor support");
        }
    }

    /**
     * @return the lootLoader
     */
    @Override
    public ILootLoader getLootLoader() {
        return lootLoader;
    }

    /**
     * @param lootLoader the lootLoader to set
     */
    @Override
    public final void setLootLoader(ILootLoader loader) {
        this.lootLoader = loader;
    }
}