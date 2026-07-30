package fr.nekotine.vi6clean.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class Vi6Config {

	@Comment("Définit si la configuration actuelle doit être écrasée par celle par défaut fournie avec le plugin")
	private boolean override_config = true;

	@Comment("Configuration des différentes étapes de la partie")
	private GameConfig game = new GameConfig();

	public boolean override_config() {
		return override_config;
	}

	// Getters and setters

	public void override_config(boolean value) {
		override_config = value;
	}

	public GameConfig game() {
		return game;
	}

	public void game(GameConfig value) {
		game = value;
	}

}
