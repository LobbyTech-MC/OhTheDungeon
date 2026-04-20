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
package otd.lib.async;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.BlockPos;
import forge_sandbox.greymerk.roguelike.treasure.ITreasureChest;
import forge_sandbox.greymerk.roguelike.treasure.TreasureManager;
import forge_sandbox.greymerk.roguelike.worldgen.Cardinal;
import forge_sandbox.greymerk.roguelike.worldgen.Coord;
import forge_sandbox.greymerk.roguelike.worldgen.IBlockFactory;
import forge_sandbox.greymerk.roguelike.worldgen.IPositionInfo;
import forge_sandbox.greymerk.roguelike.worldgen.IStair;
import forge_sandbox.greymerk.roguelike.worldgen.IWorldEditor;
import forge_sandbox.greymerk.roguelike.worldgen.MetaBlock;
import forge_sandbox.greymerk.roguelike.worldgen.PositionInfo;
import forge_sandbox.greymerk.roguelike.worldgen.blocks.BlockType;
import forge_sandbox.greymerk.roguelike.worldgen.shapes.RectSolid;
import otd.MultiVersion;
import otd.lib.ZoneWorld;
import otd.lib.async.later.roguelike.Later;

/**
 * @author
 * @modified FAWE 2.15.1
 */
public class AsyncWorldEditor implements IWorldEditor {

    private final World world;
    private TreasureManager chests;
    private Map<Material, Integer> stats;

    // FAWE BlockState 缓存
    private static final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();
    
    private static BlockState getCachedBlockState(Material material) {
        if (material == null) return null;
        return blockStateCache.computeIfAbsent(material, 
            m -> BukkitAdapter.adapt(m.createBlockData()));
    }
    
    private static BlockState getCachedBlockState(BlockData blockData) {
        if (blockData == null) return null;
        return getCachedBlockState(blockData.getMaterial());
    }

    private static List<Material> invalid;
    static {
        invalid = new ArrayList<>();
        invalid.add(Material.OAK_PLANKS);
        invalid.add(Material.WATER);
        invalid.add(Material.CACTUS);
        invalid.add(Material.SNOW);
        invalid.add(Material.SHORT_GRASS);
        invalid.add(Material.STONE);
        invalid.add(Material.OAK_LEAVES);
        invalid.add(Material.POPPY);
        invalid.add(Material.DANDELION);
    };
    
    private final int spawnX, spawnZ;

    public void setDefaultState(Material data) {
        this.zone_world.default_state = data;
    }

    public AsyncWorldEditor(World world) {
        this.world = world;
        stats = new HashMap<>();
        this.chests = new TreasureManager();
        this.spawnX = world.getSpawnLocation().getBlockX();
        this.spawnZ = world.getSpawnLocation().getBlockZ();
    }

    public final static int DEFAULT_SEALEVEL = 63;
    private int seaLevel = DEFAULT_SEALEVEL;

    public void setSeaLevel(int seaLevel) {
        this.seaLevel = seaLevel;
    }

    public int getSeaLevel() {
        return this.seaLevel;
    }

    private int bottom = 5;

    public void setBottom(int bottom) {
        this.bottom = bottom;
    }

    public int getBottom() {
        return this.bottom;
    }

    public int[] getUpdatedRange(int bottom, int top) {
        int diff = 63 - top;
        int ntop = this.seaLevel - diff;
        diff = ntop - top;
        top = top + diff;
        bottom = bottom + diff;
        return new int[] { bottom, top };
    }

    public int getSpawnX() {
        return this.spawnX;
    }

    public int getSpawnZ() {
        return this.spawnZ;
    }

    private int chunkx, chunkz;

    public void setChunk(int chunkx, int chunkz) {
        this.chunkx = chunkx;
        this.chunkz = chunkz;
    }

    public void setChunkType(int x, int y, int z, Material type) {
        this.setBlockType(chunkx * 16 + x, y, chunkz * 16 + z, type);
    }

    public void setChunkData(int x, int y, int z, BlockData data) {
        this.setBlockData(chunkx * 16 + x, y, chunkz * 16 + z, data);
    }

    public Material getChunkType(int x, int y, int z) {
        return this.zone_world.getType(chunkx * 16 + x, y, chunkz * 16 + z);
    }

    @Override
    public String getWorldName() {
        return world.getName();
    }

    @Override
    public Block getBlock(Coord pos) {
        return world.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public long getSeed() {
        return this.world.getSeed();
    }

    @Override
    public void fillDown(Random rand, Coord origin, IBlockFactory blocks) {
        Coord cursor = new Coord(origin);

        while (!getMaterial(cursor).isSolid() && cursor.getY() > 1) {
            blocks.set(this, rand, cursor);
            cursor.add(Cardinal.DOWN);
        }
    }

    @Override
    public boolean canPlace(MetaBlock block, Coord pos, Cardinal dir) {
        return this.isAirBlock(pos);
    }

    private int virtualGroundHeight = -1;
    private boolean virtualGround = false;

    public void setVirtualGround(boolean state) {
        virtualGround = state;
    }

    public void setVirtualGroundHeight(int height) {
        virtualGroundHeight = height;
    }

    public int getVirtualGroundHeight() {
        return virtualGroundHeight;
    }

    @Override
    public boolean validGroundBlock(Coord pos) {
        if (virtualGround) {
            return pos.getY() <= virtualGroundHeight;
        }
        if (isAirBlock(pos))
            return false;
        Material material = getMaterial(pos);
        return !invalid.contains(material);
    }

    @Override
    public void spiralStairStep(Random rand, Coord origin, IStair stair, IBlockFactory fill) {
        MetaBlock air = BlockType.get(BlockType.AIR);
        Coord cursor;
        Coord start;
        Coord end;

        start = new Coord(origin);
        start.add(new Coord(-1, 0, -1));
        end = new Coord(origin);
        end.add(new Coord(1, 0, 1));

        RectSolid.fill(this, rand, start, end, air);
        fill.set(this, rand, origin);

        Cardinal dir = Cardinal.directions[origin.getY() % 4];
        cursor = new Coord(origin);
        cursor.add(dir);
        stair.setOrientation(Cardinal.left(dir), false).set(this, cursor);
        cursor.add(Cardinal.right(dir));
        stair.setOrientation(Cardinal.right(dir), true).set(this, cursor);
        cursor.add(Cardinal.reverse(dir));
        stair.setOrientation(Cardinal.reverse(dir), true).set(this, cursor);
    }

    public void setBlockState(BlockPos pos, BlockData data, int flag) {
        zone_world.setBlockData(pos.getX(), pos.getY(), pos.getZ(), data, false);
    }

    public void setBlockState(int x, int y, int z, BlockData data) {
        zone_world.setBlockData(x, y, z, data, false);
    }

    public void setBlockState(BlockPos pos, Material data, int flag) {
        zone_world.setType(pos.getX(), pos.getY(), pos.getZ(), data, false);
    }

    public void setBlockState(int x, int y, int z, Material data) {
        zone_world.setType(x, y, z, data, false);
    }

    public Material getBlockState(BlockPos pos) {
        return zone_world.getType(pos.getX(), pos.getY(), pos.getZ());
    }

    public BlockData getBlockStateBD(BlockPos pos) {
        return zone_world.getData(pos.getX(), pos.getY(), pos.getZ());
    }

    public Material getBlockState(int x, int y, int z) {
        return zone_world.getType(x, y, z);
    }

    @Override
    public int getStat(Block block) {
        return 0;
    }

    @Override
    public Map<Material, Integer> getStats() {
        return this.stats;
    }

    @Override
    public TreasureManager getTreasure() {
        return this.chests;
    }

    @Override
    public void addChest(ITreasureChest toAdd) {
        this.chests.add(toAdd);
    }

    @Override
    public IPositionInfo getInfo(Coord pos) {
        return new PositionInfo(this.world, pos);
    }

    @Override
    public Biome getBiome(Coord pos) {
        return MultiVersion.getBiome(this.world, pos.getX(), pos.getZ());
    }

    @Override
    public World getWorld() {
        return this.world;
    }

    @Override
    public boolean isFakeWorld() {
        return true;
    }

    @Override
    public void addLater(Later later) {
        this.zone_world.addLater(later);
    }

    public final ZoneWorld zone_world = new ZoneWorld();

    public ZoneWorld getAsyncWorld() {
        return this.zone_world;
    }

    @Override
    public boolean commit(int count) {
        return true;
    }

    @Override
    public MetaBlock getMetaBlock(Coord pos) {
        return null;
    }

    @Override
    public Material getMaterial(Coord pos) {
        return zone_world.getType(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean isAirBlock(Coord pos) {
        return zone_world.getType(pos.getX(), pos.getY(), pos.getZ()) == Material.AIR;
    }

    @Override
    public boolean setBlock(Coord pos, MetaBlock block, boolean fillAir, boolean replaceSolid) {
        return this.setBlock(pos, block, block.getFlag(), fillAir, replaceSolid);
    }

    /**
     * 判断是否为需要延迟处理的材质
     */
    private static boolean isPatchMaterial(Material material) {
        return material == Material.IRON_BARS || material == Material.REDSTONE_WIRE 
                || material == Material.WATER || material == Material.LAVA
                || material == Material.OAK_FENCE || material == Material.SPRUCE_FENCE
                || material == Material.JUNGLE_FENCE || material == Material.BIRCH_FENCE
                || material == Material.DARK_OAK_FENCE || material == Material.ACACIA_FENCE
                || material == Material.NETHER_BRICK_FENCE;
    }

    public void setBlockData(int x, int y, int z, BlockData bd) {
        Material material = bd.getMaterial();
        boolean patch = isPatchMaterial(material);
        zone_world.setBlockData(x, y, z, bd, patch);
    }

    public void setBlockType(int x, int y, int z, Material material) {
        boolean patch = isPatchMaterial(material);
        zone_world.setType(x, y, z, material, patch);
    }

    private boolean setBlock(Coord pos, MetaBlock block, int flags, boolean fillAir, boolean replaceSolid) {
        Material material = block.getBlock();
        Material mat = getMaterial(pos);

        if (mat == Material.CHEST)
            return false;
        if (mat == Material.TRAPPED_CHEST)
            return false;
        if (mat == Material.SPAWNER)
            return false;

        boolean isAir = mat == Material.AIR;

        if (!fillAir && isAir)
            return false;
        if (!replaceSolid && !isAir)
            return false;

        boolean patch = isPatchMaterial(material);

        try {
            if (!patch) {
                zone_world.setBlockData(pos.getX(), pos.getY(), pos.getZ(), block.getState(), flags == 1);
            } else {
                zone_world.setType(pos.getX(), pos.getY(), pos.getZ(), block.getBlock(), true);
            }

        } catch (NullPointerException npe) {
            // ignore it.
        }

        Integer count = stats.get(material);
        if (count == null) {
            stats.put(material, 1);
        } else {
            stats.put(material, count + 1);
        }

        return true;
    }
    
    /**
     * 使用 FAWE 批量设置多个方块
     * @param blocks 需要设置的方块映射
     */
    public void setBlocksBulk(Map<BlockVector3, BlockState> blocks) {
        if (blocks == null || blocks.isEmpty()) return;
        
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            for (Map.Entry<BlockVector3, BlockState> entry : blocks.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 使用 FAWE 批量设置多个方块（带延迟材质处理）
     * @param normalBlocks 普通方块
     * @param patchBlocks 需要延迟处理的方块
     */
    public void setBlocksBulkWithPatch(Map<BlockVector3, BlockState> normalBlocks,
                                        Map<BlockVector3, Material> patchBlocks) {
        if ((normalBlocks == null || normalBlocks.isEmpty()) && 
            (patchBlocks == null || patchBlocks.isEmpty())) return;
        
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            // 设置普通方块
            if (normalBlocks != null) {
                for (Map.Entry<BlockVector3, BlockState> entry : normalBlocks.entrySet()) {
                    editSession.setBlock(entry.getKey(), entry.getValue());
                }
            }
            
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 延迟处理的方块需要单独设置（因为涉及 TileEntity 或需要物理更新）
        if (patchBlocks != null) {
            for (Map.Entry<BlockVector3, Material> entry : patchBlocks.entrySet()) {
                BlockVector3 pos = entry.getKey();
                world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ())
                    .setType(entry.getValue(), true);
            }
        }
    }
}