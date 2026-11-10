package fr.nekotine.vi6clean.bot;

import java.util.Locale;

public enum BotTeamIntent {
	GUARD, THIEF, ANY;

	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static BotTeamIntent fromCode(String code) {
		return valueOf(code.toUpperCase(Locale.ROOT));
	}
}
