package fr.nekotine.vi6clean.impl.game.phase;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.NekotineCore;
import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.state.ItemState;
import fr.nekotine.core.state.ItemWrappingState;
import fr.nekotine.core.state.RegisteredEventListenerState;
import fr.nekotine.core.state.State;
import fr.nekotine.core.usable.Usable;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.Vi6Main;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.Entrance;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.PreparationPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class Vi6PhasePreparation extends CollectionPhase<Vi6PhaseInMap,Player> implements Listener{
	
	private final ItemStack guardSword = new ItemStackBuilder(Material.DIAMOND_SWORD)
			.name(Component.text("Épée de garde", NamedTextColor.GOLD))
			.unbreakable()
			.oldPvp()
			.flags(ItemFlag.values())
			.build();
	
	private Map<ArmorStand, Entrance> minimapEntrancesIndicator = new HashMap<>();
	
	private Usable openMenuUsable;
	
	public Vi6PhasePreparation(IPhaseMachine machine) {
		super(machine);
	}
	
	@Override
	public Class<Vi6PhaseInMap> getParentType() {
		return Vi6PhaseInMap.class;
	}

	@Override
	public ObservableCollection<Player> getItemCollection() {
		return Vi6Main.IOC.resolve(Vi6Game.class).getPlayerList();
	}
	
	@Override
	public void globalSetup(Object inputData) {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		var world = game.getWorld();
		var map = getParent().getMap();
		minimapEntrancesIndicator.clear();
		map.getEntrances().backingMap().values().stream().forEach(entrance -> {
			var pos = entrance.getMinimapPosition();
			var armorStand = (ArmorStand)world.spawnEntity(pos.toLocation(world), EntityType.ARMOR_STAND);
			armorStand.setVisible(false);
			armorStand.setInvulnerable(true);
			armorStand.setGravity(false);
			armorStand.setVisualFire(true);
			armorStand.addDisabledSlots(EquipmentSlot.CHEST,EquipmentSlot.FEET,EquipmentSlot.HAND,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFF_HAND);
			minimapEntrancesIndicator.put(armorStand, entrance);
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
		for (var armorStand : minimapEntrancesIndicator.keySet()) {
			armorStand.remove();
		}
		minimapEntrancesIndicator.clear();
		openMenuUsable.unregister();
	}

	@Override
	public void itemSetup(Player item) {
		var wrap = NekotineCore.MODULES.get(WrappingModule.class).getWrapper(item, PlayerWrapper.class);
		var inv = item.getInventory();
		inv.clear();
		inv.addItem(openMenuUsable.getItemStack());
		if (!wrap.isGuard()) {
			inv.addItem(guardSword);
		}
	}

	@Override
	public void itemTearDown(Player item) {
		item.getInventory().remove(openMenuUsable.getItemStack());
	}
	
	@Override
	protected Object handleComplete() {
		var wrappingModule = NekotineCore.MODULES.get(WrappingModule.class);
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		return game.getThiefs().stream()
				.peek(p -> {
					var wrap = wrappingModule.getWrapper(p, PreparationPhasePlayerWrapper.class);
					if (wrap.getSelectedEntrance() == null) {
						wrap.setSelectedEntrance(randomEntrance());
					}
				})
				.collect(Collectors.toMap(p -> p,
				p -> wrappingModule.getWrapper(p, PreparationPhasePlayerWrapper.class).getSelectedEntrance()));
	}
	
	private Entrance randomEntrance() {
		var rand = new Random();
		var entrances = getParent().getMap().getEntrances().backingMap().values();
		return entrances.stream().skip(rand.nextInt(entrances.size()+1)-1).findFirst().orElse(null);
	}
	
	@Override
	protected List<ItemState<Player>> makeAppliedItemStates() {
		var list = new LinkedList<ItemState<Player>>();
		list.add(new ItemWrappingState<>(PreparationPhasePlayerWrapper::new));
		return list;
	}
	
	@Override
	protected List<State> makeAppliedStates() {
		var list = new LinkedList<State>();
		list.add(new RegisteredEventListenerState(this));
		return list;
	}
	
	public void checkForCompletion() {
		var game = Vi6Main.IOC.resolve(Vi6Game.class);
		var wrappingModule = NekotineCore.MODULES.get(WrappingModule.class);
		if (game.getPlayerList().stream().allMatch(p -> wrappingModule.getWrapper(p, PreparationPhasePlayerWrapper.class).isReadyForNextPhase())) {
			complete();
		}
	}
	
	@EventHandler
	public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent e) {
		var entrance = minimapEntrancesIndicator.get(e.getRightClicked());
		if (entrance != null) {
			var wrapper = NekotineCore.MODULES.get(WrappingModule.class).getWrapper(e.getPlayer(), PreparationPhasePlayerWrapper.class);
			wrapper.setSelectedEntrance(entrance);
		}
	}

}
