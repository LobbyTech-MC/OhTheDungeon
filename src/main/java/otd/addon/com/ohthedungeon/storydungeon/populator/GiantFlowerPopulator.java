/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package otd.addon.com.ohthedungeon.storydungeon.populator;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

public class GiantFlowerPopulator extends BlockPopulator {
	private static class IWorld {
		public World world;

		public IWorld(World world) {
			this.world = world;
		}

		public void setMaterial(int x, int y, int z, BlockType blockType, EditSession editSession) {
			BlockVector3 position = BlockVector3.at(x, y, z);
			editSession.setBlock(position, blockType);
			//world.getBlockAt(x, y, z).setType(material, false);
		}

		public boolean isAirBlock(int x, int y, int z, EditSession editSession) {
			BlockVector3 position = BlockVector3.at(x, y, z);
			return editSession.getBlock(position) == BlockTypes.AIR.getDefaultState();
			//return world.getBlockAt(x, y, z).getType() == BlockTypes.AIR;
		}
	}

	public static boolean generate_yellow(World world, Random random, int xx, int yy, int zz, EditSession editSession) {
		IWorld var1 = new IWorld(world);
		while (var1.isAirBlock(xx, yy, zz, editSession) && yy > 2) {
			--yy;
		}

		{
			for (int var7 = -2; var7 <= 2; ++var7) {
				for (int var8 = -2; var8 <= 2; ++var8) {
					if (var1.isAirBlock(xx + var7, yy - 1, zz + var8, editSession) && var1.isAirBlock(xx + var7, yy - 2, zz + var8, editSession))
						return false;
				}
			}

			var1.setMaterial(xx, yy, zz, BlockTypes.DIRT, editSession);
			var1.setMaterial(xx, yy + 1, zz, BlockTypes.BIRCH_LOG, editSession);
			var1.setMaterial(xx, yy + 2, zz, BlockTypes.BIRCH_LOG, editSession);
			var1.setMaterial(xx, yy + 3, zz, BlockTypes.BIRCH_LOG, editSession);
			var1.setMaterial(xx, yy + 4, zz, BlockTypes.BIRCH_LOG, editSession);
			var1.setMaterial(xx, yy + 5, zz, BlockTypes.BIRCH_LOG, editSession);

			var1.setMaterial(xx - 1, yy + 5, zz, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx + 1, yy + 5, zz, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx, yy + 5, zz - 1, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx, yy + 5, zz + 1, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx + 2, yy + 5, zz + 2, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx + 2, yy + 5, zz - 2, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx - 2, yy + 5, zz + 2, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx - 2, yy + 5, zz - 2, BlockTypes.YELLOW_WOOL, editSession);

			var1.setMaterial(xx, yy + 6, zz, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx - 1, yy + 6, zz, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx + 1, yy + 6, zz, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx, yy + 6, zz - 1, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx, yy + 6, zz + 1, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx + 1, yy + 6, zz + 1, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx + 1, yy + 6, zz - 1, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx - 1, yy + 6, zz + 1, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx - 1, yy + 6, zz - 1, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx + 2, yy + 6, zz, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx - 2, yy + 6, zz, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx, yy + 6, zz + 2, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx, yy + 6, zz - 2, BlockTypes.YELLOW_WOOL, editSession);

			var1.setMaterial(xx, yy + 7, zz, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx + 3, yy + 7, zz, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx - 3, yy + 7, zz, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx, yy + 7, zz + 3, BlockTypes.YELLOW_WOOL, editSession);
			var1.setMaterial(xx, yy + 7, zz - 3, BlockTypes.YELLOW_WOOL, editSession);

			return true;
		}
	}

	public static boolean generate_red(World world, Random random, int xx, int yy, int zz, EditSession editSession) {
		IWorld var1 = new IWorld(world);
		while (var1.isAirBlock(xx, yy, zz, editSession) && yy > 2) {
			--yy;
		}

		{
			for (int var7 = -2; var7 <= 2; ++var7) {
				for (int var8 = -2; var8 <= 2; ++var8) {
					if (var1.isAirBlock(xx + var7, yy - 1, zz + var8, editSession) && var1.isAirBlock(xx + var7, yy - 2, zz + var8, editSession))
						return false;
				}
			}

			var1.setMaterial(xx, yy, zz, BlockTypes.DIRT, editSession);
			var1.setMaterial(xx, yy + 1, zz, BlockTypes.ACACIA_LOG, editSession);
			var1.setMaterial(xx, yy + 2, zz, BlockTypes.ACACIA_LOG, editSession);
			var1.setMaterial(xx, yy + 3, zz, BlockTypes.ACACIA_LOG, editSession);
			var1.setMaterial(xx, yy + 4, zz, BlockTypes.ACACIA_LOG, editSession);
			var1.setMaterial(xx, yy + 5, zz, BlockTypes.ACACIA_LOG, editSession);

			var1.setMaterial(xx - 1, yy + 5, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx + 1, yy + 5, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 5, zz - 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 5, zz + 1, BlockTypes.RED_WOOL, editSession);

			var1.setMaterial(xx, yy + 6, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 1, yy + 6, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx + 1, yy + 6, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 6, zz - 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 6, zz + 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx + 1, yy + 6, zz + 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx + 1, yy + 6, zz - 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 1, yy + 6, zz + 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 1, yy + 6, zz - 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx + 2, yy + 6, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 2, yy + 6, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 6, zz + 2, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 6, zz - 2, BlockTypes.RED_WOOL, editSession);

			var1.setMaterial(xx + 1, yy + 7, zz + 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx + 1, yy + 7, zz - 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 1, yy + 7, zz + 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 1, yy + 7, zz - 1, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx + 2, yy + 7, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 2, yy + 7, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 7, zz + 2, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 7, zz - 2, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx + 2, yy + 7, zz + 2, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx + 2, yy + 7, zz - 2, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 2, yy + 7, zz + 2, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 2, yy + 7, zz - 2, BlockTypes.RED_WOOL, editSession);

			var1.setMaterial(xx + 2, yy + 8, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 2, yy + 8, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 8, zz + 2, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 8, zz - 2, BlockTypes.RED_WOOL, editSession);

			var1.setMaterial(xx + 3, yy + 9, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx - 3, yy + 9, zz, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 9, zz + 3, BlockTypes.RED_WOOL, editSession);
			var1.setMaterial(xx, yy + 9, zz - 3, BlockTypes.RED_WOOL, editSession);

			return true;
		}
	}

	@Override
	public void populate(World world, Random random, Chunk chunk) {
		int X = random.nextInt(15);
		int Z = random.nextInt(15);

		int x = chunk.getX() * 16 + X;
		int z = chunk.getZ() * 16 + Z;
		int y = world.getHighestBlockYAt(x, z);

		Material type = world.getHighestBlockAt(x, z).getType();
		if (type != Material.DIRT && type != Material.GRASS_BLOCK)
			return;

		try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
        		.world(BukkitAdapter.adapt(world))
                .allowedRegionsEverywhere() // 允许任何区域
                .limitUnlimited() // 解除限制
                .changeSetNull() // 不记录变化
                .fastMode(true) // 禁用快速模式（true = 无物理/粒子，false = 有物理/粒子）
                .build()) {
			
			if (random.nextInt() % 2 == 0)
				generate_yellow(world, random, x, y, z, editSession);
			else
				generate_red(world, random, x, y, z, editSession);
			
			editSession.flushQueue();
		} catch (Exception e) {
        	e.printStackTrace();
            throw new RuntimeException("批量设置方块失败", e);
        }
		
	}
}
