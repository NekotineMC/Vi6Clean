package fr.nekotine.vi6clean.impl.tool.personal.pants;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.plugin.java.JavaPlugin;

import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.status.flag.StatusFlagModule;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.InventoryUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpEndEvent;
import fr.nekotine.vi6clean.impl.status.event.EntityEmpStartEvent;
import fr.nekotine.vi6clean.impl.status.flag.EmpStatusFlag;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

@ToolCode("pants")
public class PantsHandler extends ToolHandler<PantsHandler.Pants> {

	public static final NamespacedKey SNEAK_ATTRIBUTE_KEY = NamespacedKey.fromString("pants/sneaking_speed",
			Ioc.resolve(JavaPlugin.class));
	private final NamespacedKey ARMOR_REMOVE_ATTRIBUTE_KEY = NamespacedKey.fromString("pants/remove_armor",
			Ioc.resolve(JavaPlugin.class));

	private final double SNEAK_MULTIPLIER = getConfiguration().getDouble("sneak_multiplier", 1.5);
	private final double OFFSET = 0.65;

	public PantsHandler() {
		super(Pants::new);
	}

	@Override
	protected void onAttachedToPlayer(Pants tool) {
		var player = tool.getOwner();
		player.getAttribute(Attribute.SNEAKING_SPEED).addModifier(
				new AttributeModifier(SNEAK_ATTRIBUTE_KEY, SNEAK_MULTIPLIER - 1, Operation.MULTIPLY_SCALAR_1));

		var leggings = ItemStackUtil.make(Material.LEATHER_LEGGINGS, getDisplayName(), getLore());
		leggings.addEnchantment(Enchantment.BINDING_CURSE, 1);
		leggings.addItemFlags(ItemFlag.values());
		leggings.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes().addModifier(
				Attribute.ARMOR, new AttributeModifier(ARMOR_REMOVE_ATTRIBUTE_KEY, -1, Operation.MULTIPLY_SCALAR_1)));
		leggings.unsetData(DataComponentTypes.DAMAGE);
		leggings.unsetData(DataComponentTypes.MAX_DAMAGE);
		leggings.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
		player.getInventory().setLeggings(leggings);
	}

	@Override
	protected void onDetachFromPlayer(Pants tool) {
		var player = tool.getOwner();
		player.getAttribute(Attribute.SNEAKING_SPEED).removeModifier(SNEAK_ATTRIBUTE_KEY);
		player.getInventory().setLeggings(null);
		if (tool.crawling) {
			stand(tool);
		}
	}

	@Override
	protected void onToolCleanup(Pants tool) {
	}

	@EventHandler
	private void onPlayerInteract(PlayerInteractEvent evt) {
		var tool = getToolFromItem(evt.getItem());
		if (tool == null) {
			return;
		}
		var owner = tool.getOwner();
		if (Ioc.resolve(StatusFlagModule.class).hasAny(owner, EmpStatusFlag.get())) {
			return;
		}
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			if (tool.crawling) {
				stand(tool);
			} else {
				crawl(tool);
			}
		}
	}

	@EventHandler
	private void onEmpStart(EntityEmpStartEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.setData(DataComponentTypes.ITEM_MODEL, Material.CHAINMAIL_LEGGINGS.key());
				item.editMeta(m -> m.displayName(getDisplayName().decorate(TextDecoration.STRIKETHROUGH)
						.append(Component.text(" - ")).append(Component.text("Brouillé", NamedTextColor.RED))));
				var tool = getToolFromItem(item);
				if (tool.crawling) {
					stand(tool);
				}
				p.getAttribute(Attribute.SNEAKING_SPEED).removeModifier(SNEAK_ATTRIBUTE_KEY);
			});
		}
	}

	@EventHandler
	private void onEmpStop(EntityEmpEndEvent evt) {
		if (evt.getEntity() instanceof Player p) {
			InventoryUtil.taggedItems(p.getInventory(), TOOL_TYPE_KEY, getToolCode()).forEach(item -> {
				item.resetData(DataComponentTypes.ITEM_MODEL);
				item.editMeta(m -> m.displayName(getDisplayName()));
				p.getAttribute(Attribute.SNEAKING_SPEED).addModifier(
						new AttributeModifier(SNEAK_ATTRIBUTE_KEY, SNEAK_MULTIPLIER - 1, Operation.MULTIPLY_SCALAR_1));
			});
		}
	}

	private void crawl(Pants tool) {
		tool.crawling = true;
		var player = tool.getOwner();
		player.setSprinting(false);
		player.setPose(Pose.SWIMMING, true);

		editItem(tool, item -> {
			item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
			player.getInventory().getLeggings().setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		});

		var loc = player.getLocation();
		var nmsPlayer = ((CraftPlayer) player).getHandle();
		var connection = nmsPlayer.connection;
		@SuppressWarnings("deprecation")
		var displayId = Bukkit.getUnsafe().nextEntityId(player.getWorld());
		@SuppressWarnings("deprecation")
		var shulkerId = Bukkit.getUnsafe().nextEntityId(player.getWorld());

		tool.displayId = displayId;
		tool.shulkerId = shulkerId;

		// Spawn Display
		var spawnDisplayPacket = new ClientboundAddEntityPacket(displayId, UUID.randomUUID(), loc.getX(),
				loc.getY() + OFFSET, loc.getZ(), 0, 0, EntityTypes.ITEM_DISPLAY, 0, Vec3.ZERO, 0);

		// Spawn Shulker
		var spawnShulkerPacket = new ClientboundAddEntityPacket(shulkerId, UUID.randomUUID(), loc.getX(),
				loc.getY() + OFFSET, loc.getZ(), 0, 0, EntityTypes.SHULKER, 0, Vec3.ZERO, 0);

		// Metadata Display
		List<SynchedEntityData.DataValue<?>> dataDisplayValues = List.of(
				new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) 0x20), // Invisible
				new SynchedEntityData.DataValue<>(10, EntityDataSerializers.INT, 0) // teleport_duration
		);
		var metadataDisplayPacket = new ClientboundSetEntityDataPacket(displayId, dataDisplayValues);

		// Metadata Shulker
		List<SynchedEntityData.DataValue<?>> dataShulkerValues = List.of(
				new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) 0x20), // Invisible
				new SynchedEntityData.DataValue<>(4, EntityDataSerializers.BOOLEAN, true), // Silent
				new SynchedEntityData.DataValue<>(5, EntityDataSerializers.BOOLEAN, true), // No Gravity
				new SynchedEntityData.DataValue<>(15, EntityDataSerializers.BYTE, (byte) 0x01) // No AI
		);
		var metadataShulkerPacket = new ClientboundSetEntityDataPacket(shulkerId, dataShulkerValues);

		// Mount Shulker to Display
		ClientboundSetPassengersPacket passengerPacket;
		try {
			var constructor = ClientboundSetPassengersPacket.class.getDeclaredConstructor(FriendlyByteBuf.class);
			constructor.setAccessible(true);
			var buf = new FriendlyByteBuf(Unpooled.buffer());
			buf.writeVarInt(displayId);
			buf.writeVarIntArray(new int[]{shulkerId});
			passengerPacket = constructor.newInstance(buf);
		} catch (Exception e) {
			throw new RuntimeException("Could not create ClientboundSetPassengersPacket", e);
		}

		connection.send(spawnDisplayPacket);
		connection.send(spawnShulkerPacket);
		connection.send(metadataDisplayPacket);
		connection.send(metadataShulkerPacket);
		connection.send(passengerPacket);
	}

	private void stand(Pants tool) {
		tool.crawling = false;

		editItem(tool, item -> {
			item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
			tool.getOwner().getInventory().getLeggings().setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, false);
		});

		var player = tool.getOwner();
		player.setPose(Pose.STANDING);
		if (tool.shulkerId != -1 || tool.displayId != -1) {
			var nmsPlayer = ((CraftPlayer) player).getHandle();
			var connection = nmsPlayer.connection;
			var destroyPacket = new ClientboundRemoveEntitiesPacket(tool.shulkerId, tool.displayId);
			connection.send(destroyPacket);
			tool.shulkerId = -1;
			tool.displayId = -1;
		}
	}

	@EventHandler
	private void onPlayerMove(PlayerMoveEvent evt) {
		var player = evt.getPlayer();
		for (Pants tool : getTools()) {
			if (!player.equals(tool.getOwner()))
				continue;
			if (!tool.crawling || tool.displayId == -1) {
				break;
			}
			var to = evt.getTo();
			var nmsPlayer = ((CraftPlayer) player).getHandle();
			PositionMoveRotation movement = new PositionMoveRotation(new Vec3(to.getX(), to.getY() + OFFSET, to.getZ()),
					Vec3.ZERO, 0f, 0f);
			var teleportPacket = new ClientboundEntityPositionSyncPacket(tool.displayId, movement, false);
			nmsPlayer.connection.send(teleportPacket);
			break;
		}
	}

	public static class Pants extends Tool {
		private boolean crawling = false;
		private int shulkerId = -1;
		private int displayId = -1;

		public Pants(ToolHandler<?> handler) {
			super(handler);
		}
	}
}
