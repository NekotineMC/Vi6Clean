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

/**
 * Server log lines captured while open: error lines, and lines that carry an
 * exception.
 */
final class LogCapture extends AbstractAppender implements AutoCloseable {

	private final Queue<String> failures = new ConcurrentLinkedQueue<>();

	private final Queue<String> errors = new ConcurrentLinkedQueue<>();

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
		var message = evt.getMessage().getFormattedMessage();
		if (evt.getLevel().isMoreSpecificThan(Level.ERROR)) {
			errors.add(message);
		}
		var thrown = evt.getThrown();
		if (thrown != null) {
			failures.add(message + " " + thrown);
		}
	}

	/** @return the lines that carry an exception */
	List<String> failures() {
		return List.copyOf(failures);
	}

	/** @return the error lines, without their exception */
	List<String> errors() {
		return List.copyOf(errors);
	}

	void clear() {
		failures.clear();
		errors.clear();
	}

	@Override
	public void close() {
		context.getConfiguration().getRootLogger().removeAppender(getName());
		context.updateLoggers();
		stop();
	}
}
