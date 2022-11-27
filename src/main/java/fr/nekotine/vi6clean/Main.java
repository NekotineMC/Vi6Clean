package fr.nekotine.vi6clean;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIConfig;
import fr.nekotine.core.game.GameModeModule;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.visibility.EntityVisibilityModule;
import fr.nekotine.vi6clean.impl.game.GM_Vi6;
import fr.nekotine.vi6clean.impl.map.MAP_Vi6;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.map.artefact.BlockArtefactVisual;
import fr.nekotine.vi6clean.impl.map.artefact.EntityArtefactVisual;

public class Main extends JavaPlugin implements Listener{
	
	@Override
	public void onEnable() {
		super.onEnable();
		
		ModuleManager.Load(this,
				EntityVisibilityModule.class
				);
		
		ModuleManager.EnableAll();
		
		ModuleManager.GetModule(GameModeModule.class).registerGameMode("vi6", new GM_Vi6());
		
		registerConfigurationSerializables();
		
	}
	
	@Override
	public void onLoad() {
		super.onLoad();
		CommandAPI.onLoad(new CommandAPIConfig());
	}
	
	@Override
	public void onDisable() {
		ModuleManager.DisableAll();
	}
	
	private void registerConfigurationSerializables() {
		ConfigurationSerialization.registerClass(MAP_Vi6.class);
		ConfigurationSerialization.registerClass(Artefact.class);
		ConfigurationSerialization.registerClass(BlockArtefactVisual.class);
		ConfigurationSerialization.registerClass(EntityArtefactVisual.class);
	}
}
