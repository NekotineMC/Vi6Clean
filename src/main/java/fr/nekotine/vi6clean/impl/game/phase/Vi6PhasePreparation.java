package fr.nekotine.vi6clean.impl.game.phase;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.eventargs.PhaseFailureEventArgs;
import fr.nekotine.core.map.MapModule;
import fr.nekotine.core.usable.Usable;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.Vi6Map;
import fr.nekotine.vi6clean.impl.wrapper.PreparationPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Vi6PhasePreparation extends CollectionPhase<Player> implements Listener{
	
	private List<ArmorStand> minimapEntrancesIndicator = new LinkedList<>();
	
	private Usable openMenuUsable;
	
	public Vi6PhasePreparation(Runnable onSuccess, Consumer<PhaseFailureEventArgs> onFailure, Supplier<Stream<Player>> source) {
		super(onSuccess, onFailure, source);
	}
	
	public Vi6PhasePreparation(Runnable onSuccess, Consumer<PhaseFailureEventArgs> onFailure) {
		super(onSuccess, onFailure);
	}
	
	public Vi6PhasePreparation(Runnable onSuccess) {
		super(onSuccess);
	}
	
	public Vi6PhasePreparation(Consumer<PhaseFailureEventArgs> onFailure) {
		super(onFailure);
	}
	
	@Override
	public void globalSetup() {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		var world = game.getWorld();
		var map = NekotineCore.MODULES.get(MapModule.class).getMapFinder().findByName(Vi6Map.class, game.getMapName()).loadConfig();
		game.setMap(map);
		map.getEntrances().backingMap().values().stream().map(e -> e.getMinimapPosition()).forEach(pos -> {
			var armorStand = (ArmorStand)world.spawnEntity(pos.toLocation(world), EntityType.ARMOR_STAND);
			armorStand.setVisible(false);
			armorStand.setInvulnerable(true);
			armorStand.setGravity(false);
			armorStand.setVisualFire(true);
			armorStand.addDisabledSlots(EquipmentSlot.CHEST,EquipmentSlot.FEET,EquipmentSlot.HAND,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFF_HAND);
			minimapEntrancesIndicator.add(armorStand);
		});
		game.getGuards().spawnInMap();
		game.getThiefs().spawnInMinimap();
		openMenuUsable = new Usable(ItemStackUtil.make(Material.NETHERITE_INGOT,
				Component.text("Magasin", NamedTextColor.GOLD),
				Component.text("Intéragir pour ouvrir le magasin", NamedTextColor.LIGHT_PURPLE))) {
			@Override
			protected void OnInteract(PlayerInteractEvent e) {
				var  wrapper = NekotineCore.MODULES.get(WrappingModule.class).getWrapper(e.getPlayer(), PreparationPhasePlayerWrapper.class);
				wrapper.getMenu().displayTo(e.getPlayer());
				e.setCancelled(true);
			}
			
			@Override
			protected void OnDrop(PlayerDropItemEvent e) {
				var  wrapper = NekotineCore.MODULES.get(WrappingModule.class).getWrapper(e.getPlayer(), PreparationPhasePlayerWrapper.class);
				wrapper.getMenu().displayTo(e.getPlayer());
				e.setCancelled(true);
			}
		}.register();
	}

	@Override
	public void globalTearDown() {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		game.setMap(null);
		for (var armorStand : minimapEntrancesIndicator) {
			armorStand.remove();
		}
		openMenuUsable.unregister();
	}

	@Override
	public void itemSetup(Player item) {
		var wrapper = new PreparationPhasePlayerWrapper(item);
		NekotineCore.MODULES.get(WrappingModule.class).putWrapper(item, wrapper);
		var inv = item.getInventory();
		inv.clear();
		inv.addItem(openMenuUsable.getItemStack());
		
	}

	@Override
	public void itemTearDown(Player item) {
		NekotineCore.MODULES.get(WrappingModule.class).removeWrapper(item, PreparationPhasePlayerWrapper.class);
		item.getInventory().clear();
	}
	
	public void checkForCompletion() {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		var wrappingModule = NekotineCore.MODULES.get(WrappingModule.class);
		if (game.all().allMatch(p -> wrappingModule.getWrapper(p, PreparationPhasePlayerWrapper.class).isReady())) {
			complete();
		}
	}

}
