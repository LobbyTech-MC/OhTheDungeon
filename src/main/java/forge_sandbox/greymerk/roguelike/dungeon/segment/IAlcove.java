package forge_sandbox.greymerk.roguelike.dungeon.segment;

import java.util.Random;

import forge_sandbox.greymerk.roguelike.dungeon.settings.LevelSettings;
import forge_sandbox.greymerk.roguelike.worldgen.Cardinal;
import forge_sandbox.greymerk.roguelike.worldgen.Coord;
import forge_sandbox.greymerk.roguelike.worldgen.IWorldEditor;

public interface IAlcove {

	public void generate(IWorldEditor editor, Random rand, LevelSettings settings, Coord pos, Cardinal dir);

	public boolean isValidLocation(IWorldEditor editor, Coord pos, Cardinal dir);

}
