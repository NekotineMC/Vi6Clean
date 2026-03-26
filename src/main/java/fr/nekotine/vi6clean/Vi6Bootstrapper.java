package fr.nekotine.vi6clean;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import fr.nekotine.core.NekotineCoreBootstrapper;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class Vi6Bootstrapper extends NekotineCoreBootstrapper{

	@Override
	public void bootstrap(BootstrapContext context) {
		
		var lifecycle = context.getLifecycleManager();
		
		lifecycle.registerEventHandler(RegistryEvents.DIALOG.entryAdd().newHandler( evt -> {
			evt.builder()
					.base(DialogBase.builder(MiniMessage.miniMessage().deserialize("<red>V<dark_aqua>oleur <red>I<dark_aqua>ndustriel <red>6"))
							.canCloseWithEscape(true)
							.externalTitle(MiniMessage.miniMessage().deserialize("<red>V<aqua>oleur <red>I<aqua>ndustriel <red>6"))
							.pause(false)
							.inputs(List.of(
									DialogInput.singleOption("team",Component.text("Équipe"),List.of(
											SingleOptionDialogInput.OptionEntry.create("guard",Component.text("Garde",NamedTextColor.BLUE), true),
											SingleOptionDialogInput.OptionEntry.create("thief",Component.text("Voleur",NamedTextColor.RED), false)
									)).build()
							))
							.body(List.of(
									DialogBody.plainMessage(Component.text(new Date().toString()))
									))
							.build())
					.type(
						DialogType.notice(
								ActionButton.builder(Component.text("Enregistrer",NamedTextColor.GREEN))
										.action(DialogAction.customClick((response,audiance) -> {
												audiance.sendMessage(Component.text("Selected team is "+response.getText("team")));
											},
											ClickCallback.Options.builder().lifetime(Duration.ofMinutes(5)).build()
										))
									.build()
					));
		}).filter(DialogKeys.CUSTOM_OPTIONS));
		
	}
	
}
