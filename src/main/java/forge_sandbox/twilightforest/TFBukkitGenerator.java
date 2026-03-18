/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package forge_sandbox.twilightforest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import forge_sandbox.StructureBoundingBox;
import forge_sandbox.twilightforest.structures.lichtower.StructureStartLichTower;
import io.papermc.lib.PaperLib;
import otd.Main;
import otd.api.event.DungeonGeneratedEvent;
import otd.config.SimpleWorldConfig;
import otd.config.WorldConfig;
import otd.lib.ZoneWorld;
import otd.lib.async.AsyncWorldEditor;
import otd.lib.async.later.roguelike.Later;
import otd.lib.async.later.twilightforest.Art_Later;
import otd.world.DungeonType;

/**
 *
 * @author shadow
 */
public class TFBukkitGenerator {

	private final static Map<UUID, Integer> POOL = new HashMap<>();

	public static boolean generateLichTower(World world, Location location, Random random) {
		AsyncWorldEditor w = new AsyncWorldEditor(world);
		w.setDefaultState(Material.AIR);
		int i = location.getBlockX();
		int j = location.getBlockZ();

		String name = world.getName();
		int bottom = 5;
		if (WorldConfig.wc.dict.containsKey(name)) {
			SimpleWorldConfig swc = WorldConfig.wc.dict.get(name);
			bottom = swc.worldParameter.bottom;
		}

		int y = location.getBlockY();
		if (y < bottom)
			y = bottom;
		int offsetY = y - 63;

		Bukkit.getScheduler().runTaskAsynchronously(Main.instance, () -> {
			StructureStartLichTower l = new StructureStartLichTower(w, TFFeature.LICH_TOWER, random,
					location.getChunk().getX(), location.getChunk().getZ());
			l.generateStructure(w, random, new StructureBoundingBox(i - 48, j - 48, i + 48, j + 48));
			asyncGenerate(w, offsetY, i, j);
		});

		return true;
	}

	public static void asyncGenerate(AsyncWorldEditor w, int offsetY, int x, int z) {
		Set<int[]> chunks0 = w.getAsyncWorld().getCriticalChunks();

		int delay = 0;

		UUID id;
		do {
			id = UUID.randomUUID();
		} while (POOL.containsKey(id));
		UUID uuid = id;
		synchronized (POOL) {
			POOL.put(uuid, chunks0.size());
		}

		try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
        		.world(BukkitAdapter.adapt(w.getWorld()))
                .allowedRegionsEverywhere() // 允许任何区域
                .limitUnlimited() // 解除限制
                .changeSetNull() // 不记录变化
                .fastMode(true) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                .build();
				EditSession practicalSession = WorldEdit.getInstance().newEditSessionBuilder()
		        		.world(BukkitAdapter.adapt(w.getWorld()))
		                .allowedRegionsEverywhere() // 允许任何区域
		                .limitUnlimited() // 解除限制
		                .changeSetNull() // 不记录变化
		                .fastMode(true) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
		                .build()) {
			
			for (int[] chunk : chunks0) {
				int chunkX = chunk[0];
				int chunkZ = chunk[1];

				List<ZoneWorld.CriticalNode> cn = w.getAsyncWorld().getCriticalBlock(chunkX, chunkZ);
				List<Later> later = w.getAsyncWorld().getCriticalLater(chunkX, chunkZ);

				delay++;

				Bukkit.getScheduler().runTaskLater(Main.instance, () -> {
					PaperLib.getChunkAtAsync(w.getWorld(), chunkX, chunkZ, true).thenAccept((Chunk c) -> {
						for (ZoneWorld.CriticalNode node : cn) {
							int[] pos = node.pos;
							BlockVector3 position = BlockVector3.at(pos[0], pos[1], pos[2]);
							if (node.bd != null) {
								Material type = node.bd.getMaterial();
								if (type != Material.GLASS_PANE && type != Material.OAK_FENCE) {
									BlockState blockState = BukkitAdapter.adapt(node.bd);
									editSession.setBlock(position, blockState);
									//c.getBlock(pos[0], pos[1] + offsetY, pos[2]).setBlockData(node.bd, false);
								} else {
									BlockState blockState = BukkitAdapter.adapt(node.bd.getMaterial().createBlockData());
									practicalSession.setBlock(position, blockState);
									//c.getBlock(pos[0], pos[1] + offsetY, pos[2]).setType(node.bd.getMaterial(), true);
								}
									
									
							} else {
								Material type = node.material;
								if (type != Material.GLASS_PANE && type != Material.OAK_FENCE) {
									BlockState blockState = BukkitAdapter.adapt(type.createBlockData());
									editSession.setBlock(position, blockState);
									//c.getBlock(pos[0], pos[1] + offsetY, pos[2]).setType(node.material, false);
								} else {
									BlockState blockState = BukkitAdapter.adapt(type.createBlockData());
									practicalSession.setBlock(position, blockState);
									//c.getBlock(pos[0], pos[1] + offsetY, pos[2]).setType(node.material, true);
								}
									
									
							}
						}
						if (later != null) {
							for (Later l : later) {
								l.setOffset(0, offsetY, 0);
								l.doSomethingInChunk(c);
							}
						}

						boolean isFinish = false;
						synchronized (POOL) {
							if (POOL.containsKey(uuid)) {
								int count = POOL.get(uuid);
								count--;
								POOL.put(uuid, count);

								if (count == 0) {
									isFinish = true;
									POOL.remove(uuid);
								}
							}
						}

						if (isFinish) {
							Bukkit.getScheduler().runTaskLater(Main.instance, () -> {
								String world_name = w.getWorld().getName();
								boolean flag = true;
								if (WorldConfig.wc.dict.containsKey(world_name)) {
									if (!WorldConfig.wc.dict.get(world_name).lich_tower.doArt)
										flag = false;
								}
								if (flag)
									for (Later l : w.getAsyncWorld().later_task) {
										if (l instanceof Art_Later) {
											l.setOffset(0, offsetY, 0);
											l.doSomething();
										}
									}

								Bukkit.getScheduler().runTaskLater(Main.instance, () -> {
									int ix = (w.zone_world.getMaxX() + w.zone_world.getMinX()) / 2;
									int iy = (w.zone_world.getMaxY() + w.zone_world.getMinY()) / 2 + offsetY;
									int iz = (w.zone_world.getMaxZ() + w.zone_world.getMinZ()) / 2;

									DungeonGeneratedEvent event = new DungeonGeneratedEvent(w.getWorld(), chunks0,
											DungeonType.Lich, ix, iy, iz);
									Bukkit.getServer().getPluginManager().callEvent(event);
								}, 1L);

							}, 1L);
						}
					});
				}, delay);
			}
			
			editSession.flushQueue();
			practicalSession.flushQueue();
		} catch (Exception e) {
        	e.printStackTrace();
            throw new RuntimeException("批量设置方块失败", e);
        }
		
		{
		}
	}
}
