/*
 * Copyright 2013-2026 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.jersey2;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.LoadEvent;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.command.Command;
import org.hotswap.agent.command.Scheduler;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtField;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.CtNewMethod;
import org.hotswap.agent.javassist.Modifier;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.AnnotationHelper;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

@Plugin(name = "Jersey2",
        description = "Jersey2 framework plugin - this does not handle HK2 changes",
        testedVersions = {"2.10.1"},
        expectedVersions = {"2.10.1"})
public class Jersey2Plugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(Jersey2Plugin.class);

    private static final String RELOAD_PENDING_FIELD = "__haReloadPending";
    private static final String MARK_FOR_RELOAD_METHOD = "__haMarkForReload";
    private static final String RELOAD_METHOD = "__haReload";
    private static final String TEMPLATE_CONFIG_FIELD = "__haTemplateConfig";
    private static final String FAILED_RECREATIONS_FIELD = "__haFailedRecreations";
    private static final String STOPPED_TEMPLATE_CONFIG_FIELD = "__haStoppedTemplateConfig";
    private static final String APPLICATION_REDEFINED_METHOD = "__haApplicationRedefined";
    private static final String APPLICATION_REDEFINED_FIELD = "__haApplicationRedefinedFlag";
    private static final String SHUT_DOWN_FIELD = "__haShutDown";
    private static final String JERSEY_RELOAD_METHOD = "__haJerseyReload";
    private static final String OWN_REGISTRATIONS_FIELD = "__haOwnRegistrations";
    // each failed attempt leaves a service locator behind, so stop after a few in a row
    static final int MAX_FAILED_RECREATIONS = 3;

    // CDI normal scopes, the meta-annotation of custom ones and stereotypes, without the javax or jakarta prefix
    private static final String[] CDI_SCOPES = {"enterprise.context.ApplicationScoped",
            "enterprise.context.RequestScoped", "enterprise.context.SessionScoped",
            "enterprise.context.ConversationScoped", "enterprise.context.NormalScope", "enterprise.inject.Stereotype"};

    /**
     * Returned by newApplicationConfig() for an application that is never created again, reloaded with a copy.
     */
    public static final Object NOT_RECREATABLE = new Object();
    private static final String APPLICATION_CLASS_FIELD = "__haApplicationClass";
    private static final String OWN_PROPERTIES_FIELD = "__haOwnProperties";
    private static final String OWN_RESOURCES_FIELD = "__haOwnResources";
    private static final String OWN_CLASS_LOADER_FIELD = "__haOwnClassLoader";
    private static final String OWN_APPLICATION_NAME_FIELD = "__haOwnApplicationName";
    private static final String OWN_RESOURCE_FINDERS_FIELD = "__haOwnResourceFinders";
    private static final String WRAPPING_RESOURCE_CONFIG =
            "org.glassfish.jersey.server.ResourceConfig$WrappingResourceConfig";
    private static final String SERVLET_PACKAGE = "org.glassfish.jersey.servlet.";

    @Init
    Scheduler scheduler;

    @Init
    ClassLoader appClassLoader;

    // also used by the request threads, which register the classes of a reloaded container
    Set<Object> registeredJerseyContainers =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
//    Set<Object> registeredServiceLocators = Collections.newSetFromMap(new WeakHashMap<Object, Boolean>());
    Set<Class<?>> allRegisteredClasses =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /**
     *  Initialize the plugin when Jersey's ServletContainer.init(WebConfig config) is called.  This is called from both init() for a servlet
     *  and init(Config) for a filter.
     *
     *  Also, add the ServletContainer to a list of registeredJerseyContainers so that we can call reload on it later when classes change
     */
    @OnClassLoadEvent(classNameRegexp = "org.glassfish.jersey.servlet.ServletContainer")
    public static void jerseyServletCallInitialized(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException {
        CtMethod init = ctClass.getDeclaredMethod("init", new CtClass[] { classPool.get("org.glassfish.jersey.servlet.WebConfig") });
        init.insertBefore(PluginManagerInvoker.buildInitializePlugin(Jersey2Plugin.class));
        LOGGER.info("org.glassfish.jersey.servlet.ServletContainer enhanced with plugin initialization.");

        String registerThis = PluginManagerInvoker.buildCallPluginMethod(Jersey2Plugin.class, "registerJerseyContainer", "this",
                "java.lang.Object", "getConfiguration()", "java.lang.Object"/*, "getApplicationHandler().getServiceLocator()", "java.lang.Object"*/);
        init.insertAfter(registerThis);

        // the config to create the application again from on reload, if Jersey created it from its class
        ctClass.addField(CtField.make("private volatile Object " + TEMPLATE_CONFIG_FIELD + ";", ctClass));
        ctClass.addField(CtField.make("private volatile int " + FAILED_RECREATIONS_FIELD + ";", ctClass));
        ctClass.addField(CtField.make("private volatile Object " + STOPPED_TEMPLATE_CONFIG_FIELD + ";", ctClass));
        ctClass.addField(CtField.make("private volatile boolean " + APPLICATION_REDEFINED_FIELD + ";", ctClass));
        // the application of the current web component was shut down by a reload that failed
        ctClass.addField(CtField.make("private volatile boolean " + SHUT_DOWN_FIELD + ";", ctClass));
        // reload() with the current config, which newer Jersey versions pass as a copy, tracked per thread
        CtMethod reloadCurrent = ctClass.getDeclaredMethod("reload", new CtClass[0]);
        reloadCurrent.insertBefore(Jersey2Plugin.class.getName() + ".enterCurrentReload();");
        reloadCurrent.insertAfter(Jersey2Plugin.class.getName() + ".exitCurrentReload();", true);
        // an application a failed reload shut down already is not shut down again
        CtMethod destroy = ctClass.getDeclaredMethod("destroy", new CtClass[0]);
        // after a reload in progress, which holds the container lock
        destroy.insertBefore("synchronized (this) { if (this." + SHUT_DOWN_FIELD + ") { containerListener = null; } }");
        // also when the same servlet instance is initialized again, e.g. by a restarted context
        init.insertAfter("this." + TEMPLATE_CONFIG_FIELD + " = " + Jersey2Plugin.class.getName()
                + ".getTemplateConfig(getConfiguration()); "
                + "this." + STOPPED_TEMPLATE_CONFIG_FIELD + " = null; "
                + "this." + FAILED_RECREATIONS_FIELD + " = 0; "
                + "this." + APPLICATION_REDEFINED_FIELD + " = false; "
                + "this." + SHUT_DOWN_FIELD + " = false;");
        String registerReloaded = PluginManagerInvoker.buildCallPluginMethod(Jersey2Plugin.class,
                "registerReloadedJerseyContainer", "getConfiguration()", "java.lang.Object");

        // reload(ResourceConfig) of Jersey, wrapped:
        // - workaround a Jersey issue where ServletContainer cannot be reloaded since it is in an immutable state: the
        //   config is copied, except a fresh config created from an application class, Jersey creates the application
        //   from it
        // - after a successful reload with another config than the current one (by the application itself), the
        //   application is created again from the template of that config, if any; reload() keeps the template
        // - the old application is shut down once by the wrapper, not again by Jersey nor after a failed reload;
        //   the web component is replaced only by a successful reload
        // - the classes of the application the container is reloaded with are watched
        // It runs under the container lock, which a reload by the plugin holds too.
        CtMethod reload = ctClass.getDeclaredMethod("reload",
                new CtClass[] { classPool.get("org.glassfish.jersey.server.ResourceConfig") });
        ctClass.addMethod(CtNewMethod.copy(reload, JERSEY_RELOAD_METHOD, ctClass, null));
        reload.setBody("{ synchronized (this) { "
                + "boolean haOther = $1 != getConfiguration() && !" + Jersey2Plugin.class.getName()
                + "    .isCurrentReload(); "
                + "if (!" + Jersey2Plugin.class.getName() + ".isUnusedApplicationConfig($1)) { "
                + "  $1 = new org.glassfish.jersey.server.ResourceConfig($1); "
                + "} "
                // the application is shut down here once, Jersey gets a listener that does nothing
                + "org.glassfish.jersey.server.spi.ContainerLifecycleListener haListener = containerListener; "
                + "if (!this." + SHUT_DOWN_FIELD + ") { "
                + "  this." + SHUT_DOWN_FIELD + " = true; "
                + "  try { haListener.onShutdown(this); } "
                + "  catch (Exception e) { " + Jersey2Plugin.class.getName() + ".logShutdownFailed(this, e); } "
                + "  catch (LinkageError e) { " + Jersey2Plugin.class.getName() + ".logShutdownFailed(this, e); } "
                + "} "
                + "containerListener = (org.glassfish.jersey.server.spi.ContainerLifecycleListener) "
                + "    " + Jersey2Plugin.class.getName() + ".noOpLifecycleListener(haListener); "
                + "Object haWebComponent = webComponent; "
                + "try { " + JERSEY_RELOAD_METHOD + "($1); } "
                + "finally { "
                + "  if (webComponent != haWebComponent) { "
                + "    this." + SHUT_DOWN_FIELD + " = false; "
                + "    if (haOther) { "
                // with the checks done at startup, now that the application is created
                + "      this." + TEMPLATE_CONFIG_FIELD + " = " + Jersey2Plugin.class.getName()
                + "          .getTemplateConfig(getConfiguration()); "
                + "      this." + STOPPED_TEMPLATE_CONFIG_FIELD + " = null; "
                + "      this." + FAILED_RECREATIONS_FIELD + " = 0; "
                + "    } "
                + "  } else { "
                // the reload failed, the old web component with its shut down application stays
                + "    containerListener = haListener; "
                + "  } "
                + "} "
                + registerReloaded
                + "} }");

        // The container is reloaded at the start of the next request, in the request thread. There the application
        // server provides the same context as at startup, e.g. java:comp/BeanManager used by Jersey CDI on Payara.
        ctClass.addField(CtField.make("private volatile boolean " + RELOAD_PENDING_FIELD + ";", ctClass));
        ctClass.addMethod(CtNewMethod.make("public void " + MARK_FOR_RELOAD_METHOD + "() { "
                + "this." + RELOAD_PENDING_FIELD + " = true; }", ctClass));
        // As reload(ResourceConfig): the old application is shut down, then the new web component is created, with a
        // config created from the template, so the application is created again. Without a template reload() is
        // called as before. If that fails, the web component is
        // created with a copy of the current config as before. The template is replaced only by a config the
        // application was created from. After MAX_FAILED_RECREATIONS failures in a row (a failed attempt leaves its
        // service locator behind, it cannot be reached from here) it is put aside until the application class is
        // redefined. A subclass overriding reload(ResourceConfig) is not called when the application is created again,
        // calling it would create the application twice. The template keeps the application it created until the
        // next one is created.
        String plugin = Jersey2Plugin.class.getName();
        ctClass.addMethod(CtNewMethod.make("public void " + RELOAD_METHOD + "() { "
                // the application class was redefined, so it may be created again if the attempts stopped
                + "if (this." + APPLICATION_REDEFINED_FIELD + ") { "
                + "  this." + APPLICATION_REDEFINED_FIELD + " = false; "
                + "  this." + FAILED_RECREATIONS_FIELD + " = 0; "
                + "  if (this." + STOPPED_TEMPLATE_CONFIG_FIELD + " != null) { "
                + "    this." + TEMPLATE_CONFIG_FIELD + " = this." + STOPPED_TEMPLATE_CONFIG_FIELD + "; "
                + "    this." + STOPPED_TEMPLATE_CONFIG_FIELD + " = null; "
                + "  } "
                + "} "
                + "Object haTemplate = this." + TEMPLATE_CONFIG_FIELD + "; "
                // a subclass overriding reload is reloaded as before
                + "if (haTemplate != null && " + plugin + ".overridesReload(this)) { haTemplate = null; } "
                + "Object haConfig = null; "
                + "if (haTemplate != null) { "
                + "  haConfig = " + plugin + ".newApplicationConfig(this, haTemplate, "
                + "      this." + FAILED_RECREATIONS_FIELD + " + 1); "
                + "  if (haConfig == " + plugin + ".NOT_RECREATABLE) { "
                + "    this." + TEMPLATE_CONFIG_FIELD + " = null; haTemplate = null; haConfig = null; "
                + "  } "
                + "} "
                // no template: reloaded by reload() as before, the state of the template is kept, as reload() would
                // take a template from the config it reloads with
                + "if (haTemplate == null "
                + "    && (!this." + SHUT_DOWN_FIELD + " || " + plugin + ".overridesReload(this))) { "
                + "  Object haKeptTemplate = this." + TEMPLATE_CONFIG_FIELD + "; "
                + "  Object haStopped = this." + STOPPED_TEMPLATE_CONFIG_FIELD + "; "
                + "  int haFailed = this." + FAILED_RECREATIONS_FIELD + "; "
                + "  try { reload(); } "
                + "  catch (Exception e) { " + plugin + ".logReloadWithCopyFailed(this, e); } "
                + "  catch (LinkageError e) { " + plugin + ".logReloadWithCopyFailed(this, e); } "
                + "  finally { "
                + "    this." + TEMPLATE_CONFIG_FIELD + " = haKeptTemplate; "
                + "    this." + STOPPED_TEMPLATE_CONFIG_FIELD + " = haStopped; "
                + "    this." + FAILED_RECREATIONS_FIELD + " = haFailed; "
                + "  } "
                // reload(ResourceConfig) registered the classes of the reloaded container
                + "  return; "
                + "} "
                + "org.glassfish.jersey.server.ResourceConfig haCopy = "
                + "    new org.glassfish.jersey.server.ResourceConfig(getConfiguration()); "
                // not again if a failed reload shut it down already
                + "if (!this." + SHUT_DOWN_FIELD + ") { "
                + "  this." + SHUT_DOWN_FIELD + " = true; "
                + "  try { containerListener.onShutdown(this); } "
                + "  catch (Exception e) { " + plugin + ".logShutdownFailed(this, e); } "
                + "  catch (LinkageError e) { " + plugin + ".logShutdownFailed(this, e); } "
                + "} "
                + "org.glassfish.jersey.servlet.WebComponent haWebComponent = null; "
                + "if (haConfig != null) { "
                + "  try { haWebComponent = new org.glassfish.jersey.servlet.WebComponent(webComponent.webConfig, "
                + "      (org.glassfish.jersey.server.ResourceConfig) haConfig); } "
                + "  catch (Exception e) { " + plugin + ".logReloadFailed(this, e, "
                + "      this." + FAILED_RECREATIONS_FIELD + " + 1); } "
                + "  catch (LinkageError e) { " + plugin + ".logReloadFailed(this, e, "
                + "      this." + FAILED_RECREATIONS_FIELD + " + 1); } "
                + "} "
                + "boolean haRecreated = haWebComponent != null; "
                + "if (haRecreated) { "
                + "  this." + FAILED_RECREATIONS_FIELD + " = 0; "
                + "} else { "
                + "  boolean haCounted = haTemplate != null; "
                + "  try { haWebComponent = new org.glassfish.jersey.servlet.WebComponent(webComponent.webConfig, "
                + "      haCopy); } "
                + "  catch (Exception e) { " + plugin + ".logReloadWithCopyFailed(this, e); } "
                + "  catch (LinkageError e) { " + plugin + ".logReloadWithCopyFailed(this, e); } "
                // a failed attempt counts only if the copy works, else the cause is elsewhere (e.g. a resource)
                + "  if (haWebComponent != null && haCounted) { "
                + "    this." + FAILED_RECREATIONS_FIELD + " = this." + FAILED_RECREATIONS_FIELD + " + 1; "
                + "    if (this." + FAILED_RECREATIONS_FIELD + " >= " + MAX_FAILED_RECREATIONS + ") { "
                + "      " + plugin + ".logRecreationStopped(this); "
                + "      this." + STOPPED_TEMPLATE_CONFIG_FIELD + " = haTemplate; "
                + "      this." + TEMPLATE_CONFIG_FIELD + " = null; "
                + "    } "
                + "  } "
                + "} "
                + "if (haWebComponent != null) { "
                + "  webComponent = haWebComponent; "
                + "  containerListener = haWebComponent.appHandler; "
                + "  this." + SHUT_DOWN_FIELD + " = false; "
                + "  try { "
                + "    containerListener.onReload(this); "
                + "    containerListener.onStartup(this); "
                + "  } "
                + "  catch (Exception e) { " + plugin + ".logStartupFailed(this, e); } "
                + "  catch (LinkageError e) { " + plugin + ".logStartupFailed(this, e); } "
                + "} "
                // the config the application was created from, with the checks done at startup (e.g. CDI)
                + "if (haRecreated) { "
                + "  this." + TEMPLATE_CONFIG_FIELD + " = " + plugin + ".getTemplateConfig(getConfiguration()); "
                + "} "
                // the application created again may register other classes
                + registerReloaded
                + "}", ctClass));
        // the application class is redefined, applied by the next reload; called by the thread redefining the class,
        // so it must not wait for the container, which may be reloading
        ctClass.addMethod(CtNewMethod.make("public void " + APPLICATION_REDEFINED_METHOD + "(Class c) { "
                + "Object haTemplate = this." + TEMPLATE_CONFIG_FIELD + "; "
                + "Object haStopped = this." + STOPPED_TEMPLATE_CONFIG_FIELD + "; "
                + "if ((haTemplate != null && " + plugin + ".isTemplateConfigOf(haTemplate, c)) "
                + "    || (haStopped != null && " + plugin + ".isTemplateConfigOf(haStopped, c))) { "
                + "  this." + APPLICATION_REDEFINED_FIELD + " = true; "
                + "} }", ctClass));
        String reloadIfPending = "if (this." + RELOAD_PENDING_FIELD + ") { synchronized (this) { "
                + "if (this." + RELOAD_PENDING_FIELD + ") { this." + RELOAD_PENDING_FIELD + " = false; "
                + RELOAD_METHOD + "(); } } }";
        for (CtMethod method : ctClass.getDeclaredMethods()) {
            if (isRequestEntryPoint(method)) {
                method.insertBefore(reloadIfPending);
            }
        }
    }

    // service(ServletRequest, ServletResponse) and doFilter(ServletRequest, ServletResponse, FilterChain),
    // javax or jakarta
    private static boolean isRequestEntryPoint(CtMethod method) throws NotFoundException {
        if (!Modifier.isPublic(method.getModifiers())) {
            return false;
        }
        CtClass[] params = method.getParameterTypes();
        boolean service = method.getName().equals("service") && params.length == 2;
        boolean doFilter = method.getName().equals("doFilter") && params.length == 3;
        return (service || doFilter) && params[0].getSimpleName().equals("ServletRequest");
    }

    /**
     * Keep the application class a WrappingResourceConfig is created with, Jersey clears it when it creates the
     * application, and a config created for an application instance looks the same afterwards. The fields for its own
     * state are set by keepOwnRegistrations().
     */
    @OnClassLoadEvent(classNameRegexp = "org.glassfish.jersey.server.ResourceConfig.WrappingResourceConfig",
            events = LoadEvent.DEFINE)
    public static void keepApplicationClass(CtClass ctClass, ClassPool classPool)
            throws NotFoundException, CannotCompileException {
        ctClass.addField(CtField.make("public Class " + APPLICATION_CLASS_FIELD + ";", ctClass));
        ctClass.addField(CtField.make("public java.util.Map " + OWN_PROPERTIES_FIELD + ";", ctClass));
        ctClass.addField(CtField.make("public java.util.Set " + OWN_RESOURCES_FIELD + ";", ctClass));
        ctClass.addField(CtField.make("public ClassLoader " + OWN_CLASS_LOADER_FIELD + ";", ctClass));
        ctClass.addField(CtField.make("public String " + OWN_APPLICATION_NAME_FIELD + ";", ctClass));
        ctClass.addField(CtField.make("public java.util.Set " + OWN_RESOURCE_FINDERS_FIELD + ";", ctClass));
        ctClass.addField(CtField.make("public java.util.List " + OWN_REGISTRATIONS_FIELD + ";", ctClass));
        for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
            constructor.insertAfter("this." + APPLICATION_CLASS_FIELD + " = $2;");
        }
    }

    /**
     * Keep what was registered on a config before the servlet container providers add their own registrations to it.
     */
    @OnClassLoadEvent(classNameRegexp = "org.glassfish.jersey.servlet.WebComponent", events = LoadEvent.DEFINE)
    public static void keepOwnRegistrations(CtClass ctClass) throws NotFoundException, CannotCompileException {
        for (CtMethod method : ctClass.getDeclaredMethods("configure")) {
            method.insertBefore(Jersey2Plugin.class.getName() + ".keepOwnRegistrations($1);");
        }
    }

    /**
     *  Fix a scanning issue with jersey pre-2.4 versions.  https://java.net/jira/browse/JERSEY-1936
     */
    @OnClassLoadEvent(classNameRegexp = "org.glassfish.jersey.server.internal.scanning.AnnotationAcceptingListener")
    public static void fixAnnoationAcceptingListener(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod process = ctClass.getDeclaredMethod("process");
        process.insertAfter("try { $2.close(); } catch (Exception e) {}");
    }

    /**
     * Fix CDI CDI_MULTIPLE_LOCATORS_INTO_SIMPLE_APP exception on class redefinition
     */
    @OnClassLoadEvent(classNameRegexp = "org.glassfish.jersey.ext.cdi1x.internal.SingleHk2LocatorManager")
    public static void fixSingleHk2LocatorManager(CtClass ctClass) throws NotFoundException, CannotCompileException {
        CtMethod process = ctClass.getDeclaredMethod("registerLocator");
        process.insertBefore("if (this.locator != null) return;");
        LOGGER.debug("SingleHk2LocatorManager : patched()");
    }

    /**
     * Register the jersey container and the classes involved in configuring the Jersey Application
     */
    public void registerJerseyContainer(Object jerseyContainer, Object resourceConfig/*, Object serviceLocator*/) {
        try {
            Class<?> resourceConfigClass = resolveClass("org.glassfish.jersey.server.ResourceConfig");

            LOGGER.info("Jersey2Plugin - registerJerseyContainer : " + jerseyContainer.getClass().getName());

            Set<Class<?>> containerClasses = getContainerClasses(resourceConfigClass, resourceConfig);
            Class<?> applicationClass = getCreatedApplicationClass(resourceConfig);
            if (applicationClass != null) {
                // a change of the application is applied by creating it again on reload
                containerClasses.add(applicationClass);
            }

            registeredJerseyContainers.add(jerseyContainer);
            allRegisteredClasses.addAll(containerClasses);
            /*
            if (serviceLocator != null) {
                registeredServiceLocators.add(serviceLocator);
            }
            */

            LOGGER.debug("registerJerseyContainer : finished");
        } catch (Exception e) {
            LOGGER.error("Error registering Jersey Container.", e);
        }
    }

    /**
     * The class Jersey created the application from at startup, or null.
     */
    private static Class<?> getCreatedApplicationClass(Object runtimeConfig) {
        try {
            Object template = getTemplateConfig(runtimeConfig);
            return template != null ? (Class<?>) ReflectionHelper.get(template, APPLICATION_CLASS_FIELD) : null;
        } catch (Exception e) {
            LOGGER.debug("Unable to get the application class of Jersey config {}.", e, runtimeConfig);
            return null;
        }
    }

    /**
     * Register the classes of a reloaded Jersey container, the application created again may register other classes.
     * Called by the container in the request thread, the classes are those Jersey has just found, no new scan.
     *
     * @param resourceConfig the runtime config of the reloaded container
     */
    public void registerReloadedJerseyContainer(Object resourceConfig) {
        try {
            Class<?> resourceConfigClass = Class.forName("org.glassfish.jersey.server.ResourceConfig", false,
                    resourceConfig.getClass().getClassLoader());
            @SuppressWarnings("unchecked")
            Set<Class<?>> classes = (Set<Class<?>>) resourceConfigClass.getMethod("getClasses").invoke(resourceConfig);
            allRegisteredClasses.addAll(classes);
            // the application class of the template, also one the application reloaded the container with
            Class<?> applicationClass = getCreatedApplicationClass(resourceConfig);
            if (applicationClass != null) {
                allRegisteredClasses.add(applicationClass);
            }
            LOGGER.debug("Registered {} classes of the reloaded Jersey container.", classes.size());
        } catch (Exception e) {
            LOGGER.debug("Unable to register the classes of the reloaded Jersey container.", e);
        }
    }

    /**
     * Gets a list of classes used in configure the Jersey Application
     */
    private Set<Class<?>> getContainerClasses(Class<?> resourceConfigClass, Object resourceConfig)
                throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        Method scanClassesMethod = resourceConfigClass.getDeclaredMethod("scanClasses");
        scanClassesMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Class<?>> scannedClasses = (Set<Class<?>>) scanClassesMethod.invoke(resourceConfig);

        Method getRegisteredClassesMethod = resourceConfigClass.getDeclaredMethod("getRegisteredClasses");
        getRegisteredClassesMethod.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<Class<?>> registeredClasses = (Set<Class<?>>)getRegisteredClassesMethod.invoke(resourceConfig);

        Set<Class<?>> containerClasses = Collections.newSetFromMap(new WeakHashMap<>());
        containerClasses.addAll(scannedClasses);
        containerClasses.addAll(registeredClasses);
        return containerClasses;
    }

    /**
     * Call reload on the jersey Application when any class changes that is either involved in configuring
     * the Jersey Application, or if was newly annotated and will be involved in configuring the application.
     */
    @OnClassLoadEvent(classNameRegexp = ".*", events = LoadEvent.REDEFINE)
    public void invalidate(CtClass ctClass, Class original) throws Exception {
        boolean reloaded = false;
        if (allRegisteredClasses.contains(original)) {
            notifyApplicationRedefined(original);
            scheduler.scheduleCommand(reloadJerseyContainers);
            reloaded = true;
        } else {
            // TODO: When a class is not annotated at startup, and is annotated during debug, it never gets found
            // here.  Is this a DCEVM issue?  Also, the Jersey Container  does not find the newly annotated class
            // during a reload called from reloadJerseyContainers, so this seems like the annotation is not being
            // added
            if (AnnotationHelper.hasAnnotation(original, "javax.ws.rs.Path")
                    || AnnotationHelper.hasAnnotation(ctClass, "javax.ws.rs.Path")
                    || AnnotationHelper.hasAnnotation(original, "jakarta.ws.rs.Path")
                    || AnnotationHelper.hasAnnotation(ctClass, "jakarta.ws.rs.Path")) {
                allRegisteredClasses.add(original);
                scheduler.scheduleCommand(reloadJerseyContainers);
                reloaded = true;
            }

        }
        if (!reloaded) {
            // reload if HK2 Service class is changed
            if (AnnotationHelper.hasAnnotation(original, "org.jvnet.hk2.annotations.Service")
                    || AnnotationHelper.hasAnnotation(ctClass, "org.jvnet.hk2.annotations.Service")) {

                scheduler.scheduleCommand(reloadJerseyContainers);
                // TODO : reload SystemDescriptor in case of Service change?
                // scheduler.scheduleCommand(disposeReflectionCaches);
            }
        }
    }

    // a container that stopped creating its application again tries again if the application class is redefined
    private void notifyApplicationRedefined(Class<?> original) {
        synchronized (registeredJerseyContainers) {
            for (Object jerseyContainer : registeredJerseyContainers) {
                try {
                    jerseyContainer.getClass().getMethod(APPLICATION_REDEFINED_METHOD, Class.class)
                            .invoke(jerseyContainer, original);
                } catch (Exception e) {
                    LOGGER.debug("Unable to notify Jersey container {} of redefined class {}.", e, jerseyContainer,
                            original.getName());
                }
            }
        }
    }

    /**
     * Mark the Jersey containers for reload. Each container reloads itself at the start of its next request.
     */
    private Command reloadJerseyContainers = new Command() {
        public void executeCommand() {
            try {
                synchronized (registeredJerseyContainers) {
                    for (Object jerseyContainer : registeredJerseyContainers) {
                        try {
                            jerseyContainer.getClass().getMethod(MARK_FOR_RELOAD_METHOD).invoke(jerseyContainer);
                        } catch (Exception e) {
                            LOGGER.error("Error reloading Jersey Container {}.", e, jerseyContainer);
                        }
                    }
                }
                LOGGER.info("Jersey Containers will be reloaded on the next request");
            } catch (Exception e) {
                LOGGER.error("Error reloading Jersey Container.", e);
            }
        }
    };

    /**
     * Dispose service locators reflection caches
     */
    /*
    private Command disposeReflectionCaches = new Command() {
        public void executeCommand() {
            if (!registeredServiceLocators.isEmpty()) {
                try {
                    LOGGER.debug("Disposing reflection caches");
                    for (Object serviceLocator : registeredServiceLocators) {
                        ReflectionHelper.invoke(serviceLocator, serviceLocator.getClass(), "clearReflectionCache", new Class[]{});
                    }
                    LOGGER.info("Reflection caches disposed.");
                } catch (Exception e) {
                    LOGGER.error("executeCommand() exception {}.", e.getMessage());
                }
            }
        }
    };
    */

    /**
     * The config to create the application again from on reload: the config Jersey created the application from at
     * startup (from the init parameter, @ApplicationPath or the servlet registration), or the config the container is
     * reloaded with if it is such a config. Null for a config or an application given as an instance (e.g. by Spring
     * Boot) and for an application provided by CDI, which Jersey does not create again: those are reloaded with a copy
     * of the current config as before.
     *
     * @param config the runtime config of the container, or the config it is reloaded with
     * @return the template config, or null
     */
    public static Object getTemplateConfig(Object config) {
        try {
            Object templateConfig = config;
            if (!isWrappingResourceConfig(templateConfig)) {
                templateConfig = Class.forName("org.glassfish.jersey.server.ResourceConfig", false,
                        config.getClass().getClassLoader()).getMethod("getApplication").invoke(config);
            }
            if (!isWrappingResourceConfig(templateConfig)) {
                return null;
            }
            Class<?> applicationClass = (Class<?>) ReflectionHelper.get(templateConfig, APPLICATION_CLASS_FIELD);
            if (applicationClass == null || isProvidedByCdi(applicationClass)) {
                return null;
            }
            // created by a provider as a subclass or proxy of the application class
            Object application = Class.forName("org.glassfish.jersey.server.ResourceConfig", false,
                    templateConfig.getClass().getClassLoader()).getMethod("getApplication").invoke(templateConfig);
            if (application != null && application.getClass() != applicationClass) {
                return null;
            }
            return templateConfig;
        } catch (Exception e) {
            LOGGER.debug("Unable to get the template config of Jersey config {}.", e, config);
            return null;
        }
    }

    /**
     * Checks if the config was created from an application class and Jersey did not create the application from it
     * yet, so it is used as it is, not copied: a copy would lose the application class.
     *
     * @param config the config a container is reloaded with
     * @return true for such a config
     */
    public static boolean isUnusedApplicationConfig(Object config) {
        try {
            return isWrappingResourceConfig(config) && ReflectionHelper.get(config, APPLICATION_CLASS_FIELD) != null
                    && Class.forName("org.glassfish.jersey.server.ResourceConfig", false,
                    config.getClass().getClassLoader()).getMethod("getApplication").invoke(config) == null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWrappingResourceConfig(Object config) {
        return config != null && WRAPPING_RESOURCE_CONFIG.equals(config.getClass().getName());
    }

    // a CDI bean, which Jersey gets from CDI as the same instance again instead of creating it: a normal scope or a
    // stereotype on the class or a superclass, also as meta-annotation of a custom scope. Not @Dependent, a new
    // instance for each lookup, and not JSR-330 @Singleton, HK2 creates such an application itself.
    private static boolean isProvidedByCdi(Class<?> applicationClass) {
        for (Class<?> c = applicationClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Annotation annotation : c.getAnnotations()) {
                if (isCdiAnnotation(annotation.annotationType())) {
                    return true;
                }
                for (Annotation meta : annotation.annotationType().getAnnotations()) {
                    if (isCdiAnnotation(meta.annotationType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isCdiAnnotation(Class<?> annotationType) {
        String name = annotationType.getName();
        for (String scope : CDI_SCOPES) {
            if (name.equals("javax." + scope) || name.equals("jakarta." + scope)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The resource finders registered on a config, except those the servlet container registers itself. Called by
     * WrappingResourceConfig.
     *
     * @param resourceConfig the config
     * @return the resource finders
     */
    public static Set<Object> getResourceFinders(Object resourceConfig) {
        Set<Object> resourceFinders = new HashSet<>();
        try {
            Class<?> resourceConfigClass = Class.forName("org.glassfish.jersey.server.ResourceConfig", false,
                    resourceConfig.getClass().getClassLoader());
            Object state = ReflectionHelper.get(resourceConfig, resourceConfigClass, "state");
            for (Object resourceFinder : (Collection<?>) ReflectionHelper.get(state, "resourceFinders")) {
                if (!resourceFinder.getClass().getName().startsWith(SERVLET_PACKAGE)) {
                    resourceFinders.add(resourceFinder);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to get the resource finders of Jersey config {}.", e, resourceConfig);
        }
        return resourceFinders;
    }

    /**
     * Create a new config from the template the same way Jersey created it: from the application class, with the
     * default classes found by the servlet container initializer, with the classes and instances registered on the
     * template (with their contracts and priorities) and with its own properties, resources, resource finders, class
     * loader and name.
     * Jersey then creates the application again with the new service locator, so the bindings the application adds to
     * the locator in its constructor (e.g. the HK2 Guice bridge) are there after reload too. A copy of the current
     * config keeps only its state and drops the application. Called by the container in the request thread.
     *
     * @param jerseyContainer the ServletContainer
     * @param template the config the application was created from last time, see getTemplateConfig()
     * @param failures the number of failures in a row if it fails, for logging
     * @return the new config, null if it cannot be created, or NOT_RECREATABLE if the application is never created
     *         again
     */
    public static Object newApplicationConfig(Object jerseyContainer, Object template, int failures) {
        if (template == null) {
            return null;
        }
        Class<?> applicationClass = null;
        try {
            ClassLoader jerseyClassLoader = template.getClass().getClassLoader();
            Class<?> resourceConfigClass = Class.forName("org.glassfish.jersey.server.ResourceConfig", false,
                    jerseyClassLoader);
            applicationClass = (Class<?>) ReflectionHelper.get(template, APPLICATION_CLASS_FIELD);
            Set<?> defaultClasses = (Set<?>) ReflectionHelper.get(template, "defaultClasses");
            Object config = resourceConfigClass.getMethod("forApplicationClass", Class.class, Set.class)
                    .invoke(null, applicationClass, defaultClasses);

            // registered on the template before the servlet container providers configured it, with their contracts
            // and priorities
            Method registerClass = resourceConfigClass.getMethod("register", Class.class, Map.class);
            Method registerInstance = resourceConfigClass.getMethod("register", Object.class, Map.class);
            List<?> registrations = (List<?>) ReflectionHelper.get(template, OWN_REGISTRATIONS_FIELD);
            if (registrations == null) {
                // its own state was not kept, it cannot be told from what Jersey merged into it
                LOGGER.debug("The state of Jersey config {} was not kept, the application {} is not created again.",
                        template, applicationClass.getName());
                return NOT_RECREATABLE;
            }
            for (Object registration : registrations) {
                Object component = ((Object[]) registration)[0];
                // a copy, Jersey removes the contracts it rejects
                Object contracts = new HashMap<>((Map<?, ?>) ((Object[]) registration)[1]);
                if (component instanceof Class) {
                    registerClass.invoke(config, component, contracts);
                } else {
                    registerInstance.invoke(config, component, contracts);
                }
            }

            // set on the template itself, before Jersey merged those of the application into it
            resourceConfigClass.getMethod("addProperties", Map.class).invoke(config,
                    ReflectionHelper.get(template, OWN_PROPERTIES_FIELD));
            Object resources = ReflectionHelper.get(template, OWN_RESOURCES_FIELD);
            if (resources != null) {
                resourceConfigClass.getMethod("registerResources", Set.class).invoke(config, resources);
            }
            Object resourceFinders = ReflectionHelper.get(template, OWN_RESOURCE_FINDERS_FIELD);
            if (resourceFinders != null) {
                Method registerFinder = resourceConfigClass.getMethod("registerFinder",
                        Class.forName("org.glassfish.jersey.server.ResourceFinder", false, jerseyClassLoader));
                Method reset = registerFinder.getParameterTypes()[0].getMethod("reset");
                for (Object resourceFinder : (Collection<?>) resourceFinders) {
                    // read to the end by the previous scan
                    try {
                        reset.invoke(resourceFinder);
                    } catch (InvocationTargetException e) {
                        // e.g. UnsupportedOperationException: it would not find its resources again, but a copy of
                        // the config has them registered
                        LOGGER.debug("Resource finder {} cannot be reset, the Jersey application {} is not created "
                                + "again.", e.getCause(), resourceFinder, applicationClass.getName());
                        return NOT_RECREATABLE;
                    }
                    registerFinder.invoke(config, resourceFinder);
                }
            }
            Object classLoader = ReflectionHelper.get(template, OWN_CLASS_LOADER_FIELD);
            if (classLoader != null) {
                resourceConfigClass.getMethod("setClassLoader", ClassLoader.class).invoke(config, classLoader);
            }
            Object applicationName = ReflectionHelper.get(template, OWN_APPLICATION_NAME_FIELD);
            if (applicationName != null) {
                resourceConfigClass.getMethod("setApplicationName", String.class).invoke(config, applicationName);
            }
            return config;
        } catch (Exception e) {
            logReloadFailed(jerseyContainer, e, failures);
            return null;
        }
    }

    /**
     * Keep the state set on a WrappingResourceConfig itself (registrations, properties, resources, resource finders,
     * class loader, name) before the servlet container providers configure it and Jersey merges the application into
     * it, so a config created from it later does not get their stale ones. Called by WebComponent.
     *
     * @param resourceConfig the config of the web component
     */
    public static void keepOwnRegistrations(Object resourceConfig) {
        try {
            if (isWrappingResourceConfig(resourceConfig)
                    && ReflectionHelper.get(resourceConfig, OWN_REGISTRATIONS_FIELD) == null) {
                Class<?> resourceConfigClass = Class.forName("org.glassfish.jersey.server.ResourceConfig", false,
                        resourceConfig.getClass().getClassLoader());
                Class<?> wrapping = resourceConfig.getClass();
                ReflectionHelper.set(resourceConfig, wrapping, OWN_PROPERTIES_FIELD, new HashMap<>(
                        (Map<?, ?>) resourceConfigClass.getMethod("getProperties").invoke(resourceConfig)));
                ReflectionHelper.set(resourceConfig, wrapping, OWN_RESOURCES_FIELD, new HashSet<>(
                        (Set<?>) resourceConfigClass.getMethod("getResources").invoke(resourceConfig)));
                ReflectionHelper.set(resourceConfig, wrapping, OWN_RESOURCE_FINDERS_FIELD,
                        getResourceFinders(resourceConfig));
                ReflectionHelper.set(resourceConfig, wrapping, OWN_CLASS_LOADER_FIELD,
                        resourceConfigClass.getMethod("getClassLoader").invoke(resourceConfig));
                ReflectionHelper.set(resourceConfig, wrapping, OWN_APPLICATION_NAME_FIELD,
                        resourceConfigClass.getMethod("getApplicationName").invoke(resourceConfig));
                // set last, it marks the state as kept
                ReflectionHelper.set(resourceConfig, wrapping, OWN_REGISTRATIONS_FIELD,
                        getRegistrations(resourceConfig));
            }
        } catch (Exception e) {
            LOGGER.debug("Unable to keep the registrations of Jersey config {}.", e, resourceConfig);
        }
    }

    // classes and instances registered on a config, with their contract maps, except those of the servlet container
    private static List<Object[]> getRegistrations(Object resourceConfig) throws Exception {
        ClassLoader jerseyClassLoader = resourceConfig.getClass().getClassLoader();
        Class<?> resourceConfigClass = Class.forName("org.glassfish.jersey.server.ResourceConfig", false,
                jerseyClassLoader);
        Object state = ReflectionHelper.get(resourceConfig, resourceConfigClass, "state");
        Object componentBag = Class.forName("org.glassfish.jersey.model.internal.CommonConfig", false,
                jerseyClassLoader).getMethod("getComponentBag").invoke(state);
        Class<?> componentBagClass = Class.forName("org.glassfish.jersey.model.internal.ComponentBag", false,
                jerseyClassLoader);
        Method getModel = componentBagClass.getMethod("getModel", Class.class);
        Method getContractMap = Class.forName("org.glassfish.jersey.model.ContractProvider", false,
                jerseyClassLoader).getMethod("getContractMap");
        List<Object[]> registrations = new ArrayList<>();
        for (Object registered : (Collection<?>) componentBagClass.getMethod("getClasses").invoke(componentBag)) {
            Class<?> registeredClass = (Class<?>) registered;
            if (!registeredClass.getName().startsWith(SERVLET_PACKAGE)) {
                registrations.add(new Object[]{registeredClass,
                        getContractMap.invoke(getModel.invoke(componentBag, registeredClass))});
            }
        }
        for (Object registered : (Collection<?>) componentBagClass.getMethod("getInstances").invoke(componentBag)) {
            if (!registered.getClass().getName().startsWith(SERVLET_PACKAGE)) {
                registrations.add(new Object[]{registered,
                        getContractMap.invoke(getModel.invoke(componentBag, registered.getClass()))});
            }
        }
        return registrations;
    }

    /**
     * Checks if the container is a subclass overriding reload(), which is called on reload as before.
     *
     * @param jerseyContainer the ServletContainer
     * @return true if a subclass declares reload() or reload(ResourceConfig)
     */
    public static boolean overridesReload(Object jerseyContainer) {
        for (Class<?> c = jerseyContainer.getClass();
             c != null && !c.getName().equals("org.glassfish.jersey.servlet.ServletContainer"); c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (method.getName().equals("reload") && (parameters.length == 0 || parameters.length == 1
                        && parameters[0].getName().equals("org.glassfish.jersey.server.ResourceConfig"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the template config creates the application of the class.
     *
     * @param template the template config
     * @param applicationClass the redefined class
     * @return true if the template creates the application of the class
     */
    public static boolean isTemplateConfigOf(Object template, Class<?> applicationClass) {
        try {
            return ReflectionHelper.get(template, APPLICATION_CLASS_FIELD) == applicationClass;
        } catch (Exception e) {
            return false;
        }
    }

    // threads inside reload() of a container, which reloads it with its current config
    private static final ThreadLocal<Integer> CURRENT_RELOADS = new ThreadLocal<>();

    /**
     * Called by ServletContainer.reload() when it starts.
     */
    public static void enterCurrentReload() {
        Integer reloads = CURRENT_RELOADS.get();
        CURRENT_RELOADS.set(reloads == null ? 1 : reloads + 1);
    }

    /**
     * Called by ServletContainer.reload() when it ends.
     */
    public static void exitCurrentReload() {
        Integer reloads = CURRENT_RELOADS.get();
        if (reloads == null || reloads <= 1) {
            CURRENT_RELOADS.remove();
        } else {
            CURRENT_RELOADS.set(reloads - 1);
        }
    }

    /**
     * Checks if the thread is inside reload(), which reloads the container with its current config.
     *
     * @return true inside reload()
     */
    public static boolean isCurrentReload() {
        return CURRENT_RELOADS.get() != null;
    }

    /**
     * A ContainerLifecycleListener that does nothing, given to Jersey's reload(ResourceConfig) after the container
     * shut down the old application itself.
     *
     * @param listener the current listener, for the interface and its class loader
     * @return the listener that does nothing
     */
    public static Object noOpLifecycleListener(Object listener) throws ClassNotFoundException {
        Class<?> listenerInterface = Class.forName("org.glassfish.jersey.server.spi.ContainerLifecycleListener",
                false, listener.getClass().getClassLoader());
        return Proxy.newProxyInstance(listenerInterface.getClassLoader(), new Class<?>[]{listenerInterface},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        String name = method.getName();
                        return name.equals("equals") ? proxy == args[0]
                                : name.equals("hashCode") ? System.identityHashCode(proxy) : "no-op listener";
                    }
                    return null;
                });
    }

    /**
     * Called by the container if the application throws while it is started after a reload.
     *
     * @param jerseyContainer the ServletContainer
     * @param error the error
     */
    public static void logStartupFailed(Object jerseyContainer, Throwable error) {
        LOGGER.warning("Startup of the Jersey application of container {} after reload failed.", error,
                jerseyContainer);
    }

    /**
     * Called by the container if the application throws while it is shut down for a reload, the reload goes on.
     *
     * @param jerseyContainer the ServletContainer
     * @param error the error
     */
    public static void logShutdownFailed(Object jerseyContainer, Throwable error) {
        LOGGER.warning("Shutdown of the Jersey application of container {} failed.", error, jerseyContainer);
    }

    /**
     * Called by the container if it cannot create a web component with a copy of its config either, as Jersey's
     * reload(ResourceConfig) it then keeps the old web component, whose application is shut down.
     *
     * @param jerseyContainer the ServletContainer
     * @param error the error
     */
    public static void logReloadWithCopyFailed(Object jerseyContainer, Throwable error) {
        LOGGER.error("Reload of Jersey container {} failed.", error, jerseyContainer);
    }

    /**
     * Called by the container if it cannot create a new config or a web component with it. Logged as a warning for
     * the first failure in a row.
     *
     * @param jerseyContainer the ServletContainer
     * @param error the error
     * @param failures the number of failures in a row, including this one
     */
    public static void logReloadFailed(Object jerseyContainer, Throwable error, int failures) {
        if (failures == 1) {
            LOGGER.warning("Unable to create the Jersey application of container {} again, the container is reloaded "
                    + "with a copy of its current config.", error, jerseyContainer);
        } else {
            LOGGER.debug("Unable to create the Jersey application of container {} again ({} times in a row).", error,
                    jerseyContainer, failures);
        }
    }

    /**
     * Called by the container when it stops creating the application again after too many failures in a row.
     *
     * @param jerseyContainer the ServletContainer
     */
    public static void logRecreationStopped(Object jerseyContainer) {
        LOGGER.warning("The Jersey application of container {} could not be created again {} times in a row, it is "
                + "not created again until its class is redefined, the container is reloaded with a copy of its "
                + "current config.",
                jerseyContainer, MAX_FAILED_RECREATIONS);
    }

    private Class<?> resolveClass(String name) throws ClassNotFoundException {
        return Class.forName(name, true, appClassLoader);
    }

}

