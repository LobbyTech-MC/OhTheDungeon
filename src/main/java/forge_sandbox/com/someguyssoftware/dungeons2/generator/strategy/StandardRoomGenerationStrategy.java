/**
 * 
 */
package forge_sandbox.com.someguyssoftware.dungeons2.generator.strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import com.google.common.collect.Multimap;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.com.someguyssoftware.dungeons2.generator.AbstractRoomGenerationStrategy;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.Arrangement;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.blockprovider.IDungeonsBlockProvider;
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
 * Builds a structure using the base rule set ie. all blocks are generated
 * regardless of location, adjacent blocks etc.
 * 
 * @author Mark Gottschling on Aug 27, 2016
 * @modified FAWE 2.15.1
 */
public class StandardRoomGenerationStrategy extends AbstractRoomGenerationStrategy {

    // 缓存常用的 BlockState
    private final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();
    
    /**
     * 
     * @param blockProvider
     */
    public StandardRoomGenerationStrategy(IDungeonsBlockProvider blockProvider) {
        super(blockProvider);
    }

    @Override
    public void generate(AsyncWorldEditor world, Random random, Room room, Theme theme, StyleSheet styleSheet,
            ILevelConfig config) {
        Multimap<DesignElement, ICoords> blueprint = room.getFloorMap();
        Map<ICoords, Arrangement> postProcessMap = new HashMap<>();

        // 使用 FAWE EditSession 批量生成
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()  // 不记录历史，提高性能
                .fastMode(true)   // 禁用物理效果
                .build()) {
            
            // 预计算所有需要设置的方块
            Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
            
            // 生成房间
            for (int y = 0; y < room.getHeight(); y++) {
                for (int z = 0; z < room.getDepth(); z++) {
                    for (int x = 0; x < room.getWidth(); x++) {
                        
                        // 创建索引坐标
                        ICoords indexCoords = new Coords(x, y, z);
                        // 获取世界坐标
                        ICoords worldCoords = room.getCoords().add(indexCoords);
                        
                        // 获取设计布局
                        Arrangement arrangement = getBlockProvider().getArrangement(worldCoords, room, room.getLayout());
                        
                        // 添加设计元素到蓝图（如果是地板层或表面空气）
                        if (worldCoords.getY() == room.getMinY() + 1
                                || arrangement.getElement().getFamily() == DesignElement.SURFACE_AIR) {
                            blueprint.put(arrangement.getElement(), worldCoords);
                        }
                        
                        // 检查是否需要后处理
                        if (isPostProcessed(arrangement, worldCoords, postProcessMap)) {
                            continue;
                        }
                        
                        // 获取方块状态
                        BlockData blockData = getBlockProvider().getBlockState(random, worldCoords, room,
                                arrangement, theme, styleSheet, config);
                        
                        if (blockData == null || blockData == IDungeonsBlockProvider.NULL_BLOCK) {
                            continue;
                        }
                        
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
            
            // 批量设置所有方块
            for (Map.Entry<BlockVector3, BlockState> entry : blocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            // 刷新队列确保所有更改被应用
            editSession.flushQueue();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 生成后处理方块
        postProcess(world, random, postProcessMap, room.getLayout(), theme, styleSheet, config);
    }
    
    /**
     * 获取缓存的 BlockState
     */
    protected BlockState getCachedBlockState(BlockData blockData) {
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
        // 委托给新版本
        generate(world, random, room, theme, styleSheet, (ILevelConfig) config);
    }
}