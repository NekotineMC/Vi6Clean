package fr.nekotine.vi6clean.it;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * Server log lines captured while open: every message, error lines, and lines
 * that carry an exception.
 */
final class LogCapture extends AbstractAppender implements AutoCloseable {

	/**
	 * A captured line.
	 *
	 * @param tick
	 *            the server tick when it was appended
	 * @param thrown
	 *            its exception, or {@code null}
	 */
	record Line(int tick, Level level, String message, @Nullable Throwable thrown) {
	}

	private final Queue<Line> lines = new ConcurrentLinkedQueue<>();

	private final LoggerContext context;

	LogCapture() {
		super("Vi6IT-capture", null, null, true, Property.EMPTY_ARRAY);
		context = (LoggerContext) LogManager.getContext(Bukkit.class.getClassLoader(), false);
		start();
		context.getConfiguration().getRootLogger().addAppender(this, null, null);
		context.updateLoggers();
	}

	@Override
	public void append(LogEvent evt) {
		lines.add(new Line(Bukkit.getCurrentTick(), evt.getLevel(), evt.getMessage().getFormattedMessage(),
				evt.getThrown()));
	}

	/** @return every line, of every level, in order */
	List<Line> lines() {
		return List.copyOf(lines);
	}

	/** @return the lines that carry an exception */
	List<String> failures() {
		return lines.stream().filter(l -> l.thrown() != null).map(l -> l.message() + " " + l.thrown()).toList();
	}

	/** @return the error lines, without their exception */
	List<String> errors() {
		return lines.stream().filter(l -> l.level().isMoreSpecificThan(Level.ERROR)).map(Line::message).toList();
	}

	/** @return every message, of every level, without its exception */
	List<String> messages() {
		return lines.stream().map(Line::message).toList();
	}

	void clear() {
		lines.clear();
	}

	@Override
	public void close() {
		context.getConfiguration().getRootLogger().removeAppender(getName());
		context.updateLoggers();
		stop();
	}
}
