/*
 * Copyright 2013-2023 the HotswapAgent authors.
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
package org.hotswap.agent.plugin.jetty;

import org.hotswap.agent.annotation.Init;
import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.annotation.Plugin;
import org.hotswap.agent.config.PluginConfiguration;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtMethod;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;
import org.hotswap.agent.util.ReflectionHelper;

import java.lang.reflect.Array;
import java.net.URL;

/**
 * Jetty servlet container support.
 *
 * <p/>
 * <p>Plugin</p><ul>
 *     <li>Configure webapp classloader before first servlet is loaded</li>
 *      <li>webappDir configuration property</li>
 *     <li>extraClasspath configuration property (handled by WatchResourcesPlugin)</li>
 *     <li>watchResources configuration property (handled by WatchResourcesPlugin)</li>
 * </ul>
 *
 * @author Jiri Bubnik
 */
@Plugin(name = "Jetty", description = "Jetty plugin.",
        testedVersions = {"6.1.26", "7.6.14", "8.1.14", "9.1.2"},
        expectedVersions = {"4x", "5x", "6x", "7x", "8x", "9x"}
)
public class JettyPlugin {
    private static AgentLogger LOGGER = AgentLogger.getLogger(JettyPlugin.class);

    @Init
    PluginConfiguration pluginConfiguration;

    /**
     * Plugin initialization step needs to be fine tuned. It can be intialized only AFTER the classloader
     * already knows about hotswap-agent.properties file (i.e. after webapp basic path is added to the classloader),
     * but BEFORE first servlet is initialized.
     *
     * WebXmlConfiguration seems to be good place which should work in most setups. The plugin is intialized before
     * web.xml file is processed - basic paths should be known, but nothing is processed yet.
     *
     * Application classloader is processed during plugin initialization. It means that other plugins triggered
     * on plugin init should fire as well - for jetty is important core watchResources plugin, which will handle
     * extraClassPath and watchResources configuration properties (jetty fortunately depends only on basic
     * URLClassLoader behaviour which is handled by that plugin).
     */
    @OnClassLoadEvent(classNameRegexp = "org.eclipse.jetty.webapp.WebXmlConfiguration")
    public static void patchWebXmlConfiguration(CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {

        try {
            // after application context initialized, but before processing started
            CtMethod doStart = ctClass.getDeclaredMethod("configure");

            // init the plugin
            String src = PluginManagerInvoker.buildInitializePlugin(JettyPlugin.class, "context.getClassLoader()");
            src += PluginManagerInvoker.buildCallPluginMethod("context.getClassLoader()", JettyPlugin.class,
                    "init", "context", "java.lang.Object");

            doStart.insertBefore(src);
        } catch (NotFoundException e) {
            LOGGER.warning("org.eclipse.jetty.webapp.WebAppContext does not contain startContext method. Jetty plugin will be disabled.\n" +
                    "*** This is Ok, Jetty plugin handles only special properties ***");
            return;
        }
    }

    // same as above for older jetty versions
    @OnClassLoadEvent(classNameRegexp = "org.mortbay.jetty.webapp.WebXmlConfiguration")
    public static void patchWebXmlConfiguration6x(CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {
        try {
            // after application context initialized, but before processing started
            CtMethod doStart = ctClass.getDeclaredMethod("configureWebApp");

            // init the plugin
            String src = PluginManagerInvoker.buildInitializePlugin(JettyPlugin.class, "getWebAppContext().getClassLoader()");
            src += PluginManagerInvoker.buildCallPluginMethod("getWebAppContext().getClassLoader()", JettyPlugin.class,
                    "init", "getWebAppContext()", "java.lang.Object");

            doStart.insertBefore(src);
        } catch (NotFoundException e) {
            LOGGER.warning("org.mortbay.jetty.webapp.WebXmlConfiguration does not contain startContext method. Jetty plugin will be disabled.\n" +
                    "*** This is Ok, Jetty plugin handles only special properties ***");
            return;
        }
    }

    /**
     * Before app context is stopped, clean the classloader (and associated plugin instance).
     */
    @OnClassLoadEvent(classNameRegexp = "(org.mortbay.jetty.webapp.WebAppContext)|(org.eclipse.jetty.webapp.WebAppContext)")
    public static void patchContextHandler6x(CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {


        try {
            ctClass.getDeclaredMethod("doStop").insertBefore(
                    PluginManagerInvoker.buildCallCloseClassLoader("getClassLoader()")
            );
        } catch (NotFoundException e) {
            LOGGER.debug("org.eclipse.jetty.webapp.WebAppContext does not contain doStop() method. Hotswap agent will not be able to free Jetty plugin resources.");
        }
    }

    /**
     * Actual plugin initialization write plugin info and handle webappDir property.
     *
     * If the webappDir property is set, call:
     * <pre>
     *      contextHandler.setBaseResource(new ResourceCollection(
     *          new FileResource(webappDir),
     *          contextHandler.getBaseResource()
     *      ));
     *</pre>
     * @param contextHandler instance of ContextHandler - main jetty class for webapp.
     */
    public void init(Object contextHandler) {

        // resolve jetty classes
        ClassLoader loader = contextHandler.getClass().getClassLoader();
        Class contextHandlerClass;
        Class resourceClass;
        Class fileResourceClass;
        Class resourceCollectionClass;

        try {
            contextHandlerClass = loader.loadClass("org.eclipse.jetty.server.handler.ContextHandler");
            resourceClass = loader.loadClass("org.eclipse.jetty.util.resource.Resource");
            fileResourceClass = loader.loadClass("org.eclipse.jetty.util.resource.FileResource");
            resourceCollectionClass = loader.loadClass("org.eclipse.jetty.util.resource.ResourceCollection");
        } catch (ClassNotFoundException e) {
            try {
                contextHandlerClass = loader.loadClass("org.mortbay.jetty.handler.ContextHandler");
                resourceClass = loader.loadClass("org.mortbay.resource.Resource");
                fileResourceClass = loader.loadClass("org.mortbay.resource.FileResource");
                resourceCollectionClass = loader.loadClass("org.mortbay.resource.ResourceCollection");
            } catch (ClassNotFoundException e1) {
                LOGGER.error("Unable to load ContextHandler class from contextHandler {} classloader", contextHandler);
                return;
            }
        }

        String version;
        // find version in Servlet package (http://docs.codehaus.org/display/JETTY/How+to+find+out+the+version+of+Jetty)
        try {
            Object server = ReflectionHelper.invoke(contextHandler, contextHandlerClass, "getServer", new Class[]{});
            version = server.getClass().getPackage().getImplementationVersion();
        } catch (Exception e) {
            version = "unknown [" + e.getMessage() + "]";
        }

        // set different resource
        URL[] webappDir = pluginConfiguration.getWebappDir();
        if (webappDir.length > 0) {
            try {
                Object originalBaseResource = ReflectionHelper.invoke(contextHandler, contextHandlerClass,
                        "getBaseResource", new Class[] {});
                Object resourceArray = Array.newInstance(resourceClass, webappDir.length + 1);
                for (int i = 0; i < webappDir.length; i++) {
                    LOGGER.debug("Watching 'webappDir' for changes: {} in Jetty webapp: {}", webappDir[i],
                            contextHandler);
                    Object fileResource = fileResourceClass.getDeclaredConstructor(URL.class).newInstance(webappDir[i]);
                    Array.set(resourceArray, i, fileResource);
                }
                Array.set(resourceArray, webappDir.length, originalBaseResource);
                Object resourceCollection = resourceCollectionClass.getDeclaredConstructor(resourceArray.getClass())
                        .newInstance(resourceArray);

                ReflectionHelper.invoke(contextHandler, contextHandlerClass, "setBaseResource",
                        new Class[] { resourceClass }, resourceCollection);
            } catch (Exception e) {
                LOGGER.error(
                        "Unable to set webappDir to directory '{}' for Jetty webapp {}. This configuration will not work.",
                        e, webappDir[0], contextHandler);
            }
        }

        LOGGER.info("Jetty plugin initialized - Jetty version '{}', context {}", version, contextHandler);
    }
}
