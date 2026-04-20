/**
 * 
 */
package forge_sandbox.com.someguyssoftware.dungeons2.generator.strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
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
import forge_sandbox.com.someguyssoftware.dungeons2.generator.ISupportedBlock;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.SupportedBlock;
import forge_sandbox.com.someguyssoftware.dungeons2.generator.SupportedBlockProcessor;
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
 * @author Mark Gottschling on Aug 28, 2016
 * @modified FAWE 2.15.1
 */
public class SupportedRoomGenerationStrategy extends AbstractRoomGenerationStrategy {

    // 缓存常用的 BlockState
    private final Map<Material, BlockState> blockStateCache = new ConcurrentHashMap<>();
    
    /**
     * 
     * @param provider
     */
    public SupportedRoomGenerationStrategy(IDungeonsBlockProvider provider) {
        super(provider);
    }

    @Override
    public void generate(AsyncWorldEditor world, Random random, Room room, Theme theme, StyleSheet styleSheet,
            ILevelConfig config) {

        SupportedBlockProcessor supportProcessor = new SupportedBlockProcessor(getBlockProvider(), room);
        Map<ICoords, Arrangement> postProcessMap = new HashMap<>();
        Multimap<DesignElement, ICoords> blueprint = room.getFloorMap();

        // 使用 FAWE EditSession
        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world.getWorld()))
                .allowedRegionsEverywhere()
                .limitUnlimited()
                .changeSetNull()
                .fastMode(true)
                .build()) {
            
            // 存储需要设置的方块
            Map<BlockVector3, BlockState> blocksToSet = new HashMap<>();
            // 存储需要设置为空气的方块（用于第二遍不支持的方块）
            Map<BlockVector3, BlockState> airBlocksToSet = new HashMap<>();
            
            BlockData blockState;
            ISupportedBlock supportedBlock;

            // 第一遍：正向遍历
            for (int y = 0; y < room.getHeight(); y++) {
                for (int z = 0; z < room.getDepth(); z++) {
                    for (int x = 0; x < room.getWidth(); x++) {

                        ICoords indexCoords = new Coords(x, y, z);
                        ICoords worldCoords = room.getCoords().add(indexCoords);

                        Arrangement arrangement = getBlockProvider().getArrangement(worldCoords, room, room.getLayout());

                        if (isPostProcessed(arrangement, worldCoords, postProcessMap)) {
                            continue;
                        }

                        blockState = getBlockProvider().getBlockState(random, worldCoords, room, arrangement, theme,
                                styleSheet, config);

                        // 处理空气和支持计算
                        if (blockState == null || blockState.getMaterial() == Material.AIR
                                || blockState == IDungeonsBlockProvider.NULL_BLOCK) {
                            supportedBlock = new SupportedBlock(blockState, 100);
                            if (blockState != null && blockState != IDungeonsBlockProvider.NULL_BLOCK) {
                                BlockVector3 pos = BlockVector3.at(
                                    worldCoords.getX(), worldCoords.getY(), worldCoords.getZ()
                                );
                                blocksToSet.put(pos, getCachedBlockState(blockState));
                                if (worldCoords.getY() == room.getMinY() + 1
                                        || arrangement.getElement().getFamily() == DesignElement.SURFACE_AIR) {
                                    blueprint.put(arrangement.getElement(), worldCoords);
                                }
                            }
                        } else {
                            int amount = supportProcessor.applySupportRulesPass1(world, indexCoords, worldCoords,
                                    arrangement.getElement());
                            if (amount >= 100) {
                                supportedBlock = new SupportedBlock(blockState, 100);
                                BlockVector3 pos = BlockVector3.at(
                                    worldCoords.getX(), worldCoords.getY(), worldCoords.getZ()
                                );
                                blocksToSet.put(pos, getCachedBlockState(blockState));
                                if (worldCoords.getY() == room.getMinY() + 1
                                        || arrangement.getElement().getFamily() == DesignElement.SURFACE_AIR) {
                                    blueprint.put(arrangement.getElement(), worldCoords);
                                }
                            } else {
                                supportedBlock = new SupportedBlock(blockState, amount);
                            }
                        }
                        supportProcessor.getSupportMatrix()[y][z][x] = supportedBlock;
                    }
                }

                // 第二遍：反向遍历
                for (int z = room.getDepth() - 1; z >= 0; z--) {
                    for (int x = room.getWidth() - 1; x >= 0; x--) {
                        supportedBlock = supportProcessor.getSupportMatrix()[y][z][x];
                        if (supportedBlock == null || supportedBlock.getAmount() < 100) {

                            ICoords indexCoords = new Coords(x, y, z);
                            ICoords worldCoords = room.getCoords().add(indexCoords);

                            Arrangement arrangement = getBlockProvider().getArrangement(worldCoords, room,
                                    room.getLayout());

                            BlockData pass2BlockState;
                            if (arrangement.getElement() != DesignElement.AIR) {
                                pass2BlockState = getBlockProvider().getBlockState(random, worldCoords, room,
                                        arrangement, theme, styleSheet, config);
                            } else {
                                pass2BlockState = Bukkit.createBlockData(Material.AIR);
                            }

                            BlockVector3 pos = BlockVector3.at(
                                worldCoords.getX(), worldCoords.getY(), worldCoords.getZ()
                            );

                            // 如果是空气，直接设置
                            if (pass2BlockState != null && pass2BlockState.getMaterial() == Material.AIR) {
                                blocksToSet.put(pos, getCachedBlockState(pass2BlockState));
                            } else if (pass2BlockState != null) {
                                // 计算支持度
                                supportedBlock = new SupportedBlock(pass2BlockState, 0);
                                int amount = supportProcessor.applySupportRulesPass2(world, indexCoords, worldCoords,
                                        arrangement.getElement());
                                supportedBlock.setAmount(supportedBlock.getAmount() + amount);

                                if (supportedBlock.getAmount() >= 100) {
                                    blocksToSet.put(pos, getCachedBlockState(pass2BlockState));
                                    if (worldCoords.getY() == room.getMinY() + 1) {
                                        blueprint.put(arrangement.getElement(), worldCoords);
                                    }
                                } else {
                                    // 不支持的方块设置为空气
                                    airBlocksToSet.put(pos, getCachedBlockState(
                                        Bukkit.createBlockData(Material.AIR)
                                    ));
                                }
                            }
                        }
                    }
                }
            }
            
            // 批量设置所有方块（先设置普通方块）
            for (Map.Entry<BlockVector3, BlockState> entry : blocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
            // 批量设置空气方块
            for (Map.Entry<BlockVector3, BlockState> entry : airBlocksToSet.entrySet()) {
                editSession.setBlock(entry.getKey(), entry.getValue());
            }
            
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
    public void generate(AsyncWorldEditor world, Random random, Room room, Theme theme, StyleSheet styleSheet,
            LevelConfig config) {
        generate(world, random, room, theme, styleSheet, (ILevelConfig) config);
    }
}