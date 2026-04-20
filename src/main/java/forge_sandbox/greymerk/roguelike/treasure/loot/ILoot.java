package forge_sandbox.greymerk.roguelike.treasure.loot;

import org.bukkit.inventory.ItemStack;
//import net.minecraft.item.ItemStack;

import forge_sandbox.greymerk.roguelike.util.IWeighted;

public interface ILoot {

	public IWeighted<ItemStack> get(Loot type, int level);

}
