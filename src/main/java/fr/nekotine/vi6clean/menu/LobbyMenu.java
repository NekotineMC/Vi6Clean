package fr.nekotine.vi6clean.menu;

import java.time.Duration;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import fr.nekotine.core.usable.Usable;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.vi6clean.game.Vi6Team;
import fr.nekotine.vi6clean.game.phase.lobby.LobbyPhase;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class LobbyMenu {

	public static Usable openMenuItem(LobbyPhase lobbyPhase) {
		return new Usable(
				ItemStackUtil.make(Material.BEACON, Component.text("Menu Vi6", NamedTextColor.GOLD))) {
			@Override
			protected void OnInteract(PlayerInteractEvent e) {
				if (e.getHand() != EquipmentSlot.HAND) {
					return;
				}
				var player = e.getPlayer();
				var dialog = LobbyMenu.dialogForPlayer(player,lobbyPhase);
				player.showDialog(dialog);
				e.setCancelled(true);
			}

			@Override
			protected void OnDrop(PlayerDropItemEvent e) {
				var player = e.getPlayer();
				var dialog = LobbyMenu.dialogForPlayer(player,lobbyPhase);
				player.showDialog(dialog);
				e.setCancelled(true);
			}
		};
	}
	
	public static Dialog dialogForPlayer(Player player, LobbyPhase lobbyPhase) {
		var wrapper = lobbyPhase.wrapperFor(player);
		return Dialog.create(builder -> builder.empty().base(DialogBase
				.builder(MiniMessage.miniMessage()
						.deserialize("<red>V<dark_aqua>oleur <red>I<dark_aqua>ndustriel <red>6"))
				.canCloseWithEscape(true).pause(false)
				.inputs(List.of(
						DialogInput
								.singleOption("team", Component.text("Équipe"), List.of(
										SingleOptionDialogInput.OptionEntry.create("guard",
												Component.text(Vi6Team.Guard.name(), NamedTextColor.BLUE), wrapper.team() == Vi6Team.Guard),
										SingleOptionDialogInput.OptionEntry.create("thief",
												Component.text(Vi6Team.Thief.name(), NamedTextColor.RED), wrapper.team() == Vi6Team.Thief)))
								.build(),
						DialogInput.bool("ready", Component.text("Je suis prêt")).initial(wrapper.isReady())
								.build()))
				.build())
				.type(DialogType.confirmation(ActionButton.builder(Component.text("Enregistrer", NamedTextColor.GREEN))
						.action(DialogAction.customClick((response, _) -> {
							wrapper.team(Vi6Team.valueOf(response.getText("team")));
							wrapper.setReady(response.getBoolean("ready"));
						}, ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build())).build(),
						ActionButton.builder(Component.text("Annuler", NamedTextColor.RED)).build())));
	}
	
}
