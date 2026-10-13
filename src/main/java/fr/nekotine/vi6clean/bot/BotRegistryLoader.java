package fr.nekotine.vi6clean.bot;

import java.util.Optional;

import fr.nekotine.core.logging.NekotineLogger;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/**
 * Builds the roster without ever naming {@link BotRegistry} in its own
 * bytecode.
 * <p>
 * This is the last piece of the classloader barrier. A class is verified when
 * it is linked, and the verifier resolves the target of every {@code new},
 * {@code invokevirtual} and {@code invokestatic} it sees, so even a
 * {@code new BotRegistry()} sitting behind a runtime guard drags Citizens into
 * a server that has none: the error happens while {@code Vi6Main} is being
 * verified, long before the guard is reached. Going through
 * {@link Class#forName} keeps this class free of any Citizens reference, so it
 * links anywhere and only fails, gracefully, once it is actually called.
 * <p>
 * The name is a string on purpose. It is the single place a rename of
 * {@code BotRegistry} has to be mirrored.
 */
public final class BotRegistryLoader {

	private static final ComponentLogger logger = NekotineLogger.make(BotRegistryLoader.class);

	private static final String REGISTRY_CLASS = "fr.nekotine.vi6clean.bot.BotRegistry";

	private BotRegistryLoader() {
	}

	/**
	 * Instantiates the roster. Only call this once
	 * {@link CitizensAvailability#check()} reported
	 * {@link CitizensAvailability#READY}.
	 * <p>
	 * Nothing thrown here may escape: this runs inside {@code Vi6Main.onEnable},
	 * whose single {@code catch (Exception)} would otherwise swallow it and leave
	 * the plugin half enabled. The class name is a string, so a rename of
	 * {@code BotRegistry} cannot be caught by the compiler — it is checked at
	 * runtime instead, and the failure is reported rather than swallowed.
	 *
	 * @return the roster, or empty when Citizens turned out to be unusable after
	 *         all
	 */
	public static Optional<BotService> load() {
		try {
			var type = Class.forName(REGISTRY_CLASS);
			if (!BotService.class.isAssignableFrom(type)) {
				logger.error("{} n'implemente pas {}", REGISTRY_CLASS, BotService.class.getName());
				return Optional.empty();
			}
			var roster = (BotService) type.getConstructor().newInstance();
			logger.info("Registre de bots chargé : {}", type.getName());
			return Optional.of(roster);
		} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
			logger.error("Le registre de bots n'a pas pu être chargé", e);
			return Optional.empty();
		}
	}

}
