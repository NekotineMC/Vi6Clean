package fr.nekotine.vi6clean.bot;

import java.util.UUID;

/**
 * The identity of one bot: the Citizens NPC id and the name it answers to.
 * <p>
 * No Citizens type appears here on purpose. Bot state keys on
 * {@code npc.getUniqueId()} and never on {@code npc.getEntity().getUniqueId()},
 * because Citizens hands a player NPC an entity UUID of its own, so the two ids
 * are different keys and a roster built on the wrong one cannot be repaired
 * later. Keeping the record free of Citizens also lets it be held and listed on
 * a server where Citizens is not installed.
 */
public record Bot(UUID npcId, String name) {
}
