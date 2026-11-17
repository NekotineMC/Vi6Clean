package fr.nekotine.vi6clean.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

/**
 * Phase durations: the setters reject what is not between 1 tick and
 * {@link Integer#MAX_VALUE} ticks, and an invalid value in {@code config.yml}
 * falls back to its key's default with a warning.
 */
class PhaseDurationValidationTest {

	private static final Duration MAX = Duration.ofMillis(Integer.MAX_VALUE * 50L);

	record Field(String name, Supplier<Duration> get, Consumer<Duration> set) {

		@Override
		public String toString() {
			return name;
		}
	}

	static Stream<Field> fields() {
		var config = new Vi6Config();
		var preparation = config.game().preparation();
		var infiltration = config.game().infiltration();
		return Stream.of(new Field("duration", preparation::duration, preparation::duration),
				new Field("lostDuration", infiltration::lostDuration, infiltration::lostDuration),
				new Field("delayBetweenCapture", infiltration::delayBetweenCapture, infiltration::delayBetweenCapture),
				new Field("delayBeforeEscape", infiltration::delayBeforeEscape, infiltration::delayBeforeEscape),
				new Field("endSpectatorTime", infiltration::endSpectatorTime, infiltration::endSpectatorTime));
	}

	static Stream<Arguments> validValues() {
		return fields()
				.flatMap(f -> Stream.of(Duration.ofMillis(50), Duration.ofMillis(2750), Duration.ofSeconds(300), MAX)
						.map(d -> Arguments.of(f, d)));
	}

	static Stream<Arguments> invalidValues() {
		return fields().flatMap(f -> Stream.of(Duration.ZERO, Duration.ofSeconds(-5), Duration.ofMillis(49),
				MAX.plusMillis(50), Duration.ofSeconds(Long.MAX_VALUE), null).map(d -> Arguments.of(f, d)));
	}

	@ParameterizedTest
	@MethodSource("validValues")
	void setterStoresAValidDuration(Field field, Duration value) {
		field.set().accept(value);
		assertEquals(value, field.get().get());
	}

	@ParameterizedTest
	@MethodSource("invalidValues")
	void setterRejectsAnInvalidDurationAndKeepsTheOldValue(Field field, Duration value) {
		field.set().accept(Duration.ofMillis(2750));
		var e = assertThrows(IllegalArgumentException.class, () -> field.set().accept(value));
		assertTrue(e.getMessage().startsWith(field.name() + " "), "message does not name the field: " + e.getMessage());
		assertEquals(Duration.ofMillis(2750), field.get().get(), "old value not kept");
	}

	@ParameterizedTest
	@ValueSource(strings = {"0", "-5", ".nan", ".inf", "-.inf", "0.01"})
	void invalidValueInTheFileFallsBackToItsDefault(String yamlValue) throws Exception {
		var config = load("""
				game:
				  preparation:
				    duration-seconds: 12
				  infiltration:
				    lost-duration-seconds: %s
				    delay-between-capture-seconds: 0.5
				""".formatted(yamlValue));
		var warnings = DurationRules.resetInvalidDurations(config);

		var defaults = new InfiltrationPhaseConfig();
		assertEquals(defaults.lostDuration(), config.game().infiltration().lostDuration(), "bad key not defaulted");
		assertEquals(Duration.ofSeconds(12), config.game().preparation().duration(), "valid key not kept");
		assertEquals(Duration.ofMillis(500), config.game().infiltration().delayBetweenCapture(), "valid key not kept");
		assertEquals(1, warnings.size(), "warnings: " + warnings);
		var warning = warnings.get(0);
		var expectedValue = Double.toString(Double.parseDouble(
				yamlValue.replace(".nan", "NaN").replace("-.inf", "-Infinity").replace(".inf", "Infinity")));
		assertTrue(warning.contains("game.infiltration.lost-duration-seconds: " + expectedValue + " ("),
				"warning does not name the key and the bad value " + expectedValue + ": " + warning);
		assertTrue(warning.contains("0.05 s") && warning.contains("107374182.35 s"),
				"warning does not give the bounds in seconds: " + warning);
	}

	@Test
	void everyInvalidKeyGetsItsOwnWarning() throws Exception {
		var config = load("""
				game:
				  preparation:
				    duration-seconds: 0
				  infiltration:
				    lost-duration-seconds: -1
				    delay-between-capture-seconds: .nan
				    delay-before-escape-seconds: .inf
				    end-spectator-time-seconds: 0.01
				""");
		var warnings = DurationRules.resetInvalidDurations(config);
		assertEquals(5, warnings.size(), "warnings: " + warnings);
		var keys = List.of("game.preparation.duration-seconds: 0.0 (",
				"game.infiltration.lost-duration-seconds: -1.0 (",
				"game.infiltration.delay-between-capture-seconds: NaN (",
				"game.infiltration.delay-before-escape-seconds: Infinity (",
				"game.infiltration.end-spectator-time-seconds: 0.01 (");
		for (int i = 0; i < keys.size(); i++) {
			assertTrue(warnings.get(i).contains(keys.get(i)), "warning " + i + ": " + warnings.get(i));
		}
		var defaults = new Vi6Config();
		assertEquals(DurationSnapshot.of(defaults), DurationSnapshot.of(config), "not every key defaulted");
	}

	@Test
	void defaultsAreValid() {
		assertEquals(List.of(), DurationRules.resetInvalidDurations(new Vi6Config()));
	}

	@Test
	void snapshotDescribesOnlyChangedDurations() {
		var config = new Vi6Config();
		var loaded = DurationSnapshot.of(config);
		assertEquals("", loaded.describeChanges(DurationSnapshot.of(config)));
		config.game().infiltration().lostDuration(Duration.ofSeconds(3));
		assertEquals("game.infiltration.lost-duration-seconds 240.0s -> 3.0s",
				loaded.describeChanges(DurationSnapshot.of(config)));
	}

	private static Vi6Config load(String yaml) throws Exception {
		var loader = YamlConfigurationLoader.builder().nodeStyle(NodeStyle.BLOCK)
				.source(() -> new BufferedReader(new StringReader(yaml))).build();
		return loader.load().get(Vi6Config.class, new Vi6Config());
	}
}
