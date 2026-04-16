package fr.nekotine.vi6clean.impl.tool.personal.portable_teleporter;

import com.comphenix.protocol.wrappers.EnumWrappers;
import fr.nekotine.core.glow.EntityGlowModule;
import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.ticking.event.TickElapsedEvent;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.SpatialUtil;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.type.StructureBlock;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

@ToolCode("portable_teleporter")
public class PortableTeleporterHandler extends ToolHandler<PortableTeleporterHandler.PortableTeleporter> {

	private static final float CARPET_SIZE = 0.06f;

	private final int CHARGES = getConfiguration().getInt("teleportation_charges", 3);

	private final int DELAY_TICK = (int) (20 * getConfiguration().getDouble("teleportation_delay", 4));

	private final int COOLDOWN_TICK = (int) (20 * getConfiguration().getDouble("teleportation_cooldown", 10));

	private final int VFX_DELAY_TICK = getConfiguration().getInt("vfx_delay", 5);

	private final List<Consumer<Location>> PLAYER_VFX_1 = new ArrayList<>();
	private final List<Consumer<Location>> PLAYER_VFX_2 = new ArrayList<>();
	private final List<Consumer<Location>> PAD_VFX_1 = new ArrayList<>();
	private final List<Consumer<Location>> PAD_VFX_2 = new ArrayList<>();

	public PortableTeleporterHandler() {
		super(PortableTeleporter::new);
		SpatialUtil.helix(3, 0.75, 3, 0, 0.25, v -> PLAYER_VFX_1
				.add(l -> l.getWorld().spawnParticle(Particle.GLOW, l.clone().add(v), 1, 0, 0, 0, 0, null)));
		SpatialUtil.helix(3, 0.75, 3, Math.PI, 0.25, v -> PLAYER_VFX_2
				.add(l -> l.getWorld().spawnParticle(Particle.GLOW, l.clone().add(v), 1, 0, 0, 0, 0, null)));
		SpatialUtil.helix(3, 0.75, 3, 0, 0.25, v -> PAD_VFX_1
				.add(l -> l.getWorld().spawnParticle(Particle.GLOW, l.clone().add(v), 1, 0, 0, 0, 0, null)));
		SpatialUtil.helix(3, 0.75, 3, Math.PI, 0.25, v -> PAD_VFX_2
				.add(l -> l.getWorld().spawnParticle(Particle.GLOW, l.clone().add(v), 1, 0, 0, 0, 0, null)));
	}

	//

	@EventHandler
	private void onTick(TickElapsedEvent evt) {
		for (var tool : getTools()) {
			var owner = tool.getOwner();
			var toolWielded = owner != null && (itemMatch(tool, owner.getInventory().getItemInMainHand())
					|| itemMatch(tool, owner.getInventory().getItemInOffHand()));

			tool.aimed = toolWielded ? tool.pads.stream().filter(pad -> {
				var eyeLoc = owner.getEyeLocation();
				var start = eyeLoc.toVector();
				var dir = eyeLoc.getDirection();

				var bb = pad.display.getBoundingBox();
				var scale = pad.display.getTransformation().getScale();
				var x1 = bb.getMinX();
				var y1 = bb.getMinY();
				var z1 = bb.getMinZ();

				return bb.resize(x1 - 0.25, y1 - 0.5, z1 - 0.25, x1 + scale.x + 0.25, y1 + 0.5, z1 + scale.z + 0.25)
						.rayTrace(start, dir, 100.0) != null;
			}).min((o1, o2) -> (int) (o1.display.getLocation().distanceSquared(owner.getLocation())
					- o2.display.getLocation().distanceSquared(owner.getLocation()))).orElse(null) : null;

			if (tool.teleporting && tool.aimed != null && owner != null) {
				if (--tool.teleportationDelayTick <= 0) {
					tool.teleporting = false;
					owner.teleport(tool.aimed.display.getLocation().add(0.5, 0, 0.5));
					owner.setCooldown(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, COOLDOWN_TICK);
					owner.setAllowFlight(false);
					owner.setFlying(false);
					owner.setFlySpeed(0.1F);
				}
				if (--tool.vfxDelayTick == 0) {

					var vfxMin = Math.min(PLAYER_VFX_1.size(), PAD_VFX_1.size());
					var vfxCount = Math
							.ceil(((double) (DELAY_TICK - tool.teleportationDelayTick) / DELAY_TICK) * vfxMin);
					var disLoc = tool.aimed.display.getLocation().add(0.5, 0, 0.5);
					for (int i = 0; i < vfxCount; i++) {

						var otherEndIndex = vfxMin - i - 1;
						PAD_VFX_1.get(otherEndIndex).accept(disLoc);
						PAD_VFX_2.get(otherEndIndex).accept(disLoc);

						var ownLoc = owner.getLocation();
						PLAYER_VFX_1.get(i).accept(ownLoc);
						PLAYER_VFX_2.get(i).accept(ownLoc);
					}
					tool.vfxDelayTick = VFX_DELAY_TICK;
				}
			}

			for (var pad : tool.pads) {
				if (toolWielded) {
					Ioc.resolve(EntityGlowModule.class).glowEntityFor(pad.display, owner,
							pad.equals(tool.aimed)
									? EnumWrappers.ChatFormatting.AQUA
									: EnumWrappers.ChatFormatting.DARK_PURPLE);
				} else {
					Ioc.resolve(EntityGlowModule.class).unglowEntityFor(pad.display, owner);
				}
			}
		}
	}

	@EventHandler
	private void onPlayerInterract(PlayerInteractEvent evt) {
		if (evt.getHand() != EquipmentSlot.HAND || !EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			return;
		}
		var player = evt.getPlayer();
		if (Ioc.resolve(StatusFlagModule.class).hasAny(player, EmpStatusFlag.get())) {
			return;
		}
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}

		if (player.isSneaking()) {
			// TRY PLACE
			var ploc = player.getLocation();
			if (tool.pads.size() >= CHARGES || !ploc.clone().subtract(0, 0.1, 0).getBlock().isSolid()) {
				return;
			}
			var newpad = new PortableTeleporterHandler.PortableTeleporter.TeleportationPad();
			var newloc = ploc.toVector().toLocation(ploc.getWorld(),player.getEyeLocation().getYaw(),0);
			newpad.display = (BlockDisplay) newloc.getWorld().spawnEntity(newloc, EntityType.BLOCK_DISPLAY,
					SpawnReason.CUSTOM, e -> {
						if (e instanceof BlockDisplay dis) {
							var trans = dis.getTransformation();
							var scale = trans.getScale();
							scale.set(1, CARPET_SIZE, 1);
							dis.setTransformation(trans);
							var bd = (StructureBlock) Material.STRUCTURE_BLOCK.createBlockData();
							bd.setMode(StructureBlock.Mode.CORNER);
							dis.setBlock(bd);
						}
					});
			tool.pads.add(newpad);
			editItem(tool, item -> item.editMeta(meta -> meta.displayName(getDisplayName().append(Component
					.text(" [", NamedTextColor.WHITE).append(Component.text(tool.pads.size(), NamedTextColor.GREEN))
					.append(Component.text("/", NamedTextColor.WHITE))
					.append(Component.text(CHARGES, NamedTextColor.GREEN))
					.append(Component.text("]", NamedTextColor.WHITE))))));
			evt.setCancelled(true);
		} else if (!tool.getOwner().hasCooldown(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE)) {
			// TRY TELEPORT

			if (tool.aimed == null || tool.teleporting) {
				return;
			}

			tool.teleporting = true;
			tool.teleportationDelayTick = DELAY_TICK;
			tool.vfxDelayTick = VFX_DELAY_TICK;

			player.setAllowFlight(true);
			player.teleport(player.getLocation().add(0, 0.1, 0));
			player.setFlying(true);
			player.setFlySpeed(0);
			player.setVelocity(new Vector(0, 0, 0));

			player.setCooldown(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, DELAY_TICK);
			evt.setCancelled(true);
		}
	}

	//

	protected ItemStack getItem(int amount) {
		var amountcpnt = Component.text(" [", NamedTextColor.WHITE).append(Component.text(amount, NamedTextColor.GREEN))
				.append(Component.text("/", NamedTextColor.WHITE)).append(Component.text(CHARGES, NamedTextColor.GREEN))
				.append(Component.text("]", NamedTextColor.WHITE));
		return new ItemStackBuilder(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE).lore(getLore())
				.name(getDisplayName().append(amountcpnt)).flags(ItemFlag.values()).build();
	}

	@Override
	protected void onAttachedToPlayer(PortableTeleporter tool) {
	}

	@Override
	protected void onDetachFromPlayer(PortableTeleporter tool) {
	}

	@Override
	protected void onToolCleanup(PortableTeleporter tool) {
		for (var pad : tool.pads) {
			pad.display.remove();
		}
		tool.pads.clear();
	}

	@Override
	protected ItemStack makeItem(PortableTeleporter tool) {
		return new ItemStackBuilder(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE).lore(getLore())
				.name(getDisplayName().append(
						Component.text(" [", NamedTextColor.WHITE).append(Component.text(CHARGES, NamedTextColor.GREEN))
								.append(Component.text("/", NamedTextColor.WHITE))
								.append(Component.text(CHARGES, NamedTextColor.GREEN))
								.append(Component.text("]", NamedTextColor.WHITE))))
				.flags(ItemFlag.values()).build();
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				var tool = getToolFromItem(item);
				item.resetData(DataComponentTypes.ITEM_MODEL); // back to default model
				item.editMeta(m -> m.displayName(getDisplayName().append(Component.text(" [", NamedTextColor.WHITE)
						.append(Component.text(CHARGES - tool.pads.size(), NamedTextColor.GREEN))
						.append(Component.text("/", NamedTextColor.WHITE))
						.append(Component.text(CHARGES, NamedTextColor.GREEN))
						.append(Component.text("]", NamedTextColor.WHITE)))));
			});
		}
	}

	public static class PortableTeleporter extends Tool {

		public PortableTeleporter(ToolHandler<?> handler) {
			super(handler);
		}

		private final ArrayList<TeleportationPad> pads = new ArrayList<>();

		private TeleportationPad aimed;

		private boolean teleporting;

		private int teleportationDelayTick;

		private int vfxDelayTick;

		private static class TeleportationPad {

			private BlockDisplay display;
		}
	}
}
