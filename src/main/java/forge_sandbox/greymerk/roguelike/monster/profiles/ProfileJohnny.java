package forge_sandbox.greymerk.roguelike.monster.profiles;

import java.util.Random;

import org.bukkit.World;
import org.bukkit.inventory.EquipmentSlot;

import forge_sandbox.greymerk.roguelike.monster.IEntity;
import forge_sandbox.greymerk.roguelike.monster.IMonsterProfile;
import forge_sandbox.greymerk.roguelike.monster.MobType;
import forge_sandbox.greymerk.roguelike.monster.MonsterProfile;
import forge_sandbox.greymerk.roguelike.treasure.loot.Equipment;
import forge_sandbox.greymerk.roguelike.treasure.loot.provider.ItemSpecialty;

public class ProfileJohnny implements IMonsterProfile {

	@Override
	public void addEquipment(World world, Random rand, int level, IEntity mob) {
		mob.setMobClass(MobType.VINDICATOR, false);
		mob.setSlot(EquipmentSlot.HAND, ItemSpecialty.getRandomItem(Equipment.AXE, rand, 4));
		MonsterProfile.get(MonsterProfile.TALLMOB).addEquipment(world, rand, 3, mob);
		mob.setName("Johnny");
	}

}
