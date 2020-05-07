package org.hotswap.agent.plugin.vaadin;

import javax.servlet.ServletContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.hotswap.agent.logging.AgentLogger;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.RouteRegistry;
import com.vaadin.flow.server.VaadinContext;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServlet;
import com.vaadin.flow.server.startup.ApplicationRouteRegistry;

public class VaadinIntegration {

    private static final AgentLogger LOGGER = AgentLogger
            .getLogger(VaadinIntegration.class);

    private VaadinServlet vaadinServlet = null;

    /**
     * Sets the Vaadin servlet once instantiated.
     * 
     * @param servlet
     *            the Vaadin serlvet
     */
    public void servletInitialized(VaadinServlet servlet) {
        vaadinServlet = servlet;
        LOGGER.info("{} initialized for servlet {}", getClass(), servlet);
    }

    /**
     * Update Flow route registry and push refresh to UIs (concrete parameter
     * types as {@link org.hotswap.agent.command.ReflectionCommand} determines
     * the method from actual argument types).
     * 
     * @param addedClasses
     *            returns classes that have been added or modified
     * @param modifiedClasses
     *            returns classes that have been deleted
     */
    public void updateRoutes(HashSet<Class<?>> addedClasses,
                             HashSet<Class<?>> modifiedClasses) {
        assert (vaadinServlet != null);

        LOGGER.debug("The following classes were added:");
        addedClasses.forEach(clazz -> LOGGER.debug("+ {}", clazz));

        LOGGER.debug("The following classes were modified:");
        modifiedClasses.forEach(clazz -> LOGGER.debug("# {}", clazz));

        Method getInstanceMethod = null;
        Object getInstanceMethodParam = null;
        try {
            // Vaadin 14.2+
            getInstanceMethod = ApplicationRouteRegistry.class.getMethod("getInstance", VaadinContext.class);
            getInstanceMethodParam = vaadinServlet.getService().getContext();
        } catch (NoSuchMethodException ex1) {
            // In Vaadin 14.1, this method instead takes a ServletContext parameter
            LOGGER.debug("ApplicationRouteRegistry::getInstance(VaadinContext) not found");
            try {
                getInstanceMethod = ApplicationRouteRegistry.class.getMethod("getInstance", ServletContext.class);
                getInstanceMethodParam = vaadinServlet.getServletContext();
            } catch (NoSuchMethodException ex2) {
                // In Vaadin 14.1, this method takes a ServletContext parameter
                LOGGER.warning("Unable to obtain ApplicationRouteRegistry instance; routes are not updated ");
                return;
            }
        }

        try {
            ApplicationRouteRegistry registry = (ApplicationRouteRegistry)
                    getInstanceMethod.invoke(null, getInstanceMethodParam);
            updateRouteRegistry(registry, addedClasses, modifiedClasses,
                    Collections.emptySet());
        } catch (IllegalAccessException | InvocationTargetException ex) {
            LOGGER.warning("Unable to obtain ApplicationRouteRegistry instance; routes are not updated:", ex);
        }
    }

    /**
     * Reload UI in browser.
     */
    public void reload() {
        VaadinService vaadinService = vaadinServlet.getService();
        try {
            // TODO: Remove reflection when public flow release with
            // com.vaadin.flow.internal.BrowserLiveReload* is published
            Class<?> browserLiveReloadAccessClass = Class.forName("com.vaadin.flow.internal.BrowserLiveReloadAccess");
            Method getLiveReloadMethod = browserLiveReloadAccessClass.getMethod("getLiveReload", VaadinService.class);
            Class<?> browserLiveReloadClass = Class.forName("com.vaadin.flow.internal.BrowserLiveReload");
            Method reloadMethod = browserLiveReloadClass.getMethod("reload");
            Object browserLiveReloadAccess = vaadinService.getInstantiator()
                    .getOrCreate(browserLiveReloadAccessClass);
            Object browserLiveReload = getLiveReloadMethod.invoke(browserLiveReloadAccess, vaadinService);
            reloadMethod.invoke(browserLiveReload);
            LOGGER.info("Live reload triggered");
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            LOGGER.info("No live reload connection established");
            LOGGER.debug("Encountered the following exception invoking BrowserLiveReload::reload",
                    ex);
        }
    }

    /**
     * Updates route registry as necessary when classes have been added /
     * modified / deleted.
     *
     * TODO: move to flow-server internal utility methods.
     *
     * @param registry
     *            route registry
     * @param addedClasses
     *            added classes
     * @param modifiedClasses
     *            modified classes
     * @param deletedClasses
     *            deleted classes
     */
    private static void updateRouteRegistry(RouteRegistry registry,
                                            Set<Class<?>> addedClasses,
                                            Set<Class<?>> modifiedClasses,
                                            Set<Class<?>> deletedClasses) {
        RouteConfiguration routeConf = RouteConfiguration.forRegistry(registry);

        registry.update(() -> {
            // remove deleted classes and classes that lost the annotation from registry
            Stream.concat(deletedClasses.stream(),
                    modifiedClasses.stream().filter(
                            clazz -> !clazz.isAnnotationPresent(Route.class)))
                    .filter(Component.class::isAssignableFrom)
                    .forEach(clazz -> {
                        Class<? extends Component> componentClass = (Class<? extends Component>) clazz;
                        routeConf.removeRoute(componentClass);
                    });

            // add new routes to registry
            Stream.concat(addedClasses.stream(), modifiedClasses.stream())
                    .distinct()
                    .filter(Component.class::isAssignableFrom)
                    .filter(clazz -> clazz.isAnnotationPresent(Route.class))
                    .forEach(clazz -> {
                        Class<? extends Component> componentClass = (Class<? extends Component>) clazz;
                        LOGGER.info(
                                "Updating route '{}' to {}", componentClass
                                        .getAnnotation(Route.class).value(),
                                clazz);
                        routeConf.removeRoute(componentClass);
                        routeConf.setAnnotatedRoute(componentClass);
                    });
        });
    }
}
