package org.hotswap.agent.plugin.vaadin;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Vaadin Platform hotswap support
 *
 * https://vaadin.com
 *
 * @author Artur Signell
 */
@Plugin(name = "Vaadin", description = "Vaadin Platform support", testedVersions = {
        "10.0.0.beta9" }, expectedVersions = { "10.0+" })
public class VaadinPlugin {

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    ReflectionCommand clearReflectionCache = new ReflectionCommand(this,
            "com.vaadin.flow.internal.ReflectionCache", "clearAll");

    private Object vaadinServlet;

    private Method vaadinServletGetServletContext;

    private Method routeRegistryGet;

    private static AgentLogger LOGGER = AgentLogger
            .getLogger(VaadinPlugin.class);

    public VaadinPlugin() {
    }

    @OnClassLoadEvent(classNameRegexp = "com.vaadin.flow.server.VaadinServlet")
    public static void init(CtClass ctClass)
            throws NotFoundException, CannotCompileException {
        String src = PluginManagerInvoker
                .buildInitializePlugin(VaadinPlugin.class);
        src += PluginManagerInvoker.buildCallPluginMethod(VaadinPlugin.class,
                "registerServlet", "this", "java.lang.Object");
        ctClass.getDeclaredConstructor(new CtClass[0]).insertAfter(src);

        LOGGER.info("Initialized Vaadin plugin");
    }

    public void registerServlet(Object vaadinServlet) {
        this.vaadinServlet = vaadinServlet;

        try {
            Class<?> servletContextClass = resolveClass(
                    "javax.servlet.ServletContext");
            vaadinServletGetServletContext = resolveClass(
                    "javax.servlet.GenericServlet")
                            .getDeclaredMethod("getServletContext");
            routeRegistryGet = getRouteRegistryClass()
                    .getDeclaredMethod("getInstance", servletContextClass);
        } catch (NoSuchMethodException | SecurityException
                | ClassNotFoundException e) {
            e.printStackTrace();
        }

        LOGGER.info("Plugin {} initialized for servlet {}", getClass(),
                vaadinServlet);
    }

    private Class<?> getRouteRegistryClass() throws ClassNotFoundException {
        return resolveClass("com.vaadin.flow.server.startup.RouteRegistry");
    }

    public Object getRouteRegistry() {
        try {
            Object servletContext = vaadinServletGetServletContext
                    .invoke(vaadinServlet);
            Object routeRegistry = routeRegistryGet.invoke(null,
                    servletContext);
            return routeRegistry;
        } catch (IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidateReflectionCache() throws Exception {
        LOGGER.debug("Clearing Vaadin reflection cache");
        scheduler.scheduleCommand(clearReflectionCache);
    }

    @OnClassFileEvent(classNameRegexp = ".*", events = { FileEvent.CREATE,
            FileEvent.MODIFY })
    public void addNewRoute(CtClass ctClass) throws Exception {
        LOGGER.debug("Class file event for " + ctClass.getName());
        if (ctClass.hasAnnotation("com.vaadin.flow.router.Route")) {
            LOGGER.debug("New route class: " + ctClass.getName());
            ensureInRouter(ctClass);
        }
    }

    private void ensureInRouter(CtClass ctClass)
            throws ReflectiveOperationException {
        Object routeRegistry = getRouteRegistry();
        Set<Class<?>> routeClasses = getCurrentRouteClasses(routeRegistry);

        Class<?> hashSet = resolveClass("java.util.HashSet");
        Object classSet = hashSet.newInstance();
        Method addAll = hashSet.getMethod("addAll",
                resolveClass("java.util.Collection"));
        Method add = hashSet.getMethod("add", resolveClass("java.lang.Object"));
        addAll.invoke(classSet, routeClasses);
        add.invoke(classSet, resolveClass(ctClass.getName()));

        forceRouteUpdate(routeRegistry, classSet);

    }

    private void forceRouteUpdate(Object routeRegistry, Object routeClassSet)
            throws ReflectiveOperationException {

        Field targetRoutesField = getRouteRegistryClass()
                .getDeclaredField("targetRoutes");
        Field routesField = getRouteRegistryClass().getDeclaredField("routes");
        Field routeDataField = getRouteRegistryClass()
                .getDeclaredField("routeData");

        targetRoutesField.setAccessible(true);
        routesField.setAccessible(true);
        routeDataField.setAccessible(true);

        targetRoutesField.set(routeRegistry, createAtomicRef());
        routesField.set(routeRegistry, createAtomicRef());
        routeDataField.set(routeRegistry, createAtomicRef());

        Method setNavigationTargets = getRouteRegistryClass().getDeclaredMethod(
                "setNavigationTargets", resolveClass("java.util.Set"));
        setNavigationTargets.invoke(routeRegistry, routeClassSet);
    }

    private Object createAtomicRef() throws InstantiationException,
            IllegalAccessException, ClassNotFoundException {
        return resolveClass("java.util.concurrent.atomic.AtomicReference")
                .newInstance();
    }

    private Set<Class<?>> getCurrentRouteClasses(Object routeRegistry)
            throws ReflectiveOperationException {
        Field targetRoutesField = getRouteRegistryClass()
                .getDeclaredField("targetRoutes");
        targetRoutesField.setAccessible(true);
        AtomicReference<Map> ref = (AtomicReference<Map>) targetRoutesField
                .get(routeRegistry);
        return ref.get().keySet();
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}
