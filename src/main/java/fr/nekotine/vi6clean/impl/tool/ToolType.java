package fr.nekotine.vi6clean.impl.tool;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import fr.nekotine.core.inventory.menu.element.ActionMenuItem;
import fr.nekotine.core.inventory.menu.element.MenuElement;
import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.text.TextModule;
import fr.nekotine.core.text.tree.Leaf;
import fr.nekotine.core.util.ItemStackUtil;
import fr.nekotine.core.wrapper.WrappingModule;
<<<<<<< HEAD
import fr.nekotine.vi6clean.constant.Vi6Styles;
=======
import fr.nekotine.vi6clean.constant.Vi6ToolLoreText;
import fr.nekotine.vi6clean.impl.tool.personal.bush.BushHandler;
import fr.nekotine.vi6clean.impl.tool.personal.dephaser.DephaserHandler;
import fr.nekotine.vi6clean.impl.tool.personal.doublejump.DoubleJumpHandler;
import fr.nekotine.vi6clean.impl.tool.personal.emp.EmpHandler;
import fr.nekotine.vi6clean.impl.tool.personal.invisneak.InviSneakHandler;
import fr.nekotine.vi6clean.impl.tool.personal.lantern.LanternHandler;
import fr.nekotine.vi6clean.impl.tool.personal.omnicaptor.OmniCaptorHandler;
import fr.nekotine.vi6clean.impl.tool.personal.parabolic_mic.ParabolicMicHandler;
import fr.nekotine.vi6clean.impl.tool.personal.radar.RadarHandler;
import fr.nekotine.vi6clean.impl.tool.personal.regenerator.RegeneratorHandler;
import fr.nekotine.vi6clean.impl.tool.personal.scanner.ScannerHandler;
import fr.nekotine.vi6clean.impl.tool.personal.shadow.ShadowHandler;
import fr.nekotine.vi6clean.impl.tool.personal.sonar.SonarHandler;
import fr.nekotine.vi6clean.impl.tool.personal.tazer.TazerHandler;
import fr.nekotine.vi6clean.impl.tool.personal.tracker.TrackerHandler;
import fr.nekotine.vi6clean.impl.tool.personal.warner.WarnerHandler;
import fr.nekotine.vi6clean.impl.tool.personal.watcher.WatcherHandler;
>>>>>>> refs/heads/master
import fr.nekotine.vi6clean.impl.wrapper.PreparationPhasePlayerWrapper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public enum ToolType {

	INVISNEAK(
			ItemStackUtil.make(Material.GLASS_PANE, Component.text("InviSneak", NamedTextColor.GOLD), InviSneakHandler.LORE),
			InviSneakHandler::new,
			400, 		// PRICE
			1 			// LIMIT
			),
	OMNICAPTOR(
			ItemStackUtil.make(Material.REDSTONE_TORCH, Component.text("OmniCapteur", NamedTextColor.GOLD), OmniCaptorHandler.LORE),
			OmniCaptorHandler::new,
			250, 		// PRICE
			2 			// LIMIT
			),
	SONAR(
			ItemStackUtil.make(Material.TARGET, Component.text("Sonar", NamedTextColor.GOLD), SonarHandler.LORE),
			SonarHandler::new,
			400, 		// PRICE
			1 			// LIMIT
			),
	DOUBLEJUMP(
			ItemStackUtil.make(Material.GOLDEN_BOOTS, Component.text("Double Saut", NamedTextColor.GOLD), Vi6ToolLoreText.DOUBLEJUMP.make()),
			DoubleJumpHandler::new,
			300, 		// PRICE
			1 			// LIMIT
			),
	TAZER(
			ItemStackUtil.make(Material.SHEARS, Component.text("Tazer", NamedTextColor.GOLD), TazerHandler.LORE),
			TazerHandler::new,
			300, 		// PRICE
			1 			// LIMIT
			),
	LANTERN(
			ItemStackUtil.make(Material.LANTERN, Component.text("Lanterne", NamedTextColor.GOLD), LanternHandler.LORE),
			LanternHandler::new,
			200, 		// PRICE
			1 			// LIMIT
			),
	RADAR (
			ItemStackUtil.make(Material.DAYLIGHT_DETECTOR, Component.text("Radar", NamedTextColor.GOLD), RadarHandler.LORE),
			RadarHandler::new,
			200,		// PRICE
			1 			// LIMIT
			),
	SHADOW (
			ItemStackUtil.make(Material.WITHER_SKELETON_SKULL, Component.text("Ombre", NamedTextColor.GOLD), Vi6ToolLoreText.SHADOW.make()),
			ShadowHandler::new,
			250,		// PRICE
			2 			// LIMIT
			),
	WATCHER (
			ItemStackUtil.make(Material.SILVERFISH_SPAWN_EGG, Component.text("Observateur", NamedTextColor.GOLD), WatcherHandler.LORE),
			WatcherHandler::new,
			200,		// PRICE
			1 			// LIMIT
			),
	WARNER (
			ItemStackUtil.make(Material.ENDER_EYE, Component.text("Avertisseur", NamedTextColor.GOLD), WarnerHandler.LORE),
			WarnerHandler::new,
			150,		// PRICE
			-1
			),
	REGENERATOR (
			ItemStackUtil.make(Material.CAMPFIRE, Component.text("Régénérateur", NamedTextColor.GOLD), RegeneratorHandler.LORE),
			RegeneratorHandler::new,
			150,		// PRICE
			-1 			// LIMIT
			),
	TRACKER (
			ItemStackUtil.make(Material.CROSSBOW, Component.text("Traceur", NamedTextColor.GOLD), Vi6ToolLoreText.TRACKER.make()),
			TrackerHandler::new,
			100,		// PRICE
			-1			// LIMIT
			),
	SCANNER (
			ItemStackUtil.make(Material.CLOCK, Component.text("Scanner", NamedTextColor.GOLD), ScannerHandler.LORE),
			ScannerHandler::new,
			600,		// PRICE
			1			// LIMIT
			),
	PARABOLIC_MIC (
			ItemStackUtil.make(Material.CALIBRATED_SCULK_SENSOR, Component.text("Micro parabolique", NamedTextColor.GOLD), ParabolicMicHandler.LORE),
			ParabolicMicHandler::new,
			600,		// PRICE
			1			// LIMIT
			),
	BUSH (
			ItemStackUtil.make(Material.OAK_LEAVES, Component.text("Buisson furtif", NamedTextColor.GOLD), Vi6ToolLoreText.BUSH.make()),
			BushHandler::new,
			350,		// PRICE
			1			// LIMIT
			),
	DEPHASER (
			ItemStackUtil.make(Material.IRON_NUGGET, Component.text("Déphasage", NamedTextColor.GOLD), DephaserHandler.LORE),
			DephaserHandler::new,
			200,		// PRICE
			1			// LIMIT
			),
	EMP(
			ItemStackUtil.make(Material.BEACON, Component.text("IEM", NamedTextColor.GOLD), EmpHandler.LORE),
			EmpHandler::new,
			150,		// PRICE
			-1
			)
	;
	
}
