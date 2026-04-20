package forge_sandbox.greymerk.roguelike.treasure.loot;

import org.bukkit.inventory.ItemStack;
//import net.minecraft.item.ItemStack;

import forge_sandbox.greymerk.roguelike.treasure.loot.books.BookStarter;

public enum Book {

	CREDITS;

	public static ItemStack get(Book type) {
		switch (type) {

		default:
			return new BookStarter().get();
		}
	}

}
