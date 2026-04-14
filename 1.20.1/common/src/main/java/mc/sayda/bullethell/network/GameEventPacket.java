package mc.sayda.bullethell.network;

import mc.sayda.bullethell.arena.GameEvent;
import net.minecraft.network.FriendlyByteBuf;

/** S → C | game events (HIT, DEATH, PHASE_CHANGE, etc.) for screen FX. */
public class GameEventPacket {

    public final GameEvent event;

    public GameEventPacket(GameEvent event) { this.event = event; }

    public void encode(FriendlyByteBuf buf) { buf.writeByte(event.ordinal()); }

    public static GameEventPacket decode(FriendlyByteBuf buf) {
        return new GameEventPacket(GameEvent.fromId(buf.readByte()));
    }
}
