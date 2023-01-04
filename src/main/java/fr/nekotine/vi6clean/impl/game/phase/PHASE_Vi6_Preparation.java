package fr.nekotine.vi6clean.impl.game.phase;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import fr.nekotine.core.game.Game;
import fr.nekotine.core.game.GamePhase;
import fr.nekotine.core.game.GameTeam;
import fr.nekotine.core.inventory.menu.MenuInventory;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.usable.Usable;
import fr.nekotine.core.usable.UsableModule;
import fr.nekotine.core.util.EntityUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.GD_Vi6;
import fr.nekotine.vi6clean.impl.game.GM_Vi6;
import fr.nekotine.vi6clean.impl.menu.ToolShopLayout;
import fr.nekotine.vi6clean.impl.menu.ToolShopTab;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PHASE_Vi6_Preparation extends GamePhase<GD_Vi6, GM_Vi6>{

	private final PotionEffect playerSaturationEffect;
	
	private final PotionEffect thiefNightVisionEffect;
	
	private final Usable shopUsable;
	
	public PHASE_Vi6_Preparation(GM_Vi6 gamemode) {
		super(gamemode);
		playerSaturationEffect = new PotionEffect(PotionEffectType.SATURATION, Integer.MAX_VALUE, 0, false, false, false);
		thiefNightVisionEffect = new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false);
		shopUsable = new Usable(ItemStackUtil.make(Material.NETHERITE_INGOT, Component.text("Magasin").color(NamedTextColor.GOLD))) {
			@Override
			protected void OnInteract(PlayerInteractEvent e) {
				var player = e.getPlayer();
				var wrapper = ModuleManager.GetModule(WrappingModule.class).getWrapper(player, PlayerWrapper.class);
				var shopMenu = wrapper.getShopMenu();
				if (shopMenu != null) {
					shopMenu.displayTo(player);
				}
			}
		};
	}

	@Override
	public void globalBegin(Game<GD_Vi6> game) {
		ModuleManager.GetModule(UsableModule.class).register(shopUsable);
	}

	@Override
	public void globalEnd(Game<GD_Vi6> game) {
		ModuleManager.GetModule(UsableModule.class).unregister(shopUsable);
	}

	@Override
	public void playerBegin(Game<GD_Vi6> game, Player player, GameTeam team) {
		var inv = player.getInventory();
		inv.clear();
		inv.addItem(shopUsable.getItemStack());
		var wrapper = ModuleManager.GetModule(WrappingModule.class).getWrapper(player, PlayerWrapper.class);
		wrapper.setShopMenu(makeShopMenu());
		player.setSprinting(false); // Le changements des attributs par défaut fera chier les gens qui sprint
		EntityUtil.clearPotionEffects(player);
		EntityUtil.defaultAllAttributes(player);
		player.addPotionEffect(playerSaturationEffect);
		if (team == game.getGameData().getThiefTeam()){
			player.addPotionEffect(thiefNightVisionEffect);
		}
	}

	@Override
	public void playerEnd(Game<GD_Vi6> game, Player player, GameTeam team) {
	}
	
	private MenuInventory makeShopMenu() {
		var shopMenuLayout = new ToolShopLayout(Material.GRAY_STAINED_GLASS_PANE);
		var allTab = new ToolShopTab(ItemStackUtil.make(Material.STONE, Component.text("Tout").color(NamedTextColor.WHITE)));
		var testTab = new ToolShopTab(ItemStackUtil.make(Material.GRASS_BLOCK, Component.text("Test").color(NamedTextColor.GREEN)));
		shopMenuLayout.addTab(allTab);
		shopMenuLayout.addTab(testTab);
		return new MenuInventory(shopMenuLayout,6);
	}

}
