package fr.nekotine.vi6clean.impl.wrapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.ItemStackBuilder;
import fr.nekotine.core.inventory.menu.MenuInventory;
import fr.nekotine.core.inventory.menu.element.BooleanInputMenuItem;
import fr.nekotine.core.inventory.menu.element.ClickableDisplayMenuItem;
import fr.nekotine.core.inventory.menu.layout.BorderMenuLayout;
import fr.nekotine.core.inventory.menu.layout.WrapMenuLayout;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.map.MapModule;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrapperBase;
import fr.nekotine.core.wrapper.WrappingModule;
import fr.nekotine.vi6clean.impl.game.Vi6Game;
import fr.nekotine.vi6clean.impl.game.phase.Vi6PhaseLobby;
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

public class LobbyPhasePlayerWrapper extends WrapperBase<Player> {

	private MenuInventory menu;

	private boolean readyForNextPhase;

	public LobbyPhasePlayerWrapper(Player wrapped) {
		super(wrapped);
		var game = Ioc.resolve(Vi6Game.class);
		var playerWrapper = getParentWrapper();
		var changeTeamItem = new BooleanInputMenuItem(
				ItemStackUtil.make(Material.RED_BANNER, Component.text("Voleur", NamedTextColor.RED)),
				ItemStackUtil.make(Material.BLUE_BANNER, Component.text("Garde", NamedTextColor.BLUE)),
				playerWrapper::isThief, (isThief) -> {
					if (isThief) {
						game.addPlayerInThiefs(wrapped);
					} else {
						game.addPlayerInGuards(wrapped);
					}
				});
		var readyItem = new BooleanInputMenuItem(
				ItemStackUtil.make(Material.EMERALD_BLOCK, Component.text("Prêt", NamedTextColor.GREEN)),
				ItemStackUtil.make(Material.REDSTONE_BLOCK, Component.text("En attente", NamedTextColor.RED)),
				this::isReadyForNextPhase, this::setReadyForNextPhase);
		var changeMapItem = new ClickableDisplayMenuItem(new ItemStack(Material.GRASS_BLOCK), () -> {
			return Component.text("Carte: " + Ioc.resolve(Vi6Game.class).getMapName());
		}, _ -> {
			var mapname = game.getMapName();
			var mm = Ioc.resolve(MapModule.class);
			var maps = new ArrayList<>(mm.listMaps());
			var current = maps.indexOf(maps.stream().filter(m -> m.getName().equals(mapname)).findFirst().orElse(null));
			if (current >= maps.size() - 1) {
				game.setMapName(maps.getFirst().getName());
			} else {
				game.setMapName(maps.get(current + 1).getName());
			}
		});
		var debugItem = new BooleanInputMenuItem(
				new ItemStackBuilder(Material.GOLDEN_PICKAXE).flags(ItemFlag.values()).enchant()
						.name(Component.text("Débug activé", NamedTextColor.YELLOW)).build(),
				ItemStackUtil.make(Material.GOLDEN_PICKAXE, Component.text("Débug désactivé", NamedTextColor.GRAY)),
				game::isDebug, game::setDebug);
		var wrapLayout = new WrapMenuLayout();
		wrapLayout.addElement(readyItem);
		wrapLayout.addElement(changeTeamItem);
		wrapLayout.addElement(changeMapItem);
		wrapLayout.addElement(debugItem);
		var border = new BorderMenuLayout(ItemStackUtil.make(Material.GREEN_STAINED_GLASS_PANE, Component.empty()),
				wrapLayout);
		menu = new MenuInventory(border, 3);
	}

	public PlayerWrapper getParentWrapper() {
		return Ioc.resolve(WrappingModule.class).getWrapper(wrapped, PlayerWrapper.class);
	}

	public void displayMenu() {
		var game = Ioc.resolve(Vi6Game.class);
		var playerWrapper = getParentWrapper();
		var dialog = Dialog.create(builder -> builder.empty().base(DialogBase
				.builder(MiniMessage.miniMessage()
						.deserialize("<red>V<dark_aqua>oleur <red>I<dark_aqua>ndustriel <red>6"))
				.canCloseWithEscape(true).pause(false)
				.inputs(List.of(
						DialogInput
								.singleOption("team", Component.text("Équipe"), List.of(
										SingleOptionDialogInput.OptionEntry.create("guard",
												Component.text("Garde", NamedTextColor.BLUE), playerWrapper.isGuard()),
										SingleOptionDialogInput.OptionEntry.create("thief",
												Component.text("Voleur", NamedTextColor.RED), playerWrapper.isThief())))
								.build(),
						DialogInput.bool("ready", Component.text("Je suis prêt")).initial(this.isReadyForNextPhase())
								.build()))
				.build())
				.type(DialogType.confirmation(ActionButton.builder(Component.text("Enregistrer", NamedTextColor.GREEN))
						.action(DialogAction.customClick((response, _) -> {
							switch (response.getText("team")) {
								case "guard" :
									game.addPlayerInGuards(wrapped);
									break;
								case "thief" :
									game.addPlayerInThiefs(wrapped);
									break;
							}
							setReadyForNextPhase(response.getBoolean("ready"));
						}, ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build())).build(),
						ActionButton.builder(Component.text("Annuler", NamedTextColor.RED)).build())));
		wrapped.showDialog(dialog);
	}

	public boolean isReadyForNextPhase() {
		return readyForNextPhase;
	}

	public void setReadyForNextPhase(boolean readyForNextPhase) {
		this.readyForNextPhase = readyForNextPhase;
		var lobby = Ioc.resolve(Vi6Game.class).getPhaseMachine().getPhase(Vi6PhaseLobby.class);
		lobby.getSidebarObjective().getScore(wrapped).setScore(readyForNextPhase ? 1 : 0);
		if (readyForNextPhase) {
			lobby.checkForCompletion();
		}
	}
}
