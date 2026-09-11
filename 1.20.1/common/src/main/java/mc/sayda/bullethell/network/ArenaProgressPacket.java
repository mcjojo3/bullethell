package mc.sayda.bullethell.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * C → S | one client tick's worth of results from a client-side simulation.
 *
 * This is the packet the whole client-sim rewrite exists for: instead of the server
 * pushing every bullet down to the client, the client pushes back the only things the
 * shared fight actually depends on - how much it hurt the boss, and where its player
 * ended up. Roughly thirty bytes against the twenty-odd kilobytes a tick of bullet
 * state used to cost.
 *
 * Damage and score are both reported rather than re-derived: a client that runs its own
 * simulation necessarily owns both. The server clamps what it accepts per tick (see
 * {@code ArenaContext.applyRemoteScore}) and drops damage reported against a phase it has
 * already ended, which bounds what a bad packet can do without pretending the numbers are
 * proven.
 */
public class ArenaProgressPacket {

    public final int bossDamage;
    /** Score earned locally this tick - graze, pickups and captures as well as damage. */
    public final long scoreDelta;
    public final float x, y;
    public final int lives, bombs, power, graze, grazeChain;
    public final int lifePieces, bombPieces, invulnTicks;
    /** Client's phase epoch; lets the server spot a client that missed a transition. */
    public final int phaseEpoch;

    public ArenaProgressPacket(int bossDamage, long scoreDelta, float x, float y, int lives, int bombs,
            int power, int graze, int grazeChain,
            int lifePieces, int bombPieces, int invulnTicks, int phaseEpoch) {
        this.bossDamage = bossDamage;
        this.scoreDelta = scoreDelta;
        this.x = x;
        this.y = y;
        this.lives = lives;
        this.bombs = bombs;
        this.power = power;
        this.graze = graze;
        this.grazeChain = grazeChain;
        this.lifePieces = lifePieces;
        this.bombPieces = bombPieces;
        this.invulnTicks = invulnTicks;
        this.phaseEpoch = phaseEpoch;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(bossDamage);
        buf.writeVarLong(scoreDelta);
        buf.writeFloat(x);
        buf.writeFloat(y);
        buf.writeVarInt(lives + 1); // lives can be -1 between death and game over
        buf.writeVarInt(bombs);
        buf.writeVarInt(power);
        buf.writeVarInt(graze);
        buf.writeVarInt(grazeChain);
        buf.writeVarInt(lifePieces);
        buf.writeVarInt(bombPieces);
        buf.writeVarInt(invulnTicks);
        buf.writeVarInt(phaseEpoch);
    }

    public static ArenaProgressPacket decode(FriendlyByteBuf buf) {
        int bossDamage = buf.readVarInt();
        long scoreDelta = buf.readVarLong();
        float x = buf.readFloat();
        float y = buf.readFloat();
        int lives = buf.readVarInt() - 1;
        int bombs = buf.readVarInt();
        int power = buf.readVarInt();
        int graze = buf.readVarInt();
        int grazeChain = buf.readVarInt();
        int lifePieces = buf.readVarInt();
        int bombPieces = buf.readVarInt();
        int invulnTicks = buf.readVarInt();
        int phaseEpoch = buf.readVarInt();
        return new ArenaProgressPacket(bossDamage, scoreDelta, x, y, lives, bombs, power,
                graze, grazeChain, lifePieces, bombPieces, invulnTicks, phaseEpoch);
    }
}
