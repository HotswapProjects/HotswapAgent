/*
 * Copyright 2013-2024 the HotswapAgent authors.
 *
 * This file is part of HotswapAgent.
 *
 * HotswapAgent is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 2 of the License, or (at your
 * option) any later version.
 *
 * HotswapAgent is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with HotswapAgent. If not, see http://www.gnu.org/licenses/.
 */
package org.hotswap.agent.plugin.vaadin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.hotswap.agent.annotation.FileEvent;
import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassFileEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.OnResourceFileEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.ReflectionCommand;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

import static java.util.stream.Collectors.toCollection;

import static org.hotswap.agent.annotation.FileEvent.CREATE;
import static org.hotswap.agent.annotation.FileEvent.DELETE;
import static org.hotswap.agent.annotation.FileEvent.MODIFY;
import static org.hotswap.agent.annotation.LoadEvent.REDEFINE;

/**
 * Vaadin 14.0+ plugin for HotswapAgent.
 * <p>
 * https://vaadin.com
 *
 * @author Artur Signell
 * @author Matti Tahvonen
 * @author Johannes Eriksson
 */
@Plugin(name = "Vaadin",
        description = "Vaadin support",
        testedVersions = {"23.0.0", "24.0.0.beta1"},
        expectedVersions = {"23 - 24"})
public class VaadinPlugin {

    static final String VAADIN_SERVLET = "com.vaadin.flow.server.VaadinServlet";
    static final String VAADIN_SERVICE = "com.vaadin.flow.server.VaadinService";


    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    @Init
    PluginConfiguration pluginConfiguration;

    private boolean hasVaadinHotSwapper;

    // --> Vaadin [24.5,)
    private final Set<String> watchedPackages = new HashSet<>();
    private Object vaadinHotswapperObj;
    private ResourceChangedCommand resourceChangedCommand;
    // <-- Vaadin [24.5,)

    // --> Vaadin [23, 24.5)
    private UpdateRoutesCommand updateRouteRegistryCommand;
    private ReflectionCommand reloadCommand;
    private ReflectionCommand clearReflectionCache = new ReflectionCommand(this,
            "com.vaadin.flow.internal.ReflectionCache", "clearAll");
    private Set<Class<?>> addedClasses = new HashSet<>();
    private Set<Class<?>> modifiedClasses = new HashSet<>();
    // <-- Vaadin [23, 24.5)

    private boolean pluginReady;

    private static final AgentLogger LOGGER = AgentLogger.getLogger(VaadinPlugin.class);

    private static final String WATCHED_PACKAGES_PARAMETER = "vaadin.watched-packages";
    private static final String RELOAD_QUIET_TIME_PARAMETER = "vaadin.liveReloadQuietTime";

    private static final int DEFAULT_RELOAD_QUIET_TIME = 1000; // ms

    private int reloadQuietTime = 0;

    public VaadinPlugin() {
    }

    @Init
    public void initPlugin() {
        String watchedPackages = this.pluginConfiguration.getProperty(WATCHED_PACKAGES_PARAMETER);
        if (watchedPackages != null) {
            Stream.of(watchedPackages
                            .split("\\s*,\\s*")).map(String::trim).filter(pkg -> !pkg.isEmpty())
                    .collect(toCollection(() -> this.watchedPackages));
            LOGGER.info("Packages watched for class changes: {}", String.join(", ", this.watchedPackages));
        }

        reloadQuietTime = DEFAULT_RELOAD_QUIET_TIME;
        String reloadQuietTimeValue = pluginConfiguration.getProperty(RELOAD_QUIET_TIME_PARAMETER);
        if (reloadQuietTimeValue != null) {
            if (reloadQuietTimeValue.matches("[1-9][0-1]+")) {
                reloadQuietTime = Integer.parseInt(reloadQuietTimeValue);
                LOGGER.info("Live-reload quiet time is {} ms", reloadQuietTime);
            } else {
                LOGGER.error("Illegal value '{}' for parameter {}, using default of {} ms",
                        reloadQuietTimeValue, RELOAD_QUIET_TIME_PARAMETER, DEFAULT_RELOAD_QUIET_TIME);
            }
        }

    }

    @OnClassLoadEvent(classNameRegexp = VAADIN_SERVLET)
    public static void init(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        boolean hasVaadinHotSwapper = classPool.getOrNull("com.vaadin.flow.hotswap.Hotswapper") != null;

        String src = PluginManagerInvoker
                .buildInitializePlugin(VaadinPlugin.class);
        if (!hasVaadinHotSwapper) {
            src += PluginManagerInvoker.buildCallPluginMethod(VaadinPlugin.class,
                    "registerServlet", "this", Object.class.getName());
        }
        ctClass.getDeclaredConstructor(new CtClass[0]).insertAfter(src);

        LOGGER.info("Initialized Vaadin plugin");
    }

    // Inject Vaadin Hotswapper initialization in VaadinService
    @OnClassLoadEvent(classNameRegexp = VAADIN_SERVICE)
    public static void transformVaadinService(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        boolean hasVaadinHotSwapper = classPool.getOrNull("com.vaadin.flow.hotswap.Hotswapper") != null;

        StringBuilder src = new StringBuilder();
        if (hasVaadinHotSwapper) {
            String initHotswapperPluginCall = PluginManagerInvoker.buildCallPluginMethod(
                    VaadinPlugin.class, "initializeHotswapper", "hotswapper",
                    "java.lang.Object");
            src.append("try { ")
                    .append("java.util.Optional maybeHotswapper = com.vaadin.flow.hotswap.Hotswapper.register(this);")
                    .append("if (maybeHotswapper.isPresent()) { ")
                    .append("Object hotswapper = maybeHotswapper.get();")
                    .append(initHotswapperPluginCall)
                    .append("} } catch (Exception e) { ")
                    .append("e.printStackTrace(); }"); // TODO: ignore exception
        } else {
            src.append(PluginManagerInvoker.buildCallPluginMethod(
                    VaadinPlugin.class, "vaadinServiceInitialized"));
        }
        ctClass.getDeclaredMethod("init").insertBefore(src.toString());

        ctClass.getDeclaredMethod("destroy").insertBefore(PluginManagerInvoker.buildCallPluginMethod(
                VaadinPlugin.class, "vaadinServiceDestroyed"));

        LOGGER.debug("{} has been enhanced.{}", VAADIN_SERVICE, hasVaadinHotSwapper ? "Vaadin Hotswapper registered" : "");


    }

    public void registerServlet(Object vaadinServlet) {
        try {
            Class<?> vaadinIntegrationClass = resolveClass("org.hotswap.agent.plugin.vaadin.VaadinIntegration");
            Object vaadinIntegration = vaadinIntegrationClass.getConstructor()
                    .newInstance();
            Class<?> vaadinServletClass = resolveClass("com.vaadin.flow.server.VaadinServlet");
            Method m = vaadinIntegrationClass.getDeclaredMethod("servletInitialized",
                    vaadinServletClass);
            m.invoke(vaadinIntegration, vaadinServlet);

            updateRouteRegistryCommand = new UpdateRoutesCommand(vaadinIntegration);
            reloadCommand = new ReflectionCommand(vaadinIntegration, "reload");
        } catch (ClassNotFoundException | NoSuchMethodException
                 | InstantiationException | IllegalAccessException
                 | InvocationTargetException ex) {
            LOGGER.error(null, ex);
        }
    }

    public void initializeHotswapper(Object vaadinHotswapperObj) {
        LOGGER.trace("Obtained Vaadin Hotswapper instance: {}", vaadinHotswapperObj);
        this.vaadinHotswapperObj = vaadinHotswapperObj;
        this.resourceChangedCommand = new ResourceChangedCommand(
                vaadinHotswapperObj);
        this.pluginReady = true;

        // Vaadin 24.5+, can nullify unused commands
        this.updateRouteRegistryCommand = null;
        this.reloadCommand = null;
        this.clearReflectionCache = null;
        this.addedClasses = null;
        this.modifiedClasses = null;
    }

    public void vaadinServiceInitialized() {
        pluginReady = true;
    }

    public void vaadinServiceDestroyed() {
        pluginReady = false;
    }

    private boolean isVaadin_24_5_orNewer() {
        return vaadinHotswapperObj != null;
    }

    private boolean isPluginReady(String message) {
        if (!pluginReady && LOGGER.isLevelEnabled(AgentLogger.Level.TRACE)) {
            LOGGER.trace("Plugin not ready. {}", message);
        }
        return pluginReady;
    }

    private boolean isWatchedPackage(String packageName) {
        return watchedPackages.isEmpty() || watchedPackages.stream().anyMatch(packageName::startsWith);
    }

    @OnClassLoadEvent(classNameRegexp = ".*", events = {LoadEvent.DEFINE, LoadEvent.REDEFINE})
    public void onClassLoadEvent(LoadEvent event, CtClass ctClass) throws Exception {
        String className = ctClass.getName();
        if (!isPluginReady(event + " for class " + className)) {
            return;
        }
        if (!isWatchedPackage(ctClass.getPackageName())) {
            LOGGER.trace("Ignoring class {} because it is not in the watched-packages list", className);
            return;
        }
        if (isVaadin_24_5_orNewer()) {
            LOGGER.debug("Reloading class {} because of {}", className,
                    event);
            ReflectionCommand command = new ReflectionCommand(vaadinHotswapperObj,
                    "onHotswap", new String[]{className},
                    event == REDEFINE);
            scheduler.scheduleCommand(command, reloadQuietTime);
        } else if (event == LoadEvent.REDEFINE) {
            LOGGER.debug("Redefined class {}, clearing Vaadin reflection cache and reloading browser", className);
            scheduler.scheduleCommand(clearReflectionCache);
            scheduler.scheduleCommand(reloadCommand, reloadQuietTime);
        }
    }

    @OnClassFileEvent(classNameRegexp = ".*", events = {FileEvent.CREATE, FileEvent.MODIFY})
    public void classCreated(FileEvent eventType, CtClass ctClass) throws Exception {
        if (!isPluginReady(eventType + " for class " + ctClass.getName())) {
            return;
        }
        if (!isVaadin_24_5_orNewer()) {
            if (FileEvent.CREATE.equals(eventType)) {
                LOGGER.debug("Create class file event for " + ctClass.getName());
                addedClasses.add(resolveClass(ctClass.getName()));
            } else if (FileEvent.MODIFY.equals(eventType)) {
                LOGGER.debug("Modify class file event for " + ctClass.getName());
                modifiedClasses.add(resolveClass(ctClass.getName()));
            }
            // Note that scheduling multiple calls to the same command postpones it
            scheduler.scheduleCommand(updateRouteRegistryCommand);
        }
    }

    @OnResourceFileEvent(path = "/")
    public void resourceChanged(URI uri, FileEvent event) {
        if (isVaadin_24_5_orNewer() && !uri.getPath().endsWith(".class") && isPluginReady(event + " for resource " + uri)) {
            LOGGER.trace("Resource {} {}", uri, event);
            resourceChangedCommand.registerEvent(event, uri);
            scheduler.scheduleCommand(resourceChangedCommand, 200);
        }
    }


    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

    private class UpdateRoutesCommand extends ReflectionCommand {
        private final Object vaadinIntegration;

        UpdateRoutesCommand(Object vaadinIntegration) {
            super(vaadinIntegration, "updateRoutes", addedClasses, modifiedClasses);
            this.vaadinIntegration = vaadinIntegration;
        }

        // NOTE: Identity equality semantics

        @Override
        public boolean equals(Object that) {
            return this == that;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(vaadinIntegration);
        }

        @Override
        public void executeCommand() {
            super.executeCommand();
            addedClasses.clear();
            modifiedClasses.clear();
        }
    }

    private static class ResourceChangedCommand extends ReflectionCommand {

        Map<FileEvent, List<URI>> changedFiles = new ConcurrentHashMap<>();

        public ResourceChangedCommand(Object hotswapper) {
            super(hotswapper, "onHotswap");
            changedFiles.put(CREATE, new ArrayList<>());
            changedFiles.put(MODIFY, new ArrayList<>());
            changedFiles.put(DELETE, new ArrayList<>());
        }

        @Override
        public List<Object> getParams() {
            List<URI> modifiedFiles = changedFiles.get(MODIFY);
            changedFiles.get(CREATE).removeIf(modifiedFiles::contains);
            changedFiles.get(DELETE).removeIf(modifiedFiles::contains);
            List<Object> parameters = new ArrayList<>(3);
            parameters.add(changedFiles.get(CREATE).toArray(new URI[0]));
            parameters.add(modifiedFiles.toArray(new URI[0]));
            parameters.add(changedFiles.get(DELETE).toArray(new URI[0]));
            return parameters;
        }

        @Override
        public void executeCommand() {
            super.executeCommand();
            changedFiles.values().forEach(List::clear);
        }

        void registerEvent(FileEvent event, URI uri) {
            changedFiles.computeIfAbsent(event, k -> new ArrayList<>())
                    .add(uri);
        }

    }

}
