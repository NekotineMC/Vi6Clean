package fr.nekotine.vi6clean;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIConfig;
import fr.nekotine.core.game.GameModeModule;
import fr.nekotine.core.inventory.menu.MenuModule;
import fr.nekotine.core.lobby.LobbyModule;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.usable.UsableModule;
import fr.nekotine.core.visibility.EntityVisibilityModule;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.GM_Vi6;
import fr.nekotine.vi6clean.impl.map.MAP_Vi6;
import fr.nekotine.vi6clean.impl.map.artefact.Artefact;
import fr.nekotine.vi6clean.impl.map.artefact.BlockArtefactVisual;
import fr.nekotine.vi6clean.impl.map.artefact.EntityArtefactVisual;

public class Vi6Main extends JavaPlugin implements Listener{
	
	private static Vi6Main main;
	
	/**
	 * Récupère la dernière instance de Vi6Main créée.
	 * Cette methode existe principalement pour permettre la création de NamespacedKey
	 * @return
	 */
	public static Vi6Main getOneVi6Main() {
		return main;
	}
	
	@Override
	public void onEnable() {
		main = this;
		super.onEnable();
		
		ModuleManager.Load(this,
				EntityVisibilityModule.class,
				WrappingModule.class,
				UsableModule.class,
				MenuModule.class
				);
		
		ModuleManager.EnableAll();
		
		ModuleManager.GetModule(GameModeModule.class).registerGameMode("vi6", new GM_Vi6(this));
		
		ModuleManager.GetModule(LobbyModule.class).registerCommands();
		
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
