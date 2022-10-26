package fr.nekotine.vi6clean;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIConfig;
import fr.nekotine.core.bowcharge.BowChargeModule;
import fr.nekotine.core.charge.ChargeModule;
import fr.nekotine.core.damage.DamageModule;
import fr.nekotine.core.effect.CustomEffectModule;
import fr.nekotine.core.itemcharge.ItemChargeModule;
import fr.nekotine.core.lobby.GameModeIdentifier;
import fr.nekotine.core.lobby.LobbyModule;
import fr.nekotine.core.map.MapModule;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.projectile.ProjectileModule;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.usable.UsableModule;
import fr.nekotine.core.visibility.EntityVisibilityModule;
import fr.nekotine.vi6clean.impl.game.GM_Vi6;
import fr.nekotine.vi6clean.impl.game.MAP_Vi6;

public class Main extends JavaPlugin implements Listener{
	
	@Override
	public void onEnable() {
		super.onEnable();
		
		ModuleManager.Load(this,
				ChargeModule.class,
				TickingModule.class,
				ItemChargeModule.class,
				ProjectileModule.class,
				DamageModule.class,
				BowChargeModule.class,
				UsableModule.class,
				EntityVisibilityModule.class,
				CustomEffectModule.class,
				LobbyModule.class,
				MapModule.class
				);
		
		ModuleManager.EnableAll();
		
		GameModeIdentifier.registerGameMode(GM_Vi6.IDENTIFIER);
		
		MapModule.registerMapTypes(MAP_Vi6.IDENTIFIER);
		
		var mapModule = ModuleManager.GetModule(MapModule.class);
		
		mapModule.generateCommands();
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
}
