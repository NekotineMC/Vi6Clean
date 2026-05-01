package fr.nekotine.vi6clean.impl.tool.personal.pants;

import java.util.ArrayList;
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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.util.CustomAction;
import fr.nekotine.core.util.EventUtil;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.impl.tool.Tool;
import fr.nekotine.vi6clean.impl.tool.ToolCode;
import fr.nekotine.vi6clean.impl.tool.ToolHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

@ToolCode("pants")
public class PantsHandler extends ToolHandler<PantsHandler.Pants> {

	public static final NamespacedKey SNEAK_ATTRIBUTE_KEY = NamespacedKey.fromString("pants/sneaking_speed",
			Ioc.resolve(JavaPlugin.class));
	private final NamespacedKey ARMOR_REMOVE_ATTRIBUTE_KEY = NamespacedKey.fromString("pants/remove_armor",
			Ioc.resolve(JavaPlugin.class));

	private final double SNEAK_MULTIPLIER = getConfiguration().getDouble("sneak_multiplier", 2);

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
		if (EventUtil.isCustomAction(evt, CustomAction.HIT_ANY)) {
			if (tool.crawling) {
				stand(tool);
			} else {
				crawl(tool);
			}
		}
	}

	private void crawl(Pants tool) {
		tool.crawling = true;
		tool.getOwner().setSprinting(false);
		tool.getOwner().setPose(Pose.SWIMMING, true);

		editItem(tool, item -> {
			item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
			tool.getOwner().getInventory().getLeggings().setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		});

		var player = tool.getOwner();
		var loc = player.getLocation();
		var pmanager = ProtocolLibrary.getProtocolManager();
		@SuppressWarnings("deprecation")
		var shulkerId = Bukkit.getUnsafe().nextEntityId();
		@SuppressWarnings("deprecation")
		var displayId = Bukkit.getUnsafe().nextEntityId();

		tool.shulkerId = shulkerId;
		tool.displayId = displayId;

		// Spawn Display
		var spawnDisplayPacket = pmanager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
		spawnDisplayPacket.getIntegers().write(0, displayId);
		spawnDisplayPacket.getUUIDs().write(0, UUID.randomUUID());
		spawnDisplayPacket.getEntityTypeModifier().write(0, EntityType.ITEM_DISPLAY);
		spawnDisplayPacket.getDoubles().write(0, loc.getX()).write(1, loc.getY()).write(2, loc.getZ());

		// Spawn Shulker
		var spawnShulkerPacket = pmanager.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
		spawnShulkerPacket.getIntegers().write(0, shulkerId);
		spawnShulkerPacket.getUUIDs().write(0, UUID.randomUUID());
		spawnShulkerPacket.getEntityTypeModifier().write(0, EntityType.SHULKER);
		spawnShulkerPacket.getDoubles().write(0, loc.getX()).write(1, loc.getY()).write(2, loc.getZ());

		var byteSerializer = WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Byte.class);
		var boolSerializer = WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Boolean.class);
		var intSerializer = WrappedDataWatcher.Registry.get((java.lang.reflect.Type) Integer.class);

		// Metadata As
		var metadataDisplayPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
		metadataDisplayPacket.getIntegers().write(0, displayId);
		var dataDisplayValues = new ArrayList<WrappedDataValue>();
		dataDisplayValues.add(new WrappedDataValue(0, byteSerializer, (byte) 0x20)); // Invisible
		dataDisplayValues.add(new WrappedDataValue(4, boolSerializer, true)); // Silent
		dataDisplayValues.add(new WrappedDataValue(5, boolSerializer, true)); // No Gravity
		dataDisplayValues.add(new WrappedDataValue(10, intSerializer, 0));
		metadataDisplayPacket.getDataValueCollectionModifier().write(0, dataDisplayValues);

		// Metadata Shulker
		var metadataShulkerPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
		metadataShulkerPacket.getIntegers().write(0, shulkerId);
		var dataShulkerValues = new ArrayList<WrappedDataValue>();
		dataShulkerValues.add(new WrappedDataValue(0, byteSerializer, (byte) 0x20)); // Invisible
		dataShulkerValues.add(new WrappedDataValue(4, boolSerializer, true)); // Silent
		dataShulkerValues.add(new WrappedDataValue(5, boolSerializer, true)); // No Gravity
		dataShulkerValues.add(new WrappedDataValue(15, byteSerializer, (byte) 0x01)); // No AI
		metadataShulkerPacket.getDataValueCollectionModifier().write(0, dataShulkerValues);

		var passengerPacket = pmanager.createPacket(PacketType.Play.Server.MOUNT);
		passengerPacket.getIntegers().write(0, displayId);
		passengerPacket.getIntegerArrays().write(0, new int[]{shulkerId});

		pmanager.sendServerPacket(player, spawnDisplayPacket);
		pmanager.sendServerPacket(player, spawnShulkerPacket);
		pmanager.sendServerPacket(player, metadataDisplayPacket);
		pmanager.sendServerPacket(player, metadataShulkerPacket);
		pmanager.sendServerPacket(player, passengerPacket);
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
			var pmanager = ProtocolLibrary.getProtocolManager();
			var destroyPacket = pmanager.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
			destroyPacket.getIntLists().write(0, List.of(tool.shulkerId, tool.displayId));
			pmanager.sendServerPacket(player, destroyPacket);
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
			PositionMoveRotation movement = new PositionMoveRotation(new Vec3(to.getX(), to.getY(), to.getZ()),
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
