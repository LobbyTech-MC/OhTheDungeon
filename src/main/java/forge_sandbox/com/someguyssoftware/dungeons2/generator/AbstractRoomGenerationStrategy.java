/**
 * 
 */
package forge_sandbox.com.someguyssoftware.dungeons2.generator;

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

import forge_sandbox.BlockPos;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.strategy.IRoomGenerationStrategy;
import forge_sandbox.com.someguyssoftware.dungeons2.model.LevelConfig;
import forge_sandbox.com.someguyssoftware.dungeons2.style.DesignElement;
import forge_sandbox.com.someguyssoftware.dungeons2.style.Layout;
import forge_sandbox.com.someguyssoftware.dungeons2.style.Style;
import forge_sandbox.com.someguyssoftware.dungeons2.style.StyleSheet;
import forge_sandbox.com.someguyssoftware.dungeons2.style.Theme;
import forge_sandbox.com.someguyssoftware.dungeonsengine.config.ILevelConfig;
import forge_sandbox.com.someguyssoftware.gottschcore.positional.ICoords;
import otd.lib.async.AsyncWorldEditor;

/**
 * @author Mark Gottschling on Aug 28, 2016
 * @modified FAWE 2.15.1
 */
public abstract class AbstractRoomGenerationStrategy implements IRoomGenerationStrategy {
    
    private IDungeonsBlockProvider blockProvider;
    
    // 缓存常用的 BlockState
    private final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();

    /**
     * 
     * @param provider
     */
    public AbstractRoomGenerationStrategy(IDungeonsBlockProvider provider) {
        this.blockProvider = provider;
    }

    /**
     * 
     * @param arrangement
     * @param coords
     * @param postProcess
     * @return
     */
    protected boolean isPostProcessed(Arrangement arrangement, ICoords coords, Map<ICoords, Arrangement> postProcess) {
        if (arrangement.getElement() == DesignElement.LADDER) {
            postProcess.put(coords, arrangement);
            return true;
        }
        return false;
    }

    /**
     * 后处理 - 使用 FAWE 批量操作
     */
    protected void postProcess(AsyncWorldEditor world, Random random, Map<ICoords, Arrangement> post, Layout layout,
            Theme theme, StyleSheet styleSheet, ILevelConfig config) {
        
        if (post.isEmpty()) {
            return;
        }
        
        // 使用 FAWE EditSession 批量生成
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            // 存储需要设置的方块
            Map<BlockVector3, BlockState> blocksToSet = new java.util.HashMap<>();
            
            for (Map.Entry<ICoords, Arrangement> entry : post.entrySet()) {
                ICoords keyCoords = entry.getKey();
                Arrangement arrangement = entry.getValue();
                DesignElement element = arrangement.getElement();

                // 检查是否有相邻的支撑方块
                if (!hasAdjacentSupport(world, keyCoords)) {
                    continue;
                }

                // 获取样式和方块状态
                Style style = getBlockProvider().getStyle(element, layout, theme, styleSheet);
                int decayIndex = getBlockProvider().getDecayIndex(random, config.getDecayMultiplier(), style);
                BlockData blockData = getBlockProvider().getBlockState(arrangement, style, decayIndex);
                
                if (blockData != null && blockData != IDungeonsBlockProvider.NULL_BLOCK) {
                    BlockVector3 position = BlockVector3.at(
                        keyCoords.getX(),
                        keyCoords.getY(),
                        keyCoords.getZ()
                    );
                    blocksToSet.put(position, getCachedBlockState(blockData));
                }
            }
            
            // 批量设置所有后处理方块
            for (Map.Entry<BlockVector3, BlockState> entry : blocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 检查是否有相邻的支撑方块
     * @param world 世界
     * @param coords 坐标
     * @return 是否有支撑
     */
    protected boolean hasAdjacentSupport(AsyncWorldEditor world, ICoords coords) {
        BlockPos pos = coords.toPos();
        
        // 获取相邻位置的方块类型（使用异步世界读取）
        Material east = world.getBlockState(pos.east());
        Material west = world.getBlockState(pos.west());
        Material north = world.getBlockState(pos.north());
        Material south = world.getBlockState(pos.south());
        
        // 检查是否有任何相邻方块是固体方块
        return (east != null && east.isBlock()) ||
               (west != null && west.isBlock()) ||
               (north != null && north.isBlock()) ||
               (south != null && south.isBlock());
    }
    
    /**
     * 检查是否有相邻的支撑方块（带 Material 参数）
     * @deprecated 使用 hasAdjacentSupport(AsyncWorldEditor, ICoords)
     */
    @Deprecated
    protected boolean hasAdjacentSupport(Material east, Material west, Material north, Material south) {
        return (east != null && east.isBlock()) ||
               (west != null && west.isBlock()) ||
               (north != null && north.isBlock()) ||
               (south != null && south.isBlock());
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
     * 清除 BlockState 缓存（用于内存管理）
     */
    protected void clearCache() {
        blockStateCache.clear();
    }

    /**
     * @deprecated 使用新的 ILevelConfig 版本
     */
    @Deprecated
    protected void postProcess(AsyncWorldEditor world, Random random, Map<ICoords, Arrangement> post, Layout layout,
            Theme theme, StyleSheet styleSheet, LevelConfig config) {
        // 委托给新版本
        postProcess(world, random, post, layout, theme, styleSheet, (ILevelConfig) config);
    }

    /**
     * @return the blockProvider
     */
    @Override
    public IDungeonsBlockProvider getBlockProvider() {
        return blockProvider;
    }
}