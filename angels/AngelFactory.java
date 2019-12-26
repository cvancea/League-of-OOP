package angels;

import angels.good.LevelUpAngel;
import angels.good.DamageAngel;
import angels.good.LifeGiver;
import map.GameMap;

public final class AngelFactory {
    private AngelFactory() { }

    public static BasicAngel createAngel(final String angelName, final int x, final int y,
                                         final GameMap gameMap) {
        switch (angelName) {
            case "DamageAngel":
                return new DamageAngel(x, y, gameMap);
            case "LifeGiver":
                return new LifeGiver(x, y, gameMap);
            case "LevelUpAngel":
                return new LevelUpAngel(x, y, gameMap);
            default:
                return null;
        }
    }
}
