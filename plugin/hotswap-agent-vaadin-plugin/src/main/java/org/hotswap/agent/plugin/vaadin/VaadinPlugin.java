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
 * @author Matti Tahvonen
 */
@Plugin(name = "Vaadin", description = "Vaadin Platform support", testedVersions = {
    "10.0.0.beta9","13.0.1"}, expectedVersions = {"10.0+"})
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

    private boolean vaadin13orNewer;

    private static AgentLogger LOGGER = AgentLogger
            .getLogger(VaadinPlugin.class);
    private Object routeConfiguration;
    private Method setRouteMethod;

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

    @OnClassLoadEvent(classNameRegexp = "com.vaadin.flow.router.RouteConfiguration")
    public static void initRouteRegistry(CtClass ctClass)
            throws NotFoundException, CannotCompileException {
        String src = PluginManagerInvoker
                .buildInitializePlugin(VaadinPlugin.class);
        src += PluginManagerInvoker.buildCallPluginMethod(VaadinPlugin.class,
                "registerRouteConfiguration", "this", "java.lang.Object");
        ctClass.getDeclaredConstructors()[0].insertAfter(src);
    }
    
    public void registerRouteConfiguration(Object routeConfiguration) {
        try {
            if(routeConfiguration != null) {
                // first RouteConfiguration is the one used for global routes
                setRouteMethod = routeConfiguration.getClass().getMethod("setAnnotatedRoute", Class.class);
                this.routeConfiguration = routeConfiguration;
            }
        } catch (NoSuchMethodException | SecurityException ex) {
            LOGGER.error(null, ex);
        }
        
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
        } catch (IllegalArgumentException ex) {
            LOGGER.error(null, ex);
        }

        LOGGER.info("Plugin {} initialized for servlet {}", getClass(),
                vaadinServlet);
    }

    private Class<?> getRouteRegistryClass() throws ClassNotFoundException {
        try {
            return resolveClass("com.vaadin.flow.server.startup.RouteRegistry");
        } catch (ClassNotFoundException e) {
            // Vaadin 13+ app
            LOGGER.debug("Vaadin 13 app detected");
            vaadin13orNewer = true;
            return resolveClass("com.vaadin.flow.server.startup.ApplicationRouteRegistry");
        }
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

    @OnClassFileEvent(classNameRegexp = ".*", events = {FileEvent.CREATE,
        FileEvent.MODIFY})
    public void addNewRoute(CtClass ctClass) throws Exception {
        LOGGER.debug("Class file event for " + ctClass.getName());
        if (ctClass.hasAnnotation("com.vaadin.flow.router.Route")) {
            LOGGER.info("HotSwapAgent dynamically added new route to " + ctClass.getName());
            if (vaadin13orNewer) {
                addToRouterConfiguration(ctClass);
            } else {
                ensureInRouter(ctClass);
            }
        }
    }

    private void ensureInRouter(CtClass ctClass)
            throws ReflectiveOperationException {
        if (vaadin13orNewer) {
            throw new RuntimeException("This method is not supported with Vaadin 13");
        }
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

    /**
     * Metacode: conf = new RouteConfiguration(getApplicationRegistry()) conf =
     * RouteConfiguration.forApplicationScope(); conf.setAnnotatedRoute(cls)
     *
     *
     * @param ctClass
     */
    private void addToRouterConfiguration(CtClass ctClass) {
        try {
            setRouteMethod.invoke(routeConfiguration, resolveClass(ctClass.getName()));
        } catch(InvocationTargetException ex) {
            //com.vaadin.flow.server.InvalidRouteConfigurationException
            if(ex.getCause().getClass().getName().equals("com.vaadin.flow.server.InvalidRouteConfigurationException")) {
                LOGGER.debug("Already registered");
            } else {
                LOGGER.error(null, ex);
            }
        } catch (ClassNotFoundException | SecurityException | IllegalAccessException | IllegalArgumentException ex) {
            LOGGER.error(null, ex);
        }

    }

}
