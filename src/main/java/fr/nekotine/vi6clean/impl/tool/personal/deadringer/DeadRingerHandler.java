package fr.nekotine.vi6clean.impl.tool.personal.deadringer;

import java.time.Duration;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.effect.StatusEffect;
import fr.nekotine.core.status.effect.StatusEffectModule;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.constant.Vi6Keys;
import fr.nekotine.vi6clean.constant.Vi6Sound;
import fr.nekotine.vi6clean.impl.status.effect.invisibility.TrueInvisibilityStatusEffectType;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import fr.nekotine.vi6clean.impl.wrapper.InMapPhasePlayerWrapper;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("deadringer")
public class DeadRingerHandler extends ToolHandler<DeadRingerHandler.DeadRinger> {

	private final int EXIT_DELAY_TICK = Tick.tick()
			.fromDuration(Duration.ofSeconds(getConfiguration().getInt("exit_delay_seconds", 10)));

	private final int INVISIBILITY_DURATION_TICK = Tick.tick()
			.fromDuration(Duration.ofSeconds(getConfiguration().getInt("invisibility_duration_seconds", 3)));

	private final StatusEffect invisibleEffect = new StatusEffect(TrueInvisibilityStatusEffectType.get(),
			INVISIBILITY_DURATION_TICK);

	public DeadRingerHandler() {
		super(DeadRinger::new);
	}

	@EventHandler
	public void onDamage(EntityDamageEvent e) {
		var entity = e.getEntity();
		if (!(entity instanceof Player player)) {
			return;
		}

		var statusFlagModule = Ioc.resolve(StatusFlagModule.class);
		if (statusFlagModule.hasAny(player, EmpStatusFlag.get())) {
			return;
		}

		if (e.getFinalDamage() >= player.getHealth()
				|| player.getInventory().getItemInMainHand().getPersistentDataContainer().get(TOOL_TYPE_KEY,
						PersistentDataType.STRING) == getToolCode()
				|| player.getInventory().getItemInOffHand().getPersistentDataContainer().get(TOOL_TYPE_KEY,
						PersistentDataType.STRING) == getToolCode()) { // Si les dégats sont mortels ou que l'item est
																		// équipé en main
			var toolsItems = InventoryUtil.taggedItems(player.getInventory(), TOOL_TYPE_KEY, getToolCode());
			toolsItems.stream().limit(1).forEach(item -> {
				var tool = getToolFromItem(item);
				var statusEffectModule = Ioc.resolve(StatusEffectModule.class);
				statusEffectModule.addEffect(player, invisibleEffect);
				new BukkitRunnable() {

					@Override
					public void run() {
						Vi6Sound.DEAD_RINGER_UNCLOAK.play(player.getLocation());
						remove(tool);
					}

				}.runTaskLater(Ioc.resolve(JavaPlugin.class), INVISIBILITY_DURATION_TICK);
				var wrapper = Ioc.resolve(WrappingModule.class).getWrapper(player, InMapPhasePlayerWrapper.class);
				wrapper.setCanLeaveMap(false);
				new BukkitRunnable() {

					@Override
					public void run() {
						wrapper.setCanLeaveMap(true);
					}

				}.runTaskLater(Ioc.resolve(JavaPlugin.class), EXIT_DELAY_TICK);
				e.setDamage(0.0001);
				player.getWorld().sendMessage(player.getCombatTracker().getDeathMessage());
			});
		}
	}

	@Override
	protected void onAttachedToPlayer(DeadRinger tool) {
	}

	@Override
	protected void onDetachFromPlayer(DeadRinger tool) {
	}

	@Override
	protected void onToolCleanup(DeadRinger tool) {
	}

	@Override
	protected ItemStack makeBaseItem() {
		return new ItemStackBuilder(Material.CLOCK).name(getDisplayName()).lore(getLore()).unstackable()
				.flags(ItemFlag.values()).postApply(i -> {
					i.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(EquipmentSlot.HAND)
							.equipSound(Key.key("vi6clean:tool.dead_ringer.equip")));
					i.setData(DataComponentTypes.ITEM_MODEL, Key.key(Vi6Keys.DEAD_RINGER_ITEM_MODEL));
				}).build();
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.editMeta(m -> m.displayName(getDisplayName()));
			});
		}
	}

	public static class DeadRinger extends Tool {

		public DeadRinger(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
