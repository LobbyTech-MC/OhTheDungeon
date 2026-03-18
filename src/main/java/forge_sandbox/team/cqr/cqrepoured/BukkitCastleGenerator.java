package forge_sandbox.team.cqr.cqrepoured;

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

import forge_sandbox.team.cqr.cqrepoured.world.structure.generation.dungeons.DungeonRandomizedCastle;
import forge_sandbox.team.cqr.cqrepoured.world.structure.generation.generation.GeneratableDungeon;
import forge_sandbox.team.cqr.cqrepoured.world.structure.generation.generators.AbstractDungeonGenerator;
import io.papermc.lib.PaperLib;
import otd.Main;
import otd.api.event.DungeonGeneratedEvent;
import otd.lib.ZoneWorld;
import otd.lib.async.AsyncWorldEditor;
import otd.lib.async.later.roguelike.Later;
import otd.world.DungeonType;

public class BukkitCastleGenerator {

	public static void generate(World w, Location loc, Random random) {
		Bukkit.getScheduler().runTaskAsynchronously(Main.instance, () -> {
			AsyncGenerate(new AsyncWorldEditor(w), loc, random);
		});
	}

	private final static Map<UUID, Integer> POOL = new HashMap<>();

	public static void AsyncGenerate(AsyncWorldEditor w, Location loc, Random random) {
		DungeonRandomizedCastle dungeon = new DungeonRandomizedCastle("castle", null);
		AbstractDungeonGenerator<DungeonRandomizedCastle> generator = dungeon.createDungeonGenerator(w, loc.getBlockX(),
				loc.getBlockY(), loc.getBlockZ(), random);
		GeneratableDungeon gd = generator.get();
		gd.generate(w);

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
		                .fastMode(false) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
		                .build()
				) {
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
								if (node.bd.getMaterial() != Material.IRON_BARS
										&& node.bd.getMaterial() != Material.OAK_FENCE) {
									BlockState blockState = BukkitAdapter.adapt(node.bd);
									editSession.setBlock(position, blockState);
									//c.getBlock(pos[0], pos[1], pos[2]).setBlockData(node.bd, false);
								} else {
									BlockState blockState = BukkitAdapter.adapt(node.bd.getMaterial().createBlockData());
									editSession.setBlock(position, blockState);
									//c.getBlock(pos[0], pos[1], pos[2]).setType(node.bd.getMaterial(), true);
								}
									
								
							} else {
								if (node.material != Material.IRON_BARS && node.material != Material.OAK_FENCE) {
									BlockState blockState = BukkitAdapter.adapt(node.material.createBlockData());
									editSession.setBlock(position, blockState);
									//c.getBlock(pos[0], pos[1], pos[2]).setType(node.material, false);
								} else {
									BlockState blockState = BukkitAdapter.adapt(node.material.createBlockData());
									practicalSession.setBlock(position, blockState);
									//c.getBlock(pos[0], pos[1], pos[2]).setType(node.material, true);
								}
									
								
									
							}
						}
						if (later != null) {
							for (Later l : later) {
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
								int x = (w.zone_world.getMaxX() + w.zone_world.getMinX()) / 2;
								int y = (w.zone_world.getMaxY() + w.zone_world.getMinY()) / 2;
								int z = (w.zone_world.getMaxZ() + w.zone_world.getMinZ()) / 2;

								DungeonGeneratedEvent event = new DungeonGeneratedEvent(w.getWorld(), chunks0,
										DungeonType.Castle, x, y, z);
								Bukkit.getServer().getPluginManager().callEvent(event);
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
		

	}
}
