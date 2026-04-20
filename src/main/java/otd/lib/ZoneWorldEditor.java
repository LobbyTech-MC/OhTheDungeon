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
package otd.lib;

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

import forge_sandbox.greymerk.roguelike.treasure.ITreasureChest;
import forge_sandbox.greymerk.roguelike.treasure.TreasureManager;
import forge_sandbox.greymerk.roguelike.worldgen.Cardinal;
import forge_sandbox.greymerk.roguelike.worldgen.Coord;
import forge_sandbox.greymerk.roguelike.worldgen.IBlockFactory;
import forge_sandbox.greymerk.roguelike.worldgen.IPositionInfo;
import forge_sandbox.greymerk.roguelike.worldgen.IStair;
import forge_sandbox.greymerk.roguelike.worldgen.IWorldEditor;
import forge_sandbox.greymerk.roguelike.worldgen.MetaBlock;
import forge_sandbox.greymerk.roguelike.worldgen.blocks.BlockType;
import forge_sandbox.greymerk.roguelike.worldgen.shapes.RectSolid;
import otd.lib.async.later.roguelike.Later;
import otd.util.FormatItem;

/**
 * @author shadow
 * @modified FAWE 2.15.1
 */
public class ZoneWorldEditor implements IWorldEditor {

    public ZoneWorld world;
    public World w;
    private Map<Material, Integer> stats;
    public TreasureManager chests;
    
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
    
    private final static List<Material> INVALID;
    static {
        INVALID = new ArrayList<>();
        INVALID.add(Material.OAK_PLANKS);
        INVALID.add(Material.WATER);
        INVALID.add(Material.CACTUS);
        INVALID.add(Material.SNOW);
        INVALID.add(Material.SHORT_GRASS);
        INVALID.add(Material.STONE);
        INVALID.add(Material.OAK_LEAVES);
        INVALID.add(Material.POPPY);
        INVALID.add(Material.DANDELION);
    };
    
    private int seaLevel = 63;

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

    ZonePositionInfo info;

    public ZoneWorldEditor(Biome b, World world) {
        this.info = new ZonePositionInfo(b);
        this.w = world;
        this.world = new ZoneWorld();
        this.stats = new HashMap<>();
        this.chests = new TreasureManager();
    }

    @Override
    public void addLater(Later later) {
        world.addLater(later);
    }

    @Override
    public boolean isFakeWorld() {
        return true;
    }

    @Override
    public String getWorldName() {
        return DungeonWorldManager.WORLD_NAME;
    }

    @Override
    public boolean commit(int count) {
        return true;
    }

    @Override
    public World getWorld() {
        return this.w;
    }

    @Override
    public Biome getBiome(Coord pos) {
        return Biome.THE_VOID;
    }

    public ZoneWorldEditor(World world) {
        this.world = new ZoneWorld();
        stats = new HashMap<>();
        this.chests = new TreasureManager();
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
                world.setBlockData(pos.getX(), pos.getY(), pos.getZ(), block.getState(), flags == 1);
            } else {
                world.setType(pos.getX(), pos.getY(), pos.getZ(), block.getBlock(), true);
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

    @Override
    public boolean setBlock(Coord pos, MetaBlock block, boolean fillAir, boolean replaceSolid) {
        return this.setBlock(pos, block, block.getFlag(), fillAir, replaceSolid);
    }

    @Override
    public Block getBlock(Coord pos) {
        return w.getBlockAt(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public boolean isAirBlock(Coord pos) {
        return world.getType(pos.getX(), pos.getY(), pos.getZ()) == Material.AIR;
    }

    @Override
    public long getSeed() {
        return 17L;
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

    @Override
    public void fillDown(Random rand, Coord origin, IBlockFactory blocks) {
        Coord cursor = new Coord(origin);

        while (!getMaterial(cursor).isSolid() && cursor.getY() > 1) {
            blocks.set(this, rand, cursor);
            cursor.add(Cardinal.DOWN);
        }
    }

    @Override
    public Material getMaterial(Coord pos) {
        return world.getType(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public MetaBlock getMetaBlock(Coord pos) {
        return null;
    }

    @Override
    public boolean validGroundBlock(Coord pos) {
        if (isAirBlock(pos))
            return false;
        Material material = getMaterial(pos);
        return !INVALID.contains(material);
    }

    @Override
    public int getStat(Block type) {
        return 0;
    }

    @Override
    public Map<Material, Integer> getStats() {
        return this.stats;
    }

    @Override
    public void addChest(ITreasureChest toAdd) {
        this.chests.add(toAdd);
    }

    @Override
    public TreasureManager getTreasure() {
        return this.chests;
    }

    @Override
    public boolean canPlace(MetaBlock block, Coord pos, Cardinal dir) {
        return this.isAirBlock(pos);
    }

    @Override
    public IPositionInfo getInfo(Coord pos) {
        return this.info;
    }

    @Override
    public String toString() {
        String toReturn = "";

        for (Map.Entry<Material, Integer> pair : stats.entrySet()) {
            toReturn += (new FormatItem(pair.getKey())).getUnlocalizedName() + ": " + pair.getValue() + "\n";
        }

        return toReturn;
    }
    
    // ======================== FAWE 批量操作方法 ========================
    
    /**
     * 批量设置多个方块（使用 FAWE）
     * @param blocks 需要设置的方块映射 (位置 -> 材质)
     */
    public void setBlocksBulk(Map<BlockVector3, Material> blocks) {
        if (blocks == null || blocks.isEmpty()) return;
        
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(w))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            for (Map.Entry<BlockVector3, Material> entry : blocks.entrySet()) {
                editSession.setBlock(entry.getKey(), getCachedBlockState(entry.getValue()));
            }
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 批量设置方块（带 BlockData）
     * @param blocks 需要设置的方块映射 (位置 -> BlockData)
     */
    public void setBlocksBulkWithData(Map<BlockVector3, BlockData> blocks) {
        if (blocks == null || blocks.isEmpty()) return;
        
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(w))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            for (Map.Entry<BlockVector3, BlockData> entry : blocks.entrySet()) {
                editSession.setBlock(entry.getKey(), getCachedBlockState(entry.getValue()));
            }
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 批量设置多个区域的相同材质
     * @param positions 位置列表
     * @param material 材质
     */
    public void setBlocksBulk(List<BlockVector3> positions, Material material) {
        if (positions == null || positions.isEmpty()) return;
        
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(w))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            BlockState state = getCachedBlockState(material);
            for (BlockVector3 pos : positions) {
                editSession.setBlock(pos, state);
            }
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}