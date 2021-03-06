package entities.heroes;

import abilities.IAbility;
import abilities.IPassive;
import entities.angels.BasicAngel;
import map.GameMap;
import entities.IEntity;
import entities.EntityType;
import map.surface.ISurface;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

public abstract class BasicHero implements IEntity {
    private static final int BASE_XP_FOR_LEVEL_UP = 250;
    private static final int MULTIPLIER_FOR_LEVEL_UP = 50;
    private static final int BASE_XP_FOR_BONUS_KILL = 200;
    private static final int MULTIPLIER_FOR_BONUS_KILL = 40;

    private int id;
    private int x, y;
    private int xp;
    private int level;
    private int hp;
    private int damageTaken;
    private GameMap map;
    private final ArrayList<IAbility> abilities;

    private IEntity lastAttacker;
    private int passiveNumRounds;
    private IPassive passivePenalty;
    private IPassive passivePenaltyFinish;
    private boolean passiveJustEnded;
    private boolean isStunned;

    private float additiveModifier;

    private final PropertyChangeSupport support;

    public BasicHero() {
        abilities = new ArrayList<IAbility>();
        support = new PropertyChangeSupport(this);

        setId(0);
        setMap(null);
        setPosition(-1, -1);
        setXP(0);
        setLevel(0);
        setHP(getMaxHP());
        setDamageTaken(0);
        setPassivePenalty(0, null, null);
        setAdditiveModifier(0.0f);
    }

    @Override
    public final String toString() {
        return String.format("%s %d", getHeroType(), getId());
    }

    /**
     * Returneaza tipul eroului.
     * @return HeroType
     */
    public abstract HeroType getHeroType();

    /**
     * Returneaza Base HP pentru jucator.
     * @return Base HP
     */
    public abstract int getInitialHP();

    /**
     * Returneaza bonusul de HP per nivel. (multiplicativ)
     * @return bonus HP
     */
    public abstract int getHPBonusPerLevel();

    /**
     * Returneaza *land modifier*-ul in functie de suprafata pe care se afla eroul la momentul
     * apelarii metodei.
     * @return *land modifier*
     */
    public abstract float getLandModifier();

    /**
     * Accepta efectul unei abilitati. Implementare double-dispatch.
     * @param ability abilitatea ce se va aplica asupra eroului this
     */
    public abstract void acceptAbility(IAbility ability);

    /**
     * Returneaza viata maxima ce o poate avea eroul in functie de nivelul curent.
     * @return max HP
     */
    public final int getMaxHP() {
        return getInitialHP() + getLevel() * getHPBonusPerLevel();
    }

    /**
     * Verifica daca eroul este mort.
     * @return True daca este mort, altfel False
     */
    public final boolean isDead() {
        return getHP() <= 0;
    }

    /**
     * Returneaza tipul de MapEntity. Necesara pentru a putea fi plasat pe harta.
     * @return HERO
     */
    @Override
    public final EntityType getEntityType() {
        return EntityType.HERO;
    }

    /**
     * Metode de deplasare.
     */
    public final void goUp() {
        setPosition(getX(), getY() - 1);
    }
    public final void goDown() {
        setPosition(getX(), getY() + 1);
    }
    public final void goLeft() {
        setPosition(getX() - 1, getY());
    }
    public final void goRight() {
        setPosition(getX() + 1, getY());
    }
    public final int getX() {
        return x;
    }
    public final int getY() {
        return y;
    }
    public final void setPosition(final int newX, final int newY) {
        if (isDead() || isStunned()) {
            return;
        }

        if (getMap() != null) {
            if (this.x != -1 && this.y != -1) {
                getMap().getEntities(this.x, this.y).remove(this);
            }
            if (newX != -1 && newY != -1) {
                getMap().getEntities(newX, newY).add(this);
            }
        }

        this.x = newX;
        this.y = newY;
    }

    public final void setId(final int id) {
        this.id = id;
    }

    public final int getId() {
        return id;
    }

    public final int getXP() {
        return xp;
    }
    public final void setXP(final int newXP) {
        this.xp = newXP;
    }
    public final void increaseXP(final int amount) {
        setXP(getXP() + amount);
    }

    public final int getNeededXPForLevelUp() {
        return BASE_XP_FOR_LEVEL_UP + getLevel() * MULTIPLIER_FOR_LEVEL_UP;
    }
    public final int getLevel() {
        return level;
    }
    public final void setLevel(final int newLevel) {
        support.firePropertyChange("setLevel", level, newLevel);
        this.level = newLevel;

        if (!isDead()) {
            setHP(getMaxHP());
        }
    }
    public final void increaseLevel() {
        setLevel(getLevel() + 1);
    }
    public final void levelUp() {
        while (getXP() >= getNeededXPForLevelUp()) {
            increaseLevel();
        }
    }

    public final int getHP() {
        return hp;
    }
    public final void setHP(final int newHP) {
        if (newHP > 0 && isDead()) {
            onRevive();
        }

        this.hp = Math.min(getMaxHP(), newHP); // clamp between [-inf .. maxHP]

        if (isDead()) {
            IEntity attacker = getLastAttacker();
            if (attacker != null && attacker.getEntityType() == EntityType.HERO) {
                ((BasicHero) attacker).onKill(this);
            }

            onDeath(attacker);
        }
    }
    public final void increaseHP(final int amount) {
        setHP(getHP() + amount);
    }
    public final void decreaseHP(final int amount) {
        setHP(getHP() - amount);
    }

    /**
     * Apelata dupa efectuarea unui kill.
     * @param attacked eroul omorat
     */
    public final void onKill(final BasicHero attacked) {
        int levelDiff = getLevel() - attacked.getLevel();
        int bonusXP = BASE_XP_FOR_BONUS_KILL - levelDiff * MULTIPLIER_FOR_BONUS_KILL;
        bonusXP = Math.max(0, bonusXP);

        increaseXP(bonusXP);
    }

    /**
     * Apelata dupa moarte.
     * @param attacker entitatea care a omorat
     */
    public final void onDeath(final IEntity attacker) {
        support.firePropertyChange("death", null, attacker);
    }

    /**
     * Apelata inainte de a fi inviat.
     */
    public final void onRevive() {
        support.firePropertyChange("revive", null, null);
    }

    /**
     * Returneaza damage-ul luat (dar inca neaplicat) in runda curenta.
     * @return damage
     */
    public final int getDamageTaken() {
        return damageTaken;
    }

    /**
     * Seteaza damage-ul ce va fi luat la sfarsitul rundei curente.
     * @param damage
     */
    public final void setDamageTaken(final int damage) {
        this.damageTaken = damage;
    }

    /**
     * Creste damage-ul ce va fi luat la sfarsitul rundei curente.
     * @param amount cu cat creste
     */
    public final void increaseDamageTaken(final int amount) {
        setDamageTaken(getDamageTaken() + amount);
    }

    /**
     * Aplica damage-ul acumulat in runda curenta si reseteaza contorul.
     */
    public final void applyDamageTaken() {
        decreaseHP(getDamageTaken());
        setDamageTaken(0);
    }

    public final GameMap getMap() {
        return map;
    }
    public final void setMap(final GameMap map) {
        this.map = map;
    }

    public final ISurface getSurface() {
        return getMap().getSurface(x, y);
    }

    public final ArrayList<IAbility> getAbilities() {
        return abilities;
    }

    /**
     * Seteaza o actiune pasiva ce va afecta eroul pana:
     *   * se termina cele `rounds` runde;
     *   * este inlocuita de alta actiune pasiva;
     *   * moare eroul.
     *
     * @param rounds cate runde va fi aplicata actiunea
     * @param action actiunea pasiva
     * @param finish actiunea ce va fi indeplinita imediat dupa terminarea efectului pasiv
     */
    public final void setPassivePenalty(final int rounds,
                                        final IPassive action, final IPassive finish) {
        if (passivePenaltyFinish != null) {
            passivePenaltyFinish.apply(this);
        }

        passiveNumRounds = rounds;
        passivePenalty = action;
        passivePenaltyFinish = finish;
        passiveJustEnded = false;
    }

    /**
     * Aplica actiunea pasiva.
     */
    public final void applyPassivePenalty() {
        if (passiveJustEnded) {
            setPassivePenalty(0, null, null);
        }

        if (passiveNumRounds != 0) {
            if (passivePenalty != null && !isDead()) {
                passivePenalty.apply(this);
            }
            if (--passiveNumRounds == 0) {
                passiveJustEnded = true;
            }
        }
    }

    /**
     * Verifica daca jucatorul este imobilizat.
     * @return boolean
     */
    public final boolean isStunned() {
        return isStunned;
    }

    /**
     * Seteaza statutul de imobilizat.
     * @param stunned boolean
     */
    public final void setStunned(final boolean stunned) {
        isStunned = stunned;
    }

    /**
     * Returneaza ultimul atacator activ.
     * @return BasicHero
     */
    public final IEntity getLastAttacker() {
        return lastAttacker;
    }

    /**
     * Seteaza ultimul atacator activ.
     * @param lastAttacker
     */
    public final void setLastAttacker(final IEntity lastAttacker) {
        this.lastAttacker = lastAttacker;
    }

    /**
     * Seteaza modificatorul aditiv pentru damage.
     * @param additiveModifier
     */
    public final void setAdditiveModifier(final float additiveModifier) {
        this.additiveModifier = additiveModifier;
    }

    /**
     * Returneaza modificatorul aditiv pentru damage.
     */
    public final float getAdditiveModifier() {
        return additiveModifier;
    }

    /**
     * Creste modificatorul aditiv pentru damage.
     * @param amount
     */
    public final void increaseAdditiveModifier(final float amount) {
        setAdditiveModifier(getAdditiveModifier() + amount);
    }

    /**
     * Scade modificatorul aditiv pentru damage.
     * @param amount
     */
    public final void decreaseAdditiveModifier(final float amount) {
        setAdditiveModifier(getAdditiveModifier() - amount);
    }

    /**
     * Accepta efectul unei inger. Implementare double-dispatch.
     * @param angel ingerul ce se va aplica asupra eroului this
     */
    public abstract void acceptAngel(BasicAngel angel);

    /**
     * Adauga un listener.
     * @param pcl listener
     */
    public final void addPropertyChangeListener(final PropertyChangeListener pcl) {
        support.addPropertyChangeListener(pcl);
    }

    /**
     * Sterge un listener.
     * @param pcl listener
     */
    public final void removePropertyChangeListener(final PropertyChangeListener pcl) {
        support.removePropertyChangeListener(pcl);
    }

}
