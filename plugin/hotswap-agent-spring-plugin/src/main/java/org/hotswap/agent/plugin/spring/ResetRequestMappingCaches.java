package org.hotswap.agent.plugin.spring;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Map.Entry;

import org.hotswap.agent.logging.AgentLogger;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * Support for Spring MVC mapping caches.
 */
public class ResetRequestMappingCaches {
	
	private static AgentLogger LOGGER = AgentLogger.getLogger(ResetRequestMappingCaches.class);
	
	private static Class<?> getHandlerMethodMappingClassOrNull() {
		try {
			//This is probably a bad idea as Class.forName has lots of issues but this was easiest for now.
			return Class.forName("org.springframework.web.servlet.handler.AbstractHandlerMethodMapping");
		} catch (ClassNotFoundException e) {
			LOGGER.trace("HandlerMethodMapping class not found");
			return null;
		}
	}
	
	public static void reset(DefaultListableBeanFactory beanFactory) {
		
		Class<?> c = getHandlerMethodMappingClassOrNull();
        if (c == null)
            return;

		Map<String, ?> mappings =
				BeanFactoryUtils.beansOfTypeIncludingAncestors(beanFactory, c, true, false);
		if (mappings.isEmpty()) {
			LOGGER.trace("Spring: no HandlerMappings found");
		}
		try {
			for (Entry<String, ?> e : mappings.entrySet()) {
				Object am = e.getValue();
				LOGGER.info("Spring: clearing HandlerMapping for {}", am.getClass());
				try {
					Field f = c.getDeclaredField("handlerMethods");
					f.setAccessible(true);
					((Map<?,?>)f.get(am)).clear();
					f = c.getDeclaredField("urlMap");
					f.setAccessible(true);
					((Map<?,?>)f.get(am)).clear();
					try {
						f = c.getDeclaredField("nameMap");
						f.setAccessible(true);
						((Map<?,?>)f.get(am)).clear();
					} catch(NoSuchFieldException nsfe) {
						LOGGER.trace("Probably using Spring 4.0 or below", nsfe);
					}
				} catch(NoSuchFieldException nsfe) {
					LOGGER.trace("Probably using Spring 4.2+", nsfe);
					Method m = c.getDeclaredMethod("getHandlerMethods", new Class[0]);
					Class<?>[] parameterTypes = new Class[1];
					parameterTypes[0] = Object.class;
					Method u = c.getDeclaredMethod("unregisterMapping", parameterTypes);
					Map<?,?> unmodifiableHandlerMethods = (Map<?,?>) m.invoke(am);
					Object[] keys = unmodifiableHandlerMethods.keySet().toArray();
					unmodifiableHandlerMethods = null;
					for (Object key : keys) {
						LOGGER.trace("Unregistering handler method {}", key);
						u.invoke(am, key);
					}
				}
				if (am instanceof InitializingBean) {
					((InitializingBean) am).afterPropertiesSet();
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to clear HandlerMappings", e);
		}
		
	}

}
