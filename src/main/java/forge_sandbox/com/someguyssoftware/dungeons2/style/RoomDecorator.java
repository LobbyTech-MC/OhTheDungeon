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

import org.bukkit.Bukkit;
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
import forge_sandbox.com.someguyssoftware.dungeons2.model.LevelConfig;
import forge_sandbox.com.someguyssoftware.dungeons2.model.Room;
import forge_sandbox.com.someguyssoftware.dungeons2.rotate.RotatorHelper;
import forge_sandbox.com.someguyssoftware.dungeons2.spawner.SpawnGroup;
import forge_sandbox.com.someguyssoftware.dungeons2.spawner.SpawnSheet;
import forge_sandbox.com.someguyssoftware.dungeons2.spawner.SpawnerPopulator;
import forge_sandbox.com.someguyssoftware.dungeonsengine.chest.ILootLoader;
import forge_sandbox.com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import forge_sandbox.com.someguyssoftware.gottschcore.enums.Direction;
import forge_sandbox.com.someguyssoftware.gottschcore.enums.Rarity;
import forge_sandbox.com.someguyssoftware.gottschcore.positional.ICoords;
import forge_sandbox.com.someguyssoftware.gottschcore.random.RandomHelper;
import forge_sandbox.com.someguyssoftware.gottschcore.random.RandomProbabilityCollection;
import otd.lib.async.AsyncWorldEditor;
import otd.lib.async.later.dungeons2.Chest_Later;

/**
 * @author Mark Gottschling on Sep 7, 2016
 * @modified FAWE 2.15.1
 */
public class RoomDecorator implements IRoomDecorator {

    private SpawnerPopulator spawnerPopulator;
    private ILootLoader lootLoader;
    
    // 缓存 BlockState
    private final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();
    
    // 批处理大小
    private static final int BATCH_SIZE = 500;

    public RoomDecorator() {}

    public RoomDecorator(ILootLoader loader, SpawnSheet spawnSheet) {
        this.spawnerPopulator = new SpawnerPopulator(spawnSheet);
        this.setLootLoader(loader);
    }

    @Override
    public void decorate(AsyncWorldEditor world, Random random, Dungeon dungeon, IDungeonsBlockProvider provider,
            Room room, ILevelConfig config) {
        
        List<Entry<DesignElement, ICoords>> surfaceAirZone = room.getFloorMap().entries().stream()
                .filter(x -> x.getKey().getFamily() == DesignElement.SURFACE_AIR).collect(Collectors.toList());

        if (surfaceAirZone == null || surfaceAirZone.isEmpty()) return;

        List<Entry<DesignElement, ICoords>> wallZone = null;
        List<Entry<DesignElement, ICoords>> floorZone = null;

        if (config.isDecorations() || ModConfig.enableChests) {
            floorZone = surfaceAirZone.stream().filter(f -> f.getKey() == DesignElement.FLOOR_AIR)
                    .collect(Collectors.toList());
        }

        // 使用 FAWE EditSession 批量处理装饰
        if (config.isDecorations()) {
            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(world.getWorld()))
                    .allowedRegionsEverywhere()
                    .limitUnlimited()
                    .changeSetNull()
                    .fastMode(true)
                    .build()) {
                
                Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
                
                // 蜘蛛网
                addWebsWithFAWE(world, random, provider, room, surfaceAirZone, config, blocksToSet);
                
                // 获取墙壁区域
                wallZone = surfaceAirZone.stream().filter(f -> f.getKey() == DesignElement.WALL_AIR)
                        .collect(Collectors.toList());
                
                // 藤蔓
                addVinesWithFAWE(world, random, provider, room, wallZone, config, blocksToSet);
                
                // 草/蘑菇
                addGrassWithFAWE(world, random, provider, room, floorZone, config, blocksToSet);
                
                // 批量设置所有方块
                flushBlocksToWorld(editSession, blocksToSet);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // 宝箱
        if (ModConfig.enableChests) {
            addChest(world, random, provider, room, floorZone, config);
        }

        // 刷怪笼
        if (ModConfig.enableSpawners) {
            ICoords spawnerCoords = addSpawner(world, random, provider, room, floorZone, config);
            if (spawnerCoords != null) {
                Dungeons2.log.debug("Adding spawner @ " + spawnerCoords.toShortString());
                List<SpawnGroup> groups = new ArrayList<>(spawnerPopulator.getSpawnSheet().getGroups().values());
                RandomProbabilityCollection<SpawnGroup> spawnerProbCol = new RandomProbabilityCollection<>(groups);
                SpawnGroup spawnGroup = (SpawnGroup) spawnerProbCol.next();
                spawnerPopulator.populate(world, spawnerCoords, random, spawnGroup);
            }
        }
    } 
    
    /**
     * 使用 FAWE 批量添加蜘蛛网
     */
    protected void addWebsWithFAWE(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                                   List<Entry<DesignElement, ICoords>> zone, ILevelConfig config,
                                   Map<BlockVector3, BlockState> blocksToSet) {
        double freq = RandomHelper.randomDouble(random, config.getWebFrequency().getMin(),
                config.getWebFrequency().getMax());
        BlockState webState = getCachedBlockState(Material.COBWEB);
        
        for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random,
                config.getNumberOfWebs().getMinInt(), config.getNumberOfWebs().getMaxInt()), config); i++) {
            double n = random.nextDouble() * 100;
            if (n < freq && !zone.isEmpty()) {
                int zoneIndex = random.nextInt(zone.size());
                Entry<DesignElement, ICoords> entry = zone.get(zoneIndex);
                DesignElement elem = entry.getKey();
                ICoords coords = entry.getValue();
                
                if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
                    blocksToSet.put(BlockVector3.at(coords.getX(), coords.getY(), coords.getZ()), webState);
                    zone.remove(zoneIndex);
                }
            }
        }
    }
    
    /**
     * 使用 FAWE 批量添加藤蔓
     */
    protected void addVinesWithFAWE(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                                    List<Entry<DesignElement, ICoords>> zone, ILevelConfig config,
                                    Map<BlockVector3, BlockState> blocksToSet) {
        double freq = RandomHelper.randomDouble(random, config.getVineFrequency().getMin(),
                config.getVineFrequency().getMax());
        
        for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random,
                config.getNumberOfVines().getMinInt(), config.getNumberOfVines().getMaxInt()), config); i++) {
            double n = random.nextDouble() * 100;
            if (n < freq && !zone.isEmpty()) {
                int zoneIndex = random.nextInt(zone.size());
                DesignElement elem = zone.get(zoneIndex).getKey();
                ICoords coords = zone.get(zoneIndex).getValue();
                
                if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
                    Location location = provider.getLocation(coords, room, room.getLayout());
                    Direction d = provider.getDirection(coords, room, DesignElement.WALL_AIR, location);
                    BlockData vineData = RotatorHelper.rotateBlock(Bukkit.createBlockData(Material.VINE), d);
                    blocksToSet.put(BlockVector3.at(coords.getX(), coords.getY(), coords.getZ()), 
                                    getCachedBlockState(vineData));
                    zone.remove(zoneIndex);
                }
            }
        }
    }
    
    /**
     * 使用 FAWE 批量添加草/蘑菇
     */
    protected void addGrassWithFAWE(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                                    List<Entry<DesignElement, ICoords>> floorZone, ILevelConfig config,
                                    Map<BlockVector3, BlockState> blocksToSet) {
        if (floorZone == null) return;
        
        double freq = RandomHelper.randomDouble(random, config.getWebFrequency().getMin(),
                config.getWebFrequency().getMax());
        
        for (int i = 0; i < scaleNumForSizeOfRoom(room, RandomHelper.randomInt(random,
                config.getNumberOfWebs().getMinInt(), config.getNumberOfWebs().getMaxInt()), config); i++) {
            double n = random.nextDouble() * 100;
            if (n < freq && !floorZone.isEmpty()) {
                int floorZoneIndex = random.nextInt(floorZone.size());
                DesignElement elem = floorZone.get(floorZoneIndex).getKey();
                ICoords coords = floorZone.get(floorZoneIndex).getValue();
                
                if (hasSupport(world, coords, elem, provider.getLocation(coords, room, room.getLayout()))) {
                    // 选择植物类型
                    int b = random.nextInt(5);
                    Material plantMaterial;
                    Material groundMaterial;
                    
                    switch (b) {
                        case 0: plantMaterial = Material.SHORT_GRASS; break;
                        case 1: plantMaterial = Material.DEAD_BUSH; break;
                        case 2: plantMaterial = Material.FERN; break;
                        case 3: plantMaterial = Material.BROWN_MUSHROOM; break;
                        case 4: plantMaterial = Material.RED_MUSHROOM; break;
                        default: plantMaterial = Material.SHORT_GRASS;
                    }
                    groundMaterial = (b < 3) ? Material.DIRT : Material.PODZOL;
                    
                    // 添加下方方块和植物
                    BlockVector3 groundPos = BlockVector3.at(coords.getX(), coords.getY() - 1, coords.getZ());
                    BlockVector3 plantPos = BlockVector3.at(coords.getX(), coords.getY(), coords.getZ());
                    
                    blocksToSet.put(groundPos, getCachedBlockState(groundMaterial));
                    blocksToSet.put(plantPos, getCachedBlockState(plantMaterial));
                    
                    floorZone.remove(floorZoneIndex);
                }
            }
        }
    }
    
    /**
     * 批量刷新方块到世界
     */
    private void flushBlocksToWorld(EditSession editSession, Map<BlockVector3, BlockState> blocksToSet) {
        int count = 0;
        for (Map.Entry<BlockVector3, BlockState> entry : blocksToSet.entrySet()) {
            editSession.setBlock(entry.getKey(), entry.getValue());
            count++;
            if (count % BATCH_SIZE == 0) {
                editSession.flushQueue();
            }
        }
        editSession.flushQueue();
    }
    
    /**
     * 获取缓存的 BlockState
     */
    private BlockState getCachedBlockState(Material material) {
        return blockStateCache.computeIfAbsent(material, 
            m -> BukkitAdapter.adapt(m.createBlockData()));
    }
    
    /**
     * 获取缓存的 BlockState (从 BlockData)
     */
    private BlockState getCachedBlockState(BlockData blockData) {
        if (blockData == null) return null;
        return getCachedBlockState(blockData.getMaterial());
    }
    
    // ======================== 原有方法保留（已废弃） ========================
    
    @Deprecated
    @Override
    public void decorate(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
            LevelConfig config) {
        // 委托给新版本
        Dungeon tempDungeon = new Dungeon();
        decorate(world, random, tempDungeon, provider, room, (ILevelConfig) config);
    }
    
    @Deprecated
    protected void addWebs(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                          List<Entry<DesignElement, ICoords>> zone, LevelConfig config) {
        // 保留空实现，避免编译错误
    }
    
    @Deprecated
    protected void addVines(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                           List<Entry<DesignElement, ICoords>> zone, LevelConfig config) {
        // 保留空实现
    }
    
    @Deprecated
    protected void addGrass(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                           List<Entry<DesignElement, ICoords>> floorZone, LevelConfig config) {
        // 保留空实现
    }
    
    @Deprecated
    protected void addPuddles(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                             List<Entry<DesignElement, ICoords>> zone, LevelConfig config) {
        // 保留空实现
    }
    
    @Deprecated
    protected ICoords addChest(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                              List<Entry<DesignElement, ICoords>> floorZone, LevelConfig config) {
        return addChest(world, random, provider, room, floorZone, (ILevelConfig) config);
    }
    
    @Deprecated
    protected ICoords addSpawner(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                                List<Entry<DesignElement, ICoords>> floorZone, LevelConfig config) {
        return addSpawner(world, random, provider, room, floorZone, (ILevelConfig) config);
    }
    
    // ======================== 宝箱和刷怪笼方法 ========================
    
    protected ICoords addChest(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                               List<Entry<DesignElement, ICoords>> floorZone, ILevelConfig config) {
        ICoords chestCoords = null;
        double freq = RandomHelper.randomDouble(random, config.getChestFrequency().getMin(),
                config.getChestFrequency().getMax());
        
        if (RandomHelper.checkProbability(random, freq) && floorZone != null && !floorZone.isEmpty()) {
            int floorIndex = random.nextInt(floorZone.size());
            DesignElement elem = floorZone.get(floorIndex).getKey();
            chestCoords = floorZone.get(floorIndex).getValue();
            Location location = provider.getLocation(chestCoords, room, room.getLayout());
            
            if (hasSupport(world, chestCoords, elem, location)) {
                BlockFace facing = orientChest(location);
                Chest_Later.generate_later(world, random, chestCoords, Rarity.RARE, 
                                          GroupHelper.CHEST.get(facing));
                floorZone.remove(floorIndex);
            } else {
                chestCoords = null;
            }
        }
        return chestCoords;
    }
    
    protected ICoords addSpawner(AsyncWorldEditor world, Random random, IDungeonsBlockProvider provider, Room room,
                                 List<Entry<DesignElement, ICoords>> floorZone, ILevelConfig config) {
        ICoords spawnerCoords = null;
        double freq = RandomHelper.randomDouble(random, config.getSpawnerFrequency().getMin(),
                config.getSpawnerFrequency().getMax());
        
        if (random.nextDouble() * 100 < freq && floorZone != null && !floorZone.isEmpty()) {
            int floorIndex = random.nextInt(floorZone.size());
            DesignElement elem = floorZone.get(floorIndex).getKey();
            spawnerCoords = floorZone.get(floorIndex).getValue();
            Location location = provider.getLocation(spawnerCoords, room, room.getLayout());
            
            if (hasSupport(world, spawnerCoords, elem, location)) {
                floorZone.remove(floorIndex);
            }
        }
        return spawnerCoords;
    }
    
    public ILootLoader getLootLoader() {
        return lootLoader;
    }
    
    public void setLootLoader(ILootLoader lootLoader) {
        this.lootLoader = lootLoader;
    }
}