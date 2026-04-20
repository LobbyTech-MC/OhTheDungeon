package otd.lib.async.later.castle;

import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.BlockPos;
import forge_sandbox.greymerk.roguelike.worldgen.Coord;
import otd.api.event.ChestEvent;
import otd.config.LootNode;
import otd.config.SimpleWorldConfig;
import otd.config.WorldConfig;
import otd.lib.async.AsyncWorldEditor;
import otd.lib.async.later.roguelike.Later;
import otd.util.OTDLoottables;
import otd.world.DungeonType;

public class Chest_Later extends Later {

    public int x, y, z;
    public BlockFace face;
    public List<LootNode> loots;
    public OTDLoottables lootTable;
    public String world;
    public Random random;
    
    // FAWE BlockState 缓存
    private static BlockState cachedChestState = null;
    
    private static BlockState getCachedChestState(BlockFace face) {
        if (cachedChestState == null) {
            BlockData bd = Bukkit.createBlockData(Material.CHEST);
            Directional dir = (Directional) bd;
            dir.setFacing(face);
            cachedChestState = BukkitAdapter.adapt(bd);
        }
        return cachedChestState;
    }

    private Chest_Later() {

    }

    public static Chest_Later getChest(AsyncWorldEditor world, BlockPos pos, BlockFace face, OTDLoottables lootTable,
            Random random) {
        Chest_Later later = new Chest_Later();
        later.x = pos.getX();
        later.y = pos.getY();
        later.z = pos.getZ();
        later.face = face;
        later.lootTable = lootTable;
        later.loots = OTDLoottables.getLoots(lootTable, random);
        later.world = world.getWorldName();
        later.random = random;

        return later;
    }

    @Override
    public Coord getPos() {
        return new Coord(x, y, z);
    }

    @Override
    public void doSomething() {
        // 使用 FAWE 在主线程设置宝箱
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(Bukkit.getWorld(world)))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            BlockVector3 pos = BlockVector3.at(x, y, z);
            BlockState chestState = getCachedChestState(face);
            editSession.setBlock(pos, chestState);
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 处理宝箱物品（需要在主线程执行）
        processChestLoot();
    }

    @Override
    public void doSomethingInChunk(Chunk c) {
        // 获取区块内的相对坐标
        int bx = x % 16;
        int by = y;
        int bz = z % 16;
        if (bx < 0) bx += 16;
        if (bz < 0) bz += 16;

        Block block = c.getBlock(bx, by, bz);
        
        // 设置宝箱方块
        BlockData bd = Bukkit.createBlockData(Material.CHEST);
        Directional dir = (Directional) bd;
        dir.setFacing(face);
        block.setBlockData(dir, true);

        // 处理宝箱物品
        processChestLoot(block);
    }
    
    /**
     * 处理宝箱物品（使用缓存的 loots）
     */
    private void processChestLoot() {
        // 获取世界
        org.bukkit.World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) return;
        
        Block block = bukkitWorld.getBlockAt(x, y, z);
        processChestLoot(block);
    }
    
    /**
     * 处理宝箱物品
     */
    private void processChestLoot(Block block) {
        if (!(block.getState() instanceof Chest)) return;
        
        boolean builtin = true;
        if (WorldConfig.wc.dict.containsKey(world) && !WorldConfig.wc.dict.get(world).castle.builtinLoot) {
            builtin = false;
        }

        if (!builtin) {
            loots.clear();
        }

        if (WorldConfig.wc.dict.containsKey(world)) {
            SimpleWorldConfig swc = WorldConfig.wc.dict.get(world);
            for (LootNode node : swc.castle.loots) {
                loots.add(node);
            }
        }

        Chest chest = (Chest) block.getState();
        Inventory inv = chest.getInventory();
        
        for (LootNode ln : loots) {
            if (random.nextDouble() <= ln.chance) {
                ItemStack item = ln.getItem();
                int amount;
                if (ln.max == ln.min) {
                    amount = ln.max;
                } else {
                    amount = ln.min + random.nextInt(ln.max - ln.min + 1);
                }
                item.setAmount(amount);
                inv.setItem(random.nextInt(inv.getSize()), item);
            }
        }

        Location loc = new Location(block.getWorld(), x, y, z);
        ChestEvent event = new ChestEvent(DungeonType.Castle, "", loc);
        Bukkit.getServer().getPluginManager().callEvent(event);
    }
}