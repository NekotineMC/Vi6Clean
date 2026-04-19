package fr.nekotine.vi6clean.impl.game.phase;

import fr.nekotine.core.game.phase.CollectionPhase;
import fr.nekotine.core.game.phase.IPhaseMachine;
import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.module.ModuleManager;
import fr.nekotine.core.state.ItemState;
import fr.nekotine.core.state.ItemWrappingState;
import fr.nekotine.core.state.RegisteredEventListenerState;
import fr.nekotine.core.state.State;
import fr.nekotine.core.ticking.TickTimeStamp;
import fr.nekotine.core.ticking.TickingModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.usable.Usable;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.util.collection.ObservableCollection;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.map.ThiefSpawn;
import fr.nekotine.vi6clean.impl.wrapper.PlayerWrapper;
import fr.nekotine.vi6clean.impl.wrapper.PreparationPhasePlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.AttackRange;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.PiercingWeapon;
import io.papermc.paper.datacomponent.item.SwingAnimation;
import io.papermc.paper.datacomponent.item.SwingAnimation.Animation;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.util.TriState;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Vi6PhasePreparation extends CollectionPhase<Vi6PhaseInMap, Player> implements Listener {
	private final ItemStack guardSword = new ItemStackBuilder(Material.DIAMOND_SWORD)
			.name(Component.text("Épée de garde", NamedTextColor.GOLD)).unbreakable().oldPvp().flags(
					ItemFlag.values())
			.attackDamage(5).postApply(i -> i.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable() // we do a
					// little
					// bit
					// of
					// trolling
					.addEffect(ConsumeEffect
							.applyStatusEffects(List.of(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 1),
									new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0)), 1))))
			.build();

	private final ItemStack guardGun = new ItemStackBuilder(Material.STONE_AXE)
			.name(Component.text("Flashball de garde", NamedTextColor.GOLD)).unbreakable().flags(ItemFlag.values())
			.attackDamage(3)
			.attributeModifier(Attribute.ATTACK_SPEED,
					new AttributeModifier(NamespacedKey.fromString("gun_cooldown", Ioc.resolve(JavaPlugin.class)), -3.4,
							Operation.ADD_NUMBER)) // Default is 4, go to 0.6
			.postApply(i -> {
				i.setData(DataComponentTypes.ATTACK_RANGE,
						AttackRange.attackRange().maxReach(64).maxCreativeReach(64).build());
				i.setData(DataComponentTypes.MINIMUM_ATTACK_CHARGE, 1f);
				i.setData(DataComponentTypes.SWING_ANIMATION,
						SwingAnimation.swingAnimation().type(Animation.NONE).build());
				i.setData(DataComponentTypes.PIERCING_WEAPON,
						PiercingWeapon.piercingWeapon().dealsKnockback(true).dismounts(true)
								.sound(NamespacedKey.minecraft("entity.iron_golem.repair"))
								.hitSound(NamespacedKey.minecraft("entity.villager.celebrate")) // a voir pour changer
								.build());
			}).build();

	private Map<ArmorStand, ThiefSpawn> minimapSpawnIndicators = new HashMap<>();

	private Usable openMenuUsable;

	private final BossBar bossbar = BossBar.bossBar(
			Component.text("Préparation", NamedTextColor.AQUA).append(Component.text(" - ", NamedTextColor.WHITE))
					.append(Component.text("Mettez vous prêts", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC)),
			0, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS);

	private final int PREPARATION_DURATION_MAX_TICKS;
	private int preparationDurationTicks = 0;

	public Vi6PhasePreparation(IPhaseMachine machine) {
		super(machine);
		Ioc.resolve(ModuleManager.class).tryLoad(TickingModule.class);
		PREPARATION_DURATION_MAX_TICKS = 20
				* Ioc.resolve(Configuration.class).getInt("game_preparation_duration_seconds", 5 * 60);
	}

	@Override
	public Class<Vi6PhaseInMap> getParentType() {
		return Vi6PhaseInMap.class;
	}

	@Override
	public ObservableCollection<Player> getItemCollection() {
		return Ioc.resolve(Vi6Game.class).getPlayerList();
	}

	@Override
	public void globalSetup(Object inputData) {
		var game = Ioc.resolve(Vi6Game.class);
		var world = game.getWorld();
		var map = getParent().getMap();
		minimapSpawnIndicators.clear();
		map.getThiefSpawns().values().stream().forEach(entrance -> {
			var pos = entrance.getMinimapPosition();
			var armorStand = (ArmorStand) world.spawnEntity(pos.toLocation(world), EntityType.ARMOR_STAND,
					SpawnReason.CUSTOM, e -> {
						if (e instanceof ArmorStand stand) {
							stand.setPersistent(false);
							stand.setVisible(false);
							stand.setInvulnerable(true);
							stand.setGravity(false);
							stand.setVisualFire(TriState.TRUE);
							stand.addDisabledSlots(EquipmentSlot.CHEST, EquipmentSlot.FEET, EquipmentSlot.HAND,
									EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFF_HAND);
						}
					});
			minimapSpawnIndicators.put(armorStand, entrance);
		});
		game.getGuards().spawnInMap();
		game.getThiefs().spawnInMinimap();
		openMenuUsable = new Usable(
				ItemStackUtil.make(Material.NETHERITE_INGOT, Component.text("Magasin", NamedTextColor.GOLD),
						Component.text("Intéragir pour ouvrir le magasin", NamedTextColor.LIGHT_PURPLE))) {
			@Override
			protected void OnInteract(PlayerInteractEvent e) {
				var wrapper = Ioc.resolve(WrappingModule.class).getWrapper(e.getPlayer(),
						PreparationPhasePlayerWrapper.class);
				wrapper.getMenu().displayTo(e.getPlayer());
				e.setCancelled(true);
			}

			@Override
			protected void OnDrop(PlayerDropItemEvent e) {
				var wrapper = Ioc.resolve(WrappingModule.class).getWrapper(e.getPlayer(),
						PreparationPhasePlayerWrapper.class);
				wrapper.getMenu().displayTo(e.getPlayer());
				e.setCancelled(true);
			}
		}.register();
		game.getPlayerList().forEach(p -> p.showBossBar(bossbar));
	}

	@Override
	public void globalTearDown() {
		for (var armorStand : minimapSpawnIndicators.keySet()) {
			armorStand.remove();
		}
		var game = Ioc.resolve(Vi6Game.class);
		game.getPlayerList().forEach(p -> {
			p.closeInventory();
			p.hideBossBar(bossbar);
		});
		minimapSpawnIndicators.clear();
		openMenuUsable.unregister();
	}

	@Override
	public void itemSetup(Player item) {
		var wrap = Ioc.resolve(WrappingModule.class).getWrapper(item, PlayerWrapper.class);
		var inv = item.getInventory();
		inv.clear();
		inv.setItem(8, openMenuUsable.getItemStack());
		if (wrap.isGuard()) {
			inv.addItem(guardSword);
			inv.addItem(guardGun);
		}
	}

	@Override
	public void itemTearDown(Player item) {
		item.getInventory().remove(openMenuUsable.getItemStack());
		item.setAllowFlight(false);
		item.setFlying(false);
	}

	@Override
	protected Object handleComplete() {
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		var game = Ioc.resolve(Vi6Game.class);
		return game.getThiefs().stream().peek(p -> {
			var wrap = wrappingModule.getWrapper(p, PreparationPhasePlayerWrapper.class);
			if (wrap.getSelectedSpawn() == null) {
				wrap.setSelectedSpawn(randomSpawn());
			}
		}).collect(Collectors.toMap(p -> p,
				p -> wrappingModule.getWrapper(p, PreparationPhasePlayerWrapper.class).getSelectedSpawn()));
	}

	private ThiefSpawn randomSpawn() {
		var rand = new Random();
		var spawns = getParent().getMap().getThiefSpawns().values();
		return spawns.stream().skip(rand.nextInt(spawns.size() + 1) - 1).findFirst().orElse(null);
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
		var game = Ioc.resolve(Vi6Game.class);
		var wrappingModule = Ioc.resolve(WrappingModule.class);
		if (game.getPlayerList().stream().allMatch(
				p -> wrappingModule.getWrapper(p, PreparationPhasePlayerWrapper.class).isReadyForNextPhase())) {
			complete();
		}
	}

	@EventHandler
	private void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent e) {
		var entrance = minimapSpawnIndicators.get(e.getRightClicked());
		if (entrance != null) {
			var wrapper = Ioc.resolve(WrappingModule.class).getWrapper(e.getPlayer(),
					PreparationPhasePlayerWrapper.class);
			wrapper.setSelectedSpawn(entrance);
		}
	}

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		preparationDurationTicks++;
		if (preparationDurationTicks >= PREPARATION_DURATION_MAX_TICKS) {
			complete();
		}
		if (evt.timeStampReached(TickTimeStamp.Second)) {
			bossbar.progress((float) preparationDurationTicks / PREPARATION_DURATION_MAX_TICKS);
		}
	}
}
