package fr.nekotine.vi6clean.bot;

import org.bukkit.Bukkit;

import fr.nekotine.core.logging.NekotineLogger;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/**
 * Availability of the Citizens plugin, read from its Bukkit handle alone.
 * <p>
 * This enum is the classloader barrier of the bot package: it holds no Citizens
 * type on purpose, because a class mentioning {@code net.citizensnpcs} in a
 * field or in a signature cannot link while Citizens is absent. It is therefore
 * the only class a no-Citizens server is allowed to resolve, and
 * {@link BotRegistry} is left untouched until this reports {@link #READY}.
 * <p>
 * {@link #READY} only answers "is Citizens usable right now". Registration
 * happens once, in {@code Vi6Main.onEnable}, so a server where Citizens was
 * absent or disabled at boot keeps reporting it as unavailable for the whole
 * session even if the operator starts Citizens afterwards. That is deliberate:
 * there is no re-registration path.
 */
public enum CitizensAvailability {

	ABSENT, DISABLED, READY;

	private static final String CITIZENS_PLUGIN = "Citizens";

	private static final ComponentLogger logger = NekotineLogger.make(CitizensAvailability.class);

	/**
	 * Reads the Citizens plugin handle. Never throws: an unexpected plugin manager
	 * state is reported as {@link #ABSENT}, which is the answer every caller can
	 * act on safely.
	 */
	public static CitizensAvailability check() {
		try {
			var plugin = Bukkit.getPluginManager().getPlugin(CITIZENS_PLUGIN);
			if (plugin == null) {
				return ABSENT;
			}
			return plugin.isEnabled() ? READY : DISABLED;
		} catch (Exception e) {
			// Dropping the cause here would report an unrelated plugin manager failure
			// as "Citizens is required", which is the one message that sends the
			// operator looking in the wrong place.
			logger.warn("L'état du plugin Citizens n'a pas pu être lu", e);
			return ABSENT;
		}
	}

	/**
	 * The reason and nothing but the reason: this enum can only know why bots are
	 * unavailable, never what the operator just failed to do with them. That
	 * consequence belongs to the caller, which is the only place that knows whether
	 * it was an addition, a listing or a removal that was refused.
	 *
	 * @return the operator facing reason why bots cannot be used, empty when
	 *         Citizens is usable
	 */
	public String describe() {
		return switch (this) {
			case ABSENT -> "Le plugin " + CITIZENS_PLUGIN + " est requis pour utiliser les bots.";
			case DISABLED -> "Le plugin " + CITIZENS_PLUGIN + " est installé mais pas activé.";
			case READY -> "";
		};
	}

}
