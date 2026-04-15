package fr.nekotine.vi6clean;

import fr.nekotine.core.NekotineCoreBootstrapper;
import fr.nekotine.vi6clean.constant.Vi6Keys;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import io.papermc.paper.registry.keys.tags.EntityTypeTagKeys;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.EntityType;

public class Vi6Bootstrapper extends NekotineCoreBootstrapper {

	@Override
	public void bootstrap(BootstrapContext context) {
		var lifecycle = context.getLifecycleManager();

		var menuDialogKey = DialogKeys.create(Key.key(Vi6Keys.DIALOG_SETTINGS));

		lifecycle.registerEventHandler(RegistryEvents.DIALOG.compose().newHandler(evt -> {
			evt.registry().register(menuDialogKey,
					builder -> builder
							.base(DialogBase
									.builder(MiniMessage.miniMessage().deserialize(
											"<red>V<dark_aqua>oleur" + " <red>I<dark_aqua>ndustriel <red>6"))
									.canCloseWithEscape(true)
									.externalTitle(MiniMessage.miniMessage().deserialize(
											"<red>V<dark_aqua>oleur" + " <red>I<dark_aqua>ndustriel <red>6"))
									.pause(false).build())
							.type(DialogType.multiAction(
									List.of(ActionButton
											.builder(Component.text("Paramètre partie", NamedTextColor.WHITE))
											.action(DialogAction.customClick(Key.key(Vi6Keys.DIALOG_GAME_SETTINGS),
													null))
											.build()),
									ActionButton.builder(Component.text("Quitter", NamedTextColor.WHITE)).build(), 2)));
		}));

		lifecycle.registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG).newHandler(evt -> {
			var registrar = evt.registrar();
			registrar.addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, List.of(menuDialogKey));
		}));

		lifecycle.registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.ENTITY_TYPE).newHandler(evt -> {
			var registrar = evt.registrar();
			registrar.addToTag(EntityTypeTagKeys.IMMUNE_TO_OOZING,
					List.of(TypedKey.create(RegistryKey.ENTITY_TYPE, EntityType.PLAYER.getKey())));
			registrar.addToTag(EntityTypeTagKeys.IMMUNE_TO_INFESTED,
					List.of(TypedKey.create(RegistryKey.ENTITY_TYPE, EntityType.PLAYER.getKey())));
		}));
	}
}
