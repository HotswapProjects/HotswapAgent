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
package org.hotswap.agent.plugin.tomcat;

import org.hotswap.agent.annotation.OnClassLoadEvent;
import org.hotswap.agent.javassist.CannotCompileException;
import org.hotswap.agent.javassist.ClassPool;
import org.hotswap.agent.javassist.CtClass;
import org.hotswap.agent.javassist.CtConstructor;
import org.hotswap.agent.javassist.CtPrimitiveType;
import org.hotswap.agent.javassist.NotFoundException;
import org.hotswap.agent.logging.AgentLogger;
import org.hotswap.agent.util.PluginManagerInvoker;

/**
 * Created by bubnik on 9.6.2014.
 */
public class WebappLoaderTransformer {
    private static AgentLogger LOGGER = AgentLogger.getLogger(WebappLoaderTransformer.class);

    private static boolean webappClassLoaderPatched = false;

    /**
     * Init the plugin from start method.
     *
     * Hook into main init method of the loader. Init method name and resources type changes between
     * Tomcat versions.
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.catalina.loader.WebappLoader")
    public static void patchWebappLoader(CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {

        // handled by various Tomcat versions
        boolean startHandled = false;
        boolean stopHandled = false;

        // tomcat 8x
        if (!startHandled) {
            try {
                // fail for older tomcat version, which does not contain context
                ctClass.getDeclaredMethod("getContext");
                ctClass.getDeclaredMethod("startInternal").insertAfter(
                        TomcatPlugin.class.getName() + ".init(getClassLoader(), getContext().getResources());"
                );
                startHandled = true;
            } catch (NotFoundException e) {
                LOGGER.trace("WebappLoader does not contain getContext() method, trying older Tomcat version.");
            }
        }

        // tomcat 7x
        if (!startHandled) {
            try {
                ctClass.getDeclaredMethod("startInternal").insertAfter(
                        TomcatPlugin.class.getName() + ".init(getClassLoader(), container.getResources());"
                );
                startHandled = true;
            } catch (NotFoundException e) {
                LOGGER.trace("WebappLoader does not contain startInternal() method, trying older Tomcat version.");
            }
        }

        // tomcat 6x
        if (!startHandled) {
            try {
                ctClass.getDeclaredMethod("start").insertAfter(
                        TomcatPlugin.class.getName() + ".init(getClassLoader(), container.getResources());"
                );
                startHandled = true;
            } catch (NotFoundException e) {
            }
        }


        if (!startHandled) {
            LOGGER.warning("org.apache.catalina.loader.WebappLoader does not contain neither start nor startInternal method. Tomcat plugin will be disabled.\n" +
                    "*** Some properties (extraClasspath, watchResources) will NOT be supported on Tomcat level. They might be handled by another plugin though. ***");
        }

        // tomcat 7x,8x
        if (!stopHandled) {
            try {
                ctClass.getDeclaredMethod("stopInternal").insertBefore(
                        PluginManagerInvoker.buildCallCloseClassLoader("getClassLoader()") +
                        TomcatPlugin.class.getName() + ".close(getClassLoader());"
                );
                stopHandled = true;
            } catch (NotFoundException e) {
                LOGGER.debug("org.apache.catalina.core.StandardContext does not contain stopInternal() method, trying older stop() method.");
            }

        }

        // tomcat 6x
        // @Deprecated
        if (!stopHandled) {
            try {
                ctClass.getDeclaredMethod("stop").insertBefore(
                        PluginManagerInvoker.buildCallCloseClassLoader("getClassLoader()") +
                        TomcatPlugin.class.getName() + ".close(getClassLoader());"
                );
                stopHandled = true;
            } catch (NotFoundException e) {
                LOGGER.debug("org.apache.catalina.core.StandardContext does not contain neither stop() nor stopInternal() method. Hotswap agent will not be able to free Tomcat plugin resources.");
            }

        }

    }

    /**
     * Resource lookup for Tomcat 8x.
     *
     * Before the resource is handled by Tomcat, try to get extraResource handled by the plugin.
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.catalina.webresources.StandardRoot")
    public static void patchStandardRoot(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {
        CtClass ctFileResource = classPool.get("org.apache.catalina.webresources.FileResource");
        CtConstructor ctFileResourceConstructor = ctFileResource.getConstructors()[0];
        CtClass[] constructorTypes = ctFileResourceConstructor.getParameterTypes();
        String constrParams;

        if (constructorTypes.length == 4)
            constrParams = "this, path, file, false";
        else if (constructorTypes.length == 5)
            constrParams = "this, path, file, false, null";
        else {
            LOGGER.warning("org.apache.catalina.webresources.FileResource unknown constructor. Tomcat plugin will not work properly.");
            return;
        }

        try {
            ctClass.getDeclaredMethod("getResourceInternal", new CtClass[]{classPool.get(String.class.getName()), CtPrimitiveType.booleanType}).insertBefore(
                    "java.io.File file = " + TomcatPlugin.class.getName() + ".getExtraResourceFile(this, path);" +
                            "if (file != null) return new org.apache.catalina.webresources.FileResource(" + constrParams + ");"
            );
        } catch (NotFoundException e) {
            LOGGER.warning("org.apache.catalina.webresources.StandardRoot does not contain getResourceInternal method. Tomcat plugin will not work properly.");
            return;
        }

        // if getResources() should contain extraClasspath, expand the original returned array and prepend extraClasspath result
        try {
            ctClass.getDeclaredMethod("getResources", new CtClass[]{classPool.get(String.class.getName()), CtPrimitiveType.booleanType}).insertAfter(
                    "java.io.File file = " + TomcatPlugin.class.getName() + ".getExtraResourceFile(this, path);" +
                    "if (file != null) {" +
                            "org.apache.catalina.WebResource[] ret = new org.apache.catalina.WebResource[$_.length + 1];" +
                            "ret[0] = new org.apache.catalina.webresources.FileResource(" + constrParams + ");" +
                            "java.lang.System.arraycopy($_, 0, ret, 1, $_.length);" +
                            "return ret;" +
                    "} else {return $_;}"
            );
        } catch (NotFoundException e) {
            LOGGER.warning("org.apache.catalina.webresources.StandardRoot does not contain getResourceInternal method. Tomcat plugin will not work properly.");
            return;
        }

    }

    /**
     * Resource lookup for Tomcat 6x, 7x.
     *
     * Before the resource is handled by Tomcat, try to get extraResource handled by the plugin.
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.naming.resources.ProxyDirContext")
    public static void patchProxyDirContext(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {

        try {
            ctClass.getDeclaredMethod("lookup", new CtClass[] {classPool.get(String.class.getName())}).insertBefore(
                    "java.io.InputStream is = " + TomcatPlugin.class.getName() + ".getExtraResource(this, name);" +
                            "if (is != null) return new org.apache.naming.resources.Resource(is);"
            );

            ctClass.getDeclaredMethod("getAttributes", new CtClass[] {classPool.get(String.class.getName())}).insertBefore(
                    "long length = " + TomcatPlugin.class.getName() + ".getExtraResourceLength(this, name);" +
                            "if (length > 0) {" +
                            "  org.apache.naming.resources.ResourceAttributes a = new org.apache.naming.resources.ResourceAttributes();" +
                            "  a.setContentLength(length); return a;" +
                            "}"

            );

        } catch (NotFoundException e) {
            LOGGER.warning("org.apache.naming.resources.ProxyDirContext does not contain lookup method. Tomcat plugin will not work properly.");
            return;
        }

    }

    /**
     * Disable loader caches - Tomcat 7x
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.catalina.core.StandardContext")
    public static void patchStandardContext(ClassPool classPool, CtClass ctClass) throws NotFoundException, CannotCompileException, ClassNotFoundException {
        try {
            // force disable caching
            ctClass.getDeclaredMethod("isCachingAllowed").setBody("return false;");
        } catch (NotFoundException e) {
            LOGGER.debug("org.apache.catalina.core.StandardContext does not contain isCachingAllowed() method (not 7x version).");
        }

        // Tomcat version
        // this.getServletContext().getServerInfo()
    }

    /**
     * Brute force clear caches before any resource is loaded.
     * WebappClassLoader - Tomcat 7x
     * WebappClassLoaderBase - Tomcat 8x
     */
    @OnClassLoadEvent(classNameRegexp = "(org.apache.catalina.loader.WebappClassLoader)|(org.apache.catalina.loader.WebappClassLoaderBase)")
    public static void patchWebappClassLoader(ClassPool classPool,CtClass ctClass) throws CannotCompileException, NotFoundException {
        if (!webappClassLoaderPatched) {
            try {
                // clear classloader cache
                ctClass.getDeclaredMethod("getResource", new CtClass[]{classPool.get("java.lang.String")}).insertBefore(
                        "resourceEntries.clear();"
                );
                ctClass.getDeclaredMethod("getResourceAsStream", new CtClass[]{classPool.get("java.lang.String")}).insertBefore(
                        "resourceEntries.clear();"
                );
                webappClassLoaderPatched = true;
            } catch (NotFoundException e) {
                LOGGER.trace("WebappClassLoader does not contain getResource(), getResourceAsStream method.");
            }
        }
    }

    /**
     * Make sure development mode is true so that JSP compilation will always be triggered, especially for embedded
     * Tomcat running in Spring Boot.
     */
    @OnClassLoadEvent(classNameRegexp = "org.apache.catalina.core.StandardWrapper")
    public static void patchStandardWrapper(ClassPool classPool, CtClass ctClass) {
        try {
            ctClass.getDeclaredMethod("getInitParameter", new CtClass[]{classPool.get("java.lang.String")}).insertBefore(
                    "if ($1.equals(\"development\")) return \"true\";"
            );
        } catch (CannotCompileException | NotFoundException e) {
            LOGGER.debug("org.apache.catalina.core.StandardWrapper does not contain getInitParameter method.");
        }
    }

}
