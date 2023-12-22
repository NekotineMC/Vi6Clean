package fr.nekotine.vi6clean.impl.tool;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import fr.nekotine.core.ioc.Ioc;
import fr.nekotine.core.logging.NekotineLogger;
import fr.nekotine.core.reflexion.ReflexionUtil;

public class ToolHandlerContainer {

	private Logger logger = new NekotineLogger(getClass());
	
	private Collection<ToolHandler<?>> toolHandlers;
	
	public void discoverHandlers() {
		for (var tool : toolHandlers) {
			tool.stopHandling();
			tool.removeAll();
			Ioc.getProvider().unregister(tool.getClass());
		}
		toolHandlers.clear();
		logger.info("Découverte et ajout des tools: ");
		try {
			for (var tool : ReflexionUtil.streamClassesFromPackage("fr.nekotine.vi6clean.impl.tool")
					.filter(c -> Tool.class.isAssignableFrom(c) && c.isAnnotationPresent(ToolCode.class)).collect(Collectors.toSet())) {
				var ctor = tool.getConstructor();
				var handler = (ToolHandler<?>)ctor.newInstance();
				toolHandlers.add(handler);
				Ioc.resolve(tool);
				logger.info(String.format("Type: %s",tool.getSimpleName()));
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Une erreur est survenue lors de l'ajout des class Tool au registre", e);
		}
	}
	
	public Collection<ToolHandler<?>> getHandlers(){
		return toolHandlers;
	}
	
}
