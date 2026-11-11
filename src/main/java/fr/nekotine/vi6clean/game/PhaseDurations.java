package fr.nekotine.vi6clean.game;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.module.IPluginModule;
import fr.nekotine.vi6clean.configuration.ConfigManager;
import fr.nekotine.vi6clean.configuration.GameConfig;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

/**
 * The single point through which phases read their durations. Each duration is
 * the live {@code config.yml} value unless a test has overridden it.
 *
 * <p>
 * Phases and wrappers read their durations when they are built, so an override
 * only affects the phases built after it is set: set it before the phase
 * starts, and clear it when the test is done. Overrides persist until
 * {@link #clearOverrides()} or until the module unloads, and each one is logged
 * as a warning so a leftover override shows in the server log. Main thread
 * only.
 */
public class PhaseDurations implements IPluginModule {

	public enum Key {
		/** Length of the preparation phase. */
		PREPARATION(c -> c.preparation().duration()),
		/** Time without an artefact steal before the thieves are lost. */
		INFILTRATION_LOST(c -> c.infiltration().lostDuration()),
		/** Minimum time between two artefact captures by one thief. */
		DELAY_BETWEEN_CAPTURE(c -> c.infiltration().delayBetweenCapture()),
		/** Minimum time between a thief's last capture and their escape. */
		DELAY_BEFORE_ESCAPE(c -> c.infiltration().delayBeforeEscape()),
		/** Time spent as spectator once the game is over. */
		END_SPECTATOR_TIME(c -> c.infiltration().endSpectatorTime());

		private final Function<GameConfig, Duration> configValue;

		Key(Function<GameConfig, Duration> configValue) {
			this.configValue = configValue;
		}
	}

	private final ComponentLogger logger = NekotineLogger.make();

	private final Map<Key, Duration> overrides = new EnumMap<>(Key.class);

	/** @return the override for {@code key} if one is set, else its config value */
	public Duration get(Key key) {
		var override = overrides.get(Objects.requireNonNull(key, "key"));
		if (override != null) {
			return override;
		}
		return key.configValue.apply(Ioc.resolve(ConfigManager.class).getConfig().game());
	}

	/**
	 * Overrides a duration for the phases built from now on.
	 *
	 * @throws IllegalArgumentException
	 *             if {@code value} is null, shorter than one tick, or too long to
	 *             count in ticks
	 */
	public void override(Key key, Duration value) {
		if (key == null || value == null || value.compareTo(Tick.of(1)) < 0
				|| value.toMillis() / Tick.of(1).toMillis() > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Durée invalide pour " + key + ": " + value);
		}
		overrides.put(key, value);
		logger.warn("Durée " + key + " remplacée par " + value + " (seam de test)");
	}

	/** Returns every duration to its config value. */
	public void clearOverrides() {
		overrides.clear();
	}

	@Override
	public void unload() {
		clearOverrides();
	}
}
