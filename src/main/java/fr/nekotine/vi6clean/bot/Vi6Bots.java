package fr.nekotine.vi6clean.bot;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import fr.nekotine.core.bot.Bot;
import fr.nekotine.core.module.IPluginModule;

public class Vi6Bots implements IPluginModule {

	private final Map<Bot, BotTeamIntent> intents = new HashMap<>();

	public void setIntent(Bot bot, BotTeamIntent intent) {
		intents.put(bot, intent);
	}

	public @Nullable BotTeamIntent getIntent(Bot bot) {
		return intents.get(bot);
	}

	// Kicked bots and lost connections leave the roster without a command
	public void forgetAllBut(Collection<Bot> rosterBots) {
		intents.keySet().retainAll(rosterBots);
	}

	@Override
	public void unload() {
		intents.clear();
	}
}
